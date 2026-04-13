package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FriendEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FriendRepository implements PanacheRepositoryBase<FriendEntity, Integer> { }
