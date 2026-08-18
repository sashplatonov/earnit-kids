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
public class AdminRewardsResponse {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RewardMetrics {
        private int requestCount;
        private int issuedCount;
        private double medianPrice;
        private double selectedPrice;
        private double failedRate;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RewardRanking {
        private String category;
        private int count;
        private double percent;
        private int rank;
    }

    private RewardMetrics metrics;
    private List<RewardRanking> rankings;
    private String updatedAt;
}
