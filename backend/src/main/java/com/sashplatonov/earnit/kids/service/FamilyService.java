package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.List;
import java.util.Map;

public interface FamilyService {

    OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId);

    OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                        Map<String, Object> payload);

    OperationResult<ChildInfo> createChild(String familyId, String childName);

    OperationResult<Void> deleteChild(String familyId, int childId);

    OperationResult<Void> updateNickname(String familyId, int childId, String newName);

    OperationResult<Void> updateChildSettings(String familyId, int childId,
                                               String name, int dailyCoinLimit, int monthlyLimit);

    OperationResult<Void> updateChildTheme(int childId, String theme);

    OperationResult<List<FriendDto>> searchByNickname(String nickname, int excludeChildId);

    OperationResult<Void> addFriend(String familyId, int childId, int friendChildId);

    OperationResult<List<FriendDto>> getFriendsData(int childId);

    OperationResult<Map<String, Object>> getAnalyticsData(String familyId, Integer childId, String timeframe);

    OperationResult<PaginatedHistory> getHistory(int childId, int page, int limit);

    OperationResult<PaginatedRequests> getRequests(String familyId, int page, int limit);

    OperationResult<String> getChildLoginLink(int childId);

    OperationResult<String> regenerateChildToken(int childId);

    OperationResult<Void> updatePreference(String familyId, String key, Object value);
}
