package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.TelegramParentInvitationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@ApplicationScoped
public class TelegramParentInvitationRepository
    implements PanacheRepositoryBase<TelegramParentInvitationEntity, Integer> {
    public Optional<TelegramParentInvitationEntity> findByDigestForUpdate(String digest) {
        return find("secretDigest = ?1", digest)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResultOptional();
    }
}