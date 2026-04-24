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
        Map<String, Object> state = sleeperClient.getNflState();
        int season = state.get("season") instanceof Number n ? n.intValue() : 2025;
        Map<String, Map<String, Object>> weekProjections = sleeperClient.getWeekProjections(season, week);

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
            if (teams.size() >= 1) home = buildMatchupTeam(teams.get(0), rosterIdToUser, rosterIdToRoster, playersMap, weekProjections, "ppr");
            if (teams.size() >= 2) away = buildMatchupTeam(teams.get(1), rosterIdToUser, rosterIdToRoster, playersMap, weekProjections, "ppr");
            if (home != null) pairs.add(new MatchupPair(home, away));
        }
        System.out.println("[MatchupService] Returning " + pairs.size() + " MatchupPair(s) to controller.");
        return new MatchupsResponse(leagueId, week, pairs);
    }

    private MatchupTeam buildMatchupTeam(
            Map<String, Object> teamEntry,
            Map<Object, Map<String, Object>> rosterIdToUser,
            Map<Object, Map<String, Object>> rosterIdToRoster,
            Map<String, Map<String, Object>> playersMap,
            Map<String, Map<String, Object>> weekProjections,
            String format
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
            Double proj = projectionStore.projectedPoints(pid, pos, weekProjections, format);
            Double value = projectionStore.marketValue(pid, pos);
            starters.add(new Player(pid, name, pos, team, proj, value));
            projTotal += proj != null ? proj : 0.0;
        }
        projTotal = Math.round(projTotal * 10.0) / 10.0;

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

    // --- BEGIN projections API compatibility stubs ---

    public com.sleeper.advisor.model.ProjectionMapResponse getProjectionMapResponse(int season, int week, String format) {
        // TODO: Implement for projections API - currently returns empty map/meta for build fix.
        return new com.sleeper.advisor.model.ProjectionMapResponse(
            java.util.Collections.emptyMap(),
            new com.sleeper.advisor.model.ProjectionMapResponse.Meta(season, week, format, "FantasyNerds")
        );
    }

    public com.sleeper.advisor.model.LeagueProjectionsResponse getLeagueProjections(
            int season, int week, String leagueId, String format) {
        if (leagueId == null || leagueId.isBlank()) {
            throw new IllegalArgumentException("leagueId required for getLeagueProjections");
        }
        // Fetch all data needed
        List<Map<String, Object>> usersList = sleeperClient.getLeagueMembers(leagueId);
        List<Map<String, Object>> rostersList = sleeperClient.getLeagueRosters(leagueId);
        List<Map<String, Object>> matchupsList = sleeperClient.getMatchups(leagueId, week);
        Map<String, Map<String, Object>> playersMap = sleeperClient.getPlayersMap();
        Map<String, Map<String, Object>> weekProjections = sleeperClient.getWeekProjections(season, week);

        // Build roster_id -> owner_id (user_id)
        Map<Integer, String> rosterToOwner = new HashMap<>();
        for (Map<String, Object> r : rostersList) {
            Integer rosterId = r.get("roster_id") instanceof Number n ? n.intValue() : null;
            String ownerId = r.get("owner_id") != null ? r.get("owner_id").toString() : null;
            if (rosterId != null && ownerId != null) rosterToOwner.put(rosterId, ownerId);
        }

        // Group by matchup_id (handle null/undefined by assigning a unique key)
        Map<String, List<Map<String, Object>>> matchupGroups = new HashMap<>();
        for (Map<String, Object> row : matchupsList) {
            Object matchupId = row.get("matchup_id");
            Integer rosterId = row.get("roster_id") instanceof Number n ? n.intValue() : null;
            String gid = (matchupId != null) ? matchupId.toString() : ("solo-" + rosterId);
            matchupGroups.computeIfAbsent(gid, k -> new ArrayList<>()).add(row);
        }

        List<com.sleeper.advisor.model.ProjectionsMatchupPair> results = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : matchupGroups.entrySet()) {
            String matchupId = entry.getKey();
            List<Map<String, Object>> teams = entry.getValue();
            // Sort for deterministic order
            teams.sort(Comparator.comparingInt(t -> {
                Object rid = t.get("roster_id");
                if (rid instanceof Number n) return n.intValue();
                try { return Integer.parseInt(rid.toString()); } catch (Exception e) { return 0; }
            }));
            Map<String, Object> a = teams.get(0);
            Map<String, Object> b = teams.size() > 1 ? teams.get(1) : null;

            // For each team, map to MatchupSide with userId (owner_id) and projectedTotal (projected sum for starters)
            com.sleeper.advisor.model.MatchupSide sideA = makeProjectedMatchupSide(a, rosterToOwner, playersMap, weekProjections, format);
            com.sleeper.advisor.model.MatchupSide sideB = b != null ? makeProjectedMatchupSide(b, rosterToOwner, playersMap, weekProjections, format) : null;

            String aUserId = sideA != null ? sideA.userId() : "";
            String bUserId = sideB != null ? sideB.userId() : "";

            // Home/away: lower userId lex sorts first
            String homeFirst = (bUserId != null && !bUserId.isEmpty() && (aUserId.compareTo(bUserId) > 0)) ? bUserId : aUserId;
            String awaySecond = (homeFirst.equals(aUserId) ? bUserId : aUserId);

            com.sleeper.advisor.model.MatchupSide home = homeFirst.equals(aUserId) ? sideA : sideB;
            com.sleeper.advisor.model.MatchupSide away = homeFirst.equals(aUserId) ? sideB : sideA;

            // If only one team in the matchup (rare), duplicate as away for UI
            if (away == null) away = home;

            results.add(new com.sleeper.advisor.model.ProjectionsMatchupPair(
                    matchupId,
                    home,
                    away
            ));
        }

        com.sleeper.advisor.model.LeagueProjectionsResponse resp =
                new com.sleeper.advisor.model.LeagueProjectionsResponse(season, week, results);

        try {
            System.out.println("[getLeagueProjections] returning JSON: " + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(resp));
        } catch (Exception e) {
            System.out.println("[getLeagueProjections] error logging response: " + e);
        }
        return resp;
    }

    private com.sleeper.advisor.model.MatchupSide makeProjectedMatchupSide(
            Map<String, Object> teamRow,
            Map<Integer, String> rosterToOwner,
            Map<String, Map<String, Object>> playersMap,
            Map<String, Map<String, Object>> weekProjections,
            String format
    ) {
        // Get roster_id, userId
        Integer rosterId = teamRow.get("roster_id") instanceof Number n ? n.intValue() : null;
        String userId = rosterId != null ? rosterToOwner.get(rosterId) : null;
        // starters field: player ID array
        List<String> starterIds = objStringList(teamRow.get("starters"));
        double proj = 0.0;
        List<Player> starters = new ArrayList<>();
        for (String pid : starterIds) {
            if (pid == null || pid.equalsIgnoreCase("EMPTY")) continue;
            Map<String, Object> pmap = playersMap.get(pid);
            String name = pmap != null && pmap.get("full_name") != null ? pmap.get("full_name").toString() : pid;
            String pos = pmap != null && pmap.get("position") != null ? pmap.get("position").toString() : "";
            String team = pmap != null && pmap.get("team") != null ? pmap.get("team").toString() : "";
            Double playerProj = projectionStore.projectedPoints(pid, pos, weekProjections, format);
            Double value = projectionStore.marketValue(pid, pos);
            starters.add(new Player(pid, name, pos, team, playerProj, value));
            proj += playerProj != null ? playerProj : 0.0;
        }
        proj = Math.round(proj * 10.0) / 10.0; // round to 1 decimal
        // Overloaded MatchupSide with an extra starters field
        return new com.sleeper.advisor.model.MatchupSide(userId == null ? "—" : userId, proj, starters);
    }

    // --- END projections API compatibility stubs ---

}
