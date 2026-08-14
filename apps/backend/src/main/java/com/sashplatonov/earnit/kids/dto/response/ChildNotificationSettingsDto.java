package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record ChildNotificationSettingsDto(
    int childId,
    String childName,
    List<NotificationPreferenceDto> preferences
) {
}
