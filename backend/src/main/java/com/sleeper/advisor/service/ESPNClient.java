package com.sleeper.advisor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ESPNClient {

    private static final Logger log = LoggerFactory.getLogger(ESPNClient.class);
    private final RestTemplate restTemplate = new RestTemplate();

    // ESPN team IDs for all 32 NFL teams
    private static final int[] NFL_TEAM_IDS = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 33, 34
    };

    // Returns a map of player name (lowercase) -> injury status string
    @Cacheable(cacheNames = "espnInjuries", key = "'all'")
    public Map<String, String> getAllInjuries() {
        Map<String, String> injuries = new HashMap<>();
        for (int teamId : NFL_TEAM_IDS) {
            try {
                String url = String.format(
                    "https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/teams/%d/injuries?limit=50",
                    teamId
                );
                Object resp = restTemplate.getForObject(url, Object.class);
                if (resp instanceof Map<?, ?> root) {
                    Object items = root.get("items");
                    if (items instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> entry) {
                                String status = extractStr(entry, "status");
                                Object athleteRef = entry.get("athlete");
                                if (athleteRef instanceof Map<?, ?> ath) {
                                    String name = extractStr(ath, "displayName");
                                    if (name != null && status != null) {
                                        injuries.put(name.toLowerCase(), status);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("ESPN injury fetch skipped for team {}: {}", teamId, e.getMessage());
            }
        }
        log.info("ESPN injuries loaded: {} players", injuries.size());
        return injuries;
    }

    // Returns recent NFL news items: [{headline, description, published}]
    @Cacheable(cacheNames = "espnNews", key = "'nfl'")
    public List<Map<String, String>> getNFLNews() {
        String url = "https://site.api.espn.com/apis/site/v2/sports/football/nfl/news?limit=20";
        List<Map<String, String>> out = new ArrayList<>();
        try {
            Object resp = restTemplate.getForObject(url, Object.class);
            if (resp instanceof Map<?, ?> root) {
                Object articles = root.get("articles");
                if (articles instanceof List<?> list) {
                    for (Object a : list) {
                        if (a instanceof Map<?, ?> art) {
                            Map<String, String> item = new HashMap<>();
                            item.put("headline", extractStr(art, "headline"));
                            item.put("description", extractStr(art, "description"));
                            item.put("published", extractStr(art, "published"));
                            out.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ESPN news fetch failed: {}", e.getMessage());
        }
        return out;
    }

    private static String extractStr(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
