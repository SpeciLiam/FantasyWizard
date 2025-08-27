package com.sleeper.advisor.web;

import com.sleeper.advisor.model.ProjectionMapResponse;
import com.sleeper.advisor.model.LeagueProjectionsResponse;
import com.sleeper.advisor.service.MatchupService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/projections")
public class ProjectionsController {

    private final MatchupService matchupService;

    public ProjectionsController(MatchupService matchupService) {
        this.matchupService = matchupService;
    }

    // GET /api/projections/ros?format=ppr|half|std
    @GetMapping("/ros")
    public Map<String, Object> rosSeasonProjections(@RequestParam(defaultValue = "ppr") String format) {
        // TEMP: Return dummy map until upstream mapping is clarified
        Map<String, Double> projMap = new HashMap<>();
        projMap.put("1234", 210.7);
        projMap.put("5678", 185.3);
        projMap.put("PHI", 125.4);

        Map<String, Object> meta = Map.of(
                "format", format,
                "source", "FantasyNerds-ROS"
        );
        return Map.of("map", projMap, "meta", meta);
    }

    // GET /api/projections/{season}/{week}?format=ppr|half|std
    @GetMapping("/{season}/{week}")
    public ProjectionMapResponse projectionMap(
            @PathVariable int season,
            @PathVariable int week,
            @RequestParam(defaultValue = "ppr") String format
    ) {
        return matchupService.getProjectionMapResponse(season, week, format);
    }

    // GET /api/projections/{season}/{week}/league/{leagueId}?format=ppr|half|std
    @GetMapping("/{season}/{week}/league/{leagueId}")
    public LeagueProjectionsResponse leaguePairs(
            @PathVariable int season,
            @PathVariable int week,
            @PathVariable String leagueId,
            @RequestParam(defaultValue = "ppr") String format
    ) {
        return matchupService.getLeagueProjections(season, week, leagueId, format);
    }
}
