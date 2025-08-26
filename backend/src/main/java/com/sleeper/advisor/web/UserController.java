package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
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

    public UserController(SleeperClient sleeperClient) {
        this.sleeperClient = sleeperClient;
    }

    @GetMapping("/{username}/leagues")
    public Map<String, Object> getUserLeagues(
            @PathVariable String username,
            @RequestParam(required = false, defaultValue = "2025") String season) {

        log.info("GET /api/user/{}/leagues?season={}", username, season);

        Map<String, Object> user = sleeperClient.getUserId(username);
        if (user == null) {
            throw new RuntimeException("Sleeper user not found: " + username);
        }
        String userId = user.get("user_id").toString();

        Object leaguesRaw = sleeperClient.getUserLeagues(userId, season);
        List<Map<String, Object>> leagues = new java.util.ArrayList<>();
        if (leaguesRaw instanceof List) {
            for (Object obj : (List<?>) leaguesRaw) {
                if (obj instanceof Map) {
                    Map<?,?> league = (Map<?,?>) obj;
                    Map<String, Object> leagueSummary = new HashMap<>();
                    leagueSummary.put("leagueId", league.get("league_id"));
                    leagueSummary.put("name", league.get("name"));
                    leagues.add(leagueSummary);
                }
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("leagues", leagues);
        return response;
    }
}
