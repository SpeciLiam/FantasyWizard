package com.sleeper.advisor.model;

public record LeagueUser(
    String userId,
    String displayName,
    String avatar,
    boolean isMe
) {}
