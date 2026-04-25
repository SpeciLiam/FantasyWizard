package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import com.sleeper.advisor.service.ProjectionStore;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/league")
public class AllRostersController {

    private final SleeperClient sleeperClient;
    private final ProjectionStore projectionStore;

    public AllRostersController(SleeperClient sleeperClient, ProjectionStore projectionStore) {
        this.sleeperClient = sleeperClient;
        this.projectionStore = projectionStore;
    }

    /**
     * GET /api/league/{leagueId}/all-rosters?week=N
     * Returns every team's starters + bench with real projections.
     */
    @GetMapping("/{leagueId}/all-rosters")
    public List<Map<String, Object>> getAllRosters(
            @PathVariable String leagueId,
            @RequestParam(required = false, defaultValue = "1") int week) {

        List<Map<String, Object>> rosters = sleeperClient.getLeagueRosters(leagueId);
        List<Map<String, Object>> users   = sleeperClient.getLeagueMembers(leagueId);
        Map<String, Map<String, Object>> playersMap = sleeperClient.getPlayersMap();

        Map<String, Object> state  = sleeperClient.getNflState();
        int season = state.get("season") instanceof Number n ? n.intValue() : 2025;
        Map<String, Map<String, Object>> weekProj = sleeperClient.getWeekProjections(season, week);

        // roster_id -> user_id
        Map<Integer, String> rosterToUser = new HashMap<>();
        for (Map<String, Object> r : rosters) {
            Integer rid = intOrNull(r.get("roster_id"));
            Object uid  = r.get("owner_id");
            if (rid != null && uid != null) rosterToUser.put(rid, uid.toString());
        }
        // user_id -> display_name
        Map<String, String> userToName = new HashMap<>();
        for (Map<String, Object> u : users) {
            String uid  = u.get("user_id")     != null ? u.get("user_id").toString()     : null;
            String name = u.get("display_name") != null ? u.get("display_name").toString() : null;
            if (uid != null && name != null) userToName.put(uid, name);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rosters) {
            Integer rid = intOrNull(r.get("roster_id"));
            String uid  = rid != null ? rosterToUser.get(rid) : null;
            String name = uid != null ? userToName.get(uid) : "Roster " + rid;

            List<String> starterIds = toStringList(r.get("starters"));
            List<String> allIds     = toStringList(r.get("players"));
            List<String> taxiIds    = toStringList(r.get("taxi"));
            Set<String>  starterSet = new HashSet<>(starterIds);

            List<String> benchIds = allIds.stream()
                    .filter(pid -> !starterSet.contains(pid) && !taxiIds.contains(pid))
                    .collect(Collectors.toList());

            Map<String, Object> team = new LinkedHashMap<>();
            team.put("displayName", name);
            team.put("userId", uid != null ? uid : "");
            team.put("starters", hydrate(starterIds, playersMap, weekProj));
            team.put("bench",    hydrate(benchIds,   playersMap, weekProj));
            team.put("taxi",     hydrate(taxiIds,    playersMap, weekProj));
            result.add(team);
        }

        result.sort(Comparator.comparing(t -> (String) t.get("displayName")));
        return result;
    }

    private List<Map<String, Object>> hydrate(
            List<String> ids,
            Map<String, Map<String, Object>> playersMap,
            Map<String, Map<String, Object>> weekProj) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String pid : ids) {
            Map<String, Object> meta = playersMap.get(pid);
            if (meta == null) continue;
            String name = str(meta, "full_name");
            String pos  = str(meta, "position");
            String team = str(meta, "team");
            double proj = projectionStore.projectedPoints(pid, pos, weekProj, "ppr");
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id",   pid);
            p.put("name", name != null ? name : pid);
            p.put("pos",  pos  != null ? pos  : "");
            p.put("team", team != null ? team : "");
            p.put("proj", proj);
            out.add(p);
        }
        return out;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key); return v != null ? v.toString() : null;
    }
    private static Integer intOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
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
}
