package com.sleeper.advisor.web;

import com.sleeper.advisor.service.MatchupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.sleeper.advisor.service.SleeperClient;
import com.sleeper.advisor.service.YahooFantasyClient;
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
    private final MatchupService matchupService;
    private final YahooFantasyClient yahooFantasyClient;

    @Autowired
    public LeagueController(SleeperClient sleeperClient, MatchupService matchupService, YahooFantasyClient yahooFantasyClient) {
        this.sleeperClient = sleeperClient;
        this.matchupService = matchupService;
        this.yahooFantasyClient = yahooFantasyClient;
    }

    @GetMapping("/{leagueId}/matchups/{week}")
    public com.sleeper.advisor.model.MatchupsResponse getMatchups(
            @PathVariable String leagueId,
            @PathVariable int week,
            @RequestParam(required = false, defaultValue = "sleeper") String provider
    ) {
        log.info("API GET /api/league/{}/matchups/{}?provider={}", leagueId, week, provider);
        if ("yahoo".equalsIgnoreCase(provider)) {
            return new com.sleeper.advisor.model.MatchupsResponse(leagueId, week, java.util.List.of());
        }
        com.sleeper.advisor.model.MatchupsResponse resp = matchupService.getMatchups(leagueId, week);
        log.info("Returning matchups: {} pairs", resp.pairs().size());
        return resp;
    }

    @GetMapping("/{leagueId}/members")
    public List<LeagueUser> getLeagueMembers(
            @PathVariable String leagueId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false, defaultValue = "sleeper") String provider
    ) {
        log.info("GET /api/league/{}/members?username={}&provider={}", leagueId, username, provider);
        if ("yahoo".equalsIgnoreCase(provider)) {
            return yahooFantasyClient.getLeagueMembers(leagueId, username);
        }
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
