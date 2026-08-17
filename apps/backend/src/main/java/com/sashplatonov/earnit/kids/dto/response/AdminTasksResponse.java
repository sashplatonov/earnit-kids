package com.sashplatonov.earnit.kids.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
public class AdminTasksResponse {

    private TaskMetrics metrics;
    private List<TopTaskPattern> topPatterns;
    private String updatedAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskMetrics {
        private int tasksConfigured;
        private double familiesWithTasksPercent;
        private long taskCompletions;
        private long approvedCompletions;
        private long rejectedCompletions;
        private double approvalRate;
        private double medianCoinsPerTask;
        private double medianCompletionsPerChild;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TopTaskPattern {
        private String groupName;
        private String icon;
        private long count;
        private double percent;
    }
}
