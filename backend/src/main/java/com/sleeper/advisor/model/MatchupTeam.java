package com.sleeper.advisor.model;

import java.util.List;

public record MatchupTeam(
  String userId,
  String displayName,
  String avatar,
  double projectedTotal,
  List<Player> starters
){}
