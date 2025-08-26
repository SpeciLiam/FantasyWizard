package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/league")
public class RosterController {

    private final SleeperClient sleeperClient;

    public RosterController(SleeperClient sleeperClient) {
        this.sleeperClient = sleeperClient;
    }

    @GetMapping("/{leagueId}/roster/{userId}")
    public Map<String, Object> getRoster(
            @PathVariable String leagueId,
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") String weekStr) {

        int week = 1;
        try { week = Integer.parseInt(weekStr); } catch (NumberFormatException ignored) {}

        // Fetch rosters and players map from Sleeper
        List<Map<String, Object>> rosters = (List<Map<String, Object>>) sleeperClient.getLeagueRosters(leagueId);
        Map<String, Object> playersMap = sleeperClient.getPlayersMap();

        // Find the roster for the specified userId
        Map<String, Object> userRoster = rosters.stream()
                .filter(r -> r.containsKey("owner_id") && Objects.equals(r.get("owner_id"), userId))
                .findFirst()
                .orElse(Collections.emptyMap());

        List<String> startersIds = userRoster.get("starters") instanceof List ? (List<String>) userRoster.get("starters") : Collections.emptyList();
        List<String> benchIds = userRoster.get("players") instanceof List ? ((List<String>) userRoster.get("players"))
                .stream().filter(pid -> startersIds == null || !startersIds.contains(pid)).collect(Collectors.toList()) : Collections.emptyList();
        List<String> taxiIds = userRoster.get("taxi") instanceof List ? (List<String>) userRoster.get("taxi") : Collections.emptyList();

        List<Map<String, Object>> starters = mapPlayers(startersIds, playersMap);
        List<Map<String, Object>> bench = mapPlayers(benchIds, playersMap);
        List<Map<String, Object>> taxi = mapPlayers(taxiIds, playersMap);

        // Fetch picks
        List<Map<String, Object>> picksRaw = (List<Map<String, Object>>) sleeperClient.getTradedPicks(leagueId);
        List<Map<String, Object>> picks = picksRaw.stream()
                .filter(pick -> Objects.equals(pick.get("owner_id"), userId))
                .map(pick -> {
                    Map<String, Object> mapped = new HashMap<>();
                    mapped.put("season", pick.get("season"));
                    mapped.put("round", pick.get("round"));
                    mapped.put("originalOwner", pick.get("previous_owner_id"));
                    mapped.put("owner", pick.get("owner_id"));
                    mapped.put("traded", pick.get("roster_id") != null); // crude "traded" marker
                    return mapped;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("starters", starters);
        response.put("bench", bench);
        response.put("taxi", taxi);
        response.put("picks", picks);

        return response;
    }

    private List<Map<String, Object>> mapPlayers(List<String> ids, Map<String, Object> playersMap) {
        if (ids == null) return Collections.emptyList();
        return ids.stream()
                .map(pid -> {
                    Map<String, Object> player = (Map<String, Object>) playersMap.get(pid);
                    if (player == null) return null;
                    Map<String, Object> mapped = new HashMap<>();
                    mapped.put("id", pid);
                    mapped.put("name", player.getOrDefault("full_name", ""));
                    mapped.put("pos", player.getOrDefault("position", ""));
                    mapped.put("team", player.getOrDefault("team", ""));
                    mapped.put("proj", player.getOrDefault("fantasy_points", null)); // This field may not be present
                    mapped.put("value", player.getOrDefault("value", null)); // Not present, for compatibility
                    return mapped;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
