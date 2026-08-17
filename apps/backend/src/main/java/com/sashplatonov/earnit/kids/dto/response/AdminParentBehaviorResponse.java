package com.sashplatonov.earnit.kids.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// EXPLAIN: ADM-10: Parent behavior and needs analytics response
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminParentBehaviorResponse {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ParentBehaviorMetrics {
        private double familiesUsingCatalogPercent;
        private double familiesUsingCustomContentPercent;
        private double medianApprovalDelayHours;
        private int pendingRequestsCount;
        private int familiesWithPendingRequests;
        private double notificationsEnabledPercent;
    }

    private ParentBehaviorMetrics parentBehaviorMetrics;
    private List<String> insights; // EXPLAIN: Optional: AI-generated insights about parent behavior
}
