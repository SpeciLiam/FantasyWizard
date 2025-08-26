package com.sleeper.advisor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class SleeperClient {

    private static final Logger log = LoggerFactory.getLogger(SleeperClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String BASE_URL = "https://api.sleeper.app";

    public String getUserId(String username) {
        String endpoint = BASE_URL + "/v1/user/" + username;
        log.info("GET Sleeper: {}", endpoint);
        try {
            Map<String, Object> resp = restTemplate.getForObject(endpoint, Map.class);
            if (resp != null && resp.get("user_id") != null) {
                log.debug("Fetched user map, keys: {}", resp.keySet());
                return resp.get("user_id").toString();
            }
            log.debug("No user found for username={}", username);
            return null;
        } catch (HttpClientErrorException ex) {
            log.error("Error fetching user: {}", ex.getMessage());
            throw ex;
        }
    }

    @Cacheable(value = "leaguesCache", key = "#userId + '_' + #season")
    public List<Map<String,Object>> getUserLeagues(String userId, String season) {
        String endpoint = BASE_URL + "/v1/user/" + userId + "/leagues/nfl/" + season;
        log.info("GET Sleeper: {}", endpoint);
        try {
            Object resp = restTemplate.getForObject(endpoint, Object.class);
            if (resp instanceof List<?> list) {
                List<Map<String,Object>> out = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        out.add((Map<String,Object>)obj);
                    }
                }
                log.debug("Fetched {} leagues for user {}", out.size(), userId);
                return out;
            }
            log.debug("No leagues found for userId={}", userId);
            return Collections.emptyList();
        } catch (HttpClientErrorException ex) {
            log.error("Error fetching leagues: {}", ex.getMessage());
            throw ex;
        }
    }

    @Cacheable(value = "membersCache", key = "#leagueId")
    public List<Map<String,Object>> getLeagueMembers(String leagueId) {
        String endpoint = BASE_URL + "/v1/league/" + leagueId + "/users";
        log.info("GET Sleeper: {}", endpoint);
        try {
            Object resp = restTemplate.getForObject(endpoint, Object.class);
            if (resp instanceof List<?> list) {
                List<Map<String,Object>> out = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        out.add((Map<String,Object>)obj);
                    }
                }
                log.debug("Fetched {} league members for league {}", out.size(), leagueId);
                return out;
            }
            log.debug("No members found for leagueId={}", leagueId);
            return Collections.emptyList();
        } catch (HttpClientErrorException ex) {
            log.error("Error fetching league members: {}", ex.getMessage());
            throw ex;
        }
    }

    @Cacheable(value = "rostersCache", key = "#leagueId")
    public List<Map<String,Object>> getLeagueRosters(String leagueId) {
        String endpoint = BASE_URL + "/v1/league/" + leagueId + "/rosters";
        log.info("GET Sleeper: {}", endpoint);
        try {
            Object resp = restTemplate.getForObject(endpoint, Object.class);
            if (resp instanceof List<?> list) {
                List<Map<String,Object>> out = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        out.add((Map<String,Object>)obj);
                    }
                }
                log.debug("Fetched {} rosters for league {}", out.size(), leagueId);
                return out;
            }
            log.debug("No rosters found for leagueId={}", leagueId);
            return Collections.emptyList();
        } catch (HttpClientErrorException ex) {
            log.error("Error fetching league rosters: {}", ex.getMessage());
            throw ex;
        }
    }

    @Cacheable(value = "matchupsCache", key = "#leagueId + '_' + #week")
    public List<Map<String,Object>> getMatchups(String leagueId, int week) {
        String endpoint = BASE_URL + "/v1/league/" + leagueId + "/matchups/" + week;
        log.info("GET Sleeper: {}", endpoint);
        try {
            Object resp = restTemplate.getForObject(endpoint, Object.class);
            if (resp instanceof List<?> list) {
                List<Map<String,Object>> out = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        out.add((Map<String,Object>)obj);
                    }
                }
                log.debug("Fetched {} matchups for league {} week {}", out.size(), leagueId, week);
                return out;
            }
            log.debug("No matchups found for leagueId={}, week={}", leagueId, week);
            return Collections.emptyList();
        } catch (HttpClientErrorException ex) {
            log.error("Error fetching matchups: {}", ex.getMessage());
            throw ex;
        }
    }

    @Cacheable(value = "tradedPicksCache", key = "#leagueId")
    public List<Map<String,Object>> getTradedPicks(String leagueId) {
        String endpoint = BASE_URL + "/v1/league/" + leagueId + "/traded_picks";
        log.info("GET Sleeper: {}", endpoint);
        try {
            Object resp = restTemplate.getForObject(endpoint, Object.class);
            if (resp instanceof List<?> list) {
                List<Map<String,Object>> out = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        out.add((Map<String,Object>)obj);
                    }
                }
                log.debug("Fetched {} traded picks for league {}", out.size(), leagueId);
                return out;
            }
            log.debug("No traded picks found for leagueId={}", leagueId);
            return Collections.emptyList();
        } catch (HttpClientErrorException ex) {
            log.error("Error fetching traded picks: {}", ex.getMessage());
            throw ex;
        }
    }

    @Cacheable(value = "playersMap")
    public Map<String, Map<String,Object>> getPlayersMap() {
        String endpoint = BASE_URL + "/v1/players/nfl";
        log.info("GET Sleeper: {}", endpoint);
        try {
            Object resp = restTemplate.getForObject(endpoint, Object.class);
            if (resp instanceof Map<?,?> map) {
                Map<String, Map<String,Object>> out = new HashMap<>();
                for (Map.Entry<?,?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() instanceof Map) {
                        out.put((String)entry.getKey(), (Map<String,Object>)entry.getValue());
                    }
                }
                log.debug("Fetched {} players", out.size());
                return out;
            }
            log.debug("No players found");
            return Collections.emptyMap();
        } catch (HttpClientErrorException ex) {
            log.error("Error fetching players map: {}", ex.getMessage());
            throw ex;
        }
    }
}
