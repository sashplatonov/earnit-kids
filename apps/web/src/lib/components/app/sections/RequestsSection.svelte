<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { approveRequest, rejectRequest, deleteRequest } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { showToast } from '$lib/stores/toasts';
    import type { Request } from '$lib/stores/app';

    $: requests = $appStore.requests;
    $: isAdmin = $appStore.isAdmin;

    $: incomingRequests = requests.filter(r => r.status === 'pending');
    $: myRequests = requests.filter(r => r.status !== 'pending');

    async function handleApprove(req: Request) {
        const res = await approveRequest(req.id, req.childId) as Record<string, unknown> | null;
        if (res) {
            applyDataSnapshot(res);
            showToast('Заявка подтверждена', 'success');
        }
    }

    async function handleReject(req: Request) {
        const res = await rejectRequest(req.id, req.childId) as Record<string, unknown> | null;
        if (res) {
            applyDataSnapshot(res);
            showToast('Заявка отклонена', 'info');
        }
    }

    async function handleDelete(reqId: unknown) {
        const ok = await deleteRequest(reqId, $appStore.currentChildId);
        if (ok) {
            appStore.setState({ requests: requests.filter(r => r.id !== reqId) });
        }
    }

    function formatDate(dateStr: string | null | undefined): string {
        if (!dateStr) return '';
        try { return new Date(dateStr).toLocaleDateString('ru-RU'); } catch { return ''; }
    }
</script>

<section class="section" id="requests-section">
    {#if isAdmin}
    <!-- Admin view: incoming requests -->
    <div class="admin-only">
        <div class="section__header">
            <h2>Входящие заявки</h2>
            <p class="section__subtitle">Показываются заявки всех детей, новые появляются автоматически.</p>
        </div>

        {#if incomingRequests.length > 0}
        <div id="incoming-requests-list" class="history-list">
            {#each incomingRequests as req (req.id)}
            <article class="history-item request-item">
                <div class="history-item__icon">
                    <span class="gamified-icon icon-envelope" aria-hidden="true"></span>
                </div>
                <div class="history-item__body">
                    <p class="history-item__title">{req.taskId ? 'Задание' : 'Покупка'}</p>
                    <p class="history-item__meta">{req.childNickname ?? ''} · {formatDate(req.createdAt as string)}</p>
                </div>
                <div class="history-item__actions">
                    <button class="btn btn--success btn--small" on:click={() => handleApprove(req)}>✓</button>
                    <button class="btn btn--danger btn--small" on:click={() => handleReject(req)}>✗</button>
                </div>
            </article>
            {/each}
        </div>
        {:else}
        <article class="empty-state" id="incoming-requests-empty">
            <span class="empty-state__icon">
                <span class="gamified-icon icon-envelope" aria-hidden="true"></span>
            </span>
            <p class="empty-state__title">Нет новых заявок</p>
            <p class="empty-state__hint">Ребенок может отправить заявку после завершения задачи — она появится тут автоматически.</p>
        </article>
        {/if}
    </div>
    {:else}
    <!-- Child view: my requests -->
    <div class="child-only" style="margin-top: 2rem;">
        <h2>Мои отправленные заявки</h2>
        {#if myRequests.length > 0}
        <div class="history-list" id="my-requests-list">
            {#each myRequests as req (req.id)}
            <article class="history-item request-item">
                <div class="history-item__icon">
                    <span class="gamified-icon icon-envelope" aria-hidden="true"></span>
                </div>
                <div class="history-item__body">
                    <p class="history-item__title">{req.status === 'approved' ? '✅ Подтверждено' : req.status === 'rejected' ? '❌ Отклонено' : '⏳ Ожидание'}</p>
                    <p class="history-item__meta">{formatDate(req.createdAt as string)}</p>
                </div>
                {#if req.status === 'rejected'}
                <button class="btn btn--ghost btn--small" on:click={() => handleDelete(req.id)}>✕</button>
                {/if}
            </article>
            {/each}
        </div>
        {:else}
        <article class="empty-state" id="my-requests-empty">
            <span class="empty-state__icon">
                <span class="gamified-icon icon-envelope" aria-hidden="true"></span>
            </span>
            <p class="empty-state__title">Нет активных заявок</p>
            <p class="empty-state__hint">Отмечайте выполнение задач — заявки сразу переходят в статус "отправлена".</p>
        </article>
        {/if}
    </div>
    {/if}
</section>
