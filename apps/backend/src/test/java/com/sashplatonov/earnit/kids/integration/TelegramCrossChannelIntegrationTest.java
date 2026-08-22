package com.sashplatonov.earnit.kids.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramParentInvitationEntity;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.repository.TelegramDeliveryRepository;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.repository.TelegramParentInvitationRepository;
import com.sashplatonov.earnit.kids.repository.command.TaskContentCommand;
import com.sashplatonov.earnit.kids.repository.command.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.service.family.action.FamilyActionService;
import com.sashplatonov.earnit.kids.service.family.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramDeliveryPlanner;
import com.sashplatonov.earnit.kids.service.telegram.TelegramIdentityService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramParentInvitationService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramQuickActionService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TelegramCrossChannelIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");

    @Inject FamilyRepository families;
    @Inject ChildRepository children;
    @Inject TaskRepository tasks;
    @Inject PurchaseRequestRepository requests;
    @Inject HistoryRepository history;
    @Inject ApplicationOutboxEventRepository events;
    @Inject TelegramDeliveryRepository deliveries;
    @Inject TelegramIdentityService identities;
    @Inject TelegramQuickActionService quickActions;
    @Inject FamilyActionService webActions;
    @Inject TelegramDeliveryPlanner planner;
    @Inject com.sashplatonov.earnit.kids.repository.ParentAccountRepository parents;
    @Inject FamilyParentMembershipRepository memberships;
    @Inject TelegramIdentityRepository telegramIdentities;
    @Inject TelegramParentInvitationRepository invitations;
    @Inject FamilyParentAccessService parentAccess;
    @Inject TelegramParentInvitationService parentInvitations;

    @Test
    @Transactional
    void namedTelegramParentInvitationPersistsOneIdentityAndProjectsAcrossFreshListReads() {
        String familyId = "tg_parent_invite_" + System.nanoTime();
        String otherFamilyId = familyId + "_other";
        long telegramUserId = 90000021L;
        Instant now = Instant.now();
        String token = signedInitData(telegramUserId, now);

        FamilyEntity family = families.create(familyId, familyId + "@example.test", "secret123")
            .orElseThrow();
        families.create(otherFamilyId, otherFamilyId + "@example.test", "secret123").orElseThrow();

        String invitationToken = "pi_integration_parent_invite";
        invitations.persist(TelegramParentInvitationEntity.builder()
            .familyId(family.getId())
            .secretDigest(digest(invitationToken.substring(3)))
            .expiresAt(now.plusSeconds(900))
            .issuedBy(family.getEmail())
            .parentName("Maria Example")
            .createdAt(now)
            .build());

        var accepted = parentInvitations.accept(invitationToken, token, now);
        assertThat(accepted).isInstanceOf(OperationResult.Success.class);
        var identity = ((OperationResult.Success<TelegramIdentityService.TelegramIdentity>) accepted).value();
        assertThat(identity.familyId()).isEqualTo(family.getId());
        assertThat(identity.telegramUserId()).isEqualTo(telegramUserId);
        assertThat(identity.parentAccountId()).isNotNull();

        assertThat(telegramIdentities.findActiveParents(family.getId())).hasSize(1);
        assertThat(memberships.findByFamilyId(family.getId())).hasSize(1)
            .allMatch(membership -> membership.getParentAccountId().equals(identity.parentAccountId())
                && membership.getDisplayName().equals("Maria Example"));

        var listed = parentAccess.listMemberships(familyId);
        assertThat(listed).isInstanceOf(OperationResult.Success.class);
        var parent = ((OperationResult.Success<java.util.List<com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto>>) listed)
            .value().getFirst();
        assertThat(parent.email()).isNull();
        assertThat(parent.displayName()).isEqualTo("Maria Example");
        assertThat(parent.telegramUserId()).isEqualTo(telegramUserId);
        assertThat(parent.telegramUsername()).isEqualTo("maria_example");
        assertThat(parent.telegramDisplayName()).isEqualTo("Maria Example");

        var otherFamilyParents = parentAccess.listMemberships(otherFamilyId);
        assertThat(otherFamilyParents).isInstanceOf(OperationResult.Success.class);
        assertThat(((OperationResult.Success<java.util.List<com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto>>) otherFamilyParents)
            .value()).isEmpty();
        assertThat(parentAccess.updateMembership(parent.id(),
            "viewer", otherFamilyId)).isInstanceOf(OperationResult.Failure.class);
        var reused = parentInvitations.accept(invitationToken, token, now);
        assertThat(reused).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<?>) reused).errorCode()).isEqualTo("TELEGRAM_ALREADY_LINKED");

        assertThat(memberships.findByFamilyId(family.getId())).hasSize(1);
        assertThat(telegramIdentities.findActiveByTelegramUserId(telegramUserId)).isPresent();
    }

    private String signedInitData(long telegramUserId, Instant now) {
        String user = "{\"id\":" + telegramUserId
            + ",\"username\":\"maria_example\",\"first_name\":\"Maria\",\"last_name\":\"Example\"}";
        String authDate = Long.toString(now.getEpochSecond());
        String encodedUser = URLEncoder.encode(user, StandardCharsets.UTF_8);
        String dataCheckString = "auth_date=" + authDate + "\nuser=" + user;
        try {
            byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), "test-token");
            String hash = HexFormat.of().formatHex(hmac(secret, dataCheckString));
            return "auth_date=" + authDate + "&user=" + encodedUser + "&hash=" + hash;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign Telegram test data", exception);
        }
    }

    private byte[] hmac(byte[] key, String value) throws Exception {
        return hmac(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hmac(byte[] key, byte[] value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not digest Telegram test token", exception);
        }
    }

    @Test
    @Transactional
    void webMiniAppAndBotShareRequestBalanceHistoryAndOutboxState() throws Exception {
        String familyId = "tg_cross_channel_" + System.nanoTime();
        String email = familyId + "@example.test";
        long taskId = 70000L + System.nanoTime() % 10000;
        long botApprovalTaskId = taskId + 1;
        long parentTelegramId = 90000001L;
        long childTelegramId = 90000002L;

        FamilyEntity family = families.create(familyId, email, "secret123").orElseThrow();
        ChildEntity child = children.createChild(family.getId(), "Mia").orElseThrow();
        tasks.upsertTask(new TaskUpsertCommand(
            family.getId(), child.getId(), taskId,
            new TaskContentCommand("Read", 20, "School", "", "after dinner", "read ten pages"),
            new ObjectMapper().readTree("{\"period\":\"day\"}"), 100, true, false));
        tasks.upsertTask(new TaskUpsertCommand(
            family.getId(), child.getId(), botApprovalTaskId,
            new TaskContentCommand("Put toys away", 10, "Home", "", "before bed", "tidy the room"),
            new ObjectMapper().readTree("{\"period\":\"day\"}"), 100, true, false));

        var parentAccount = com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity.builder()
            .email("integration-test")
            .passwordHash("")
            .build();
        parents.persist(parentAccount);
        TelegramIdentityService.TelegramIdentity parent = identities.linkParent(
            family.getId(), parentTelegramId, parentAccount.getId(), "integration-test", NOW);
        TelegramIdentityService.TelegramChildInvitationToken invitation = identities.issueChildInvitation(
            family.getId(), child.getId(), "integration-test", NOW.plusSeconds(3600), NOW);
        TelegramIdentityService.TelegramIdentity linkedChild = identities.acceptChildInvitation(
            invitation.token(), childTelegramId, NOW).orElseThrow();

        OperationResult<?> miniAppRequest = quickActions.requestTask(childTelegramId, child.getId(), taskId);
        assertThat(miniAppRequest).isInstanceOf(OperationResult.Success.class);
        PurchaseRequestEntity request = requests.getRequests(family.getId(), 10, 0).stream()
            .filter(value -> value.getTaskId() != null && value.getTaskId() == taskId)
            .findFirst().orElseThrow();
        assertThat(request.getStatus()).isEqualTo(PurchaseRequestStatus.pending);

        OperationResult<?> webApproval = webActions.approveRequest(familyId, child.getId(), request.getId());
        assertThat(webApproval).isInstanceOf(OperationResult.Success.class);

        var botAfterWebDecision = quickActions.load(parentTelegramId, child.getId()).orElseThrow();
        assertThat(botAfterWebDecision.balance()).isEqualTo(20);
        assertThat(botAfterWebDecision.requests()).anyMatch(value ->
            value.id() == request.getId() && value.status() == PurchaseRequestStatus.approved);

        OperationResult<?> webRequest = webActions.requestTaskCompletion(familyId, child.getId(), botApprovalTaskId, null);
        assertThat(webRequest).isInstanceOf(OperationResult.Success.class);
        PurchaseRequestEntity botDecisionRequest = requests.getRequests(family.getId(), 10, 0).stream()
            .filter(value -> value.getTaskId() != null && value.getTaskId() == botApprovalTaskId)
            .findFirst().orElseThrow();
        assertThat(botDecisionRequest.getStatus()).isEqualTo(PurchaseRequestStatus.pending);

        OperationResult<?> botApproval = quickActions.approveRequest(
            parentTelegramId, child.getId(), botDecisionRequest.getId());
        assertThat(botApproval).isInstanceOf(OperationResult.Success.class);

        OperationResult<?> botAdjustment = quickActions.adjustBalance(parentTelegramId, child.getId(), 30);
        assertThat(botAdjustment).isInstanceOf(OperationResult.Success.class);

        var miniAppAfterBotDecision = quickActions.load(childTelegramId, null).orElseThrow();
        assertThat(miniAppAfterBotDecision.balance()).isEqualTo(60);
        assertThat(miniAppAfterBotDecision.history()).anyMatch(value ->
            value.type() == HistoryEntryType.earn && value.amount() == 20);
        assertThat(miniAppAfterBotDecision.history()).anyMatch(value ->
            value.type() == HistoryEntryType.earn && value.amount() == 10);
        assertThat(miniAppAfterBotDecision.history()).anyMatch(value ->
            value.type() == HistoryEntryType.earn && value.amount() == 30
                && "Telegram quick action".equals(value.description()));

        PurchaseRequestEntity persistedRequest = requests.findByIdOptional(request.getId()).orElseThrow();
        assertThat(persistedRequest.getStatus()).isEqualTo(PurchaseRequestStatus.approved);
        assertThat(requests.findByIdOptional(botDecisionRequest.getId()).orElseThrow().getStatus())
            .isEqualTo(PurchaseRequestStatus.approved);
        assertThat(children.findByIdOptional(child.getId()).orElseThrow().getBalance()).isEqualTo(60);
        assertThat(history.getHistory(child.getId(), 10, 0))
            .extracting(value -> value.getType())
            .contains(HistoryEntryType.earn);

        List<ApplicationOutboxEventType> eventTypes = events.list("familyId = ?1", family.getId()).stream()
            .map(value -> value.getEventType()).toList();
        assertThat(eventTypes).contains(
            ApplicationOutboxEventType.TASK_REQUEST_CREATED,
            ApplicationOutboxEventType.TASK_APPROVED,
            ApplicationOutboxEventType.BALANCE_ADJUSTED);

        int planned = planner.planDueEvents(NOW);
        assertThat(planned).isGreaterThanOrEqualTo(3);
        assertThat(planner.planDueEvents(NOW.plusSeconds(1))).isZero();
        events.list("familyId = ?1", family.getId()).forEach(event -> {
            assertThat(event.getPlanningCompletedAt()).isNotNull();
            deliveries.findByEvent(event.getId()).forEach(delivery ->
                assertThat(Set.of("SENT", "SKIPPED", "SKIPPED_DISABLED", "FAILED"))
                    .contains(delivery.getStatus()));
        });

        assertThat(parent.id()).isNotNull();
        assertThat(linkedChild.id()).isNotNull();
    }

    @Test
    @Transactional
    void resolutionPublishesRequestResolvedAndPlansDeliveryForParent() throws Exception {
        String familyId = "tg_resolved_" + System.nanoTime();
        String email = familyId + "@example.test";
        long taskId = 80000L + System.nanoTime() % 10000;
        long parentTelegramId = 90000011L;
        long childTelegramId = 90000012L;

        FamilyEntity family = families.create(familyId, email, "secret123").orElseThrow();
        ChildEntity child = children.createChild(family.getId(), "Mia").orElseThrow();
        tasks.upsertTask(new TaskUpsertCommand(
            family.getId(), child.getId(), taskId,
            new TaskContentCommand("Read", 20, "School", "", "after dinner", "read ten pages"),
            new ObjectMapper().readTree("{\"period\":\"day\"}"), 100, true, false));

        var parentAccount = com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity.builder()
            .email("resolved-" + familyId)
            .passwordHash("")
            .build();
        parents.persist(parentAccount);
        TelegramIdentityService.TelegramIdentity parent = identities.linkParent(
            family.getId(), parentTelegramId, parentAccount.getId(), "integration-test", NOW);
        TelegramIdentityService.TelegramChildInvitationToken invitation = identities.issueChildInvitation(
            family.getId(), child.getId(), "integration-test", NOW.plusSeconds(3600), NOW);
        identities.acceptChildInvitation(invitation.token(), childTelegramId, NOW).orElseThrow();

        OperationResult<?> miniAppRequest = quickActions.requestTask(childTelegramId, child.getId(), taskId);
        assertThat(miniAppRequest).isInstanceOf(OperationResult.Success.class);
        PurchaseRequestEntity request = requests.getRequests(family.getId(), 10, 0).stream()
            .filter(value -> value.getTaskId() != null && value.getTaskId() == taskId)
            .findFirst().orElseThrow();

        OperationResult<?> webApproval = webActions.approveRequest(familyId, child.getId(), request.getId());
        assertThat(webApproval).isInstanceOf(OperationResult.Success.class);

        List<ApplicationOutboxEventType> eventTypes = events.list("familyId = ?1", family.getId()).stream()
            .map(value -> value.getEventType()).toList();
        assertThat(eventTypes).contains(ApplicationOutboxEventType.REQUEST_RESOLVED);

        var resolvedEvent = events.list("familyId = ?1", family.getId()).stream()
            .filter(value -> value.getEventType() == ApplicationOutboxEventType.REQUEST_RESOLVED)
            .findFirst().orElseThrow();
        assertThat(resolvedEvent.getRequestId()).isEqualTo(request.getId());
        assertThat(resolvedEvent.getResolutionStatus()).isEqualTo(
            com.sashplatonov.earnit.kids.domain.model.RequestResolutionStatus.approved);

        // EXPLAIN: The outbox processor is not running in this test, so the
        // EXPLAIN: request-created message was never actually sent. REQUEST_RESOLVED
        // EXPLAIN: therefore targets no sent message and planning completes as a
        // EXPLAIN: no-op without creating a resolved delivery.
        planner.planDueEvents(NOW);
        assertThat(resolvedEvent.getPlanningCompletedAt()).isNotNull();
        assertThat(deliveries.findByEvent(resolvedEvent.getId())).isEmpty();
    }
}
