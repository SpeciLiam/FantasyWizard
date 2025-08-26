package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import com.sleeper.advisor.model.LeagueUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/league")
public class LeagueController {

    private static final Logger log = LoggerFactory.getLogger(LeagueController.class);

    private final SleeperClient sleeperClient;

    public LeagueController(SleeperClient sleeperClient) {
        this.sleeperClient = sleeperClient;
    }

    @GetMapping("/{leagueId}/members")
    public List<LeagueUser> getLeagueMembers(
            @PathVariable String leagueId,
            @RequestParam(required = false) String username
    ) {
        log.info("GET /api/league/{}/members?username={}", leagueId, username);
        Object membersRaw = sleeperClient.getLeagueMembers(leagueId);
        List<LeagueUser> members = new ArrayList<>();
        if (membersRaw instanceof List) {
            for (Object obj : (List<?>) membersRaw) {
                if (obj instanceof Map<?,?> map) {
                    String userId = map.get("user_id") == null ? null : map.get("user_id").toString();
                    String displayName = map.get("display_name") == null ? null : map.get("display_name").toString();
                    String avatar = map.get("avatar") == null ? null : map.get("avatar").toString();
                    boolean isMe = (username != null
                        && displayName != null
                        && displayName.equalsIgnoreCase(username));
                    members.add(new LeagueUser(userId, displayName, avatar, isMe));
                }
            }
        }
        return members;
    }
}
