package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/league")
public class LeagueController {

    private final SleeperClient sleeperClient;

    public LeagueController(SleeperClient sleeperClient) {
        this.sleeperClient = sleeperClient;
    }

    @GetMapping("/{leagueId}/members")
    public Object getLeagueMembers(@PathVariable String leagueId) {
        // Directly return the list from the Sleeper API
        return sleeperClient.getLeagueMembers(leagueId);
    }
}
