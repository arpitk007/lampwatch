package com.uptime.monitor.common;

import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.users.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window request budgets in Redis: per user when signed in, per IP otherwise, plus a tighter
 * budget for sign-ups. Runs after Spring Security, so it knows who is calling. If Redis is unreachable
 * it lets requests through: sessions already need Redis, and a limiter outage shouldn't become an app outage.
 */
@Component
public class RateLimiter implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final UptimeProperties.RateLimit limits;

    public RateLimiter(StringRedisTemplate redis, UptimeProperties props) {
        this.redis = redis;
        this.limits = props.rateLimit();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!limits.enabled()) return true;
        String ip = request.getRemoteAddr();

        if ("POST".equals(request.getMethod()) && "/api/auth/signup".equals(request.getRequestURI())) {
            take("signup:ip:" + ip, limits.signupsPerIpPerHour(), HOUR, "Too many new accounts from this network. Try again later.");
        }
        Long userId = currentUserId();
        if (userId != null) {
            take("user:" + userId, limits.perUserPerMinute(), MINUTE, "Too many requests. Slow down and try again in a minute.");
        } else {
            take("ip:" + ip, limits.perIpPerMinute(), MINUTE, "Too many requests from this network. Try again in a minute.");
        }
        return true;
    }

    /** Counts one request in the current window and throws once the budget is spent. */
    private void take(String subject, int max, Duration window, String message) {
        long windowIndex = Instant.now().getEpochSecond() / window.toSeconds();
        String key = "rl:" + subject + ":" + windowIndex;
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, window);
        } catch (DataAccessException e) {
            log.warn("Rate limiter skipped, Redis unavailable: {}", e.getMessage());
            return;
        }
        if (count != null && count > max) {
            long retryAfter = window.toSeconds() - Instant.now().getEpochSecond() % window.toSeconds();
            throw new TooManyRequestsException(message, retryAfter);
        }
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser user ? user.id() : null;
    }
}
