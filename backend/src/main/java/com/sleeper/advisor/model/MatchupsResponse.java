package com.sleeper.advisor.model;

import java.util.List;

public record MatchupsResponse(
  String leagueId,
  int week,
  List<MatchupPair> pairs
){}
