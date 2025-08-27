package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.cors.origins:http://localhost:5173}")
public class StateController {
    private final SleeperClient client;
    public StateController(SleeperClient client) { this.client = client; }

    @GetMapping("/state")
    public Map<String,Object> state() {
        Map<String,Object> s = client.getNflState();
        // normalize keys we care about
        Object season = s.getOrDefault("season", null);
        Object week   = s.getOrDefault("week", null);
        return Map.of(
            "season", season,
            "week", week
        );
    }
}
