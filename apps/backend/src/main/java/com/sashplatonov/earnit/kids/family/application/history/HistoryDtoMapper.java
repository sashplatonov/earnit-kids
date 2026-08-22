package com.sashplatonov.earnit.kids.family.application.history;

import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.family.api.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.family.api.response.ShopItemDto;
import com.sashplatonov.earnit.kids.family.api.response.TaskDto;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class HistoryDtoMapper {

    private final FamilyDashboardMapper mapper;

    public HistoryDtoMapper(FamilyDashboardMapper mapper) {
        this.mapper = mapper;
    }

    public HistoryEntryDto toDto(HistoryEntryEntity entry,
                                 Map<Long, TaskDto> taskMap,
                                 Map<Long, ShopItemDto> shopMap) {
        FamilyRelatedDetailsResolver.HistoryDetails details =
            FamilyRelatedDetailsResolver.resolveHistoryDetails(entry, taskMap, shopMap, mapper);
        return new HistoryEntryDto(entry.getExternalId(), entry.getType(), entry.getAmount(),
            details.title(),
            details.description(), entry.getMoneyAmount(), entry.getRelatedId(), details.taskId(),
            details.taskName(), details.itemId(), details.itemName(), details.groupName(), details.comment(),
            entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null,
            entry.getChildId());
    }
}
