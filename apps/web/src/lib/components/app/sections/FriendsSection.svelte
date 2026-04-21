<script lang="ts">
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { searchFriend, addFriend } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    function tApp(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`app.${key}` as MessageKey, variables);
    }

    $: friends = $appStore.friends;

    let searchQuery = '';
    let searchResults: Array<{ id: unknown; nickname: string; balance?: number }> = [];
    let searching = false;

    async function handleSearch() {
        if (searchQuery.trim().length < 3) {
            showToast(tApp('friends.minSearchToast'), 'info');
            return;
        }
        searching = true;
        try {
            const res = await searchFriend(searchQuery.trim()) as unknown;
            searchResults = Array.isArray(res) ? res as Array<{ id: unknown; nickname: string; balance?: number }> : [];
            if (!searchResults.length) showToast(tApp('friends.noResultsToast'), 'info');
        } finally {
            searching = false;
        }
    }

    async function handleAddFriend(friendId: unknown) {
        const ok = await addFriend(friendId);
        if (ok) {
            showToast(tApp('friends.addedToast'), 'success');
            searchResults = searchResults.filter(r => r.id !== friendId);
        }
    }
</script>

<section class="section" id="friends-section">
    <div class="section__header">
        <h2>{tApp('friends.title')}</h2>
    </div>

    <div class="cards" style="align-items: stretch;">
        <!-- Search -->
        <div class="card" style="max-width: 600px; width: 100%; display: flex; flex-direction: column;">
            <div class="card__header">
                <h3 class="card__title">{tApp('friends.searchTitle')}</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-magnifier" aria-hidden="true"></span>
                </div>
            </div>

            <div class="form-group" style="margin-top: 1rem;">
                <label for="friend-search-input">{tApp('friends.nicknameLabel')}</label>
                <div style="display: flex; gap: 0.5rem;">
                    <input type="text" class="input" id="friend-search-input"
                        placeholder={tApp('friends.searchPlaceholder')}
                        bind:value={searchQuery}
                        on:keydown={e => e.key === 'Enter' && handleSearch()} />
                    <button class="btn btn--primary" id="friend-search-btn"
                        on:click={handleSearch} disabled={searching}>
                        {searching ? '…' : tApp('friends.searchButton')}
                    </button>
                </div>
            </div>

            <div id="search-results" class="search-results" style="margin-top: 1rem; flex: 1;">
                {#each searchResults as result (result.id)}
                <div class="search-result-item">
                    <span>{result.nickname}</span>
                    <button class="btn btn--primary btn--small" on:click={() => handleAddFriend(result.id)}>
                        {tApp('friends.addButton')}
                    </button>
                </div>
                {/each}
            </div>
        </div>

        <!-- Friends list -->
        <div class="card" style="max-width: 600px; width: 100%; display: flex; flex-direction: column;">
            <div class="card__header">
                <h3 class="card__title">{tApp('friends.listTitle')}</h3>
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
                    <span class="friend-balance">{$i18n.formatNumber(friend.balance)} {tApp('friends.coinsUnit')}</span>
                    {/if}
                </div>
                {/each}
                {:else}
                <div class="empty-state" id="friends-empty">
                    <p>{tApp('friends.empty')}</p>
                </div>
                {/if}
            </div>
        </div>
    </div>
</section>
