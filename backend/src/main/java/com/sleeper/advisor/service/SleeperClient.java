package com.sleeper.advisor.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SleeperClient {

    private static final Logger log = LoggerFactory.getLogger(SleeperClient.class);

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
        String endpoint = "/v1/user/" + username;
        log.info("GET Sleeper: {}", endpoint);
        Map<String, Object> user = webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (user != null) {
            userCache.put(cacheKey, user);
            log.debug("User response: {}", user.keySet());
        } else {
            log.debug("User not found for username={}", username);
        }
        return user;
    }

    public Object getUserLeagues(String userId, String season) {
        String cacheKey = "leagues:" + userId + ":" + season;
        Object cached = leaguesCache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        String endpoint = "/v1/user/" + userId + "/leagues/nfl/" + season;
        log.info("GET Sleeper: {}", endpoint);
        Object leagues = webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        leaguesCache.put(cacheKey, leagues);
        // For known contract, leagues is List
        if (leagues instanceof java.util.List l) {
            log.debug("Fetched {} leagues for user {}", l.size(), userId);
        }
        return leagues;
    }

    public Object getLeagueMembers(String leagueId) {
        String cacheKey = "members:" + leagueId;
        Object cached = membersCache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        String endpoint = "/v1/league/" + leagueId + "/users";
        log.info("GET Sleeper: {}", endpoint);
        Object members = webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        membersCache.put(cacheKey, members);
        if (members instanceof java.util.List l) {
            log.debug("Fetched {} league members for league {}", l.size(), leagueId);
        }
        return members;
    }

    public Object getLeagueRosters(String leagueId) {
        String endpoint = "/v1/league/" + leagueId + "/rosters";
        log.info("GET Sleeper: {}", endpoint);
        Object rosters = webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        if (rosters instanceof java.util.List l) {
            log.debug("Fetched {} rosters for league {}", l.size(), leagueId);
        }
        return rosters;
    }

    public Object getMatchups(String leagueId, int week) {
        String endpoint = "/v1/league/" + leagueId + "/matchups/" + week;
        log.info("GET Sleeper: {}", endpoint);
        Object matchups = webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        if (matchups instanceof java.util.List l) {
            log.debug("Fetched {} matchups for league {} week {}", l.size(), leagueId, week);
        }
        return matchups;
    }

    public Object getTradedPicks(String leagueId) {
        String endpoint = "/v1/league/" + leagueId + "/traded_picks";
        log.info("GET Sleeper: {}", endpoint);
        Object picks = webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        if (picks instanceof java.util.List l) {
            log.debug("Fetched {} traded picks for league {}", l.size(), leagueId);
        }
        return picks;
    }

    public Map<String, Object> getPlayersMap() {
        String cacheKey = "playersMap";
        Object cached = playersMapCache.getIfPresent(cacheKey);
        if (cached != null) return (Map<String, Object>) cached;
        String endpoint = "/v1/players/nfl";
        log.info("GET Sleeper: {}", endpoint);
        Map<String, Object> players = webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        playersMapCache.put(cacheKey, players);
        log.debug("Fetched {} players", players != null ? players.size() : 0);
        return players;
    }
}
