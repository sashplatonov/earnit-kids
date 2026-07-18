package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.service.observability.SlowOperationDiagnostics;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ChildRepository implements PanacheRepositoryBase<ChildEntity, Integer> {

    private final SecureTokenGenerator secureTokenGenerator;
    private final SlowOperationDiagnostics slowOperationDiagnostics;

    public List<ChildEntity> getChildren(int familyDbId) {
        return recordQuery(
            "child.getChildren",
            () -> list("familyDbId = ?1 ORDER BY id ASC", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    public Optional<ChildEntity> findByToken(String token) {
        return recordQuery(
            "child.findByToken",
            () -> find("token = ?1", token).firstResultOptional(),
            "token",
            token
        );
    }

    @Transactional
    public Optional<ChildEntity> createChild(int familyDbId, String name) {
        ChildEntity entity = ChildEntity.builder()
            .familyDbId(familyDbId)
            .name(name)
            .token(secureTokenGenerator.generateChildToken())
            .build();
        persistAndFlush(entity);
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
    public boolean updateRewardGoal(int childId, Long itemId) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setRewardGoalItemId(itemId);
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
    public boolean updateTheme(int childId, ChildTheme theme) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return false;
        }
        opt.get().setTheme(theme.name());
        return true;
    }

    @Transactional
    public boolean updateGroupOrder(int childId, GroupOrderSection section, boolean personalOrder, String groupOrder) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return false;
        }

        ChildEntity child = opt.get();
        switch (section) {
            case tasks -> {
                if (personalOrder) {
                    child.setChildTaskGroupOrder(groupOrder);
                } else {
                    child.setTaskGroupOrder(groupOrder);
                }
                return true;
            }
            case shop -> {
                if (personalOrder) {
                    child.setChildShopGroupOrder(groupOrder);
                } else {
                    child.setShopGroupOrder(groupOrder);
                }
                return true;
            }
        }
        return false;
    }

    @Transactional
    public Optional<String> regenerateToken(int childId) {
        Optional<ChildEntity> opt = findByIdOptional(childId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        String newToken = secureTokenGenerator.generateChildToken();
        opt.get().setToken(newToken);
        return Optional.of(newToken);
    }

    public boolean isNicknameTaken(int familyDbId, String name, Integer excludeChildId) {
        return recordQuery(
            "child.isNicknameTaken",
            () -> {
                if (excludeChildId != null) {
                    return count("familyDbId = ?1 AND lower(name) = lower(?2) AND id != ?3",
                        familyDbId, name, excludeChildId) > 0;
                }
                return count("familyDbId = ?1 AND lower(name) = lower(?2)", familyDbId, name) > 0;
            },
            "familyDbId",
            String.valueOf(familyDbId),
            "excludeChildId",
            String.valueOf(excludeChildId),
            "name",
            name
        );
    }

    public List<ChildEntity> searchByNickname(String nameQuery, int excludeChildId) {
        return recordQuery(
            "child.searchByNickname",
            () -> find("lower(name) LIKE lower(?1) AND id != ?2",
                    "%" + nameQuery + "%", excludeChildId)
                .page(0, 20)
                .list(),
            "excludeChildId",
            String.valueOf(excludeChildId),
            "nameQuery",
            nameQuery
        );
    }

    public List<ChildEntity> findByChildIds(List<Integer> ids) {
        return recordQuery(
            "child.findByChildIds",
            () -> {
                if (ids == null || ids.isEmpty()) {
                    return List.of();
                }
                return find("id IN ?1", ids).list();
            },
            "idsSize",
            String.valueOf(ids == null ? 0 : ids.size())
        );
    }

    private <T> T recordQuery(String operation, java.util.function.Supplier<T> action, String... details) {
        return slowOperationDiagnostics.recordQuery(operation, action, details);
    }
}
