package com.sashplatonov.earnit.kids.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

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
        private List<TopRewardPattern> topPatterns;
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

    @Data
    @Builder
    public static class TopRewardPattern {
        private String groupName;
        private String icon;
        private long count;
        private double percent;
    }
}
