package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import com.sleeper.advisor.model.Player;
import com.sleeper.advisor.model.DraftPick;
import com.sleeper.advisor.model.Roster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/league")
public class RosterController {

    private static final Logger log = LoggerFactory.getLogger(RosterController.class);

    private final SleeperClient sleeperClient;

    public RosterController(SleeperClient sleeperClient) {
        this.sleeperClient = sleeperClient;
    }

    @GetMapping("/{leagueId}/roster/{userId}")
    public Roster getRoster(
            @PathVariable String leagueId,
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") String weekStr) {

        int week = 1;
        try { week = Integer.parseInt(weekStr); } catch (NumberFormatException ignored) {}

        log.info("GET /api/league/{}/roster/{}?week={}", leagueId, userId, week);

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

        List<Player> starters = mapPlayers(startersIds, playersMap);
        List<Player> bench = mapPlayers(benchIds, playersMap);
        List<Player> taxi = mapPlayers(taxiIds, playersMap);

        // Fetch picks
        List<Map<String, Object>> picksRaw = (List<Map<String, Object>>) sleeperClient.getTradedPicks(leagueId);
        List<DraftPick> picks = picksRaw.stream()
                .filter(pick -> Objects.equals(pick.get("owner_id"), userId))
                .map(pick -> new DraftPick(
                        pick.get("season") != null ? Integer.parseInt(pick.get("season").toString()) : 0,
                        pick.get("round") != null ? Integer.parseInt(pick.get("round").toString()) : 0,
                        pick.get("previous_owner_id") == null ? null : pick.get("previous_owner_id").toString(),
                        pick.get("owner_id") == null ? null : pick.get("owner_id").toString(),
                        pick.get("roster_id") != null
                ))
                .collect(Collectors.toList());

        return new Roster(starters, bench, taxi, picks);
    }

    private List<Player> mapPlayers(List<String> ids, Map<String, Object> playersMap) {
        if (ids == null) return Collections.emptyList();
        return ids.stream()
                .map(pid -> {
                    Map<String, Object> player = (Map<String, Object>) playersMap.get(pid);
                    if (player == null) return null;
                    String id = pid;
                    String name = player.getOrDefault("full_name", "").toString();
                    String pos = player.getOrDefault("position", "").toString();
                    String team = player.getOrDefault("team", "").toString();
                    Double proj = null;
                    if (player.get("fantasy_points") instanceof Number) proj = ((Number) player.get("fantasy_points")).doubleValue();
                    else if (player.get("fantasy_points") != null) {
                        try { proj = Double.parseDouble(player.get("fantasy_points").toString()); } catch (Exception ignored) {}
                    }
                    Double value = null;
                    if (player.get("value") instanceof Number) value = ((Number) player.get("value")).doubleValue();
                    else if (player.get("value") != null) {
                        try { value = Double.parseDouble(player.get("value").toString()); } catch (Exception ignored) {}
                    }
                    return new Player(id, name, pos, team, proj, value);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
