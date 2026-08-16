package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.time.Instant;

public interface TelegramParentInvitationService {
    OperationResult<TelegramLinkLaunchResponse> invite(String familyId, String issuedBy, Instant now);

    OperationResult<TelegramIdentityService.TelegramIdentity> accept(String token, String initData,
                                                                      String email, Instant now);
}