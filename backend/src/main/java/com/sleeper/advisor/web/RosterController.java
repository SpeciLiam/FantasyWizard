package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import com.sleeper.advisor.service.ProjectionStore;
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
    private final ProjectionStore projectionStore;

    public RosterController(SleeperClient sleeperClient, ProjectionStore projectionStore) {
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
        if (week < 1) throw new IllegalArgumentException("Week must be >= 1.");

        log.info("GET /api/league/{}/roster/{}?week={}", leagueId, userId, week);

        List<Map<String, Object>> rosters = sleeperClient.getLeagueRosters(leagueId);
        Map<String, Object> myRoster = rosters.stream()
                .filter(r -> Objects.equals(r.get("owner_id"), userId))
                .findFirst()
                .orElse(Collections.emptyMap());

        List<String> startersIds = toStringList(myRoster.get("starters"));
        List<String> allPlayerIds = toStringList(myRoster.get("players"));
        List<String> taxiIds = toStringList(myRoster.get("taxi"));

        Set<String> benchSet = new LinkedHashSet<>(allPlayerIds);
        benchSet.removeAll(startersIds);
        benchSet.removeAll(taxiIds);
        List<String> benchIds = new ArrayList<>(benchSet);

        Map<String, Map<String, Object>> playersMap = sleeperClient.getPlayersMap();

        // Fetch real Sleeper projections for the requested week
        Map<String, Object> state = sleeperClient.getNflState();
        int season = state.get("season") instanceof Number n
                ? n.intValue()
                : (state.get("season") != null ? Integer.parseInt(state.get("season").toString()) : 2025);
        Map<String, Map<String, Object>> weekProjections = sleeperClient.getWeekProjections(season, week);

        List<Player> starters = startersIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid), weekProjections))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Player> bench = benchIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid), weekProjections))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Player> taxi = taxiIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid), weekProjections))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Picks: include the user's own draft_picks (default ownership) plus
        // any traded picks where current owner_id == userId
        List<DraftPick> picks = new ArrayList<>();
        Object ownDraftPicks = myRoster.get("draft_picks");
        if (ownDraftPicks instanceof List<?> ownList) {
            for (Object o : ownList) {
                if (o instanceof Map<?, ?> p) {
                    int s = intOrElse(p.get("season"), 0);
                    int r = intOrElse(p.get("round"), 0);
                    if (s == 0 || r == 0) continue;
                    picks.add(new DraftPick(s, r, userId, userId, false));
                }
            }
        }
        List<Map<String, Object>> traded = sleeperClient.getTradedPicks(leagueId);
        for (Map<String, Object> pick : traded) {
            if (Objects.equals(pick.get("owner_id"), userId)) {
                int s = intOrElse(pick.get("season"), 0);
                int r = intOrElse(pick.get("round"), 0);
                if (s == 0 || r == 0) continue;
                String orig = pick.get("previous_owner_id") != null ? pick.get("previous_owner_id").toString() : null;
                picks.add(new DraftPick(s, r, orig, userId, !Objects.equals(orig, userId)));
            }
        }

        return new Roster(starters, bench, taxi, picks);
    }

    private Player playerFromMap(String id, Map<String, Object> player, Map<String, Map<String, Object>> weekProjections) {
        if (player == null) return null;
        String name = String.valueOf(player.getOrDefault("full_name", id));
        String pos = String.valueOf(player.getOrDefault("position", ""));
        String team = String.valueOf(player.getOrDefault("team", ""));
        Double proj = projectionStore.projectedPoints(id, pos, weekProjections, "ppr");
        Double value = projectionStore.marketValue(id, pos);
        return new Player(id, name, pos, team, proj, value);
    }

    private static List<String> toStringList(Object o) {
        if (!(o instanceof List<?> list)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Object x : list) {
            if (x == null) continue;
            String s = x.toString();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s) && !"EMPTY".equalsIgnoreCase(s)) out.add(s);
        }
        return out;
    }

    private int intOrElse(Object obj, int defVal) {
        if (obj == null) return defVal;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception ignored) {}
        return defVal;
    }
}
