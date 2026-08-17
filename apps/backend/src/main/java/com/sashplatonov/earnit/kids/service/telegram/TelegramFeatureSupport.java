package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.repository.FamilyRepository;

import java.net.URI;

// EXPLAIN: Shared feature-gate and URL-normalization helpers used by both the
// EXPLAIN: webhook dispatcher and the reply keyboard navigator (SRP, no dup).
public final class TelegramFeatureSupport {

    private TelegramFeatureSupport() {
    }

    // EXPLAIN: Unlinked users receive generic /start entry without family data.
    public static boolean isEnabledForFamily(TelegramFeatureGate featureGate,
                                             TelegramIdentityService identities,
                                             FamilyRepository families,
                                             long telegramUserId) {
        if (featureGate == null) {
            return true;
        }
        return identities.findActiveByTelegramUserId(telegramUserId)
            .flatMap(identity -> families.findFamilyIdByDbId(identity.familyId()))
            .map(featureGate::isBotEnabled)
            .orElse(true);
    }

    // EXPLAIN: The public site share button must point at the site root, never
    // EXPLAIN: at a specific app page. APP_URL may carry a path/query (e.g.
    // EXPLAIN: /en/app/tasks); strip it down to the bare origin so the button
    // EXPLAIN: always opens the public marketing site.
    public static String normalizePublicSiteUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return trimmed;
            }
            String origin = uri.getScheme() + ":" + '/' + '/' + uri.getHost();
            if (uri.getPort() >= 0) {
                origin = origin + ":" + uri.getPort();
            }
            return origin;
        } catch (Exception exception) {
            return trimmed;
        }
    }
}
