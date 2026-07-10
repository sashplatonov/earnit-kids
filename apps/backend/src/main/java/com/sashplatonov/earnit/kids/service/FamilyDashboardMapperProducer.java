package com.sashplatonov.earnit.kids.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class FamilyDashboardMapperProducer {
    @Produces
    FamilyDashboardMapper familyDashboardMapper() {
        return FamilyDashboardMapper.INSTANCE;
    }
}
