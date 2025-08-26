package com.sleeper.advisor.service;

import org.springframework.stereotype.Service;

@Service
public class ProjectionStore {

    public double projectedPoints(String playerId, String pos) {
        // Deterministically map playerId to [5, 22], add up to 1 by pos
        int base = Math.abs(playerId.hashCode()) % 1800;
        double proj = 5.0 + (base / 100.0);
        proj += posBonus(pos);
        return Math.round(proj * 10.0) / 10.0; // round to 1 decimal
    }

    public double marketValue(String playerId, String pos) {
        // Map to [50, 400], with a small bump by pos
        int base = Math.abs((playerId + pos).hashCode()) % 351;
        double value = 50.0 + base;
        value += posBonus(pos) * 5;
        return Math.round(value);
    }

    private double posBonus(String pos) {
        if (pos == null) return 0;
        return switch (pos) {
            case "QB" -> 0.6;
            case "RB" -> 0.4;
            case "WR" -> 0.5;
            case "TE" -> 0.3;
            default -> 0.0;
        };
    }
}
