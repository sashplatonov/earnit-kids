package com.sashplatonov.earnit.kids.service.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class AdminCoinEconomyService {

    private static final Logger LOG = Logger.getLogger(AdminCoinEconomyService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminAnalyticsRepository repository;

    public AdminCoinEconomyResponse getCoinEconomy(String period) {
        Instant periodStart = calculatePeriodStart(period);
        LOG.infof("Fetching coin economy for period starting: %s", periodStart);

        int activeChildren = repository.countActiveChildren(periodStart);

        AdminCoinEconomyResponse.CoinMetrics coinMetrics =
            repository.getCoinMetrics(periodStart, activeChildren);

        AdminCoinEconomyResponse.BalanceMetrics balanceMetrics =
            repository.getBalanceMetrics();

        AdminCoinEconomyResponse.RewardMetrics rewardMetrics =
            repository.getRewardMetrics(periodStart);

        return AdminCoinEconomyResponse.builder()
            .coins(coinMetrics)
            .balances(balanceMetrics)
            .rewards(rewardMetrics)
            .updatedAt(ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)))
            .build();
    }

    private Instant calculatePeriodStart(String period) {
        return switch (period) {
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default -> Instant.ofEpochSecond(0);
        };
    }
}
