package com.sleeper.advisor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.sleeper.advisor.model.Player;
import com.sleeper.advisor.model.MatchupTeam;
import com.sleeper.advisor.model.MatchupPair;
import com.sleeper.advisor.model.MatchupsResponse;

@Service
public class MatchupService {

    private final SleeperClient sleeperClient;
    private final ProjectionStore projectionStore;

    @Autowired
    public MatchupService(SleeperClient sleeperClient, ProjectionStore projectionStore) {
        this.sleeperClient = sleeperClient;
        this.projectionStore = projectionStore;
    }

    public MatchupsResponse getMatchups(String leagueId, int week) {
        List<Map<String, Object>> usersList = sleeperClient.getLeagueMembers(leagueId);
        List<Map<String, Object>> rostersList = sleeperClient.getLeagueRosters(leagueId);
        Map<String, Map<String, Object>> playersMap = sleeperClient.getPlayersMap();
        List<Map<String, Object>> matchupsList = sleeperClient.getMatchups(leagueId, week);

        Map<Object, Map<String, Object>> rosterIdToUser = new HashMap<>();
        Map<String, Object> userIdToUser = new HashMap<>();
        for (Map<String, Object> u : usersList) {
            Object rosterId = u.get("roster_id");
            if (rosterId != null) rosterIdToUser.put(rosterId, u);
            String uid = getString(u, "user_id");
            if (uid != null) userIdToUser.put(uid, u);
        }

        Map<Object, Map<String, Object>> rosterIdToRoster = new HashMap<>();
        for (Map<String, Object> ro : rostersList) {
            Object rosterId = ro.get("roster_id");
            if (rosterId != null) rosterIdToRoster.put(rosterId, ro);
        }

        // Group by matchup_id (aggregate all teams for each matchup)
        Map<Object, List<Map<String, Object>>> matchupIdToTeams = new HashMap<>();
        for (Map<String, Object> team : matchupsList) {
            Object matchupId = team.get("matchup_id");
            if (matchupId == null) continue;
            matchupIdToTeams.computeIfAbsent(matchupId, k -> new ArrayList<>()).add(team);
        }

        // LOGGING
        System.out.println("[MatchupService] Sleeper returned " + matchupsList.size() + " team matchups, " + matchupIdToTeams.size() + " unique matchup_id groups.");

        List<MatchupPair> pairs = new ArrayList<>();
        for (Map.Entry<Object, List<Map<String, Object>>> entry : matchupIdToTeams.entrySet()) {
            List<Map<String, Object>> teams = entry.getValue();
            // Sort for deterministic order (ascending roster_id)
            teams.sort(Comparator.comparingInt(t -> {
                Object rid = t.get("roster_id");
                if (rid instanceof Number n) return n.intValue();
                try { return Integer.parseInt(rid.toString()); } catch (Exception e) { return 0; }
            }));

            MatchupTeam home = null;
            MatchupTeam away = null;
            if (teams.size() >= 1) home = buildMatchupTeam(teams.get(0), rosterIdToUser, rosterIdToRoster, playersMap);
            if (teams.size() >= 2) away = buildMatchupTeam(teams.get(1), rosterIdToUser, rosterIdToRoster, playersMap);
            if (home != null) pairs.add(new MatchupPair(home, away));
        }
        System.out.println("[MatchupService] Returning " + pairs.size() + " MatchupPair(s) to controller.");
        return new MatchupsResponse(leagueId, week, pairs);
    }

    private MatchupTeam buildMatchupTeam(
            Map<String, Object> teamEntry,
            Map<Object, Map<String, Object>> rosterIdToUser,
            Map<Object, Map<String, Object>> rosterIdToRoster,
            Map<String, Map<String, Object>> playersMap
    ) {
        Object rosterId = teamEntry.get("roster_id");
        Map<String, Object> userMap = rosterId != null ? rosterIdToUser.get(rosterId) : null;
        String userId = getString(userMap, "user_id");
        String displayName = getString(userMap, "display_name");
        String avatar = normalizeAvatar(getString(userMap, "avatar"));
        // ProjectedTotal: sum proj for all starters present in matchups object
        List<String> starterIds = objStringList(teamEntry.get("starters"));
        List<Player> starters = new ArrayList<>();
        double projTotal = 0.0;
        for (String pid : starterIds) {
            if (pid == null || "EMPTY".equals(pid)) continue;
            Map<String, Object> pm = playersMap.get(pid);
            String name = getString(pm, "full_name");
            String pos = getString(pm, "position");
            String team = getString(pm, "team");
            Double proj = projectionStore.projectedPoints(pid, pos);
            Double value = projectionStore.marketValue(pid, pos);
            starters.add(new Player(pid, name, pos, team, proj, value));
            projTotal += proj != null ? proj : 0.0;
        }
        projTotal = Math.round(projTotal * 10.0) / 10.0; // round to 1 decimal

        return new MatchupTeam(userId, displayName, avatar, projTotal, starters);
    }

    private static String normalizeAvatar(String hash) {
        if (hash == null || hash.isEmpty()) return null;
        // If avatar is already a full URL, return as-is.
        if (hash.startsWith("http")) return hash;
        return "https://sleepercdn.com/avatars/thumbs/" + hash;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

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
