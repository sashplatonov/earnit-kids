package com.sashplatonov.earnit.kids.identity.infrastructure.persistence;

import com.sashplatonov.earnit.kids.identity.domain.model.OAuthInvitationContinuationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class OAuthInvitationContinuationRepository
    implements PanacheRepositoryBase<OAuthInvitationContinuationEntity, Integer> {

    public Optional<OAuthInvitationContinuationEntity> findPending(
        Integer id, String browserBindingDigest, Instant now) {
        return find("id = ?1 AND browserBindingDigest = ?2 AND consumedAt IS NULL AND expiresAt > ?3",
            id, browserBindingDigest, now).firstResultOptional();
    }

    public Optional<OAuthInvitationContinuationEntity> findConsumed(
        Integer id, String browserBindingDigest, Instant now) {
        return find("id = ?1 AND browserBindingDigest = ?2 AND consumedAt IS NOT NULL AND expiresAt > ?3",
            id, browserBindingDigest, now).firstResultOptional();
    }

    public int consume(Integer id, String browserBindingDigest, Instant now, String email) {
        return update("consumedAt = ?1, verifiedEmail = ?2 "
                + "WHERE id = ?3 AND browserBindingDigest = ?4 AND consumedAt IS NULL AND expiresAt > ?1",
            now, email, id, browserBindingDigest);
    }
}
