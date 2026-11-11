package com.uptime.monitor.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Counts failed sign-ins in Redis per email and per IP, so the limit holds across
 * every app instance and survives restarts. Counters expire after the window.
 */
@Component
public class LoginThrottle {

    static final int MAX_PER_EMAIL = 5;
    static final int MAX_PER_IP = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public LoginThrottle(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String email, String ip) {
        checkKey(emailKey(email), MAX_PER_EMAIL);
        checkKey(ipKey(ip), MAX_PER_IP);
    }

    public void recordFailure(String email, String ip) {
        increment(emailKey(email));
        increment(ipKey(ip));
    }

    public void reset(String email) {
        redis.delete(emailKey(email));
    }

    private void checkKey(String key, int max) {
        String count = redis.opsForValue().get(key);
        if (count != null && Integer.parseInt(count) >= max) {
            Long ttl = redis.getExpire(key);
            throw new TooManyAttemptsException(ttl == null || ttl < 0 ? WINDOW.toSeconds() : ttl);
        }
    }

    private void increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, WINDOW); // window starts at the first failure
        }
    }

    private static String emailKey(String email) {
        return "login:fail:email:" + email;
    }

    private static String ipKey(String ip) {
        return "login:fail:ip:" + ip;
    }
}
