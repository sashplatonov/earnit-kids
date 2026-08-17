package com.sashplatonov.earnit.kids.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// EXPLAIN: ADM-12: Activation funnel analytics response
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminActivationFunnelResponse {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FunnelStage {
        private String key;
        private String label;
        private int count;
        private double percentFromPrevious;
        private double percentFromInitial;
    }

    private List<FunnelStage> stages;
}
