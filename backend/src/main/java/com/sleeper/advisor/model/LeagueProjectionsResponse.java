package com.sleeper.advisor.model;

import java.util.List;

public record LeagueProjectionsResponse(
        int season,
        int week,
        List<ProjectionsMatchupPair> pairs
) {}
