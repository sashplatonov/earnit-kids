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
    }
}
