package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdminActivationFunnelService {

    @Inject
    AdminAnalyticsRepository repository;

    public AdminActivationFunnelResponse getActivationFunnel() {
        return getActivationFunnel(AdminAnalyticsPeriod.parse("all"));
    }

    public AdminActivationFunnelResponse getActivationFunnel(AdminAnalyticsPeriod period) {
        return AdminActivationFunnelResponse.builder()
            .stages(repository.getActivationFunnel(period.start()))
            .build();
    }
}
