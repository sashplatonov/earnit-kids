package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
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

    public Optional<FamilyEntity> findById(String familyId) {
        return find("familyId = ?1", familyId).firstResultOptional();
    }

    public Optional<FamilyEntity> findByEmail(String email) {
        return find("email = ?1", email).firstResultOptional();
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
        return find("familyId = ?1", familyId)
            .firstResultOptional()
            .map(FamilyEntity::getId);
    }

    public Optional<Integer> getLastSelectedChildId(String familyId) {
        return find("familyId = ?1", familyId)
            .firstResultOptional()
            .map(FamilyEntity::getLastSelectedChildId);
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
    public boolean verifyFamily(String familyId) {
        Optional<FamilyEntity> opt = findByFamilyId(familyId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().verify();
        return true;
    }

    public Optional<FamilyEntity> findByVerificationToken(String token) {
        return find("verificationToken = ?1", token).firstResultOptional();
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
        return find("resetToken = ?1 AND resetTokenExpiresAt > ?2", token, timeProvider.now())
            .firstResultOptional();
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
        return findByIdOptional(dbId);
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
        return find("familyId = ?1", familyId).firstResultOptional();
    }
}
