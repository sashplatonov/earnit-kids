package com.sashplatonov.earnit.kids.family.application;

import com.sashplatonov.earnit.kids.family.api.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.family.application.notification.FamilyPreferenceService;
import com.sashplatonov.earnit.kids.util.OperationResult;

abstract class FamilyPreferenceOperationsDelegate implements FamilyPreferenceOperations {
  private FamilyPreferenceService preferences;

  FamilyPreferenceOperationsDelegate() {}

  protected final void initializePreferences(FamilyPreferenceService preferences) {
    this.preferences = preferences;
  }

  @Override
  public OperationResult<Void> updatePreference(
      String familyId, FamilyPreferenceKey key, Object value) {
    return preferences.updatePreference(familyId, key, value);
  }
}
