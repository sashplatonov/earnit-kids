package com.sashplatonov.earnit.kids.platform.security;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SecurityAuditEventRepository implements PanacheRepositoryBase<SecurityAuditEventEntity, Integer> {
}
