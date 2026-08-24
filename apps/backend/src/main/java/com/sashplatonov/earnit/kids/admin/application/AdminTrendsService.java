package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.dto.response.AdminTrendsResponse;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminTrendsRepository;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@ApplicationScoped
public class AdminTrendsService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminTrendsRepository repository;

    @CacheResult(cacheName = "admin-trends")
    public AdminTrendsResponse getTrends(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
        Instant now = Instant.now();

        List<AdminTrendsResponse.TrendPoint> points = repository.getTrendPoints(periodStart, now);

        return AdminTrendsResponse.builder()
            .points(points)
            .updatedAt(ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)))
            .build();
    }

}
