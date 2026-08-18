package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuperAdminFamiliesResponse(
    List<FamilySummary> families
) {
    public SuperAdminFamiliesResponse {
        families = families == null ? List.of() : List.copyOf(families);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FamilySummary(
        String id,
        String email,
        String createdAt,
        String lastActivity,
        boolean isBlocked,
        int tasksCount,
        int childrenCount,
        List<ChildSummary> children
    ) {
        public FamilySummary {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

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
