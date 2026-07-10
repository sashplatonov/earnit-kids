package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuperAdminFamilyDetailsResponse(
    String familyId,
    FamilyInfo familyInfo,
    FamilyData data
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FamilyInfo(
        String id,
        String email,
        String createdAt,
        String lastActivity,
        boolean isBlocked,
        int childrenCount,
        List<SuperAdminFamiliesResponse.ChildSummary> children,
        int monthlyLimit
    ) {
        public FamilyInfo {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FamilyData(
        int balance,
        List<TaskDto> tasks,
        List<ShopItemDto> shop,
        List<HistoryEntryDto> history,
        List<RequestDto> requests
    ) {
        public FamilyData {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            shop = shop == null ? List.of() : List.copyOf(shop);
            history = history == null ? List.of() : List.copyOf(history);
            requests = requests == null ? List.of() : List.copyOf(requests);
        }
    }
}
