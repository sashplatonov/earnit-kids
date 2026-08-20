package com.sashplatonov.earnit.kids.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminRetentionResponse {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RetentionMetrics {
        private int newFamilies;
        private int returningFamilies;
        private int activeFamilies;
        private int active7d;
        private int active30d;
    }

    private RetentionMetrics retentionMetrics;
    private String updatedAt;
}
