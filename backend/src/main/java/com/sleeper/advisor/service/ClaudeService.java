package com.sleeper.advisor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private static final String SYSTEM_PROMPT = """
            You are a fantasy football expert advisor. You help users make smart decisions
            for their Sleeper fantasy leagues: who to start, trade advice, waiver pickups,
            and matchup analysis. Be concise, data-driven, and direct. When given player
            projection data or injury info, factor it into your recommendations.
            """;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-4o-mini}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public String chat(String userMessage, List<Map<String, String>> history) {
        if (apiKey == null || apiKey.isBlank()) {
            return "Advisor chat is not configured. Set OPENAI_API_KEY to enable it.";
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (history != null) {
            for (Map<String, String> turn : history) {
                String role = turn.get("role");
                String content = turn.get("content");
                if (role != null && content != null && ("user".equals(role) || "assistant".equals(role))) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 1024);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(OPENAI_URL, new HttpEntity<>(body, headers), Map.class);
            if (resp == null) return "No response from OpenAI.";
            Object choices = resp.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object msg = first.get("message");
                if (msg instanceof Map<?, ?> m) {
                    Object content = m.get("content");
                    if (content != null) return content.toString();
                }
            }
            return "Unexpected response shape from OpenAI.";
        } catch (Exception e) {
            log.warn("OpenAI chat failed: {}", e.getMessage());
            return "Chat error: " + e.getMessage();
        }
    }
}
