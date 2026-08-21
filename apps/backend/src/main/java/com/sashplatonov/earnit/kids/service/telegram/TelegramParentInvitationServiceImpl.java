package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.MembershipStatus;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramParentInvitationEntity;
import com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.repository.TelegramParentInvitationRepository;
import com.sashplatonov.earnit.kids.service.common.ServiceResults;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@ApplicationScoped
public class TelegramParentInvitationServiceImpl implements TelegramParentInvitationService {
    private static final long INVITE_TTL_SECONDS = 900;
    private static final String UNAVAILABLE = "Telegram linking is not configured.";

    @Inject private FamilyRepository families;
    @Inject private TelegramParentInvitationRepository invitations;
    @Inject private ParentAccountRepository parents;
    @Inject private FamilyParentMembershipRepository memberships;
    @Inject private TelegramIdentityService identityService;
    @Inject private TelegramInitDataVerifier verifier;
    @Inject private TelegramConfig config;
    @Inject private SecureTokenGenerator tokens;
    @Inject private TimeProvider timeProvider;

    TelegramParentInvitationServiceImpl() {
    }

    @Override
    @Transactional
    public OperationResult<TelegramLinkLaunchResponse> invite(String familyId, String parentName, String issuedBy, Instant now) {
        String normalizedName = normalizeName(parentName);
        if (normalizedName == null) {
            return ServiceResults.failure("INVALID_PARENT_NAME", "parentAccess.nameInvalid");
        }
        Optional<Integer> dbIdOpt = families.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        String botUsername = config.botUsername().filter(value -> !value.isBlank()).orElse(null);
        if (botUsername == null) {
            return ServiceResults.failure("TELEGRAM_LINK_UNAVAILABLE", UNAVAILABLE);
        }
        String token = tokens.generateHexToken(32);
        invitations.persist(TelegramParentInvitationEntity.builder()
            .familyId(dbIdOpt.get())
            .secretDigest(digest(token))
            .expiresAt(now.plusSeconds(INVITE_TTL_SECONDS))
            .issuedBy(issuedBy)
            .parentName(normalizedName)
            .createdAt(now)
            .build());
        String launchUrl = "https://t.me/" + botUsername + "?startapp="
            + TelegramInviteToken.PARENT_INVITE_PREFIX + token;
        return OperationResult.success(new TelegramLinkLaunchResponse(launchUrl));
    }

    @Override
    @Transactional
    public OperationResult<TelegramIdentityService.TelegramIdentity> accept(String token, String initData, Instant now) {
        var verified = verifier.verify(initData).orElse(null);
        if (verified == null) {
            return ServiceResults.failure("TELEGRAM_INVITE_INVALID", "Telegram invitation is invalid or expired.");
        }
        String secret = stripPrefix(token);
        if (secret.isEmpty()) {
            return ServiceResults.failure("TELEGRAM_INVITE_INVALID", "Telegram invitation is invalid or expired.");
        }
        if (identityService.findActiveByTelegramUserId(verified.telegramUserId()).isPresent()) {
            return ServiceResults.failure("TELEGRAM_ALREADY_LINKED", "telegram.accountAlreadyLinked");
        }
        var invitation = invitations.findByDigestForUpdate(digest(secret)).orElse(null);
        if (invitation == null || invitation.getRevokedAt() != null || invitation.getConsumedAt() != null
            || !invitation.getExpiresAt().isAfter(now)) {
            return ServiceResults.failure("TELEGRAM_INVITE_INVALID", "Telegram invitation is invalid or expired.");
        }
        Optional<ParentAccountEntity> existing = Optional.empty();
        ParentAccountEntity parent = existing.orElseGet(() -> {
            var created = ParentAccountEntity.builder()
                .email(null).passwordHash("").verified(false).build();
            parents.persist(created);
            return created;
        });
        if (existing.isPresent()
            && memberships.findByParentAndFamily(parent.getId(), invitation.getFamilyId()).isPresent()) {
            return ServiceResults.failure("ALREADY_MEMBER", "parentAccess.alreadyMember");
        }
        memberships.persist(FamilyParentMembershipEntity.builder()
            .parentAccountId(parent.getId())
            .familyId(invitation.getFamilyId())
            .permission(FamilyParentMembershipEntity.Permission.editor)
            .status(MembershipStatus.active)
            .invitedByEmail(invitation.getIssuedBy())
            .displayName(invitation.getParentName())
            .invitedAt(now)
            .build());
        invitation.setConsumedAt(now);
        var identity = identityService.linkParent(
            invitation.getFamilyId(), verified.telegramUserId(), parent.getId(),
            invitation.getParentName(), verified.telegramUsername(), verified.telegramDisplayName(), now);
        return OperationResult.success(identity);
    }

    public OperationResult<TelegramIdentityService.TelegramIdentity> accept(String token, String initData,
                                                                             String ignoredEmail, Instant now) {
        if (ignoredEmail != null) {
            return acceptLegacy(token, initData, ignoredEmail, now);
        }
        return accept(token, initData, now);
    }

    private OperationResult<TelegramIdentityService.TelegramIdentity> acceptLegacy(
        String token, String initData, String email, Instant now) {
        if (email.isBlank() || email.length() > 254) {
            return ServiceResults.failure("INVALID_EMAIL", "auth.emailInvalid");
        }
        var verified = verifier.verify(initData).orElse(null);
        if (verified == null || identityService.findActiveByTelegramUserId(verified.telegramUserId()).isPresent()) {
            return ServiceResults.failure("TELEGRAM_INVITE_INVALID", "Telegram invitation is invalid or expired.");
        }
        String secret = stripPrefix(token);
        var invitation = secret.isEmpty() ? null : invitations.findByDigestForUpdate(digest(secret)).orElse(null);
        if (invitation == null || invitation.getRevokedAt() != null || invitation.getConsumedAt() != null
            || !invitation.getExpiresAt().isAfter(now)) {
            return ServiceResults.failure("TELEGRAM_INVITE_INVALID", "Telegram invitation is invalid or expired.");
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        var existing = parents.findByEmail(normalized);
        var parent = existing.orElseGet(() -> {
            var created = ParentAccountEntity.builder().email(normalized).passwordHash("").verified(false).build();
            parents.persist(created);
            return created;
        });
        memberships.persist(FamilyParentMembershipEntity.builder().parentAccountId(parent.getId())
            .familyId(invitation.getFamilyId()).permission(FamilyParentMembershipEntity.Permission.editor)
            .status(MembershipStatus.active).invitedByEmail(invitation.getIssuedBy()).invitedAt(now).build());
        invitation.setConsumedAt(now);
        return OperationResult.success(identityService.linkParent(
            invitation.getFamilyId(), verified.telegramUserId(), parent.getId(), normalized, now));
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        String normalized = name.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() || normalized.length() > 255 ? null : normalized;
    }

    private String stripPrefix(String token) {
        String prefix = TelegramInviteToken.PARENT_INVITE_PREFIX;
        return token != null && token.startsWith(prefix)
            ? token.substring(prefix.length())
            : "";
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
