package com.shipstream.common;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process cache-aside reads (replaces the Redis cache from the original design). Each instance
 * has its own cache; that's fine because the DB stays source of truth and every write path evicts
 * its own key before returning, so the only staleness window is the TTL on a key nobody wrote to.
 */
@Configuration
@EnableCaching
class CacheConfig {

    static final String SHIPMENTS_CACHE = "shipments";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SHIPMENTS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(10_000));
        return manager;
    }
}
