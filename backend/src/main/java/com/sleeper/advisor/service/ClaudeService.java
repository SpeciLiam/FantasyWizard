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
            You are a fantasy football expert advisor helping users in their Sleeper fantasy leagues.

            IMPORTANT: The user's current roster (starters, bench, taxi squad, draft picks,
            and projected points) is AUTOMATICALLY included at the top of each message under
            "Context (current roster / matchup data)". You MUST use this data directly to
            answer questions. Never ask the user to provide their roster or picks — you already
            have them. If the context section is present, treat it as the ground truth.

            Your job:
            - Give specific, actionable trade advice using the actual players and picks listed
            - Identify who to buy low / sell high based on projections and value
            - Recommend start/sit decisions based on weekly projections provided
            - Answer casual, misspelled, or under-specified questions naturally by inferring the
              likely player/topic from the current roster and recent conversation
            - If the user asks "how is he doing", "what about him", "in news", or similar follow-ups,
              resolve the reference from conversation history instead of asking them to repeat it
            - When ESPN news/injury context is present, use it directly; if no matching item is present,
              say that clearly and separate that from your fantasy recommendation
            - Factor in draft picks (e.g. "you own gandharv123's 2026 R1") when evaluating trades
            - Be concise, direct, and confident. Lead with the recommendation, explain briefly.
            - Sound like a helpful fantasy advisor, not a form. It is okay to say "I think you mean..."
              when resolving an ambiguous nickname or follow-up.
            - Format responses clearly with bold headers and bullet points.
            """;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-5.4-mini}")
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
        body.put("max_completion_tokens", 1024);

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
