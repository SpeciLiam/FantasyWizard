package com.sleeper.advisor.model;

public record DraftPick(
    int season,
    int round,
    String originalOwner,
    String owner,
    boolean traded
) {}
