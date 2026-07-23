package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.repository.cache.FamilyDbIdCache;
import com.sashplatonov.earnit.kids.service.observability.SlowOperationDiagnostics;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyRepository implements PanacheRepositoryBase<FamilyEntity, Integer> {

    private final TimeProvider timeProvider;
    private final SlowOperationDiagnostics slowOperationDiagnostics;
    private final FamilyDbIdCache familyDbIdCache;

    public Optional<FamilyEntity> findById(String familyId) {
        return recordQuery(
            "family.findById",
            () -> find("familyId = ?1", familyId).firstResultOptional(),
            "familyId",
            familyId
        );
    }

    public Optional<FamilyEntity> findByEmail(String email) {
        return recordQuery(
            "family.findByEmail",
            () -> find("email = ?1", email).firstResultOptional(),
            "email",
            email
        );
    }

    @Transactional
    public Optional<FamilyEntity> create(String familyId, String email, String adminPassword,
                                         boolean isVerified, String verificationToken) {
        if (count("email = ?1", email) > 0) {
            return Optional.empty();
        }
        FamilyEntity entity = FamilyEntity.builder()
            .familyId(familyId)
            .email(email)
            .adminPassword(adminPassword)
            .verified(isVerified)
            .verificationToken(verificationToken)
            .build();
        persistAndFlush(entity);
        return Optional.of(entity);
    }

    public Optional<Integer> getDbId(String familyId) {
        Optional<Integer> cached = familyDbIdCache.get(familyId);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Integer> loaded = recordQuery(
            "family.getDbId",
            () -> find("familyId = ?1", familyId)
                .firstResultOptional()
                .map(FamilyEntity::getId),
            "familyId",
            familyId
        );
        loaded.ifPresent(familyDbId -> familyDbIdCache.put(familyId, familyDbId));
        return loaded;
    }

    public Optional<Integer> getLastSelectedChildId(String familyId) {
        return recordQuery(
            "family.getLastSelectedChildId",
            () -> find("familyId = ?1", familyId)
                .firstResultOptional()
                .map(FamilyEntity::getLastSelectedChildId),
            "familyId",
            familyId
        );
    }

    public Optional<String> getRules(String familyId) {
        return recordQuery(
            "family.getRules",
            () -> find("familyId = ?1", familyId)
                .firstResultOptional()
                .map(FamilyEntity::getRules),
            "familyId",
            familyId
        );
    }

    public Optional<String> getTimezone(int familyDbId) {
        return recordQuery(
            "family.getTimezone",
            () -> findByIdOptional(familyDbId).map(FamilyEntity::getTimezone),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    @Transactional
    public boolean updatePassword(String familyId, String newPassword) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setAdminPassword(newPassword);
        return true;
    }

    @Transactional
    public boolean updateLastActivity(String familyId) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setLastActivity(timeProvider.now());
        return true;
    }

    @Transactional
    public boolean updateLastSelectedChild(String familyId, Integer childId) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setLastSelectedChildId(childId);
        return true;
    }

    @Transactional
    public boolean updateRules(String familyId, String rules) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setRules(rules);
        return true;
    }

    @Transactional
    public boolean verifyFamily(String familyId) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().verify();
        return true;
    }

    public Optional<FamilyEntity> findByVerificationToken(String token) {
        return recordQuery(
            "family.findByVerificationToken",
            () -> find("verificationToken = ?1", token).firstResultOptional(),
            "token",
            token
        );
    }

    @Transactional
    public boolean setResetToken(String familyId, String token, Instant expiresAt) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setResetToken(token, expiresAt);
        return true;
    }

    public Optional<FamilyEntity> findByResetToken(String token) {
        return recordQuery(
            "family.findByResetToken",
            () -> find("resetToken = ?1 AND resetTokenExpiresAt > ?2", token, timeProvider.now())
                .firstResultOptional(),
            "token",
            token
        );
    }

    @Transactional
    public boolean clearResetToken(String familyId) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().clearResetToken();
        return true;
    }

    public Optional<FamilyEntity> findByDbId(int dbId) {
        return recordQuery(
            "family.findByDbId",
            () -> findByIdOptional(dbId),
            "dbId",
            String.valueOf(dbId)
        );
    }

    @Transactional
    public boolean setBlocked(String familyId, boolean blocked) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setBlocked(blocked);
        return true;
    }

    private Optional<FamilyEntity> findByFamilyId(String familyId) {
        return recordQuery(
            "family.findByFamilyId",
            () -> find("familyId = ?1", familyId).firstResultOptional(),
            "familyId",
            familyId
        );
    }

    private <T> T recordQuery(String operation, java.util.function.Supplier<T> action, String... details) {
        return slowOperationDiagnostics.recordQuery(operation, action, details);
    }
}
