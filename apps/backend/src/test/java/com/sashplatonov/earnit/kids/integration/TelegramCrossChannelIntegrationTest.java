package com.sashplatonov.earnit.kids.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.repository.TelegramDeliveryRepository;
import com.sashplatonov.earnit.kids.repository.command.TaskContentCommand;
import com.sashplatonov.earnit.kids.repository.command.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.service.family.action.FamilyActionService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramDeliveryPlanner;
import com.sashplatonov.earnit.kids.service.telegram.TelegramIdentityService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramQuickActionService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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

    @Test
    @Transactional
    void webMiniAppAndBotShareRequestBalanceHistoryAndOutboxState() throws Exception {
        String familyId = "tg_cross_channel_" + System.nanoTime();
        String email = familyId + "@example.test";
        long taskId = 70000L + System.nanoTime() % 10000;
        long botApprovalTaskId = taskId + 1;
        long parentTelegramId = 90000001L;
        long childTelegramId = 90000002L;

        FamilyEntity family = families.create(familyId, email, "secret123", true, null).orElseThrow();
        ChildEntity child = children.createChild(family.getId(), "Mia").orElseThrow();
        tasks.upsertTask(new TaskUpsertCommand(
            family.getId(), child.getId(), taskId,
            new TaskContentCommand("Read", 20, "School", "", "after dinner", "read ten pages"),
            new ObjectMapper().readTree("{\"period\":\"day\"}"), 100, true, false));
        tasks.upsertTask(new TaskUpsertCommand(
            family.getId(), child.getId(), botApprovalTaskId,
            new TaskContentCommand("Put toys away", 10, "Home", "", "before bed", "tidy the room"),
            new ObjectMapper().readTree("{\"period\":\"day\"}"), 100, true, false));

        TelegramIdentityService.TelegramIdentity parent = identities.linkParent(
            family.getId(), parentTelegramId, "integration-test", NOW);
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
}
