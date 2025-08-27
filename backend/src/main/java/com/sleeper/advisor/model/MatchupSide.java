package com.sleeper.advisor.model;

public record MatchupSide(
        String userId,
        Double projectedTotal,
        java.util.List<com.sleeper.advisor.model.Player> starters
) {}
