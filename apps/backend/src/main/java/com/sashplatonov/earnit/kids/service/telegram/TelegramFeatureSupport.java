package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.repository.FamilyRepository;

import java.net.URI;

public final class TelegramFeatureSupport {

    private TelegramFeatureSupport() {
    }

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
