package com.sleeper.advisor.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    var manager = new SimpleCacheManager();

    var FIVE_MIN = Duration.ofMinutes(5);
    var TWENTY_FOUR_H = Duration.ofHours(24);

    Caffeine<Object,Object> shortBuilder = Caffeine.newBuilder()
        .expireAfterWrite(FIVE_MIN)
        .maximumSize(5_000);

    Caffeine<Object,Object> longBuilder = Caffeine.newBuilder()
        .expireAfterWrite(TWENTY_FOUR_H)
        .maximumSize(20_000);

    var caches = List.of(
      new CaffeineCache("playersMap", longBuilder.build()),
      new CaffeineCache("userByUsername", shortBuilder.build()),
      new CaffeineCache("userLeagues", shortBuilder.build()),
      new CaffeineCache("leagueUsers", shortBuilder.build()),
      new CaffeineCache("leagueRosters", shortBuilder.build()),
      new CaffeineCache("leagueMatchups", shortBuilder.build()),
      new CaffeineCache("tradedPicks", shortBuilder.build()),
      new CaffeineCache("nflState", shortBuilder.build()),
      new CaffeineCache("weekProjections", shortBuilder.build()),
      new CaffeineCache("espnInjuries", shortBuilder.build()),
      new CaffeineCache("espnNews", shortBuilder.build())
    );

    manager.setCaches(caches);
    return manager;
  }
}
