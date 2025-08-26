package com.sleeper.advisor.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${app.cache.playersTtlMinutes:1440}")
    private int playersTtlMinutes;

    @Value("${app.cache.leagueTtlMinutes:5}")
    private int leagueTtlMinutes;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "playersMap", "leaguesCache", "membersCache", "rostersCache", "matchupsCache", "tradedPicksCache"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(playersTtlMinutes, TimeUnit.MINUTES)
        );
        return cacheManager;
    }

    @Bean("leaguesCacheCaffeine")
    public com.github.benmanes.caffeine.cache.Cache<Object, Object> leaguesCacheCaffeine() {
        return Caffeine.newBuilder()
                .expireAfterWrite(leagueTtlMinutes, TimeUnit.MINUTES)
                .build();
    }
}
