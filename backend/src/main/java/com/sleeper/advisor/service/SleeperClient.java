package com.sleeper.advisor.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SleeperClient {

    private final WebClient webClient;

    private final Cache<String, Object> playersMapCache;
    private final Cache<String, Object> userCache;
    private final Cache<String, Object> leaguesCache;
    private final Cache<String, Object> membersCache;

    public SleeperClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.sleeper.app")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.playersMapCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(24)).build();
        this.userCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build();
        this.leaguesCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build();
        this.membersCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build();
    }

    public Map<String, Object> getUserId(String username) {
        String cacheKey = "user:" + username;
        Object cached = userCache.getIfPresent(cacheKey);
        if (cached != null) return (Map<String, Object>) cached;
        Map<String, Object> user = webClient.get()
                .uri("/v1/user/{username}", username)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        userCache.put(cacheKey, user);
        return user;
    }

    public Object getUserLeagues(String userId, String season) {
        String cacheKey = "leagues:" + userId + ":" + season;
        Object cached = leaguesCache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        Object leagues = webClient.get()
                .uri("/v1/user/{userId}/leagues/nfl/{season}", userId, season)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        leaguesCache.put(cacheKey, leagues);
        return leagues;
    }

    public Object getLeagueMembers(String leagueId) {
        String cacheKey = "members:" + leagueId;
        Object cached = membersCache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        Object members = webClient.get()
                .uri("/v1/league/{leagueId}/users", leagueId)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        membersCache.put(cacheKey, members);
        return members;
    }

    public Object getLeagueRosters(String leagueId) {
        return webClient.get()
                .uri("/v1/league/{leagueId}/rosters", leagueId)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }

    public Object getMatchups(String leagueId, int week) {
        return webClient.get()
                .uri("/v1/league/{leagueId}/matchups/{week}", leagueId, week)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }

    public Object getTradedPicks(String leagueId) {
        return webClient.get()
                .uri("/v1/league/{leagueId}/traded_picks", leagueId)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }

    public Map<String, Object> getPlayersMap() {
        String cacheKey = "playersMap";
        Object cached = playersMapCache.getIfPresent(cacheKey);
        if (cached != null) return (Map<String, Object>) cached;
        Map<String, Object> players = webClient.get()
                .uri("/v1/players/nfl")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        playersMapCache.put(cacheKey, players);
        return players;
    }
}
