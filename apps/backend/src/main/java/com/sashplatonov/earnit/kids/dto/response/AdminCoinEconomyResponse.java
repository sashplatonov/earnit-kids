package com.sashplatonov.earnit.kids.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminCoinEconomyResponse {

    private CoinMetrics coins;
    private BalanceMetrics balances;
    private RewardMetrics rewards;
    private String updatedAt;

    @Data
    @Builder
    public static class CoinMetrics {
        private long earned;
        private long spent;
        private double spendRate;
        private int activeChildren;
    }

    @Data
    @Builder
    public static class BalanceMetrics {
        private double medianBalance;
        private double averageBalance;
        private int zeroBalanceCount;
        private int zeroBalancePercent;
        private int highBalanceCount;
        private int highBalancePercent;
    }

    @Data
    @Builder
    public static class RewardMetrics {
        private int familiesWithReward;
        private int percentFamiliesWithReward;
    }
}
