package com.sleeper.advisor.web;

import com.sleeper.advisor.service.YahooFantasyClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/yahoo")
public class YahooAuthController {

    private final YahooFantasyClient yahooFantasyClient;

    public YahooAuthController(YahooFantasyClient yahooFantasyClient) {
        this.yahooFantasyClient = yahooFantasyClient;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "configured", yahooFantasyClient.isConfigured(),
                "connected", yahooFantasyClient.isConnected()
        );
    }

    @GetMapping("/auth-url")
    public Map<String, String> authUrl() {
        return Map.of("url", yahooFantasyClient.authUrl());
    }

    @GetMapping(value = "/callback", produces = "text/html")
    public String callback(@RequestParam String code) {
        yahooFantasyClient.exchangeCode(code);
        return """
                <html>
                  <body style="font-family: system-ui; background: #080809; color: #f0f0f3; padding: 32px;">
                    <h2>Yahoo connected</h2>
                    <p>You can close this tab and return to FantasyWizard.</p>
                  </body>
                </html>
                """;
    }
}
