package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/league")
public class PicksController {

    private final SleeperClient sleeperClient;

    public PicksController(SleeperClient sleeperClient) {
        this.sleeperClient = sleeperClient;
    }

    /**
     * GET /api/league/{leagueId}/picks
     * Returns all future draft picks with current ownership.
     */
    @GetMapping("/{leagueId}/picks")
    public Map<String, Object> getAllPicks(
            @PathVariable String leagueId,
            @RequestParam(required = false, defaultValue = "sleeper") String provider) {
        if ("yahoo".equalsIgnoreCase(provider)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("managers", List.of());
            empty.put("picks", List.of());
            return empty;
        }
        List<Map<String, Object>> rosters = sleeperClient.getLeagueRosters(leagueId);
        List<Map<String, Object>> users   = sleeperClient.getLeagueMembers(leagueId);
        List<Map<String, Object>> traded  = sleeperClient.getTradedPicks(leagueId);
        Map<String, Object>       info    = sleeperClient.getLeagueInfo(leagueId);

        // Number of draft rounds from league settings (default 3)
        int draftRounds = 3;
        Object settings = info.get("settings");
        if (settings instanceof Map<?,?> s && s.get("draft_rounds") instanceof Number n) {
            draftRounds = n.intValue();
        }

        // Build lookups: roster_id <-> user_id <-> display_name
        Map<Integer, String> rosterToUser = new HashMap<>();
        for (Map<String, Object> r : rosters) {
            Integer rid = intOrNull(r.get("roster_id"));
            Object uid  = r.get("owner_id");
            if (rid != null && uid != null) rosterToUser.put(rid, uid.toString());
        }
        Map<String, String> userIdToName   = new HashMap<>();
        Map<String, String> userIdToAvatar = new HashMap<>();
        for (Map<String, Object> u : users) {
            String uid  = u.get("user_id")     != null ? u.get("user_id").toString()     : null;
            String name = u.get("display_name") != null ? u.get("display_name").toString() : null;
            String av   = u.get("avatar")       != null ? u.get("avatar").toString()       : null;
            if (uid != null && name != null) {
                userIdToName.put(uid, name);
                userIdToAvatar.put(uid, av);
            }
        }

        // Determine seasons to show: current year + 2 forward, plus any in traded_picks
        int currentYear = Year.now().getValue();
        Set<Integer> seasons = new LinkedHashSet<>();
        for (int y = currentYear; y <= currentYear + 2; y++) seasons.add(y);
        for (Map<String, Object> t : traded) {
            Object sv = t.get("season");
            if (sv != null) { try { seasons.add(Integer.parseInt(sv.toString())); } catch (Exception ignored) {} }
        }
        List<Integer> sortedSeasons = seasons.stream().sorted().collect(Collectors.toList());
        List<Integer> rosterIds = rosters.stream()
                .map(r -> intOrNull(r.get("roster_id"))).filter(Objects::nonNull).collect(Collectors.toList());

        // Seed: every team owns every pick by default
        // key = "season:round:originalRosterId"
        Map<String, Integer> currentOwner = new LinkedHashMap<>();
        for (int season : sortedSeasons) {
            for (int round = 1; round <= draftRounds; round++) {
                for (int rid : rosterIds) {
                    currentOwner.put(season + ":" + round + ":" + rid, rid);
                }
            }
        }

        // Apply traded picks — season may be String or Integer from Sleeper
        for (Map<String, Object> t : traded) {
            Object sv = t.get("season");
            if (sv == null) continue;
            int s;
            try { s = Integer.parseInt(sv.toString()); } catch (Exception e) { continue; }
            int rd          = intOrElse(t.get("round"), 0);
            Integer origRid = intOrNull(t.get("roster_id"));   // original owning team
            Integer curRid  = intOrNull(t.get("owner_id"));    // current holder
            if (rd == 0 || origRid == null || curRid == null) continue;
            String key = s + ":" + rd + ":" + origRid;
            currentOwner.put(key, curRid); // overwrite with new owner
        }

        // Build response
        List<Map<String, Object>> pickList = new ArrayList<>();
        for (var e : currentOwner.entrySet()) {
            String[] parts = e.getKey().split(":");
            int s = Integer.parseInt(parts[0]);
            int rd = Integer.parseInt(parts[1]);
            int origRid = Integer.parseInt(parts[2]);
            int curRid  = e.getValue();

            String origUid  = rosterToUser.get(origRid);
            String curUid   = rosterToUser.get(curRid);
            String origName = origUid != null ? userIdToName.get(origUid) : "Roster " + origRid;
            String curName  = curUid  != null ? userIdToName.get(curUid)  : "Roster " + curRid;
            String avatar   = curUid  != null ? userIdToAvatar.get(curUid) : null;
            boolean traded_ = origRid != curRid;

            Map<String, Object> pick = new LinkedHashMap<>();
            pick.put("season",            s);
            pick.put("round",             rd);
            pick.put("originalOwnerName", origName != null ? origName : "?");
            pick.put("currentOwnerName",  curName  != null ? curName  : "?");
            pick.put("currentUserId",     curUid   != null ? curUid   : "");
            pick.put("currentAvatar",     avatar   != null
                    ? "https://sleepercdn.com/avatars/thumbs/" + avatar : null);
            pick.put("traded",            traded_);
            pickList.add(pick);
        }

        pickList.sort(Comparator
                .comparingInt((Map<?,?> p) -> (Integer) p.get("season"))
                .thenComparingInt(p -> (Integer) p.get("round"))
                .thenComparing(p -> (String) p.get("originalOwnerName")));

        List<Map<String, String>> managers = users.stream().map(u -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("userId",      u.get("user_id")      != null ? u.get("user_id").toString()      : "");
            m.put("displayName", u.get("display_name") != null ? u.get("display_name").toString() : "");
            String av = u.get("avatar") != null ? u.get("avatar").toString() : null;
            m.put("avatar", av != null ? "https://sleepercdn.com/avatars/thumbs/" + av : null);
            return m;
        }).collect(Collectors.toList());

        return Map.of("managers", managers, "picks", pickList);
    }

    private static Integer intOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
    }
    private static int intOrElse(Object o, int def) {
        Integer v = intOrNull(o); return v != null ? v : def;
    }
}
