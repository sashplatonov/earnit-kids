package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class AdminCoinEconomyService {

    private static final Logger LOG = Logger.getLogger(AdminCoinEconomyService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminAnalyticsRepository repository;

    public AdminCoinEconomyResponse getCoinEconomy(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
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

}
