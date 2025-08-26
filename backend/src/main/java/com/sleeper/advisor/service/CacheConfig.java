package com.sleeper.advisor.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Caffeine<Object, Object> shortTtlCaffeine() {
        return Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.MINUTES)
                .maximumSize(10_000);
    }

    @Bean
    public Caffeine<Object, Object> playersTtlCaffeine() {
        return Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(1_000_000);
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> shortTtlCaffeine) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "leaguesCache",
                "membersCache",
                "rostersCache",
                "matchupsCache",
                "tradedPicksCache"
        );
        cacheManager.setCaffeine(shortTtlCaffeine);
        // Note: "playersMap" cache will use a different manager.
        return cacheManager;
    }

    @Bean
    public CacheManager playersCacheManager(Caffeine<Object, Object> playersTtlCaffeine) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("playersMap");
        cacheManager.setCaffeine(playersTtlCaffeine);
        return cacheManager;
    }
}
