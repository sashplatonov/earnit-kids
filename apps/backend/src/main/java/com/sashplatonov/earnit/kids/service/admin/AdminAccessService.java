package com.sashplatonov.earnit.kids.service.admin;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class AdminAccessService {

    private final Set<Long> adminUserIds;

    @Inject
    public AdminAccessService(TelegramConfig telegramConfig) {
        this.adminUserIds = parseAdminUserIds(telegramConfig.adminUserIds());
    }

    public boolean isAdmin(Long telegramUserId) {
        if (telegramUserId == null) {
            return false;
        }
        return adminUserIds.contains(telegramUserId);
    }

    private Set<Long> parseAdminUserIds(Optional<String> adminUserIdsConfig) {
        if (adminUserIdsConfig.isEmpty() || adminUserIdsConfig.get().isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(adminUserIdsConfig.get().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(id -> id != null)
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasAdmins() {
        return !adminUserIds.isEmpty();
    }

    public int getAdminCount() {
        return adminUserIds.size();
    }
}
