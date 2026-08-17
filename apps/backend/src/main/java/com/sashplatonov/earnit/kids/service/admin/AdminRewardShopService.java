package com.sashplatonov.earnit.kids.service.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class AdminRewardShopService {

    private static final Logger LOG = Logger.getLogger(AdminRewardShopService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminAnalyticsRepository repository;

    public AdminRewardsResponse getRewardShop(String period) {
        Instant periodStart = calculatePeriodStart(period);
        LOG.infof("Fetching reward shop metrics for period starting: %s", periodStart);

        AdminRewardsResponse.RewardShopMetrics shop =
            repository.getRewardShopMetrics(periodStart);

        return AdminRewardsResponse.builder()
            .shop(shop)
            .updatedAt(ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)))
            .build();
    }

    private Instant calculatePeriodStart(String period) {
        return switch (period) {
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default -> Instant.now().minus(30, ChronoUnit.DAYS);
        };
    }
}
