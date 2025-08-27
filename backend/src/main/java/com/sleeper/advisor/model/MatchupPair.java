package com.sleeper.advisor.model;

// Used by core matchups logic (NOT projections API)
public record MatchupPair(
        MatchupTeam home,
        MatchupTeam away
) {}
