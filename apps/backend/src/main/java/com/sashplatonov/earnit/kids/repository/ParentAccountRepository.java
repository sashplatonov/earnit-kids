package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ParentAccountRepository implements PanacheRepositoryBase<ParentAccountEntity, Integer> {

    public Optional<ParentAccountEntity> findByEmail(String email) {
        return find("email = ?1", email).firstResultOptional();
    }

    public Optional<ParentAccountEntity> findByVerificationToken(String token) {
        return find("verificationToken = ?1", token).firstResultOptional();
    }

    public Optional<ParentAccountEntity> findByResetToken(String token) {
        return find("resetToken = ?1", token).firstResultOptional();
    }

    public List<ParentAccountEntity> findByIdList(List<Integer> ids) {
        return find("id in ?1", ids).list();
    }
}
