/** @file Ui Friends frontend UI module */
import { escapeHtml } from './utils.js';

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
        return;
    }
    if (emptyState) emptyState.classList.add('hidden');

    container.innerHTML = friends.map(friend => `
        <div class="friend-item">
            <div class="friend-info">
                <span class="friend-nickname">${escapeHtml(friend.nickname)}</span>
                <span class="friend-balance">💰 ${friend.balance} 🪙</span>
            </div>
            <div class="friend-actions"></div>
        </div>
    `).join('');
}
