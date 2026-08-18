export interface ShopPayloadInput {
    id?: number | string;
    title: string;
    groupName: string;
    price: number;
    comment: string;
    freqLimit: string;
    freqPeriod: 'day' | 'week' | 'month' | 'year';
    moneyLimit: string;
    itemType: 'micro' | 'small' | 'large';
    icon?: string | null;
    isActive?: boolean;
}

export interface ShopPayload {
    id?: number | string;
    name: string;
    title: string;
    groupName: string | null;
    price: number;
    coins: number;
    comment: string | null;
    frequency: { limit: number; period: 'day' | 'week' | 'month' | 'year' } | null;
    moneyLimit: number | null;
    type: 'micro' | 'small' | 'large';
    itemType: 'micro' | 'small' | 'large';
    icon?: string | null;
    isActive?: boolean;
}

export function buildShopPayload(input: ShopPayloadInput): ShopPayload {
    const name = input.title.trim();

    const payload: ShopPayload = {
        id: input.id,
        name,
        title: name,
        groupName: input.groupName.trim() || null,
        price: Number(input.price) || 50,
        coins: Number(input.price) || 50,
        comment: input.comment.trim() || null,
        frequency: input.freqLimit
            ? { limit: Number(input.freqLimit), period: input.freqPeriod }
            : null,
        moneyLimit: input.moneyLimit ? Number(input.moneyLimit) : null,
        icon: input.icon?.trim() || null,
        type: input.itemType,
        itemType: input.itemType,
    };

    if (input.isActive === false) {
        payload.isActive = false;
    }

    return payload;
}
