package com.sleeper.advisor.model;

public record DraftPick(
    int season,
    int round,
    String originalOwner,
    String originalOwnerName,
    String owner,
    boolean traded
) {}
