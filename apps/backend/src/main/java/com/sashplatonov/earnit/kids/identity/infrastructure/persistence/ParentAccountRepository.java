package com.sashplatonov.earnit.kids.identity.infrastructure.persistence;

import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ParentAccountRepository implements PanacheRepositoryBase<ParentAccountEntity, Integer> {

    public Optional<ParentAccountEntity> findByEmail(String email) {
        return find("email = ?1", email).firstResultOptional();
    }

    public List<ParentAccountEntity> findByIdList(List<Integer> ids) {
        return find("id in ?1", ids).list();
    }

    public Optional<ParentAccountEntity> findByIdForUpdate(Integer id) {
        return find("id = ?1", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    @jakarta.transaction.Transactional
    public boolean changeEmail(String currentEmail, String newEmail) {
        Optional<ParentAccountEntity> existing = findByEmail(currentEmail);
        if (existing.isEmpty()) {
            return false;
        }
        existing.get().setEmail(newEmail);
        return true;
    }

    @jakarta.transaction.Transactional
    public boolean disablePasswordLogin(String email, String unusableHash) {
        Optional<ParentAccountEntity> existing = findByEmail(email);
        if (existing.isEmpty()) {
            return false;
        }
        existing.get().setPasswordHash(unusableHash);
        return true;
    }
}
