package com.sleeper.advisor.web;

public class RateLimitException extends RuntimeException {

    private final int limit;
    private final int remaining;

    public RateLimitException(String message, int limit, int remaining) {
        super(message);
        this.limit = limit;
        this.remaining = remaining;
    }

    public int limit() {
        return limit;
    }

    public int remaining() {
        return remaining;
    }
}
