package com.sashplatonov.earnit.kids.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// EXPLAIN: ADM-11: Child behavior and needs analytics response
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminChildBehaviorResponse {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChildBehaviorMetrics {
        private double medianActiveDaysPerChild;
        private double medianTasksBeforeReward;
        private int childrenEarningNotSpending;
        private double percentChildrenEarningNotSpending;
        private int childrenRequestedNotReceived;
        private double percentChildrenRequestedNotReceived;
    }

    private ChildBehaviorMetrics childBehaviorMetrics;
}
