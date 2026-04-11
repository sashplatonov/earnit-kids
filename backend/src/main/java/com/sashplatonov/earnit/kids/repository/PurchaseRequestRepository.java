package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PurchaseRequestRepository implements PanacheRepositoryBase<PurchaseRequestEntity, Long> { }
