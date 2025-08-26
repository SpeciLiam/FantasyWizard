package com.sleeper.advisor.model;

import java.util.List;

public record Roster(
    List<Player> starters,
    List<Player> bench,
    List<Player> taxi,
    List<DraftPick> picks
) {}
