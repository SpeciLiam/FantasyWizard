package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import com.sleeper.advisor.service.YahooFantasyClient;
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
    private final YahooFantasyClient yahooFantasyClient;

    public RosterController(SleeperClient sleeperClient, ProjectionStore projectionStore, YahooFantasyClient yahooFantasyClient) {
        this.sleeperClient = sleeperClient;
        this.projectionStore = projectionStore;
        this.yahooFantasyClient = yahooFantasyClient;
    }

    @GetMapping("/{leagueId}/roster/{userId}")
    public Roster getRoster(
            @PathVariable String leagueId,
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") String weekStr,
            @RequestParam(required = false, defaultValue = "sleeper") String provider) {

        int week = 1;
        try { week = Integer.parseInt(weekStr); } catch (NumberFormatException ignored) {}
        if (week < 1) throw new IllegalArgumentException("Week must be >= 1.");

        log.info("GET /api/league/{}/roster/{}?week={}&provider={}", leagueId, userId, week, provider);

        if ("yahoo".equalsIgnoreCase(provider)) {
            return yahooFantasyClient.getRoster(leagueId, userId, week);
        }

        List<Map<String, Object>> rosters = sleeperClient.getLeagueRosters(leagueId);
        List<Map<String, Object>> users = sleeperClient.getLeagueMembers(leagueId);

        // roster_id -> user_id, user_id -> display_name
        Map<Integer, String> rosterIdToUserId = new HashMap<>();
        for (Map<String, Object> r : rosters) {
            Integer rid = intOrNull(r.get("roster_id"));
            String uid = r.get("owner_id") != null ? r.get("owner_id").toString() : null;
            if (rid != null && uid != null) rosterIdToUserId.put(rid, uid);
        }
        Map<String, String> userIdToName = new HashMap<>();
        for (Map<String, Object> u : users) {
            String uid = u.get("user_id") != null ? u.get("user_id").toString() : null;
            String name = u.get("display_name") != null ? u.get("display_name").toString() : null;
            if (uid != null && name != null) userIdToName.put(uid, name);
        }

        Map<String, Object> myRoster = rosters.stream()
                .filter(r -> Objects.equals(r.get("owner_id"), userId))
                .findFirst()
                .orElse(Collections.emptyMap());
        Integer myRosterId = intOrNull(myRoster.get("roster_id"));

        List<String> startersIds = toStringList(myRoster.get("starters"));
        List<String> allPlayerIds = toStringList(myRoster.get("players"));
        List<String> taxiIds = toStringList(myRoster.get("taxi"));

        Set<String> benchSet = new LinkedHashSet<>(allPlayerIds);
        benchSet.removeAll(startersIds);
        benchSet.removeAll(taxiIds);
        List<String> benchIds = new ArrayList<>(benchSet);

        Map<String, Map<String, Object>> playersMap = sleeperClient.getPlayersMap();

        Map<String, Object> state = sleeperClient.getNflState();
        int season = state.get("season") instanceof Number n
                ? n.intValue()
                : (state.get("season") != null ? Integer.parseInt(state.get("season").toString()) : 2025);
        Map<String, Map<String, Object>> weekProjections = sleeperClient.getWeekProjections(season, week);

        List<Player> starters = startersIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid), weekProjections))
                .filter(Objects::nonNull).collect(Collectors.toList());
        List<Player> bench = benchIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid), weekProjections))
                .filter(Objects::nonNull).collect(Collectors.toList());
        List<Player> taxi = taxiIds.stream()
                .map(pid -> playerFromMap(pid, playersMap.get(pid), weekProjections))
                .filter(Objects::nonNull).collect(Collectors.toList());

        // ---- Draft picks ----
        // Start with all of this roster's default picks (one per round per future season),
        // then apply traded_picks history to determine final ownership.
        List<Map<String, Object>> traded = sleeperClient.getTradedPicks(leagueId);

        // Map: (season, round, originalRosterId) -> currentOwnerRosterId
        // Default ownership: original roster owns it.
        // Apply trades by overwriting current owner.
        Map<String, Integer> finalOwners = new LinkedHashMap<>();
        Map<String, int[]> seasonRoundOriginal = new LinkedHashMap<>();

        // Seed from each roster's draft_picks (default ownership)
        for (Map<String, Object> r : rosters) {
            Integer originalRid = intOrNull(r.get("roster_id"));
            Object dp = r.get("draft_picks");
            if (originalRid == null || !(dp instanceof List<?> list)) continue;
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> p)) continue;
                int s = intOrElse(p.get("season"), 0);
                int rd = intOrElse(p.get("round"), 0);
                if (s == 0 || rd == 0) continue;
                String key = s + ":" + rd + ":" + originalRid;
                finalOwners.put(key, originalRid);
                seasonRoundOriginal.put(key, new int[]{s, rd, originalRid});
            }
        }
        // Apply traded picks: owner_id is current holder (roster_id), roster_id is original
        for (Map<String, Object> t : traded) {
            int s = intOrElse(t.get("season"), 0);
            int rd = intOrElse(t.get("round"), 0);
            Integer originalRid = intOrNull(t.get("roster_id"));
            Integer currentRid = intOrNull(t.get("owner_id"));
            if (s == 0 || rd == 0 || originalRid == null || currentRid == null) continue;
            String key = s + ":" + rd + ":" + originalRid;
            finalOwners.put(key, currentRid);
            seasonRoundOriginal.putIfAbsent(key, new int[]{s, rd, originalRid});
        }

        List<DraftPick> picks = new ArrayList<>();
        if (myRosterId != null) {
            for (var e : finalOwners.entrySet()) {
                if (!Objects.equals(e.getValue(), myRosterId)) continue;
                int[] sro = seasonRoundOriginal.get(e.getKey());
                if (sro == null) continue;
                int s = sro[0], rd = sro[1], originalRid = sro[2];
                String origUserId = rosterIdToUserId.get(originalRid);
                String origName = origUserId != null ? userIdToName.get(origUserId) : null;
                if (origName == null) origName = "Roster " + originalRid;
                boolean wasTraded = !Objects.equals(originalRid, myRosterId);
                picks.add(new DraftPick(s, rd, origUserId, origName, userId, wasTraded));
            }
            picks.sort(Comparator.<DraftPick>comparingInt(DraftPick::season).thenComparingInt(DraftPick::round));
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
        Integer v = intOrNull(obj);
        return v != null ? v : defVal;
    }

    private static Integer intOrNull(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception ignored) { return null; }
    }
}
