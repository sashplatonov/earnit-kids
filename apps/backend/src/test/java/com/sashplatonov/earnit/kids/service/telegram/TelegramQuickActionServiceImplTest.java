package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.family.action.FamilyActionService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramQuickActionServiceImplTest {
    @Test
    void parentWithoutSelectedChildFallsBackToFirstChild() {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        FamilyRepository families = mock(FamilyRepository.class);
        FamilyService familyService = mock(FamilyService.class);
        FamilyActionService actions = mock(FamilyActionService.class);
        var service = new TelegramQuickActionServiceImpl(identities, families, familyService, actions);
        var identity = new TelegramIdentityService.TelegramIdentity(1, 10, null, 77L, "parent");
        var child = new ChildDto(42, "Alice", 0, 0, 0, null, null, null, null, null, null);

        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(identity));
        when(families.findFamilyIdByDbId(10)).thenReturn(Optional.of("family-1"));
        when(familyService.loadFamilyData("family-1", null, true))
            .thenReturn(OperationResult.success(data(List.of(child), null, null)));
        when(familyService.loadFamilyData("family-1", 42, true))
            .thenReturn(OperationResult.success(data(List.of(child), 42, "Alice")));

        var result = service.load(77L, null);

        assertThat(result).isPresent().get().extracting(TelegramQuickActionResponse::childId).isEqualTo(42);
        verify(familyService).loadFamilyData("family-1", null, true);
        verify(familyService).loadFamilyData("family-1", 42, true);
    }

    private FamilyDataResponse data(List<ChildDto> children, Integer childId, String nickname) {
        return new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(), List.of(),
            true, children, childId, nickname, null, null);
    }
}
