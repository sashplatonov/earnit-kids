package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.domain.model.invitation.ParentEmailInvitationEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.application.invitation.ParentInvitationTokenHasher;
import java.security.SecureRandom;
import java.time.Instant;

final class FamilyParentInvitationFactory {
  private static final SecureRandom RANDOM = new SecureRandom();

  private FamilyParentInvitationFactory() {}

  static ParentEmailInvitationEntity create(
      Integer familyId,
      String normalizedEmail,
      FamilyParentMembershipEntity.Permission permission,
      String invitedByEmail,
      ParentInvitationTokenHasher tokenHasher) {
    byte[] rawToken = new byte[32];
    RANDOM.nextBytes(rawToken);
    String rawTokenValue =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
    return ParentEmailInvitationEntity.builder()
        .familyId(familyId)
        .normalizedEmail(normalizedEmail)
        .permission(permission)
        .invitedByEmail(invitedByEmail)
        .tokenDigest(tokenHasher.digest(rawTokenValue, tokenHasher.activeKeyId()))
        .tokenDigestKeyId(tokenHasher.activeKeyId())
        .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
        .build();
  }
}
