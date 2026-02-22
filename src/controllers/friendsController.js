const { getFriendsData, searchByNickname, addFriend } = require('../services/familyService');
const parseBody = require('../middleware/body-parser');
const { sendJSON } = require('../utils/controllerUtils');

async function handleFriendsList(ctx, req, res) {
    if (ctx.role !== 'child') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const friends = await getFriendsData(ctx.familyId, ctx.childId);
    sendJSON(res, friends);
}

async function handleSearchUser(ctx, req, res) {
    if (ctx.role !== 'child') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const nickname = ctx.urlObj.searchParams.get('nickname');
    const results = await searchByNickname(nickname);
    sendJSON(res, results);
}

async function handleAddFriend(ctx, req, res) {
    if (ctx.role !== 'child') return sendJSON(res, { error: 'Not Found or Forbidden' }, 404);
    const body = await parseBody(req);
    const result = await addFriend(ctx.childId, body.friendId);
    sendJSON(res, result, result.success ? 200 : 400);
}

module.exports = {
    handleFriendsList,
    handleSearchUser,
    handleAddFriend
};
