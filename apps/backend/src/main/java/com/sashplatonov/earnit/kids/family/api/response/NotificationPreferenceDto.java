package com.sashplatonov.earnit.kids.family.api.response;

import java.util.List;

public record NotificationPreferenceDto(
    String key,
    boolean enabled
) {
}
