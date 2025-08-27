package com.sleeper.advisor.model;

// For projections API only!
public record ProjectionsMatchupPair(
        String id,
        MatchupSide home,
        MatchupSide away
) {}
