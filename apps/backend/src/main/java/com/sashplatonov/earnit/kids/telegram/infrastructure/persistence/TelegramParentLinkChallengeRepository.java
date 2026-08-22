package com.sashplatonov.earnit.kids.telegram.infrastructure.persistence;

import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramParentLinkChallengeEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@ApplicationScoped
public class TelegramParentLinkChallengeRepository
    implements PanacheRepositoryBase<TelegramParentLinkChallengeEntity, Integer> {

    public Optional<TelegramParentLinkChallengeEntity> findByDigestForUpdate(String digest) {
        return find("secretDigest = ?1", digest)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResultOptional();
    }
}
