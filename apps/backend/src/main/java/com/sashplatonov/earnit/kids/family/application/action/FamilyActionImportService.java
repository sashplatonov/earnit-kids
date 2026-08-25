package com.sashplatonov.earnit.kids.family.application.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.family.api.request.ImportShopItemRowRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportShopItemsRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportTaskRowRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportTasksRequest;
import com.sashplatonov.earnit.kids.family.api.response.ImportValidationErrorItem;
import com.sashplatonov.earnit.kids.family.api.response.ImportValidationErrorResponse;
import com.sashplatonov.earnit.kids.exception.ImportValidationException;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskContentCommand;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.family.api.request.FrequencyPeriod;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;

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
                new TaskContentCommand(
                    row.title().trim(),
                    row.coins(),
                    trimToNull(row.groupName()),
                    trimToNull(row.comment()),
                    null,
                    null,
                    trimToNull(row.icon())
                ),
                frequency,
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
                false,
                trimToNull(row.icon()),
                null
            ));
        }

        return supportService.loadRefreshedFamilyData(familyId, request.childId(), true);
    }

    private <T> List<T> validatedImportRows(
        List<T> rows,
        BiConsumer<List<T>, List<ImportValidationErrorItem>> validator
    ) {
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
            String title = validateImportTitle(row.title(), rowNumber, seenTitles, errors);
            if (title == null) {
                continue;
            }

            validatePositiveCoins(row.coins(), rowNumber, "tasks.coinsRequired", errors);
            validateFrequencyRow(row.frequencyLimit(), row.frequencyPeriod(), rowNumber, errors);
            validateNonNegativeMoneyLimit(row.moneyLimit(), rowNumber, errors);
        }
    }

    private void validateShopImportRows(List<ImportShopItemRowRequest> rows, List<ImportValidationErrorItem> errors) {
        LinkedHashMap<String, Integer> seenNames = new LinkedHashMap<>();
        for (ImportShopItemRowRequest row : rows) {
            int rowNumber = row.rowNumber() > 0 ? row.rowNumber() : 0;
            String name = validateImportTitle(row.name(), rowNumber, seenNames, errors);
            if (name == null) {
                continue;
            }

            validatePositivePrice(row.price(), rowNumber, errors);
            validateFrequencyRow(row.frequencyLimit(), row.frequencyPeriod(), rowNumber, errors);
            validateNonNegativeMoneyLimit(row.moneyLimit(), rowNumber, errors);
        }
    }

    private String validateImportTitle(String rawTitle,
                                       int rowNumber,
                                       LinkedHashMap<String, Integer> seenValues,
                                       List<ImportValidationErrorItem> errors) {
        String title = trimToNull(rawTitle);
        if (title == null) {
            errors.add(new ImportValidationErrorItem(
                rowNumber,
                "title",
                BackendMessages.message("tasks.titleRequired")
            ));
            return null;
        }

        String duplicateKey = title.toLowerCase();
        if (seenValues.containsKey(duplicateKey)) {
            errors.add(new ImportValidationErrorItem(
                rowNumber,
                "title",
                BackendMessages.message("errors.duplicateRow")
            ));
            return null;
        }
        seenValues.put(duplicateKey, rowNumber);
        return title;
    }

    private void validatePositiveCoins(Integer coins, int rowNumber, String messageKey,
                                       List<ImportValidationErrorItem> errors) {
        if (coins == null || coins <= 0) {
            errors.add(new ImportValidationErrorItem(
                rowNumber,
                "coins",
                BackendMessages.message(messageKey)
            ));
        }
    }

    private void validatePositivePrice(Integer price, int rowNumber, List<ImportValidationErrorItem> errors) {
        if (price == null || price <= 0) {
            errors.add(new ImportValidationErrorItem(
                rowNumber,
                "price",
                BackendMessages.message("shop.priceRequired")
            ));
        }
    }

    private void validateFrequencyRow(Integer limit, FrequencyPeriod period, int rowNumber,
                                      List<ImportValidationErrorItem> errors) {
        if (limit == null && period == null) {
            return;
        }
        if (limit == null || limit <= 0) {
            errors.add(new ImportValidationErrorItem(
                rowNumber,
                "frequencyLimit",
                BackendMessages.message("tasks.frequencyLimitRequired")
            ));
        }
        if (period == null) {
            errors.add(new ImportValidationErrorItem(
                rowNumber,
                "frequencyPeriod",
                BackendMessages.message("tasks.frequencyPeriodRequired")
            ));
        }
    }

    private void validateNonNegativeMoneyLimit(Integer moneyLimit, int rowNumber,
                                               List<ImportValidationErrorItem> errors) {
        if (moneyLimit != null && moneyLimit < 0) {
            errors.add(new ImportValidationErrorItem(
                rowNumber,
                "moneyLimit",
                BackendMessages.message("errors.positiveNumberRequired")
            ));
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
