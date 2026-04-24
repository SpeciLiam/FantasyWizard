package com.sleeper.advisor.web;

import com.sleeper.advisor.service.ClaudeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/advisor")
public class AdvisorController {

    private final ClaudeService claudeService;

    public AdvisorController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest req) {
        String reply = claudeService.chat(req.message(), req.history());
        return Map.of("reply", reply);
    }

    public record ChatRequest(
            String message,
            List<Map<String, String>> history
    ) {}
}
