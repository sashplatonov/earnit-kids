package com.sashplatonov.earnit.kids.service.family.dashboard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class FamilyDashboardMapperProducer {
    @Produces
    FamilyDashboardMapper familyDashboardMapper() {
        return FamilyDashboardMapper.INSTANCE;
    }
}
