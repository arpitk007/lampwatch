package com.uptime.monitor.statuspages;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside in Redis for public pages. A popular page shared during an outage is the
 * traffic spike; with a 30-second TTL the database sees at most two builds a minute per
 * page however many people refresh. Saving the page evicts it so changes show at once.
 */
@Component
public class StatusPageCache {

    static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final PublicStatusQuery query;

    public StatusPageCache(StringRedisTemplate redis, JsonMapper json, PublicStatusQuery query) {
        this.redis = redis;
        this.json = json;
        this.query = query;
    }

    public Optional<PublicStatusPage> get(String slug) {
        String key = key(slug);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return Optional.of(json.readValue(cached, PublicStatusPage.class));
        }
        Optional<PublicStatusPage> built = query.build(slug);
        built.ifPresent(page -> redis.opsForValue().set(key, json.writeValueAsString(page), TTL));
        return built;
    }

    public void evict(String slug) {
        redis.delete(key(slug));
    }

    private static String key(String slug) {
        return "status-page:" + slug;
    }
}
