package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminActivationAnalyticsRepository;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdminActivationFunnelService {

    @Inject
    AdminActivationAnalyticsRepository repository;

    public AdminActivationFunnelResponse getActivationFunnel() {
        return getActivationFunnel(AdminAnalyticsPeriod.parse("all"));
    }

    @CacheResult(cacheName = "admin-activation-funnel")
    public AdminActivationFunnelResponse getActivationFunnel(AdminAnalyticsPeriod period) {
        return AdminActivationFunnelResponse.builder()
            .stages(repository.getActivationFunnel(period.start()))
            .build();
    }
}
