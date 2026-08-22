package com.sashplatonov.earnit.kids.family.domain.model.request;

public enum PurchaseRequestType {
    earn,
    shop_purchase,
    shop;

    public boolean isPurchase() {
        return this == shop_purchase || this == shop;
    }
}
