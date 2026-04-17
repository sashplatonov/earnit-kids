package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShopItemRepository implements PanacheRepositoryBase<ShopItemEntity, Long> { }
