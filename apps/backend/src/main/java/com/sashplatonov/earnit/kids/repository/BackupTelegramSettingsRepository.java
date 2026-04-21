package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.BackupTelegramSettingsEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class BackupTelegramSettingsRepository implements PanacheRepositoryBase<BackupTelegramSettingsEntity, String> {

    public Optional<BackupTelegramSettingsEntity> findSettings() {
        return findByIdOptional(BackupTelegramSettingsEntity.DEFAULT_ID);
    }

    @Transactional
    public void flushChanges() {
        getEntityManager().flush();
    }
}
