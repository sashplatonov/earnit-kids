package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuperAdminFamiliesResponse(
    List<FamilySummary> families
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FamilySummary(
        String id,
        String email,
        String createdAt,
        String lastActivity,
        boolean isBlocked,
        int tasksCount,
        int shopCount,
        int childrenCount,
        List<ChildSummary> children
    ) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChildSummary(
        int id,
        String name,
        int balance,
        String token,
        int monthlyLimit,
        int dailyCoinLimit
    ) { }
}
