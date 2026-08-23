package com.sashplatonov.earnit.kids.telegram.application.connection;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramInitDataVerifier;

import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramParentLinkChallengeEntity;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramSecurityAuditEventEntity;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramAccountConnectionResponse;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramParentLinkChallengeRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramSecurityAuditEventRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@ApplicationScoped
public class TelegramAccountConnectionServiceImpl implements TelegramAccountConnectionService {
    private static final Logger LOG = Logger.getLogger(TelegramAccountConnectionServiceImpl.class);
    private static final int LINK_TOKEN_BYTES = 32;
    private static final long LINK_TTL_SECONDS = 600;
    private static final String LINK_FAILED = "Telegram linking could not be completed.";

    private FamilyRepository families;
    private ParentAccountRepository parents;
    private FamilyParentMembershipRepository memberships;
    private TelegramIdentityRepository identities;
    private TelegramParentLinkChallengeRepository challenges;
    private TelegramSecurityAuditEventRepository audits;
    private TelegramInitDataVerifier verifier;
    private TelegramConfig config;
    private SecureTokenGenerator tokens;
    private TimeProvider timeProvider;
    TelegramFeatureGate featureGate;

    TelegramAccountConnectionServiceImpl() {
    }

    @Inject
    TelegramAccountConnectionServiceImpl(FamilyRepository families,
                                         ParentAccountRepository parents,
                                         FamilyParentMembershipRepository memberships,
                                         TelegramIdentityRepository identities,
                                         TelegramParentLinkChallengeRepository challenges,
                                         TelegramSecurityAuditEventRepository audits,
                                         TelegramInitDataVerifier verifier,
                                         TelegramConfig config,
                                         SecureTokenGenerator tokens,
                                         TimeProvider timeProvider,
                                         TelegramFeatureGate featureGate) {
        this.families = families;
        this.parents = parents;
        this.memberships = memberships;
        this.identities = identities;
        this.challenges = challenges;
        this.audits = audits;
        this.verifier = verifier;
        this.config = config;
        this.tokens = tokens;
        this.timeProvider = timeProvider;
        this.featureGate = featureGate;
    }

    @Override
    public OperationResult<TelegramAccountConnectionResponse> connection(String familyId, String email) {
        LOG.infof("Telegram connection check: familyId=%s, email=%s", familyId, email);
        return context(familyId, email).map(context -> OperationResult.success(connectionResponse(
            context, featureGate.isMiniAppEnabled(familyId))))
            .orElseGet(() -> {
                LOG.warnf("Telegram connection check failed: context not found for familyId=%s, email=%s",
                    familyId, email);
                return failure("TELEGRAM_CONNECTION_FORBIDDEN", "Account connection is unavailable.");
            });
    }


    private TelegramAccountConnectionResponse connectionResponse(ConnectionContext context, boolean miniAppEnabled) {
        var identity = identities.findActiveParentByParentAccountId(context.parentAccountId())
            .filter(candidate -> context.familyDbId().equals(candidate.getFamilyId()))
            .orElse(null);
        boolean telegramConnected = identity != null;
        LOG.infof("Telegram connection result: parentAccountId=%s, familyDbId=%s, telegramConnected=%s",
            context.parentAccountId(), context.familyDbId(), telegramConnected);
        return new TelegramAccountConnectionResponse(
            context.email(), true, telegramConnected, miniAppEnabled ? miniAppUrl().orElse(null) : null,
            identity == null ? null : identity.getTelegramUsername(),
            identity == null ? null : identity.getTelegramDisplayName());
    }

    @Override
    @Transactional
    public OperationResult<TelegramLinkLaunchResponse> start(String familyId, String email) {
        var context = context(familyId, email).orElse(null);
        if (context == null) {
            return failure("TELEGRAM_CONNECTION_FORBIDDEN", "Account connection is unavailable.");
        }
        String botUsername = config.botUsername().filter(value -> !value.isBlank()).orElse(null);
        if (botUsername == null) {
            return failure("TELEGRAM_LINK_UNAVAILABLE", "Telegram linking is not configured.");
        }
        if (identities.findActiveParentByParentAccountId(context.parentAccountId()).isPresent()) {
            return failure("TELEGRAM_ALREADY_LINKED", "A Telegram account is already linked.");
        }

        Instant now = timeProvider.now();
        String token = tokens.generateHexToken(LINK_TOKEN_BYTES);
        challenges.persist(TelegramParentLinkChallengeEntity.builder()
            .parentAccountId(context.parentAccountId())
            .familyId(context.familyDbId())
            .secretDigest(digest(token))
            .expiresAt(now.plusSeconds(LINK_TTL_SECONDS))
            .createdAt(now)
            .build());
        return OperationResult.success(new TelegramLinkLaunchResponse(botLaunchUrl(botUsername, token)));
    }

    @Override
    @Transactional
    public OperationResult<Void> complete(String token, String initData) {
        var verified = verifier.verify(initData).orElse(null);
        if (verified == null || token == null || token.isBlank()) {
            return failure("TELEGRAM_LINK_INVALID", LINK_FAILED);
        }
        var challenge = challenges.findByDigestForUpdate(digest(token)).orElse(null);
        Instant now = timeProvider.now();
        if (!isPending(challenge, now)) {
            return failure("TELEGRAM_LINK_INVALID", LINK_FAILED);
        }
        var family = families.findByDbId(challenge.getFamilyId()).orElse(null);
        var parent = parents.findByIdForUpdate(challenge.getParentAccountId()).orElse(null);
        if (!canComplete(challenge, family, parent)) {
            return failure("TELEGRAM_LINK_INVALID", LINK_FAILED);
        }
        if (!featureGate.isMiniAppEnabled(family.getFamilyId())) {
            return failure("TELEGRAM_LINK_UNAVAILABLE", "Telegram linking is not configured.");
        }
        if (isAlreadyLinked(verified.telegramUserId(), parent.getId())) {
            return failure("TELEGRAM_ALREADY_LINKED", "This Telegram account is already linked.");
        }

        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .familyId(family.getId())
            .parentAccountId(parent.getId())
            .telegramUserId(verified.telegramUserId())
            .role("parent")
            .active(true)
            .linkedAt(now)
            .build();
        identities.persist(identity);
        challenge.setConsumedAt(now);
        audits.persist(TelegramSecurityAuditEventEntity.builder()
            .familyId(family.getId())
            .identityId(identity.getId())
            .eventType("LINK")
            .actorReference(parent.getEmail())
            .createdAt(now)
            .build());
        return OperationResult.success(null);
    }

    @Override
    @Transactional
    public OperationResult<Void> unlink(String familyId, String email) {
        var context = context(familyId, email).orElse(null);
        if (context == null) {
            return failure("TELEGRAM_CONNECTION_FORBIDDEN", "Account connection is unavailable.");
        }
        var identity = identities.findActiveParentByParentAccountId(context.parentAccountId()).orElse(null);
        if (identity == null || !context.familyDbId().equals(identity.getFamilyId())) {
            return failure("TELEGRAM_NOT_LINKED", "Telegram is not linked to this account.");
        }
        Instant now = timeProvider.now();
        identity.setActive(false);
        identity.setUnlinkedAt(now);
        audits.persist(TelegramSecurityAuditEventEntity.builder()
            .familyId(context.familyDbId())
            .identityId(identity.getId())
            .eventType("UNLINK")
            .actorReference(context.email())
            .createdAt(now)
            .build());
        return OperationResult.success(null);
    }

    private java.util.Optional<ConnectionContext> context(String familyId, String email) {
        if (familyId == null || familyId.isBlank() || email == null || email.isBlank()) {
            return java.util.Optional.empty();
        }
        var family = families.findById(familyId).orElse(null);
        var parent = parents.findByEmail(email).orElse(null);
        if (family == null || parent == null || family.isBlocked() || parent.isBlocked()
            || memberships.findByParentAndFamily(parent.getId(), family.getId()).isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ConnectionContext(family.getId(), parent.getId(), parent.getEmail()));
    }

    private java.util.Optional<String> miniAppUrl() {
        return config.botUsername().filter(value -> !value.isBlank())
            .map(username -> botLaunchUrl(username, ""));
    }

    private boolean isPending(TelegramParentLinkChallengeEntity challenge, Instant now) {
        return challenge != null && challenge.getConsumedAt() == null && challenge.getExpiresAt().isAfter(now);
    }

    private boolean canComplete(TelegramParentLinkChallengeEntity challenge,
                                FamilyEntity family,
                                ParentAccountEntity parent) {
        if (family == null || parent == null || family.isBlocked() || parent.isBlocked()) {
            return false;
        }
        return memberships.findByParentAndFamily(parent.getId(), challenge.getFamilyId()).isPresent();
    }

    private boolean isAlreadyLinked(long telegramUserId, Integer parentAccountId) {
        return identities.findActiveByTelegramUserId(telegramUserId).isPresent()
            || identities.findActiveParentByParentAccountId(parentAccountId).isPresent();
    }

    private String botLaunchUrl(String username, String token) {
        return "https:" + '/' + '/' + "t.me/" + username + "?startapp=" + token;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private <T> OperationResult<T> failure(String errorCode, String message) {
        return OperationResult.failure(errorCode, message);
    }

    private record ConnectionContext(Integer familyDbId, Integer parentAccountId, String email) { }
}
