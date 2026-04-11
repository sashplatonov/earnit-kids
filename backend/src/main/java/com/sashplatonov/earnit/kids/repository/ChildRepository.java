package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ChildRepository implements PanacheRepositoryBase<ChildEntity, Integer> {

    public List<ChildEntity> getChildren(int familyDbId) {
        return list("familyDbId = ?1 ORDER BY id ASC", familyDbId);
    }

    public Optional<ChildEntity> findByToken(String token) {
        return find("token = ?1", token).firstResultOptional();
    }

    @Transactional
    public Optional<ChildEntity> createChild(int familyDbId, String name) {
        ChildEntity entity = ChildEntity.builder()
            .familyDbId(familyDbId)
            .name(name)
            .build();
        persist(entity);
        return Optional.of(entity);
    }

    @Transactional
    public boolean deleteChild(int childId) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return false;
        }
        delete(opt.get());
        return true;
    }

    @Transactional
    public boolean updateBalance(int childId, int newBalance) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setBalance(newBalance);
        return true;
    }

    @Transactional
    public boolean updateName(int childId, String name) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setName(name);
        return true;
    }

    @Transactional
    public boolean updateSettings(int childId, String name, int dailyCoinLimit, int monthlyLimit) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return false;
        }
        ChildEntity e = opt.get();
        e.setName(name);
        e.setDailyCoinLimit(dailyCoinLimit);
        e.setMonthlyLimit(monthlyLimit);
        return true;
    }

    @Transactional
    public boolean updateTheme(int childId, String theme) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setTheme(theme);
        return true;
    }

    @Transactional
    public Optional<String> regenerateToken(int childId) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        String newToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        opt.get().setToken(newToken);
        return Optional.of(newToken);
    }

    public boolean isNicknameTaken(int familyDbId, String name, Integer excludeChildId) {
        if (excludeChildId != null) {
            return count("familyDbId = ?1 AND lower(name) = lower(?2) AND id != ?3",
                familyDbId, name, excludeChildId) > 0;
        }
        return count("familyDbId = ?1 AND lower(name) = lower(?2)", familyDbId, name) > 0;
    }

    public List<ChildEntity> searchByNickname(String nameQuery, int excludeChildId) {
        return find("lower(name) LIKE lower(?1) AND id != ?2",
                "%" + nameQuery + "%", excludeChildId)
            .page(0, 20)
            .list();
    }
}
