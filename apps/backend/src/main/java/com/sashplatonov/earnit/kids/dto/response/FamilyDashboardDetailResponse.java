package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FamilyDashboardDetailResponse(
    List<HistoryEntryDto> history,
    List<RequestDto> requests,
    List<FriendDto> friends
) { }
