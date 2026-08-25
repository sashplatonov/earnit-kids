package com.sashplatonov.earnit.kids.family.application.invitation;

import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.invitation.ParentEmailInvitationEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.invitation.ParentEmailInvitationRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.identity.domain.model.OAuthInvitationContinuationEntity;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.OAuthInvitationContinuationRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.platform.security.SecurityAuditWriter;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ParentInvitationService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String FLOW_COOKIE = "invite_flow";

  private final FamilyRepository familyRepository;
  private final ParentEmailInvitationRepository invitationRepository;
  private final FamilyParentMembershipRepository membershipRepository;
  private final ParentAccountRepository parentAccountRepository;
  private final OAuthInvitationContinuationRepository continuationRepository;
  private final ParentInvitationEmailSender emailSender;
  private final SecurityAuditWriter auditWriter;
  private final ParentInvitationTokenHasher tokenHasher;

  @Transactional
  public OperationResult<ParentMembershipDto> create(
      String familyId, String email, String permission, String invitedByEmail) {
    FamilyEntity family = familyRepository.findById(familyId).orElse(null);
    String normalized = normalize(email);
    Optional<FamilyParentMembershipEntity.Permission> parsed = parse(permission);
    if (family == null || normalized == null || parsed.isEmpty()) {
      return failure("PARENT_INVITATION_INVALID");
    }
    if (invitationRepository.findPending(family.getId(), normalized).isPresent()) {
      return failure("PARENT_INVITATION_EXISTS");
    }
    if (parentAccountRepository
        .findByEmail(normalized)
        .flatMap(
            parent -> membershipRepository.findByParentAndFamily(parent.getId(), family.getId()))
        .isPresent()) {
      return failure("PARENT_ALREADY_MEMBER");
    }

    String token = randomToken();
    ParentEmailInvitationEntity invitation =
        ParentEmailInvitationEntity.builder()
            .familyId(family.getId())
            .normalizedEmail(normalized)
            .permission(parsed.get())
            .tokenDigest(tokenHasher.digest(token, tokenHasher.activeKeyId()))
            .tokenDigestKeyId(tokenHasher.activeKeyId())
            .invitedByEmail(invitedByEmail)
            .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
            .build();
    invitationRepository.persist(invitation);
    try {
      emailSender.send(
          new ParentInvitationEmailSender.Email(
              normalized,
              publicInviteUrl(token),
              parsed.get().name(),
              invitation.getExpiresAt().toString()));
    } catch (RuntimeException ex) {
      invitationRepository.revoke(invitation.getId(), Instant.now());
      return failure("PARENT_INVITATION_DELIVERY_FAILED");
    }
    auditWriter.write(
        family.getId(), null, invitedByEmail, normalized, "parent_invitation_sent", "CREATED");
    return OperationResult.success(
        new ParentMembershipDto(
            null,
            normalized,
            null,
            null,
            null,
            null,
            parsed.get(),
            null,
            ParentEmailInvitationEntity.Status.pending.name()));
  }

  @Transactional
  public OperationResult<Void> revoke(String familyId, Integer invitationId, String actorEmail) {
    FamilyEntity family = familyRepository.findById(familyId).orElse(null);
    if (family == null
        || invitationRepository.revokeForFamily(invitationId, family.getId(), Instant.now()) == 0) {
      return failure("PARENT_INVITATION_NOT_FOUND");
    }
    auditWriter.write(
        family.getId(), null, actorEmail, null, "parent_invitation_revoked", "REVOKED");
    return OperationResult.success(null);
  }

  @Transactional
  public OperationResult<Void> resend(String familyId, Integer invitationId, String actorEmail) {
    FamilyEntity family = familyRepository.findById(familyId).orElse(null);
    ParentEmailInvitationEntity invitation =
        family == null ? null : invitationRepository.findByIdOptional(invitationId).orElse(null);
    if (family == null
        || invitation == null
        || !family.getId().equals(invitation.getFamilyId())
        || invitation.getStatus() != ParentEmailInvitationEntity.Status.pending) {
      return failure("PARENT_INVITATION_NOT_FOUND");
    }
    String token = randomToken();
    Instant expiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60);
    if (invitationRepository.rotate(
            invitationId,
            family.getId(),
            tokenHasher.digest(token, tokenHasher.activeKeyId()),
            tokenHasher.activeKeyId(),
            expiresAt,
            Instant.now())
        == 0) {
      return failure("PARENT_INVITATION_NOT_FOUND");
    }
    try {
      emailSender.send(
          new ParentInvitationEmailSender.Email(
              invitation.getNormalizedEmail(),
              publicInviteUrl(token),
              invitation.getPermission().name(),
              expiresAt.toString()));
    } catch (RuntimeException ex) {
      return failure("PARENT_INVITATION_DELIVERY_FAILED");
    }
    auditWriter.write(
        family.getId(),
        null,
        actorEmail,
        invitation.getNormalizedEmail(),
        "parent_invitation_resent",
        "RESENT");
    return OperationResult.success(null);
  }

  @Transactional
  public OperationResult<Continuation> begin(
      String token, String browserBinding, String postLoginPath) {
    ParentEmailInvitationEntity invitation = findByToken(token);
    if (invitation == null
        || invitation.getStatus() != ParentEmailInvitationEntity.Status.pending
        || invitation.getExpiresAt().isBefore(Instant.now())) {
      return failure("PARENT_INVITATION_INVALID");
    }
    String path =
        postLoginPath != null && postLoginPath.startsWith("/invite/parent")
            ? postLoginPath
            : "/invite/parent";
    String nonce = randomToken();
    OAuthInvitationContinuationEntity continuation =
        OAuthInvitationContinuationEntity.builder()
            .invitationId(invitation.getId())
            .nonceDigest(digest(nonce))
            .browserBindingDigest(digest(browserBinding))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .postLoginPath(path)
            .build();
    continuationRepository.persist(continuation);
    return OperationResult.success(new Continuation(continuation.getId(), nonce));
  }

  @Transactional
  public boolean consumeOAuth(Integer continuationId, String browserBinding, String email) {
    return continuationRepository.consume(
            continuationId, digest(browserBinding), Instant.now(), normalize(email))
        == 1;
  }

  @Transactional
  public OperationResult<Void> accept(
      Integer continuationId, String browserBinding, String authenticatedEmail) {
    OAuthInvitationContinuationEntity continuation =
        continuationRepository
            .findConsumed(continuationId, digest(browserBinding), Instant.now())
            .orElse(null);
    if (continuation == null
        || !normalize(authenticatedEmail).equals(continuation.getVerifiedEmail())) {
      return failure("PARENT_INVITATION_NOT_AUTHORIZED");
    }
    ParentEmailInvitationEntity invitation =
        invitationRepository.findByIdOptional(continuation.getInvitationId()).orElse(null);
    if (invitation == null
        || !invitation.getNormalizedEmail().equals(continuation.getVerifiedEmail())) {
      return failure("PARENT_INVITATION_NOT_AUTHORIZED");
    }
    if (invitationRepository.consume(invitation.getId(), Instant.now()) == 0) {
      return OperationResult.success(null);
    }
    var parent = parentAccountRepository.findByEmail(continuation.getVerifiedEmail()).orElse(null);
    if (parent == null) {
      return failure("PARENT_INVITATION_NOT_AUTHORIZED");
    }
    if (membershipRepository
        .findByParentAndFamily(parent.getId(), invitation.getFamilyId())
        .isEmpty()) {
      membershipRepository.persist(
          FamilyParentMembershipEntity.builder()
              .parentAccountId(parent.getId())
              .familyId(invitation.getFamilyId())
              .permission(invitation.getPermission())
              .status(
                  com.sashplatonov.earnit.kids.family.domain.model.membership.MembershipStatus
                      .active)
              .invitedByEmail(invitation.getInvitedByEmail())
              .invitedAt(Instant.now())
              .build());
    }
    auditWriter.write(
        invitation.getFamilyId(),
        parent.getId(),
        continuation.getVerifiedEmail(),
        continuation.getVerifiedEmail(),
        "parent_invitation_accepted",
        "ACCEPTED");
    return OperationResult.success(null);
  }

  public record Continuation(Integer id, String nonce) {}

  private <T> OperationResult<T> failure(String code) {
    return OperationResult.failure(code, code);
  }

  private String normalize(String email) {
    if (email == null) {
      return null;
    }
    String value = email.trim().toLowerCase(Locale.ROOT);
    return value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") ? value : null;
  }

  private Optional<FamilyParentMembershipEntity.Permission> parse(String permission) {
    try {
      return Optional.of(FamilyParentMembershipEntity.Permission.valueOf(permission));
    } catch (RuntimeException ex) {
      return Optional.empty();
    }
  }

  private String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private ParentEmailInvitationEntity findByToken(String token) {
    for (String keyId : tokenHasher.verificationKeyIds()) {
      Optional<ParentEmailInvitationEntity> invitation =
          invitationRepository.findByDigest(tokenHasher.digest(token, keyId), keyId);
      if (invitation.isPresent()) {
        return invitation.get();
      }
    }
    return null;
  }

  private String digest(String raw) {
    if (raw == null) {
      return "";
    }
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private String publicInviteUrl(String token) {
    return System.getenv().getOrDefault("APP_URL", "http://localhost:3000")
        + "/invite/parent/"
        + token;
  }
}
