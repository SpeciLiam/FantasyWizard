package com.sleeper.advisor.service;

import com.sleeper.advisor.model.Player;
import com.sleeper.advisor.model.Roster;
import com.sleeper.advisor.model.DraftPick;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RosterService {

    private final SleeperClient sleeperClient;
    private final ProjectionStore projectionStore;

    @Autowired
    public RosterService(SleeperClient sleeperClient, ProjectionStore projectionStore) {
        this.sleeperClient = sleeperClient;
        this.projectionStore = projectionStore;
    }

    public Roster buildRoster(String leagueId, String userId, int week) {
        // Load all necessary data from Sleeper (cached)
        List<Map<String, Object>> usersList = sleeperClient.getLeagueMembers(leagueId);
        List<Map<String, Object>> rostersList = sleeperClient.getLeagueRosters(leagueId);
        Map<String, Map<String, Object>> playersMap = sleeperClient.getPlayersMap();
        List<Map<String, Object>> tradedPicksList = sleeperClient.getTradedPicks(leagueId);

        // Map: roster_id -> owner_id, user_id, etc.
        Map<Object, String> rosterIdToUserId = new HashMap<>();
        for (Map<String, Object> user : usersList) {
            Object rosterId = user.get("roster_id");
            String id = user.get("user_id") != null ? user.get("user_id").toString() : null;
            if (rosterId != null && id != null) rosterIdToUserId.put(rosterId, id);
        }

        // Find roster for this userId
        Map<String, Object> myRosterObj = rostersList.stream()
                .filter(ro -> {
                    Object rid = ro.get("owner_id");
                    return rid != null && rid.toString().equals(userId);
                })
                .findFirst()
                .orElse(null);
        if (myRosterObj == null) return null;

        // Get starters/bench/taxi ids (list of player ids as strings)
        List<String> starterIds = objStringList(myRosterObj.get("starters"));
        List<String> allPlayerIds = objStringList(myRosterObj.get("players"));
        List<String> taxiIds = objStringList(myRosterObj.get("taxi"));

        // Sometimes API returns starters as nulls/"EMPTY" → skip these slots
        starterIds = starterIds.stream()
                .filter(pid -> pid != null && !"EMPTY".equalsIgnoreCase(pid))
                .collect(Collectors.toList());

        // Hydrate all Players for this roster
        List<Player> starters = buildPlayers(starterIds, playersMap);
        Set<String> startersSet = new HashSet<>(starterIds);

        // Bench = all players on roster minus starters (order does not matter)
        List<String> benchIds = allPlayerIds.stream()
                .filter(pid -> pid != null && !startersSet.contains(pid))
                .collect(Collectors.toList());
        List<Player> bench = buildPlayers(benchIds, playersMap);

        // Taxi = players in taxiIds (or empty)
        List<Player> taxi = buildPlayers(taxiIds, playersMap);

        // Attach Dynasty Picks if available
        List<DraftPick> picks = computeDynastyPicks(myRosterObj, tradedPicksList, userId);

        return new Roster(starters, bench, taxi, picks.isEmpty() ? null : picks);
    }

    private List<Player> buildPlayers(List<String> ids, Map<String, Map<String, Object>> playersMap) {
        List<Player> out = new ArrayList<>();
        for (String pid : ids) {
            Map<String, Object> meta = playersMap.get(pid);
            // Defensive mapping: skip empty ids or missing meta
            if (meta == null) continue;
            String name = getString(meta, "full_name");
            String pos = getString(meta, "position");
            String team = getString(meta, "team");
            Double proj = projectionStore.projectedPoints(pid, pos);
            Double value = projectionStore.marketValue(pid, pos);
            out.add(new Player(pid, name, pos, team, proj, value));
        }
        return out;
    }

    // Walk traded picks history to compute held picks for this user
    private List<DraftPick> computeDynastyPicks(Map<String,Object> myRoster, List<Map<String,Object>> tradedPicksList, String userId) {
        List<DraftPick> picks = new ArrayList<>();
        // Map: season, round → owner (default to roster's owner_id)
        // Collect all possible picks and only keep picks currently owned by userId
        Map<String, Map<Integer, String>> seasonRoundToOwner = new HashMap<>();
        // Apply default ownership from current rosters
        Object originalPicksObj = myRoster.get("draft_picks");
        if (originalPicksObj instanceof List<?>) {
            List<?> pickArr = (List<?>) originalPicksObj;
            for (Object pickObj : pickArr) {
                // Gather base picks info
                Map<String, Object> pickMap = (pickObj instanceof Map ? (Map<String, Object>)pickObj : null);
                if (pickMap == null) continue;
                int season = ((Number) pickMap.getOrDefault("season", 0)).intValue();
                int round = ((Number) pickMap.getOrDefault("round", 0)).intValue();
                seasonRoundToOwner.computeIfAbsent(userId, x -> new HashMap<>()).put(round, userId);
            }
        }
        // Apply traded picks history
        for (Map<String, Object> tr : tradedPicksList) {
            int season = ((Number) tr.getOrDefault("season", 0)).intValue();
            int round = ((Number) tr.getOrDefault("round", 0)).intValue();
            String newOwner = getString(tr, "owner_id"); // owner_id after trade
            String orig = getString(tr, "original_owner"); // original_owner
            if (newOwner == null || orig == null) continue;
            seasonRoundToOwner.computeIfAbsent(orig, x -> new HashMap<>()).put(round, newOwner);
        }
        // Flatten held picks for this userId
        for (var e : seasonRoundToOwner.entrySet()) {
            for (var roundEntry : e.getValue().entrySet()) {
                int round = roundEntry.getKey();
                String owner = roundEntry.getValue();
                if (userId.equals(owner)) {
                    picks.add(new DraftPick(
                        Integer.parseInt(e.getKey()), // season
                        round,
                        e.getKey(), // originalOwner userId
                        null,       // originalOwnerName (not resolved here)
                        userId,
                        false
                    ));
                }
            }
        }
        return picks;
    }

    // Utils
    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    // Defensive: handles Sleeper API returning null/"EMPTY"/etc. in arrays
    private static List<String> objStringList(Object o) {
        if (!(o instanceof List<?> list)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Object x : list) {
            if (x == null) continue;
            String s = x.toString();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) out.add(s);
        }
        return out;
    }
}
