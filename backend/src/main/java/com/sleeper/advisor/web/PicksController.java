package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import org.springframework.web.bind.annotation.*;

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
     * Response: { managers: [{userId, displayName, avatar}], picks: [{season, round, originalOwnerName, currentOwnerName, currentUserId, traded}] }
     */
    @GetMapping("/{leagueId}/picks")
    public Map<String, Object> getAllPicks(@PathVariable String leagueId) {
        List<Map<String, Object>> rosters = sleeperClient.getLeagueRosters(leagueId);
        List<Map<String, Object>> users   = sleeperClient.getLeagueMembers(leagueId);
        List<Map<String, Object>> traded  = sleeperClient.getTradedPicks(leagueId);

        // Build lookup maps
        Map<Integer, String> rosterToUserId = new HashMap<>();
        for (Map<String, Object> r : rosters) {
            Integer rid = intOrNull(r.get("roster_id"));
            Object uid  = r.get("owner_id");
            if (rid != null && uid != null) rosterToUserId.put(rid, uid.toString());
        }
        Map<String, String> userIdToName = new HashMap<>();
        Map<String, String> userIdToAvatar = new HashMap<>();
        for (Map<String, Object> u : users) {
            String uid  = u.get("user_id")     != null ? u.get("user_id").toString()     : null;
            String name = u.get("display_name") != null ? u.get("display_name").toString() : null;
            String av   = u.get("avatar")       != null ? u.get("avatar").toString()       : null;
            if (uid != null && name != null) { userIdToName.put(uid, name); userIdToAvatar.put(uid, av); }
        }

        // Seed default ownership: every roster owns all of its own draft_picks
        // key = "season:round:originalRosterId"
        Map<String, Integer> currentOwner = new LinkedHashMap<>();
        Map<String, int[]>   pickMeta     = new LinkedHashMap<>();

        for (Map<String, Object> r : rosters) {
            Integer originalRid = intOrNull(r.get("roster_id"));
            Object dp = r.get("draft_picks");
            if (originalRid == null || !(dp instanceof List<?> list)) continue;
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> p)) continue;
                int s  = intOrElse(p.get("season"), 0);
                int rd = intOrElse(p.get("round"),  0);
                if (s == 0 || rd == 0) continue;
                String key = s + ":" + rd + ":" + originalRid;
                currentOwner.putIfAbsent(key, originalRid);
                pickMeta.putIfAbsent(key, new int[]{s, rd, originalRid});
            }
        }

        // Apply traded picks: roster_id = original, owner_id = current holder (both roster IDs)
        for (Map<String, Object> t : traded) {
            int s  = intOrElse(t.get("season"), 0);
            int rd = intOrElse(t.get("round"),  0);
            Integer origRid    = intOrNull(t.get("roster_id"));
            Integer currentRid = intOrNull(t.get("owner_id"));
            if (s == 0 || rd == 0 || origRid == null || currentRid == null) continue;
            String key = s + ":" + rd + ":" + origRid;
            currentOwner.put(key, currentRid);
            pickMeta.putIfAbsent(key, new int[]{s, rd, origRid});
        }

        // Build response picks list
        List<Map<String, Object>> pickList = new ArrayList<>();
        for (var e : pickMeta.entrySet()) {
            int[] meta = e.getValue();
            int s = meta[0], rd = meta[1], origRid = meta[2];
            Integer curRid = currentOwner.get(e.getKey());
            if (curRid == null) continue;

            String origUid  = rosterToUserId.get(origRid);
            String curUid   = rosterToUserId.get(curRid);
            String origName = origUid  != null ? userIdToName.get(origUid)  : "Roster " + origRid;
            String curName  = curUid   != null ? userIdToName.get(curUid)   : "Roster " + curRid;
            String avatar   = curUid   != null ? userIdToAvatar.get(curUid) : null;
            boolean wasTraded = !Objects.equals(origRid, curRid);

            Map<String, Object> pick = new LinkedHashMap<>();
            pick.put("season",           s);
            pick.put("round",            rd);
            pick.put("originalOwnerName", origName != null ? origName : "?");
            pick.put("currentOwnerName",  curName  != null ? curName  : "?");
            pick.put("currentUserId",     curUid   != null ? curUid   : "");
            pick.put("currentAvatar",     avatar   != null
                    ? "https://sleepercdn.com/avatars/thumbs/" + avatar : null);
            pick.put("traded",            wasTraded);
            pickList.add(pick);
        }

        // Sort: season asc, round asc, originalOwner asc
        pickList.sort(Comparator
                .comparingInt(p -> (Integer) ((Map<?,?>) p).get("season"))
        );
        pickList.sort(Comparator
                .comparingInt((Map<?,?> p) -> (Integer) p.get("season"))
                .thenComparingInt(p -> (Integer) p.get("round"))
                .thenComparing(p -> (String) p.get("originalOwnerName"))
        );

        // Unique manager list
        List<Map<String, String>> managers = users.stream()
                .map(u -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("userId",      u.get("user_id")      != null ? u.get("user_id").toString()      : "");
                    m.put("displayName", u.get("display_name") != null ? u.get("display_name").toString() : "");
                    String av = u.get("avatar") != null ? u.get("avatar").toString() : null;
                    m.put("avatar", av != null ? "https://sleepercdn.com/avatars/thumbs/" + av : null);
                    return m;
                })
                .collect(Collectors.toList());

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
