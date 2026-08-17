package com.sashplatonov.earnit.kids.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminRewardsResponse {

    private RewardShopMetrics shop;
    private String updatedAt;

    @Data
    @Builder
    public static class RewardShopMetrics {
        private int rewardsConfigured;
        private double familiesWithRewardPercent;
        private long rewardRequests;
        private long approvedRewards;
        private double rejectionRate;
        private double medianPrice;
        private double medianPurchasedPrice;
        private RewardPriceDistribution priceDistribution;
    }

    @Data
    @Builder
    public static class RewardPriceDistribution {
        private int bucket1to5;
        private int bucket6to10;
        private int bucket11to20;
        private int bucket21to50;
        private int bucket51plus;
    }
}
