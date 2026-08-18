package com.sashplatonov.earnit.kids.service.admin;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class AdminAccessService {

    private static final Logger LOG = Logger.getLogger(AdminAccessService.class);
    private final Set<Long> adminUserIds;

    @Inject
    public AdminAccessService(TelegramConfig telegramConfig) {
        this.adminUserIds = parseAdminUserIds(telegramConfig.adminUserIds());
        if (adminUserIds.isEmpty()) {
            LOG.warn("No Telegram admin user IDs configured (app.telegram.admin-user-ids is empty). "
                + "Dashboard will be hidden for all Telegram users. "
                + "Set TELEGRAM_ADMIN_USER_IDS env var to enable admin access.");
        } else {
            LOG.infof("Loaded %d Telegram admin user ID(s): %s", adminUserIds.size(), adminUserIds);
        }
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
