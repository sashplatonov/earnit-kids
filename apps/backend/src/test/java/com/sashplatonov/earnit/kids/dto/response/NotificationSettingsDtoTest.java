package com.sashplatonov.earnit.kids.dto.response;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSettingsDtoTest {

    @Test
    void childPreferencesAreCopiedAndExposedAsImmutableSnapshot() {
        List<NotificationPreferenceDto> preferences = new ArrayList<>();
        preferences.add(new NotificationPreferenceDto("task.completed", true));

        ChildNotificationSettingsDto settings = new ChildNotificationSettingsDto(5, "Alice", preferences);
        preferences.clear();

        assertThat(settings.preferences()).hasSize(1);
        assertThatThrownBy(() -> settings.preferences().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void familyNotificationListsAreCopiedAndExposedAsImmutableSnapshots() {
        List<NotificationPreferenceDto> parent = new ArrayList<>();
        List<ChildNotificationSettingsDto> children = new ArrayList<>();
        children.add(new ChildNotificationSettingsDto(5, "Alice", List.of()));

        FamilyNotificationSettingsResponse response = new FamilyNotificationSettingsResponse(parent, children);
        parent.add(new NotificationPreferenceDto("task.completed", true));
        children.clear();

        assertThat(response.parent()).isEmpty();
        assertThat(response.children()).hasSize(1);
        assertThatThrownBy(() -> response.children().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
