package com.sashplatonov.earnit.kids.family.application.social;

import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.api.response.FriendDto;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.social.FriendRepository;
import com.sashplatonov.earnit.kids.util.ServiceResults;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sashplatonov.earnit.kids.family.application.analytics.AnalyticsService;
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyFriendService {
    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final FriendRepository friendRepository;
    private final AnalyticsService analyticsService;

    public OperationResult<List<FriendDto>> searchByNickname(String nickname, int excludeChildId) {
        if (nickname == null || nickname.isBlank() || nickname.trim().length() < 3) {
            return OperationResult.success(List.of());
        }

        List<FriendDto> results = childRepository.searchByNickname(nickname.trim(), excludeChildId).stream()
            .map(child -> new FriendDto(child.getId(), child.getName(), child.getBalance()))
            .toList();

        return OperationResult.success(results);
    }

    public OperationResult<Void> addFriend(String familyId, int childId, int friendChildId) {
        if (childId == friendChildId) {
            return ServiceResults.failure("CANNOT_ADD_SELF", "family.cannotAddSelf");
        }

        if (familyRepository.getDbId(familyId).isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        if (childRepository.findByIdOptional(friendChildId).isEmpty()) {
            return ServiceResults.failure("USER_NOT_FOUND", "family.userNotFound");
        }

        boolean saved = friendRepository.addFriend(childId, friendChildId);
        if (!saved) {
            return ServiceResults.failure("FRIEND_ADD_FAILED", "family.friendAddFailed");
        }

        analyticsService.invalidateCache(familyId);
        return OperationResult.success(null);
    }

    public OperationResult<List<FriendDto>> getFriendsData(int childId) {
        var friendIds = friendRepository.getFriendChildIds(childId);
        List<FriendDto> friends = childRepository.findByChildIds(friendIds).stream()
            .map(friend -> new FriendDto(friend.getId(), friend.getName(), friend.getBalance()))
            .toList();
        return OperationResult.success(friends);
    }
}
