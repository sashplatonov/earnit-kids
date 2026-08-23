package com.sashplatonov.earnit.kids.family.application.invitation;

import com.sashplatonov.earnit.kids.family.domain.model.child.ChildStatus;
import com.sashplatonov.earnit.kids.family.domain.model.invitation.ChildMagicLinkInvitationEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.invitation.ChildMagicLinkInvitationRepository;
import com.sashplatonov.earnit.kids.config.auth.JwtCompatibilityConfig;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ChildMagicLinkInvitationService {
    private static final long EXPIRY_SECONDS = 15 * 60L;
    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final ChildMagicLinkInvitationRepository invitationRepository;
    private final SecureTokenGenerator tokenGenerator;
    private final JwtCompatibilityConfig jwtConfig;

    @Transactional
    public OperationResult<String> issue(String familyId, int childId) {
        var family = familyRepository.findById(familyId).orElse(null);
        if (family == null) return failure("CHILD_NOT_FOUND");
        var child = childRepository.findByIdOptional(childId).orElse(null);
        if (child == null || !family.getId().equals(child.getFamilyDbId())
            || !ChildStatus.ACTIVE.name().equals(child.getStatus())) return failure("CHILD_NOT_FOUND");
        invitationRepository.revokePending(family.getId(), childId, Instant.now());
        String token = tokenGenerator.generateHexToken(32);
        invitationRepository.persist(ChildMagicLinkInvitationEntity.builder()
            .familyId(family.getId()).childId(childId).tokenDigest(digest(token))
            .expiresAt(Instant.now().plusSeconds(EXPIRY_SECONDS)).build());
        return OperationResult.success(token);
    }

    @Transactional
    public OperationResult<Void> revoke(String familyId, int childId) {
        var family = familyRepository.findById(familyId).orElse(null);
        if (family == null || invitationRepository.revoke(family.getId(), childId, Instant.now()) == 0) {
            return failure("CHILD_INVITATION_NOT_FOUND");
        }
        return OperationResult.success(null);
    }

    public OperationResult<List<Status>> status(String familyId, int childId) {
        var family = familyRepository.findById(familyId).orElse(null);
        if (family == null || childRepository.findByIdOptional(childId)
            .filter(child -> family.getId().equals(child.getFamilyDbId())).isEmpty()) return failure("CHILD_NOT_FOUND");
        return OperationResult.success(invitationRepository.findByChild(family.getId(), childId).stream()
            .map(invitation -> new Status(invitation.getId(), invitation.getStatus().name(), invitation.getExpiresAt()))
            .toList());
    }

    @Transactional
    public OperationResult<AuthPayload> consume(String token) {
        if (token == null || token.isBlank()) return failure("CHILD_INVITATION_INVALID");
        var invitation = invitationRepository.findByDigest(digest(token)).orElse(null);
        if (invitation == null || invitation.getStatus() != ChildMagicLinkInvitationEntity.Status.pending
            || invitation.getExpiresAt().isBefore(Instant.now())) return failure("CHILD_INVITATION_INVALID");
        if (invitationRepository.consume(invitation.getId(), Instant.now()) != 1) return failure("CHILD_INVITATION_INVALID");
        var child = childRepository.findByIdOptional(invitation.getChildId()).orElse(null);
        var family = familyRepository.findByIdOptional(invitation.getFamilyId()).orElse(null);
        if (child == null || family == null || !ChildStatus.ACTIVE.name().equals(child.getStatus())) {
            return failure("CHILD_INVITATION_INVALID");
        }
        return OperationResult.success(new AuthPayload(family.getFamilyId(), family.getEmail(), "child",
            child.getId(), child.getName(), "child", null, false));
    }

    public record Status(Integer id, String status, Instant expiresAt) { }

    private String digest(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtConfig.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to digest child invitation", ex);
        }
    }

    private <T> OperationResult<T> failure(String code) {
        return OperationResult.failure(code, code);
    }
}
