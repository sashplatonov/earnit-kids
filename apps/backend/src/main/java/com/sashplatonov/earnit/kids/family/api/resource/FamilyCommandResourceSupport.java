package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.action.FamilyActionService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;

abstract class FamilyCommandResourceSupport extends FamilyResourceSupport {

    protected final FamilyActionService familyActionService;

    FamilyCommandResourceSupport(FamilyActionService familyActionService,
                                 FamilyService familyService,
                                 WebSocketNotificationService webSocketNotificationService,
                                 FamilyParentAccessService familyParentAccessService) {
        super(familyService, webSocketNotificationService, familyParentAccessService);
        this.familyActionService = familyActionService;
    }
}
