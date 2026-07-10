package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.dto.request.ImportShopItemRowRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportShopItemsRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportTaskRowRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportTasksRequest;
import com.sashplatonov.earnit.kids.dto.response.ImportValidationErrorItem;
import com.sashplatonov.earnit.kids.dto.response.ImportValidationErrorResponse;
import com.sashplatonov.earnit.kids.exception.ImportValidationException;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.repository.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.dto.request.FrequencyPeriod;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiConsumer;

final class FamilyActionImportService {

    private final FamilyActionSupportService supportService;
    private final FamilyActionFrequencyService frequencyService;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;

    FamilyActionImportService(FamilyActionSupportService supportService,
                              FamilyActionFrequencyService frequencyService,
                              TaskRepository taskRepository,
                              ShopItemRepository shopItemRepository) {
        this.supportService = supportService;
        this.frequencyService = frequencyService;
        this.taskRepository = taskRepository;
        this.shopItemRepository = shopItemRepository;
    }

    FamilyDataResponse importTasks(String familyId, ImportTasksRequest request) {
        int familyDbId = supportService.requireImportFamilyDbId(familyId);
        supportService.requireImportChild(familyDbId, request.childId());
        List<ImportTaskRowRequest> rows = validatedImportRows(request.rows(), this::validateTaskImportRows);

        long nextTaskId = supportService.nextTaskBusinessId(familyDbId, request.childId());
        for (ImportTaskRowRequest row : rows) {
            JsonNode frequency = frequencyService.buildFrequencyNode(row.frequencyLimit(), row.frequencyPeriod());
            taskRepository.upsertTask(new TaskUpsertCommand(
                familyDbId,
                request.childId(),
                nextTaskId++,
                row.title().trim(),
                row.coins(),
                trimToNull(row.groupName()),
                frequency,
                trimToNull(row.comment()),
                row.moneyLimit(),
                row.isActive() == null || row.isActive(),
                false
            ));
        }

        return supportService.loadRefreshedFamilyData(familyId, request.childId(), true);
    }

    FamilyDataResponse importShopItems(String familyId, ImportShopItemsRequest request) {
        int familyDbId = supportService.requireImportFamilyDbId(familyId);
        supportService.requireImportChild(familyDbId, request.childId());
        List<ImportShopItemRowRequest> rows = validatedImportRows(request.rows(), this::validateShopImportRows);

        long nextItemId = supportService.nextShopItemBusinessId(familyDbId, request.childId());
        for (ImportShopItemRowRequest row : rows) {
            JsonNode frequency = frequencyService.buildFrequencyNode(row.frequencyLimit(), row.frequencyPeriod());
            shopItemRepository.upsertShopItem(new ShopItemUpsertCommand(
                familyDbId,
                request.childId(),
                nextItemId++,
                row.name().trim(),
                row.price(),
                trimToNull(row.groupName()),
                frequency,
                trimToNull(row.comment()),
                row.moneyLimit(),
                row.isActive() == null || row.isActive(),
                false
            ));
        }

        return supportService.loadRefreshedFamilyData(familyId, request.childId(), true);
    }

    private <T> List<T> validatedImportRows(List<T> rows, BiConsumer<List<T>, List<ImportValidationErrorItem>> validator) {
        List<ImportValidationErrorItem> errors = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            errors.add(new ImportValidationErrorItem(0, "rows", BackendMessages.message("errors.rowsRequired")));
        } else {
            validator.accept(rows, errors);
        }
        if (!errors.isEmpty()) {
            throw new ImportValidationException(ImportValidationErrorResponse.of(
                BackendMessages.message("errors.validationFailed"),
                errors
            ));
        }
        return rows;
    }

    private void validateTaskImportRows(List<ImportTaskRowRequest> rows, List<ImportValidationErrorItem> errors) {
        LinkedHashMap<String, Integer> seenTitles = new LinkedHashMap<>();
        for (ImportTaskRowRequest row : rows) {
            int rowNumber = row.rowNumber() > 0 ? row.rowNumber() : 0;
            String title = trimToNull(row.title());
            if (title == null) {
                errors.add(new ImportValidationErrorItem(
                    rowNumber,
                    "title",
                    BackendMessages.message("tasks.titleRequired")
                ));
                continue;
            }
            String duplicateKey = title.toLowerCase();
            if (seenTitles.containsKey(duplicateKey)) {
                errors.add(new ImportValidationErrorItem(
                    rowNumber,
                    "title",
                    BackendMessages.message("errors.duplicateRow")
                ));
                continue;
            }
            seenTitles.put(duplicateKey, rowNumber);

            if (row.coins() == null || row.coins() <= 0) {
                errors.add(new ImportValidationErrorItem(
                    rowNumber,
                    "coins",
                    BackendMessages.message("tasks.coinsRequired")
                ));
            }

            if (row.frequencyLimit() != null || row.frequencyPeriod() != null) {
                if (row.frequencyLimit() == null || row.frequencyLimit() <= 0) {
                    errors.add(new ImportValidationErrorItem(
                        rowNumber,
                        "frequencyLimit",
                        BackendMessages.message("tasks.frequencyLimitRequired")
                    ));
                }
                if (!isValidFrequencyPeriod(row.frequencyPeriod())) {
                    errors.add(new ImportValidationErrorItem(
                        rowNumber,
                        "frequencyPeriod",
                        BackendMessages.message("tasks.frequencyPeriodRequired")
                    ));
                }
            }

            if (row.moneyLimit() != null && row.moneyLimit() < 0) {
                errors.add(new ImportValidationErrorItem(
                    rowNumber,
                    "moneyLimit",
                    BackendMessages.message("errors.positiveNumberRequired")
                ));
            }
        }
    }

    private void validateShopImportRows(List<ImportShopItemRowRequest> rows, List<ImportValidationErrorItem> errors) {
        LinkedHashMap<String, Integer> seenNames = new LinkedHashMap<>();
        for (ImportShopItemRowRequest row : rows) {
            int rowNumber = row.rowNumber() > 0 ? row.rowNumber() : 0;
            String name = trimToNull(row.name());
            if (name == null) {
                errors.add(new ImportValidationErrorItem(
                    rowNumber,
                    "name",
                    BackendMessages.message("shop.nameRequired")
                ));
                continue;
            }
            String duplicateKey = name.toLowerCase();
            if (seenNames.containsKey(duplicateKey)) {
                errors.add(new ImportValidationErrorItem(
                    rowNumber,
                    "name",
                    BackendMessages.message("errors.duplicateRow")
                ));
                continue;
            }
            seenNames.put(duplicateKey, rowNumber);

            if (row.price() == null || row.price() <= 0) {
                errors.add(new ImportValidationErrorItem(
                    rowNumber,
                    "price",
                    BackendMessages.message("shop.priceRequired")
                ));
            }
            if (row.frequencyLimit() != null || row.frequencyPeriod() != null) {
                if (row.frequencyLimit() == null || row.frequencyLimit() <= 0) {
                    errors.add(new ImportValidationErrorItem(
                        rowNumber,
                        "frequencyLimit",
                        BackendMessages.message("tasks.frequencyLimitRequired")
                    ));
                }
                if (!isValidFrequencyPeriod(row.frequencyPeriod())) {
                    errors.add(new ImportValidationErrorItem(
                        rowNumber,
                        "frequencyPeriod",
                        BackendMessages.message("tasks.frequencyPeriodRequired")
                    ));
                }
            }
            if (row.moneyLimit() != null && row.moneyLimit() < 0) {
                errors.add(new ImportValidationErrorItem(
                    rowNumber,
                    "moneyLimit",
                    BackendMessages.message("errors.positiveNumberRequired")
                ));
            }
        }
    }

    private boolean isValidFrequencyPeriod(FrequencyPeriod period) {
        return period != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
