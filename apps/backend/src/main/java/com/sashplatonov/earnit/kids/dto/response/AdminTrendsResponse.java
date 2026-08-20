package com.sashplatonov.earnit.kids.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminTrendsResponse {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendPoint {
        private String date;
        private long activeFamilies;
        private long coinsEarned;
        private long coinsSpent;
        private long rewardRedemptions;
        private long taskCompletions;
    }

    private List<TrendPoint> points;
    private String updatedAt;
}
