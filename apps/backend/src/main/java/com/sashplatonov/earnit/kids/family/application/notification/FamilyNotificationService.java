package com.sashplatonov.earnit.kids.family.application.notification;

import com.sashplatonov.earnit.kids.family.api.response.FamilyNotificationSettingsResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface FamilyNotificationService {
    OperationResult<FamilyNotificationSettingsResponse> getSettings(String familyId);

    OperationResult<Void> setPreference(String familyId, String scope, Integer childId,
                                        String key, boolean enabled);
}
