package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final SleeperClient sleeperClient;

    public UserController(SleeperClient sleeperClient) {
        this.sleeperClient = sleeperClient;
    }

    @GetMapping("/{username}/leagues")
    public Map<String, Object> getUserLeagues(
            @PathVariable String username,
            @RequestParam(required = false, defaultValue = "2025") String season) {

        Map<String, Object> user = sleeperClient.getUserId(username);
        String userId = user.get("user_id").toString();

        Object leaguesRaw = sleeperClient.getUserLeagues(userId, season);
        // leaguesRaw is a List<Map> from the API; shape into the same structure as before
        Map<String, Object> response = new HashMap<>();
        response.put("leagues", leaguesRaw);
        return response;
    }
}
