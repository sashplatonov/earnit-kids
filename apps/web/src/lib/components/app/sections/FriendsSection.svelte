<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { searchFriend, addFriend } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

    $: friends = $appStore.friends;

    let searchQuery = '';
    let searchResults: Array<{ id: unknown; nickname: string; balance?: number }> = [];
    let searching = false;

    async function handleSearch() {
        if (searchQuery.trim().length < 3) {
            showToast('Введите минимум 3 символа', 'info');
            return;
        }
        searching = true;
        try {
            const res = await searchFriend(searchQuery.trim()) as unknown;
            searchResults = Array.isArray(res) ? res as Array<{ id: unknown; nickname: string; balance?: number }> : [];
            if (!searchResults.length) showToast('Никого не нашли', 'info');
        } finally {
            searching = false;
        }
    }

    async function handleAddFriend(friendId: unknown) {
        const ok = await addFriend(friendId);
        if (ok) {
            showToast('Друг добавлен!', 'success');
            searchResults = searchResults.filter(r => r.id !== friendId);
        }
    }
</script>

<section class="section" id="friends-section">
    <div class="section__header">
        <h2>Друзья</h2>
    </div>

    <div class="cards" style="align-items: stretch;">
        <!-- Search -->
        <div class="card" style="max-width: 600px; width: 100%; display: flex; flex-direction: column;">
            <div class="card__header">
                <h3 class="card__title">Найти друга</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-magnifier" aria-hidden="true"></span>
                </div>
            </div>

            <div class="form-group" style="margin-top: 1rem;">
                <label for="friend-search-input">Ник друга</label>
                <div style="display: flex; gap: 0.5rem;">
                    <input type="text" class="input" id="friend-search-input"
                        placeholder="Введите ник (мин. 3 символа)..."
                        bind:value={searchQuery}
                        on:keydown={e => e.key === 'Enter' && handleSearch()} />
                    <button class="btn btn--primary" id="friend-search-btn"
                        on:click={handleSearch} disabled={searching}>
                        {searching ? '…' : 'Найти'}
                    </button>
                </div>
            </div>

            <div id="search-results" class="search-results" style="margin-top: 1rem; flex: 1;">
                {#each searchResults as result (result.id)}
                <div class="search-result-item">
                    <span>{result.nickname}</span>
                    <button class="btn btn--primary btn--small" on:click={() => handleAddFriend(result.id)}>
                        + Добавить
                    </button>
                </div>
                {/each}
            </div>
        </div>

        <!-- Friends list -->
        <div class="card" style="max-width: 600px; width: 100%; display: flex; flex-direction: column;">
            <div class="card__header">
                <h3 class="card__title">Мои друзья</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-star" aria-hidden="true"></span>
                </div>
            </div>

            <div id="friends-list" class="friends-list" style="margin-top: 1rem; flex: 1;">
                {#if friends.length > 0}
                {#each friends as friend (friend.id)}
                <div class="friend-item">
                    <span class="friend-nickname">{friend.nickname}</span>
                    {#if friend.balance != null}
                    <span class="friend-balance">{friend.balance} монет</span>
                    {/if}
                </div>
                {/each}
                {:else}
                <div class="empty-state" id="friends-empty">
                    <p>У тебя пока нет друзей. Добавь кого-нибудь по нику!</p>
                </div>
                {/if}
            </div>
        </div>
    </div>
</section>
