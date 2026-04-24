package com.sleeper.advisor.web;

import com.sleeper.advisor.service.ESPNClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/espn")
public class ESPNController {

    private final ESPNClient espnClient;

    public ESPNController(ESPNClient espnClient) {
        this.espnClient = espnClient;
    }

    @GetMapping("/injuries")
    public Map<String, String> getInjuries() {
        return espnClient.getAllInjuries();
    }

    @GetMapping("/news")
    public List<Map<String, String>> getNews() {
        return espnClient.getNFLNews();
    }
}
