package com.sashplatonov.earnit.kids.domain.model;

public enum PurchaseRequestType {
    earn,
    shop_purchase,
    shop;

    public boolean isPurchase() {
        return this == shop_purchase || this == shop;
    }
}
