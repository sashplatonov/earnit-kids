package com.sashplatonov.earnit.kids.service.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdminActivationFunnelService {

    @Inject
    AdminAnalyticsRepository repository;

    public AdminActivationFunnelResponse getActivationFunnel() {
        return AdminActivationFunnelResponse.builder()
            .stages(repository.getActivationFunnel())
            .build();
    }
}
