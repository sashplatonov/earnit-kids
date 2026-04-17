/** @file Friends frontend UI module */
import { state, setState } from './state.js';
import { searchUsers, addFriend, loadFriendsList, updateNickname } from './api.js';
import { showToast } from './utils.js';
import { renderFriends, updateChildNicknameUI } from './ui.js';

export async function handleSearch() {
    const input = document.getElementById('friend-search-input');
    const resultsContainer = document.getElementById('search-results');
    const nickname = input.value.trim();

    if (nickname.length < 3) {
        showToast('Ник должен быть не короче 3 символов', 'error');
        return;
    }

    resultsContainer.innerHTML = '<div class="empty-state">Поиск...</div>';

    const results = await searchUsers(nickname);

    if (results.length === 0) {
        resultsContainer.innerHTML = '<div class="empty-state">Пользователи не найдены</div>';
        return;
    }

    resultsContainer.innerHTML = results.map(user => `
        <div class="search-results-item">
            <span>${user.nickname}</span>
            <button class="btn btn--success btn--small" onclick="window.app.addNewFriend('${user.id}')">Добавить</button>
        </div>
    `).join('');
}

export async function addNewFriend(friendId) {
    const result = await addFriend(friendId);
    if (result.success) {
        showToast('Друг добавлен!', 'success');
        await refreshFriends();
    } else {
        showToast(result.error || 'Ошибка при добавлении', 'error');
    }
}

export async function refreshFriends() {
    const friends = await loadFriendsList();
    setState({ friends });
    renderFriends();
}

export async function saveNickname() {
    const input = document.getElementById('settings-nickname');
    const nickname = input.value.trim();

    if (nickname.length < 3) {
        showToast('Ник должен быть не короче 3 символов', 'error');
        return;
    }

    const result = await updateNickname(nickname);
    if (result.success) {
        showToast('Ник сохранен!', 'success');
        setState({ childNickname: nickname });
        updateChildNicknameUI();
    } else {
        showToast(result.error || 'Ошибка при сохранении', 'error');
    }
}
