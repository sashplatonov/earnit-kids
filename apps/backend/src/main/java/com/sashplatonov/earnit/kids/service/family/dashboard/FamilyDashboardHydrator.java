package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FriendRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.service.family.FamilyRelatedDetailsResolver;
import com.sashplatonov.earnit.kids.service.family.HistoryDtoMapper;
import com.sashplatonov.earnit.kids.service.family.RelatedEntityHydrator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDashboardHydrator {
    private final HistoryRepository historyRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FriendRepository friendRepository;
    private final ChildRepository childRepository;
    private final FamilyDashboardMapper mapper;
    private final HistoryDtoMapper historyDtoMapper;
    private final RelatedEntityHydrator relatedEntityHydrator;

    List<HistoryEntryDto> loadHistory(int familyDbId, int childId,
                                      Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                      Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        List<HistoryEntryEntity> rows = historyRepository.getHistory(childId, 50, 0);
        relatedEntityHydrator.hydrateMissingHistoryEntries(familyDbId, childId, rows, taskMap, shopMap);
        return rows.stream()
            .map(historyEntry -> historyDtoMapper.toDto(historyEntry, taskMap, shopMap))
            .toList();
    }

    List<RequestDto> loadRequests(int familyDbId,
                                  int activeChildId,
                                  boolean adminSession,
                                  Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                  Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        List<PurchaseRequestEntity> rows = purchaseRequestRepository.getRequests(familyDbId, 50, 0);
        relatedEntityHydrator.hydrateMissingRequests(familyDbId, rows, taskMap, shopMap);
        return rows.stream()
            .filter(request -> adminSession || Objects.equals(request.getChildId(), activeChildId))
            .map(request -> toRequestDto(
                request,
                Objects.equals(request.getChildId(), activeChildId) ? taskMap : Map.of(),
                Objects.equals(request.getChildId(), activeChildId) ? shopMap : Map.of()
            ))
            .toList();
    }

    List<FriendDto> loadFriends(int childId) {
        var friendIds = friendRepository.getFriendChildIds(childId);
        return childRepository.findByChildIds(friendIds).stream()
            .map(friend -> new FriendDto(friend.getId(), friend.getName(), friend.getBalance()))
            .toList();
    }

    private RequestDto toRequestDto(PurchaseRequestEntity request,
                                    Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                    Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        FamilyRelatedDetailsResolver.RequestDetails details =
            FamilyRelatedDetailsResolver.resolveRequestDetails(request, taskMap, shopMap, mapper);
        return new RequestDto(
            request.getId(),
            request.getTaskId(),
            details.taskName(),
            request.getItemId(),
            details.itemName(),
            details.title(),
            details.description(),
            details.groupName(),
            details.comment(),
            request.getNote(),
            request.getCoins(),
            request.getStatus(),
            request.getRequestType(),
            request.getMoneyAmount(),
            request.getCreatedAt() != null ? request.getCreatedAt().toString() : null,
            request.getChildId(),
            details.taskGroup(),
            details.itemGroup(),
            details.taskComment(),
            details.itemComment()
        );
    }
}
