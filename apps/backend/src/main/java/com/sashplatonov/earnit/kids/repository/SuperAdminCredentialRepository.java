package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.SuperAdminCredentialEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class SuperAdminCredentialRepository implements PanacheRepositoryBase<SuperAdminCredentialEntity, String> {

    public Optional<SuperAdminCredentialEntity> findByEmail(String email) {
        return findByIdOptional(email);
    }

    @Transactional
    public void upsertPasswordHash(String email, String passwordHash) {
        Optional<SuperAdminCredentialEntity> existing = findByIdOptional(email);
        if (existing.isPresent()) {
            existing.get().setPasswordHash(passwordHash);
            return;
        }

        persistAndFlush(SuperAdminCredentialEntity.builder()
            .email(email)
            .passwordHash(passwordHash)
            .build());
    }
}