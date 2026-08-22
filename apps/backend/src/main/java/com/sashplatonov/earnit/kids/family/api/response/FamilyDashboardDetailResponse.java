package com.sashplatonov.earnit.kids.family.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FamilyDashboardDetailResponse(
    List<HistoryEntryDto> history,
    List<RequestDto> requests,
    List<FriendDto> friends
) {
    public FamilyDashboardDetailResponse {
        history = history == null ? List.of() : List.copyOf(history);
        requests = requests == null ? List.of() : List.copyOf(requests);
        friends = friends == null ? List.of() : List.copyOf(friends);
    }
}
