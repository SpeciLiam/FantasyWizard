package com.sleeper.advisor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AdvisorRateLimiter {

    @Value("${app.advisor.daily-limit:10}")
    private int dailyLimit;

    private final Map<String, Usage> usageByKey = new ConcurrentHashMap<>();

    public LimitResult consume(String key) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Usage usage = usageByKey.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.date().equals(today)) {
                return new Usage(today, new AtomicInteger(0));
            }
            return existing;
        });

        int used = usage.count().incrementAndGet();
        if (used > dailyLimit) {
            usage.count().decrementAndGet();
            return new LimitResult(false, dailyLimit, 0);
        }
        return new LimitResult(true, dailyLimit, Math.max(0, dailyLimit - used));
    }

    public record LimitResult(boolean allowed, int limit, int remaining) {}

    private record Usage(LocalDate date, AtomicInteger count) {}
}
