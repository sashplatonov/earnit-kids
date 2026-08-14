package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record NotificationPreferenceDto(
    String key,
    boolean enabled
) {
}
