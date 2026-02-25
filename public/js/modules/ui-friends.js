/** @file Ui Friends frontend UI module */
import { escapeHtml } from './utils.js';
import { applyStaggerReveal } from './motion-feedback.js';

function buildSoftChallenge(state, friends) {
    const challenge = document.getElementById('friends-challenge');
    if (!challenge) return;

    if (!friends.length) {
        challenge.classList.add('hidden');
        challenge.innerHTML = '';
        return;
    }

    const ownBalance = Number(state.balance || 0);
    const sorted = friends
        .map(friend => ({ ...friend, balance: Number(friend.balance || 0) }))
        .sort((a, b) => Math.abs(a.balance - ownBalance) - Math.abs(b.balance - ownBalance));
    const rival = sorted[0];
    const delta = rival.balance - ownBalance;
    const actionText = delta > 0
        ? `Сделай 2 задания и сократи разрыв на ${Math.min(delta, 20)} 🪙.`
        : `Удержи темп: сделай 1 задание сегодня и останься впереди.`;
    const statusText = delta > 0
        ? `До ${escapeHtml(rival.nickname)} осталось ${delta} 🪙.`
        : `Ты впереди ${escapeHtml(rival.nickname)} на ${Math.abs(delta)} 🪙.`;

    challenge.innerHTML = `
        <div class="friends-challenge__title">🏁 Дружеский челлендж недели</div>
        <div class="friends-challenge__status">${statusText}</div>
        <div class="friends-challenge__hint">${actionText}</div>
    `;
    challenge.classList.remove('hidden');
}

export function renderFriendsUI(state) {
    const container = document.getElementById('friends-list');
    const emptyState = document.getElementById('friends-empty');
    if (!container) return;

    let friends = state.friends || [];
    if (state.isAdmin && state.currentChildId) {
        friends = friends.filter(f => !f.ownerChildId || f.ownerChildId == state.currentChildId);
    }

    if (friends.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        buildSoftChallenge(state, []);
        return;
    }
    if (emptyState) emptyState.classList.add('hidden');
    buildSoftChallenge(state, friends);

    container.innerHTML = friends.map(friend => `
        <div class="friend-item">
            <div class="friend-info">
                <span class="friend-nickname">${escapeHtml(friend.nickname)}</span>
                <span class="friend-balance">💰 ${friend.balance} 🪙</span>
            </div>
            <div class="friend-actions"></div>
        </div>
    `).join('');
    applyStaggerReveal(container);
}
