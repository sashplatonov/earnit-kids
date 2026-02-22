const {
    syncBalances,
    syncTasks,
    syncShop
} = require('./syncRepository');
const { query, getClient } = require('./connection');
const familyRepository = require('./familyRepository');

async function resolveDefaultChildId(familyId, actingChildId) {
    if (actingChildId) return actingChildId;
    const children = await familyRepository.getChildren(familyId);
    return children.length > 0 ? children[0].id : null;
}

function buildDeleteScope(dbId, actingChildId) {
    if (!actingChildId) {
        return { deleteWhere: 'WHERE family_id = $1', deleteParams: [dbId] };
    }
    return {
        deleteWhere: 'WHERE family_id = $1 AND child_id = $2',
        deleteParams: [dbId, actingChildId]
    };
}

// ... other sync helpers like syncHistory, syncRequests can be moved here

module.exports = {
    resolveDefaultChildId,
    buildDeleteScope
    // ...
};
