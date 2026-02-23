/**
 * Family Data Mappers - Data mapping and utility functions for family data
 */

function val(v, d) {
    return (v === undefined || v === null) ? d : v;
}

function mapTask(row) {
    return {
        id: row.task_id,
        childId: row.child_id,
        name: row.name,
        coins: row.coins,
        group: row.group_name,
        frequency: row.frequency,
        comment: row.comment,
        money_limit: row.money_limit,
        isDeleted: !!row.is_deleted
    };
}

function mapShopItem(row) {
    return {
        id: row.item_id,
        childId: row.child_id,
        name: row.name,
        price: row.price,
        group: row.group_name,
        frequency: row.frequency,
        money_limit: row.money_limit,
        isDeleted: !!row.is_deleted
    };
}

function mapHistoryEntry(row) {
    const amount = row.amount ?? row.coins ?? 0;
    const entry = {
        id: row.id,
        childId: row.child_id,
        type: row.type,
        amount,
        coins: amount,
        description: row.description,
        group: row.group_name,
        comment: row.comment,
        date: row.created_at,
        moneyAmount: row.money_amount
    };

    if (row.type === 'spend' && row.related_id) entry.itemId = parseInt(row.related_id);
    else if (row.type === 'earn' && row.related_id) entry.taskId = parseInt(row.related_id);

    if (row.related_id) entry.relatedId = parseInt(row.related_id);

    return entry;
}

function mapRequest(row) {
    return {
        id: row.external_id ? parseInt(row.external_id) : row.id,
        childId: row.child_id,
        requestType: val(row.request_type, 'earn'),
        taskId: row.task_id ? parseInt(row.task_id) : null,
        itemId: row.item_id ? parseInt(row.item_id) : null,
        taskName: row.task_name,
        coins: row.coins,
        moneyAmount: val(row.money_amount, 0),
        status: row.status,
        date: row.created_at,
        group: row.task_group || row.item_group,
        comment: row.task_comment
    };
}

function getHistoryParams(dbId, e) {
    const relId = e.itemId || e.taskId || e.relatedId || null;
    const d = val(e.date, val(e.timestamp, new Date()));
    return [dbId, e.childId, val(e.id, null), val(e.type, 'unknown'), val(e.amount, 0), val(e.description, ''), val(e.moneyAmount, 0), relId, d];
}

module.exports = {
    val,
    mapTask,
    mapShopItem,
    mapHistoryEntry,
    mapRequest,
    getHistoryParams
};
