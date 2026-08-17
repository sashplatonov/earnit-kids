package com.sashplatonov.earnit.kids.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAnalyticsResponse {

    private Overview overview;
    private String updatedAt;

    @Data
    @Builder
    public static class Overview {
        private int totalFamilies;
        private int activeFamilies;
        private int totalChildren;
        private int activeChildren;
        private long coinsEarned;
        private long coinsSpent;
        private int rewardPurchases;
        private int taskCompletions;
    }
}
