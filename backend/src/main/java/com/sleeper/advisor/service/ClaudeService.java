package com.sleeper.advisor.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    @Value("${app.anthropic.api-key:}")
    private String apiKey;

    private AnthropicClient client;

    @PostConstruct
    void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        } else {
            // Falls back to ANTHROPIC_API_KEY env var
            try {
                client = AnthropicOkHttpClient.fromEnv();
            } catch (Exception e) {
                log.warn("Anthropic API key not configured — advisor chat disabled");
            }
        }
    }

    public String chat(String userMessage, List<Map<String, String>> history) {
        if (client == null) {
            return "Advisor chat is not configured. Set ANTHROPIC_API_KEY to enable it.";
        }

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5)
                .maxTokens(1024L)
                .system(SYSTEM_PROMPT);

        // Replay prior turns
        if (history != null) {
            for (Map<String, String> turn : history) {
                String role = turn.get("role");
                String content = turn.get("content");
                if ("user".equals(role)) {
                    builder.addUserMessage(content);
                } else if ("assistant".equals(role)) {
                    builder.addAssistantMessage(content);
                }
            }
        }
        builder.addUserMessage(userMessage);

        Message response = client.messages().create(builder.build());
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(tb -> tb.text())
                .findFirst()
                .orElse("");
    }
}
