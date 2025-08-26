package com.sleeper.advisor.model;

public record Player(
    String id,
    String name,
    String pos,
    String team,
    Double proj,
    Double value
) {}
