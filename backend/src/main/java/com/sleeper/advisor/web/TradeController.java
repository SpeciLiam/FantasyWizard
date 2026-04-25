package com.sleeper.advisor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sleeper.advisor.service.ClaudeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

    private static final Logger log = LoggerFactory.getLogger(TradeController.class);
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-5.4-mini}")
    private String model;

    /**
     * POST /api/trade/evaluate
     * Evaluates a specific trade proposal and returns a verdict + analysis.
     */
    @PostMapping("/evaluate")
    public Map<String, Object> evaluate(@RequestBody EvaluateRequest req) {
        String prompt = String.format("""
            Evaluate this fantasy football trade proposal. Return JSON only.

            MY TEAM (%s) gives up:
            %s

            THEIR TEAM (%s) gives up:
            %s

            League context:
            %s

            Return this exact JSON structure:
            {
              "verdict": "WIN" | "LOSS" | "FAIR",
              "score": <integer -10 to 10, positive = good for me>,
              "summary": "<1 sentence verdict>",
              "prosForMe": ["<reason>", ...],
              "consForMe": ["<reason>", ...],
              "recommendation": "<ACCEPT | DECLINE | COUNTER>",
              "counterSuggestion": "<optional string if COUNTER>"
            }
            """,
                req.myTeam(), formatAssets(req.myAssets()),
                req.theirTeam(), formatAssets(req.theirAssets()),
                req.leagueContext() != null ? req.leagueContext() : "PPR dynasty league"
        );

        return callOpenAiJson(prompt);
    }

    /**
     * POST /api/trade/suggest
     * Returns 3 AI-generated trade proposals given full league context.
     */
    @PostMapping("/suggest")
    public Map<String, Object> suggest(@RequestBody SuggestRequest req) {
        String prompt = String.format("""
            You are a dynasty fantasy football expert. Given the league rosters below,
            suggest the 3 best trades for %s to improve their team.

            %s

            Return ONLY valid JSON in this exact structure:
            {
              "suggestions": [
                {
                  "targetManager": "<their display name>",
                  "myAssets": [{"name": "...", "pos": "...", "team": "..."}],
                  "theirAssets": [{"name": "...", "pos": "...", "team": "..."}],
                  "rationale": "<2-3 sentence explanation>",
                  "winProbability": "<HIGH|MEDIUM|LOW chance target accepts>"
                }
              ]
            }
            """,
                req.myTeam(), req.leagueContext()
        );

        return callOpenAiJson(prompt);
    }

    private Map<String, Object> callOpenAiJson(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of("error", "OPENAI_API_KEY not configured");
        }
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_completion_tokens", 1500);
        body.put("response_format", Map.of("type", "json_object"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    OPENAI_URL, new HttpEntity<>(body, headers), Map.class);
            if (resp == null) return Map.of("error", "No response");
            Object choices = resp.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object msg = first.get("message");
                if (msg instanceof Map<?, ?> m && m.get("content") instanceof String content) {
                    return mapper.readValue(content, Map.class);
                }
            }
        } catch (Exception e) {
            log.warn("Trade AI call failed: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
        return Map.of("error", "Unexpected response");
    }

    private String formatAssets(List<Map<String, String>> assets) {
        if (assets == null || assets.isEmpty()) return "  (none)";
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> a : assets) {
            sb.append("  - ").append(a.getOrDefault("pos", "")).append(" ")
              .append(a.getOrDefault("name", "?"))
              .append(a.containsKey("team") ? " (" + a.get("team") + ")" : "")
              .append(a.containsKey("season") ? " [" + a.get("season") + " R" + a.get("round") + " pick]" : "")
              .append("\n");
        }
        return sb.toString();
    }

    public record EvaluateRequest(
            String myTeam,
            String theirTeam,
            List<Map<String, String>> myAssets,
            List<Map<String, String>> theirAssets,
            String leagueContext
    ) {}

    public record SuggestRequest(
            String myTeam,
            String leagueContext
    ) {}
}
