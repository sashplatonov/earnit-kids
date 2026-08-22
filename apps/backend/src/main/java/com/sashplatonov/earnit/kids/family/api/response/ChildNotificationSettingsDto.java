package com.sashplatonov.earnit.kids.family.api.response;

import java.util.List;

public record ChildNotificationSettingsDto(
    int childId,
    String childName,
    List<NotificationPreferenceDto> preferences
) {
    public ChildNotificationSettingsDto {
        preferences = List.copyOf(preferences);
    }
}
