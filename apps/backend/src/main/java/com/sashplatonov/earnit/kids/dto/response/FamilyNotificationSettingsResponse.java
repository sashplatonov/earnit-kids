package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record FamilyNotificationSettingsResponse(
    List<NotificationPreferenceDto> parent,
    List<ChildNotificationSettingsDto> children
) {
    public FamilyNotificationSettingsResponse {
        parent = List.copyOf(parent);
        children = List.copyOf(children);
    }
}
