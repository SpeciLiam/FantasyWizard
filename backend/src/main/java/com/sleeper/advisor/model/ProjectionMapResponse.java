package com.sleeper.advisor.model;

import java.util.Map;

public record ProjectionMapResponse(
        Map<String, Double> map,
        Meta meta
) {
    public record Meta(int season, int week, String format, String source){}
}
