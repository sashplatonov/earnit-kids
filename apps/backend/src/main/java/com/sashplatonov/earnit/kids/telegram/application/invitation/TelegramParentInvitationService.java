package com.sashplatonov.earnit.kids.telegram.application.invitation;

import com.sashplatonov.earnit.kids.telegram.api.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import java.time.Instant;

public interface TelegramParentInvitationService {
  OperationResult<TelegramLinkLaunchResponse> invite(
      String familyId, String parentName, String issuedBy, Instant now);

  default OperationResult<TelegramLinkLaunchResponse> invite(
      String familyId, String issuedBy, Instant now) {
    return invite(familyId, "Telegram parent", issuedBy, now);
  }

  OperationResult<TelegramIdentityService.TelegramIdentity> accept(
      String token, String initData, Instant now);

  default OperationResult<TelegramIdentityService.TelegramIdentity> accept(
      String token, String initData, String ignoredEmail, Instant now) {
    return accept(token, initData, now);
  }
}
