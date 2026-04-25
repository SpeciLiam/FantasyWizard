package com.sleeper.advisor.web;

import com.sleeper.advisor.service.ClaudeService;
import com.sleeper.advisor.service.AdvisorRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/advisor")
public class AdvisorController {

    private final ClaudeService claudeService;
    private final AdvisorRateLimiter advisorRateLimiter;

    public AdvisorController(ClaudeService claudeService, AdvisorRateLimiter advisorRateLimiter) {
        this.claudeService = claudeService;
        this.advisorRateLimiter = advisorRateLimiter;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest req, HttpServletRequest request) {
        AdvisorRateLimiter.LimitResult limit = advisorRateLimiter.consume(clientKey(request));
        if (!limit.allowed()) {
            throw new RateLimitException(
                    "Daily AI advisor limit reached. You get " + limit.limit() + " requests per day.",
                    limit.limit(),
                    limit.remaining()
            );
        }

        String enrichedMessage = req.message();
        if (req.context() != null && !req.context().isBlank()) {
            enrichedMessage = "Context (current roster / matchup data):\n" + req.context()
                    + "\n\n---\nUser question: " + req.message();
        }
        String reply = claudeService.chat(enrichedMessage, req.history());
        return Map.of(
                "reply", reply,
                "remaining", String.valueOf(limit.remaining()),
                "limit", String.valueOf(limit.limit())
        );
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    public record ChatRequest(
            String message,
            List<Map<String, String>> history,
            String context
    ) {}
}
