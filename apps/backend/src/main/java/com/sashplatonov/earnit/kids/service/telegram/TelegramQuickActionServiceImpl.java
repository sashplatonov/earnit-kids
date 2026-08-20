package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.family.action.FamilyActionService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class TelegramQuickActionServiceImpl implements TelegramQuickActionService {
    private static final int MAX_ITEMS = 5;
    private final TelegramIdentityService identities;
    private final Supplier<FamilyRepository> families;
    private final Supplier<FamilyService> familyService;
    private final Supplier<FamilyActionService> actions;

    @Inject
    public TelegramQuickActionServiceImpl(TelegramIdentityService identities,
                                          FamilyRepository families,
                                          FamilyService familyService,
                                          FamilyActionService actions) {
        this.identities = identities;
        this.families = () -> families;
        this.familyService = () -> familyService;
        this.actions = () -> actions;
    }

    @Override
    public Optional<TelegramQuickActionResponse> load(long telegramUserId, Integer selectedChildId) {
        return identitiesFor(telegramUserId).flatMap(identity -> {
            return familyId(identity.familyId()).flatMap(familyId -> {
                if (!isParent(identity)) {
                    return Optional.ofNullable(identity.childId()).flatMap(childId ->
                        familyData(familyId, childId, false)
                            .map(data -> response(familyId, identity.role(), childId, data)));
                }
                Optional<FamilyDataResponse> overview = familyData(familyId, selectedChildId, true);
                return overview.flatMap(data -> {
                    Integer resolvedChildId = resolveChildId(selectedChildId, data);
                    return resolvedChildId == null ? Optional.empty() :
                        familyData(familyId, resolvedChildId, true)
                            .map(value -> response(familyId, identity.role(), resolvedChildId, value));
                });
            });
        });
    }

    @Override
    public OperationResult<TelegramQuickActionResponse> requestTask(long telegramUserId, int childId, long taskId) {
        return mutate(telegramUserId, childId, false,
            familyId -> actions.get().requestTaskCompletion(familyId, childId, taskId, null));
    }

    @Override
    public OperationResult<TelegramQuickActionResponse> requestReward(long telegramUserId, int childId, long rewardId) {
        return mutate(telegramUserId, childId, false,
            familyId -> actions.get().requestItemPurchase(familyId, childId, rewardId, null));
    }

    @Override
    public OperationResult<TelegramQuickActionResponse> approveRequest(long telegramUserId, int childId, long requestId) {
        return mutate(telegramUserId, childId, true,
            familyId -> actions.get().approveRequest(familyId, childId, requestId));
    }

    @Override
    public OperationResult<TelegramQuickActionResponse> rejectRequest(long telegramUserId, int childId, long requestId) {
        return mutate(telegramUserId, childId, true,
            familyId -> actions.get().rejectRequest(familyId, childId, requestId));
    }

    @Override
    public OperationResult<TelegramQuickActionResponse> adjustBalance(long telegramUserId, int childId, int amount) {
        return mutate(telegramUserId, childId, true,
            familyId -> actions.get().adjustBalance(familyId, childId, amount, "Telegram quick action"));
    }

    private OperationResult<TelegramQuickActionResponse> mutate(long telegramUserId,
                                                                int childId,
                                                                boolean parentOnly,
                                                                Action action) {
        Optional<TelegramIdentityService.TelegramIdentity> identity = identitiesFor(telegramUserId)
            .filter(value -> parentOnly ? isParent(value) :
                "child".equals(value.role()) && Integer.valueOf(childId).equals(value.childId()));
        if (identity.isEmpty()) {
            return OperationResult.failure("TELEGRAM_SCOPE", "Telegram identity cannot perform this action");
        }
        if (parentOnly && !isParent(identity.get())) {
            return OperationResult.failure("TELEGRAM_SCOPE", "Parent identity required");
        }
        if (!parentOnly && isParent(identity.get())) {
            return OperationResult.failure("TELEGRAM_SCOPE", "Child identity required");
        }
        Optional<String> familyId = familyId(identity.get().familyId());
        if (familyId.isEmpty()) {
            return OperationResult.failure("FAMILY_NOT_FOUND", "Family not found");
        }
        OperationResult<FamilyDataResponse> result = action.apply(familyId.get());
        if (result instanceof OperationResult.Failure<FamilyDataResponse> failure) {
            return OperationResult.failure(failure.errorCode(), failure.message());
        }
        FamilyDataResponse data = ((OperationResult.Success<FamilyDataResponse>) result).value();
        return OperationResult.success(response(familyId.get(), identity.get().role(), childId, data));
    }

    private Optional<TelegramIdentityService.TelegramIdentity> identitiesFor(long telegramUserId) {
        return identities.findActiveByTelegramUserId(telegramUserId);
    }

    private Optional<String> familyId(Integer familyDbId) {
        return families.get().findFamilyIdByDbId(familyDbId);
    }

    private Optional<FamilyDataResponse> familyData(String familyId, Integer childId, boolean parent) {
        OperationResult<FamilyDataResponse> result = familyService.get().loadFamilyData(familyId, childId, parent);
        if (result instanceof OperationResult.Success<FamilyDataResponse> success) {
            return Optional.of(success.value());
        }
        return Optional.empty();
    }

    private boolean isParent(TelegramIdentityService.TelegramIdentity identity) {
        return "parent".equals(identity.role());
    }

    // EXPLAIN: Only resolve to a child that is still visible/active in the
    // EXPLAIN: overview, so a just-deactivated child cannot leave the bot home
    // EXPLAIN: screen pointing at an inactive id while showing another child.
    private Integer resolveChildId(Integer selectedChildId, FamilyDataResponse data) {
        if (selectedChildId != null && data.children().stream()
            .anyMatch(child -> child.id() == selectedChildId)) {
            return selectedChildId;
        }
        if (data.lastSelectedChildId() != null && data.children().stream()
            .anyMatch(child -> child.id() == data.lastSelectedChildId())) {
            return data.lastSelectedChildId();
        }
        return data.children().isEmpty() ? null : data.children().getFirst().id();
    }

    private TelegramQuickActionResponse response(String familyId, String role, int childId,
                                                 FamilyDataResponse data) {
        return new TelegramQuickActionResponse(
            familyId,
            role,
            childId,
            data.childNickname() == null ? "Child" : data.childNickname(),
            data.balance(),
            data.children(),
            data.tasks().stream().filter(value -> value.isActive()).limit(MAX_ITEMS + 1).toList(),
            data.shop().stream().filter(value -> value.isActive()).limit(MAX_ITEMS + 1).toList(),
            data.requests().stream().limit(MAX_ITEMS).toList(),
            data.history().stream().limit(MAX_ITEMS).toList());
    }

    @FunctionalInterface
    private interface Action {
        OperationResult<FamilyDataResponse> apply(String familyId);
    }
}
