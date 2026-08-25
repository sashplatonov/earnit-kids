package com.sashplatonov.earnit.kids.family.application;

import com.sashplatonov.earnit.kids.family.api.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface FamilyPreferenceOperations {
  OperationResult<Void> updatePreference(String familyId, FamilyPreferenceKey key, Object value);
}
