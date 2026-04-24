package com.sleeper.advisor.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProjectionStore {

    // Resolve projected points from a pre-fetched Sleeper projections map.
    // Falls back to deterministic hash if the player/stat is missing.
    public double projectedPoints(String playerId, String pos, Map<String, Map<String, Object>> sleeperProjections, String format) {
        if (sleeperProjections != null && !sleeperProjections.isEmpty()) {
            Map<String, Object> stats = sleeperProjections.get(playerId);
            if (stats != null) {
                String statKey = switch (format) {
                    case "half" -> "pts_half_ppr";
                    case "std" -> "pts_std";
                    default -> "pts_ppr";
                };
                Object val = stats.get(statKey);
                if (val instanceof Number n) {
                    return Math.round(n.doubleValue() * 10.0) / 10.0;
                }
            }
        }
        return fallbackProjection(playerId, pos);
    }

    // Kept for callers that don't have projection data
    public double projectedPoints(String playerId, String pos) {
        return fallbackProjection(playerId, pos);
    }

    public double marketValue(String playerId, String pos) {
        int base = Math.abs((playerId + pos).hashCode()) % 351;
        double value = 50.0 + base;
        value += posBonus(pos) * 5;
        return Math.round(value);
    }

    private double fallbackProjection(String playerId, String pos) {
        int base = Math.abs(playerId.hashCode()) % 1800;
        double proj = 5.0 + (base / 100.0);
        proj += posBonus(pos);
        return Math.round(proj * 10.0) / 10.0;
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
