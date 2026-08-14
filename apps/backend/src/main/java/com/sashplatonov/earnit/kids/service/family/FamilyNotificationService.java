package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.dto.response.FamilyNotificationSettingsResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface FamilyNotificationService {
    OperationResult<FamilyNotificationSettingsResponse> getSettings(String familyId);

    OperationResult<Void> setPreference(String familyId, String scope, Integer childId,
                                        String key, boolean enabled);
}
