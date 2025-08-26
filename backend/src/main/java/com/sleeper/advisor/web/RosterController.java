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
    private final com.sleeper.advisor.service.ProjectionStore projectionStore;

    public RosterController(SleeperClient sleeperClient, com.sleeper.advisor.service.ProjectionStore projectionStore) {
        this.sleeperClient = sleeperClient;
        this.projectionStore = projectionStore;
    }

    @GetMapping("/{leagueId}/roster/{userId}")
    public Roster getRoster(
            @PathVariable String leagueId,
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") String weekStr) {

        int week = 1;
        try { week = Integer.parseInt(weekStr); } catch (NumberFormatException ignored) {}

        if (week < 1) {
            throw new IllegalArgumentException("Week must be >= 1.");
        }

        log.info("GET /api/league/{}/roster/{}?week={}", leagueId, userId, week);

        // 1. Fetch all rosters for the league
        List<Map<String, Object>> rosters = sleeperClient.getLeagueRosters(leagueId);

        // 2. Find user's roster (owner_id == userId)
        Map<String, Object> myRoster = rosters.stream()
                .filter(r -> r.containsKey("owner_id") && Objects.equals(r.get("owner_id"), userId))
                .findFirst()
                .orElse(Collections.emptyMap());

        // Parse starters, all players, taxi, bench
        List<String> startersIds = myRoster.get("starters") instanceof List<?> ? 
            ((List<?>) myRoster.get("starters")).stream().map(Object::toString).collect(Collectors.toList()) : Collections.emptyList();
        List<String> allPlayerIds = myRoster.get("players") instanceof List<?> ? 
            ((List<?>) myRoster.get("players")).stream().map(Object::toString).collect(Collectors.toList()) : Collections.emptyList();
        List<String> taxiIds = myRoster.get("taxi") instanceof List<?> ?
            ((List<?>) myRoster.get("taxi")).stream().map(Object::toString).collect(Collectors.toList()) : Collections.emptyList();

        Set<String> benchSet = new HashSet<>(allPlayerIds);
        benchSet.removeAll(startersIds);
        benchSet.removeAll(taxiIds);
        List<String> benchIds = new ArrayList<>(benchSet);

        // 3. playersMap lookup and mapping
        Map<String, Map<String, Object>> playersMap = sleeperClient.getPlayersMap();
        List<Player> starters = startersIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Player> bench = benchIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Player> taxi = taxiIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4. picks
        List<Map<String, Object>> picksRaw = sleeperClient.getTradedPicks(leagueId);
        List<DraftPick> picks = picksRaw.stream()
                .filter(pick -> Objects.equals(pick.get("owner_id"), userId))
                .map(pick -> new DraftPick(
                        intOrElse(pick.get("season"), 0),
                        intOrElse(pick.get("round"), 0),
                        pick.get("previous_owner_id") == null ? null : pick.get("previous_owner_id").toString(),
                        pick.get("owner_id") == null ? null : pick.get("owner_id").toString(),
                        !Objects.equals(pick.get("owner_id"), pick.get("previous_owner_id")) // traded if owner ≠ original
                ))
                .collect(Collectors.toList());

        // 5. Return new Roster
        return new Roster(starters, bench, taxi, picks);
    }

    private Player playerFromMap(String id, Map<String, Object> player) {
        if (player == null) return null;
        String name = player.getOrDefault("full_name", "").toString();
        String pos = player.getOrDefault("position", "").toString();
        String team = player.getOrDefault("team", "").toString();
        Double proj = projectionStore.projectedPoints(id, pos);
        Double value = projectionStore.marketValue(id, pos);
        return new Player(id, name, pos, team, proj, value);
    }

    private int intOrElse(Object obj, int defVal) {
        if (obj == null) return defVal;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception ignored) {}
        return defVal;
    }
}
