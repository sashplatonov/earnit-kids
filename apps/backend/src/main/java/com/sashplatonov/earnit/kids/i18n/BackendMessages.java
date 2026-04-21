package com.sashplatonov.earnit.kids.i18n;

import java.util.Map;

public final class BackendMessages {
    private static final Map<String, String> EN_MESSAGES = Map.ofEntries(
        Map.entry("status.badRequest", "Bad Request"),
        Map.entry("status.unauthorized", "Unauthorized"),
        Map.entry("status.forbidden", "Forbidden"),
        Map.entry("status.notFound", "Not Found"),
        Map.entry("status.conflict", "Conflict"),
        Map.entry("status.internalError", "Internal Server Error"),
        Map.entry("status.requestFailed", "Request Failed"),
        Map.entry("errors.validationFailed", "Validation failed"),
        Map.entry("errors.internalServerError", "Internal server error"),
        Map.entry("errors.unauthorized", "Unauthorized"),
        Map.entry("errors.forbidden", "Forbidden"),
        Map.entry("errors.childIdRequired", "childId is required"),
        Map.entry("errors.friendIdRequired", "friendId is required"),
        Map.entry("errors.keyRequired", "key is required"),
        Map.entry("errors.isBlockedRequired", "isBlocked is required"),
        Map.entry("security.csrfInvalid", "CSRF token is missing or invalid"),
        Map.entry("auth.invalidCredentials", "Invalid credentials"),
        Map.entry("auth.invalidAdminPassword", "Invalid super-admin password"),
        Map.entry("auth.accountBlocked", "Account is blocked"),
        Map.entry("auth.emailNotVerified", "Email is not verified. Check your inbox."),
        Map.entry("auth.invalidPassword", "Invalid password"),
        Map.entry("auth.tokenMissing", "Token is missing"),
        Map.entry("auth.invalidLink", "Invalid link"),
        Map.entry("auth.familyNotFound", "Family not found"),
        Map.entry("auth.emailRegistered", "Email is already registered"),
        Map.entry("auth.weakParentPassword", "Parent password is too weak"),
        Map.entry("auth.passwordRecoveryDisabled", "Password recovery is disabled"),
        Map.entry("auth.weakPassword", "Password is too weak"),
        Map.entry("auth.newPasswordMustDiffer", "The new password must differ from the old password"),
        Map.entry("auth.passwordUpdateFailed", "Could not update the password"),
        Map.entry("auth.invalidOrExpiredResetLink", "Invalid or expired reset link"),
        Map.entry("auth.invalidVerificationToken", "Invalid verification token"),
        Map.entry("family.familyNotFound", "Family not found"),
        Map.entry("family.childNotFound", "Child not found"),
        Map.entry("family.childNameRequired", "Child name is required"),
        Map.entry("family.childNameTooLong", "Child name is too long"),
        Map.entry("family.childNameTaken", "This name is already taken"),
        Map.entry("family.createFailed", "Could not create child"),
        Map.entry("family.nameRequired", "Name is required"),
        Map.entry("family.invalidTheme", "Invalid theme: {theme}"),
        Map.entry("family.invalidGroupOrderSection", "Unknown group-order section: {section}"),
        Map.entry("family.groupOrderSaveFailed", "Could not save group order"),
        Map.entry("family.cannotAddSelf", "Cannot add yourself"),
        Map.entry("family.userNotFound", "User not found"),
        Map.entry("family.friendAddFailed", "Already friends or could not add friend"),
        Map.entry("family.tokenGenerationFailed", "Could not generate token"),
        Map.entry("family.invalidChildId", "Invalid child identifier"),
        Map.entry("family.unknownSetting", "Unknown setting: {key}"),
        Map.entry("analytics.taskFallback", "Task"),
        Map.entry("analytics.itemFallback", "Item"),
        Map.entry("analytics.recommendationStale", "Not completed recently"),
        Map.entry("analytics.recommendationRepeat", "Worth repeating"),
        Map.entry("tasks.notFound", "Task not found"),
        Map.entry("shop.itemNotFound", "Shop item not found"),
        Map.entry("balance.insufficient", "Insufficient balance"),
        Map.entry("requests.notFound", "Request not found"),
        Map.entry("requests.alreadyProcessed", "Request is already processed"),
        Map.entry("history.entryNotFound", "History entry not found"),
        Map.entry("balance.amountZero", "Amount must not be zero"),
        Map.entry("balance.adjustmentCredit", "Credit"),
        Map.entry("balance.adjustmentDebit", "Debit"),
        Map.entry("super.notConfigured", "Super-admin is not configured"),
        Map.entry("super.invalidCurrentPassword", "Invalid current password"),
        Map.entry("super.newPasswordMustDifferOld", "The new password must differ from the old password"),
        Map.entry("super.newPasswordMustDifferCurrent", "The new password must differ from the current password"),
        Map.entry("super.familyHasNoChildren", "Family has no children"),
        Map.entry("super.catalogSaveFailed", "Could not save catalog"),
        Map.entry("limits.taskTarget", "this task"),
        Map.entry("limits.itemTarget", "this item"),
        Map.entry("limits.day", "The request limit for {target} is already used for today. Next reset at {resetAt}."),
        Map.entry("limits.week", "The request limit for {target} is already used for this week. Next reset at {resetAt}."),
        Map.entry("limits.month", "The request limit for {target} is already used for this month. Next reset at {resetAt}."),
        Map.entry("limits.year", "The request limit for {target} is already used for this year. Next reset at {resetAt}."),
        Map.entry("super.telegramNotConfigured", "Telegram alerts are not configured"),
        Map.entry("backup.settingsPayloadRequired", "Telegram settings payload is required"),
        Map.entry("backup.intervalOutOfRange", "Delivery interval must be between 1 and 720 hours"),
        Map.entry("backup.chatIdRequired", "Telegram chat id is required"),
        Map.entry("backup.botTokenRequired", "Save the Telegram bot token"),
        Map.entry("backup.sendFailed", "Failed to send backup: {reason}"),
        Map.entry("backup.backupInterrupted", "Backup creation was interrupted"),
        Map.entry("backup.emptyFile", "Backup file is empty"),
        Map.entry("backup.restoreInterrupted", "Database restore was interrupted"),
        Map.entry("backup.pgDumpMissing", "pg_dump is not available in the environment"),
        Map.entry("backup.pgCliMissing", "PostgreSQL CLI is not available in the environment"),
        Map.entry("backup.pgDumpFailed", "pg_dump finished with an error"),
        Map.entry("backup.schemaResetFailed", "Schema reset failed before restore"),
        Map.entry("backup.pgRestoreFailed", "pg_restore finished with an error"),
        Map.entry("backup.postgresOnly", "Only PostgreSQL datasources are supported"),
        Map.entry("backup.invalidJdbcUrl", "Invalid PostgreSQL JDBC URL")
    );

    private static final Map<String, String> RU_MESSAGES = Map.ofEntries(
        Map.entry("status.badRequest", "Неверный запрос"),
        Map.entry("status.unauthorized", "Требуется вход"),
        Map.entry("status.forbidden", "Доступ запрещен"),
        Map.entry("status.notFound", "Не найдено"),
        Map.entry("status.conflict", "Конфликт"),
        Map.entry("status.internalError", "Внутренняя ошибка сервера"),
        Map.entry("status.requestFailed", "Ошибка запроса"),
        Map.entry("errors.validationFailed", "Ошибка валидации"),
        Map.entry("errors.internalServerError", "Внутренняя ошибка сервера"),
        Map.entry("errors.unauthorized", "Требуется вход"),
        Map.entry("errors.forbidden", "Доступ запрещен"),
        Map.entry("errors.childIdRequired", "childId обязателен"),
        Map.entry("errors.friendIdRequired", "friendId обязателен"),
        Map.entry("errors.keyRequired", "key обязателен"),
        Map.entry("errors.isBlockedRequired", "isBlocked обязателен"),
        Map.entry("security.csrfInvalid", "CSRF токен отсутствует или неверен"),
        Map.entry("auth.invalidCredentials", "Неверные учетные данные"),
        Map.entry("auth.invalidAdminPassword", "Неверный пароль администратора"),
        Map.entry("auth.accountBlocked", "Аккаунт заблокирован"),
        Map.entry("auth.emailNotVerified", "Email не подтвержден. Проверьте почту."),
        Map.entry("auth.invalidPassword", "Неверный пароль"),
        Map.entry("auth.tokenMissing", "Токен отсутствует"),
        Map.entry("auth.invalidLink", "Неверная ссылка"),
        Map.entry("auth.familyNotFound", "Семья не найдена"),
        Map.entry("auth.emailRegistered", "Email уже зарегистрирован"),
        Map.entry("auth.weakParentPassword", "Слабый пароль родителя"),
        Map.entry("auth.passwordRecoveryDisabled", "Функция восстановления пароля отключена"),
        Map.entry("auth.weakPassword", "Слабый пароль"),
        Map.entry("auth.newPasswordMustDiffer", "Новый пароль должен отличаться от старого"),
        Map.entry("auth.passwordUpdateFailed", "Не удалось обновить пароль"),
        Map.entry("auth.invalidOrExpiredResetLink", "Недействительная или просроченная ссылка"),
        Map.entry("auth.invalidVerificationToken", "Недействительный токен верификации"),
        Map.entry("family.familyNotFound", "Семья не найдена"),
        Map.entry("family.childNotFound", "Ребенок не найден"),
        Map.entry("family.childNameRequired", "Имя ребенка обязательно"),
        Map.entry("family.childNameTooLong", "Имя слишком длинное"),
        Map.entry("family.childNameTaken", "Это имя уже занято"),
        Map.entry("family.createFailed", "Ошибка создания"),
        Map.entry("family.nameRequired", "Имя обязательно"),
        Map.entry("family.invalidTheme", "Недопустимая тема: {theme}"),
        Map.entry("family.invalidGroupOrderSection", "Неизвестный раздел порядка групп: {section}"),
        Map.entry("family.groupOrderSaveFailed", "Не удалось сохранить порядок групп"),
        Map.entry("family.cannotAddSelf", "Нельзя добавить себя"),
        Map.entry("family.userNotFound", "Пользователь не найден"),
        Map.entry("family.friendAddFailed", "Уже в друзьях или не удалось добавить"),
        Map.entry("family.tokenGenerationFailed", "Ошибка генерации токена"),
        Map.entry("family.invalidChildId", "Некорректный идентификатор ребенка"),
        Map.entry("family.unknownSetting", "Неизвестная настройка: {key}"),
        Map.entry("analytics.taskFallback", "Задание"),
        Map.entry("analytics.itemFallback", "Товар"),
        Map.entry("analytics.recommendationStale", "Давно не выполнялось"),
        Map.entry("analytics.recommendationRepeat", "Стоит повторить"),
        Map.entry("tasks.notFound", "Задание не найдено"),
        Map.entry("shop.itemNotFound", "Товар не найден"),
        Map.entry("balance.insufficient", "Недостаточно монет"),
        Map.entry("requests.notFound", "Заявка не найдена"),
        Map.entry("requests.alreadyProcessed", "Заявка уже обработана"),
        Map.entry("history.entryNotFound", "Запись истории не найдена"),
        Map.entry("balance.amountZero", "Сумма не должна быть нулевой"),
        Map.entry("balance.adjustmentCredit", "Начисление"),
        Map.entry("balance.adjustmentDebit", "Списание"),
        Map.entry("super.notConfigured", "Супер-админ не настроен"),
        Map.entry("super.invalidCurrentPassword", "Неверный текущий пароль"),
        Map.entry("super.newPasswordMustDifferOld", "Новый пароль должен отличаться от старого"),
        Map.entry("super.newPasswordMustDifferCurrent", "Новый пароль должен отличаться от текущего"),
        Map.entry("super.familyHasNoChildren", "У семьи нет детей"),
        Map.entry("super.catalogSaveFailed", "Не удалось сохранить каталог"),
        Map.entry("limits.taskTarget", "этому заданию"),
        Map.entry("limits.itemTarget", "этому товару"),
        Map.entry("limits.day", "Лимит заявок по {target} на сегодня исчерпан. Следующее обновление в {resetAt}."),
        Map.entry("limits.week", "Лимит заявок по {target} на эту неделю исчерпан. Следующее обновление {resetAt}."),
        Map.entry("limits.month", "Лимит заявок по {target} на этот месяц исчерпан. Следующее обновление {resetAt}."),
        Map.entry("limits.year", "Лимит заявок по {target} на этот год исчерпан. Следующее обновление {resetAt}."),
        Map.entry("super.telegramNotConfigured", "Оповещения Telegram не настроены"),
        Map.entry("backup.settingsPayloadRequired", "Настройки Telegram не переданы"),
        Map.entry("backup.intervalOutOfRange", "Интервал отправки должен быть от 1 до 720 часов"),
        Map.entry("backup.chatIdRequired", "Укажите Telegram chat id"),
        Map.entry("backup.botTokenRequired", "Сохраните Telegram bot token"),
        Map.entry("backup.sendFailed", "Не удалось отправить бэкап: {reason}"),
        Map.entry("backup.backupInterrupted", "Создание бэкапа было прервано"),
        Map.entry("backup.emptyFile", "Файл бэкапа пустой"),
        Map.entry("backup.restoreInterrupted", "Восстановление базы было прервано"),
        Map.entry("backup.pgDumpMissing", "pg_dump не найден в окружении"),
        Map.entry("backup.pgCliMissing", "PostgreSQL CLI не найден в окружении"),
        Map.entry("backup.pgDumpFailed", "pg_dump завершился с ошибкой"),
        Map.entry("backup.schemaResetFailed", "Подготовка схемы к восстановлению завершилась с ошибкой"),
        Map.entry("backup.pgRestoreFailed", "pg_restore завершился с ошибкой"),
        Map.entry("backup.postgresOnly", "Поддерживается только PostgreSQL datasource"),
        Map.entry("backup.invalidJdbcUrl", "Некорректный PostgreSQL JDBC URL")
    );

    private BackendMessages() {
    }

    public static String resolveLocale(String appLocaleHeader, String acceptLanguageHeader) {
        String resolved = normalizeLocale(appLocaleHeader);
        if (resolved != null) {
            return resolved;
        }

        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return "en";
        }

        String[] items = acceptLanguageHeader.split(",");
        for (String item : items) {
            String[] parts = item.trim().split(";", 2);
            resolved = normalizeLocale(parts[0]);
            if (resolved != null) {
                return resolved;
            }
        }

        return "en";
    }

    public static String currentLocale() {
        return RequestLocaleHolder.get();
    }

    public static String message(String key) {
        return message(currentLocale(), key, Map.of());
    }

    public static String message(String key, Map<String, String> variables) {
        return message(currentLocale(), key, variables);
    }

    public static String message(String locale, String key, Map<String, String> variables) {
        String template = lookup(locale, key);
        String resolved = template == null ? key : template;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return resolved;
    }

    public static String statusTitle(int status) {
        return switch (status) {
            case 400 -> message("status.badRequest");
            case 401 -> message("status.unauthorized");
            case 403 -> message("status.forbidden");
            case 404 -> message("status.notFound");
            case 409 -> message("status.conflict");
            case 500 -> message("status.internalError");
            default -> message("status.requestFailed");
        };
    }

    public static String taskLimitReached(String period, String resetAt) {
        return message("limits." + normalizePeriod(period), Map.of(
            "target", message("limits.taskTarget"),
            "resetAt", resetAt
        ));
    }

    public static String itemLimitReached(String period, String resetAt) {
        return message("limits." + normalizePeriod(period), Map.of(
            "target", message("limits.itemTarget"),
            "resetAt", resetAt
        ));
    }

    private static String lookup(String locale, String key) {
        String normalizedLocale = "ru".equalsIgnoreCase(locale) ? "ru" : "en";
        Map<String, String> localized = "ru".equals(normalizedLocale) ? RU_MESSAGES : EN_MESSAGES;
        String message = localized.get(key);
        return message != null ? message : EN_MESSAGES.get(key);
    }

    private static String normalizeLocale(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.startsWith("ru")) {
            return "ru";
        }
        if (normalized.startsWith("en")) {
            return "en";
        }
        return null;
    }

    private static String normalizePeriod(String period) {
        return switch (period) {
            case "week", "month", "year" -> period;
            default -> "day";
        };
    }
}
