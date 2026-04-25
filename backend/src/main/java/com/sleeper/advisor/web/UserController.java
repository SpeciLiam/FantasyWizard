package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import com.sleeper.advisor.service.YahooFantasyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final SleeperClient sleeperClient;
    private final YahooFantasyClient yahooFantasyClient;

    public UserController(SleeperClient sleeperClient, YahooFantasyClient yahooFantasyClient) {
        this.sleeperClient = sleeperClient;
        this.yahooFantasyClient = yahooFantasyClient;
    }

    @GetMapping("/{username}/leagues")
    public Map<String, Object> getUserLeagues(
            @PathVariable String username,
            @RequestParam(required = false, defaultValue = "2025") String season,
            @RequestParam(required = false, defaultValue = "sleeper") String provider) {

        log.info("GET /api/user/{}/leagues?season={}&provider={}", username, season, provider);

        if ("yahoo".equalsIgnoreCase(provider)) {
            return Map.of("leagues", yahooFantasyClient.getUserLeagues(season));
        }

        String userId = sleeperClient.getUserId(username);
        if (userId == null) {
            throw new RuntimeException("Sleeper user not found: " + username);
        }

        List<Map<String, Object>> leaguesRaw = sleeperClient.getUserLeagues(userId, season);
        List<Map<String, Object>> leagues = new java.util.ArrayList<>();
        for (Map<String, Object> league : leaguesRaw) {
            Map<String, Object> leagueSummary = new HashMap<>();
            leagueSummary.put("leagueId", league.get("league_id"));
            leagueSummary.put("name", league.get("name"));
            leagues.add(leagueSummary);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("leagues", leagues);
        return response;
    }
}
