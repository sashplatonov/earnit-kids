package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.dto.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.List;
import java.util.Map;

public interface FamilyService {

    OperationResult<FamilyDashboardShellResponse> loadFamilyShellData(String familyId, Integer childId,
                                                                      boolean adminSession);

    OperationResult<FamilyDashboardDetailResponse> loadFamilyDetailData(String familyId, Integer childId,
                                                                        boolean adminSession);

    OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean adminSession);

    OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                       Map<String, Object> payload,
                                                       boolean adminSession);

    OperationResult<ChildInfo> createChild(String familyId, String childName);

    OperationResult<Void> deleteChild(String familyId, int childId);

    OperationResult<Void> setChildActive(String familyId, int childId, boolean active);

    OperationResult<List<ChildDto>> listInactiveChildren(String familyId);

    OperationResult<Void> updateNickname(String familyId, int childId, String newName);

    OperationResult<Void> updateChildSettings(String familyId, int childId,
                                               String name, int dailyCoinLimit, int monthlyLimit,
                                               int dailyRewardLimit);

    OperationResult<Void> updateChildTheme(String familyId, int childId, ChildTheme theme);

    OperationResult<Void> updateChildGroupOrder(String familyId, int childId,
                                                GroupOrderSection section, List<String> groups,
                                                boolean personalOrder);

    OperationResult<List<FriendDto>> searchByNickname(String nickname, int excludeChildId);

    OperationResult<Void> addFriend(String familyId, int childId, int friendChildId);

    OperationResult<List<FriendDto>> getFriendsData(int childId);

    OperationResult<AnalyticsResponse> getAnalyticsData(String familyId, Integer childId, String timeframe);

    OperationResult<PaginatedHistory> getHistory(String familyId, int childId, int page, int limit);

    OperationResult<PaginatedRequests> getRequests(String familyId, int page, int limit);

    OperationResult<String> getChildLoginLink(String familyId, int childId);

    OperationResult<String> regenerateChildToken(String familyId, int childId);

    OperationResult<Void> updatePreference(String familyId, FamilyPreferenceKey key, Object value);
}
