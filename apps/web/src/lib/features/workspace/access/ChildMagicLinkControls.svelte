<script lang="ts">
    import { appStore, type Child } from '$lib/stores/app';
    import { adminIssueChildMagicLink, adminRevokeChildMagicLink, adminGetChildMagicLinkStatus } from '$lib/services/api';
    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';
    const i18n = useI18n();
    let busy: string | number | null = null;
    let issuedLink = '';
    let issuedFor = '';
    let message = '';

    async function issue(child: Child): Promise<void> {
        busy = child.id; message = '';
        const result = await adminIssueChildMagicLink(child.id);
        busy = null;
        if (result?.link) { issuedLink = result.link; issuedFor = child.nickname; message = $i18n.t('app.workspaceAccess.copyHint'); }
        else message = $i18n.t('app.workspaceAccess.issueError');
    }
    async function revoke(child: Child): Promise<void> { busy = child.id; message = (await adminRevokeChildMagicLink(child.id)) ? $i18n.t('app.workspaceAccess.linkRevoked') : $i18n.t('app.workspaceAccess.revokeError'); busy = null; }
    async function status(child: Child): Promise<void> { busy = child.id; const value = await adminGetChildMagicLinkStatus(child.id); message = value ? $i18n.t('app.workspaceAccess.linkPending') : $i18n.t('app.workspaceAccess.noActiveLink'); busy = null; }
    async function copy(): Promise<void> { try { await navigator.clipboard.writeText(issuedLink); message = $i18n.t('app.workspaceAccess.linkCopied'); } catch { message = $i18n.t('app.workspaceAccess.copyError'); } }
</script>

<section class="child-links" aria-labelledby="child-links-heading"><p class="eyebrow">{$i18n.t('app.workspaceAccess.childEyebrow')}</p><h2 id="child-links-heading">{$i18n.t('app.workspaceAccess.childTitle')}</h2><p class="hint">{$i18n.t('app.workspaceAccess.childHint')}</p>
    {#each $appStore.children as child (child.id)}<div class="child-row"><strong>{child.nickname}</strong><div class="actions"><button type="button" disabled={busy === child.id} on:click={() => issue(child)}><TelegramIcon name="key" size={18} />{$i18n.t('app.workspaceAccess.issue')}</button><button type="button" class="secondary" disabled={busy === child.id} on:click={() => status(child)}><TelegramIcon name="eye" size={18} />{$i18n.t('app.workspaceAccess.status')}</button><button type="button" class="danger" disabled={busy === child.id} on:click={() => revoke(child)}><TelegramIcon name="unlink" size={18} />{$i18n.t('app.workspaceAccess.revoke')}</button></div></div>{/each}
    {#if issuedLink}<div class="issued" role="status"><strong>{$i18n.t('app.workspaceAccess.linkFor', { name: issuedFor })}</strong><button type="button" on:click={copy}><TelegramIcon name="copy" size={18} />{$i18n.t('app.workspaceAccess.copyLink')}</button></div>{/if}
    {#if message}<p class="message" role="status" aria-live="polite">{message}</p>{/if}
</section>

<style>
    .child-links{display:grid;gap:.7rem;color:#18243d}.eyebrow{margin:0;color:#3867d6;font-size:.72rem;font-weight:800;letter-spacing:.08em;text-transform:uppercase}h2{margin:0;font-size:1.2rem}.hint,.message{margin:0;color:#66718a;line-height:1.45}.child-row{display:flex;align-items:center;justify-content:space-between;gap:.7rem;padding:.8rem 0;border-bottom:1px solid #edf0f5}.actions{display:flex;gap:.35rem;flex-wrap:wrap}button{display:inline-flex;align-items:center;justify-content:center;gap:.4rem;min-height:2.75rem;padding:.5rem .7rem;border:0;border-radius:.65rem;background:#3867d6;color:#fff;font:inherit;font-weight:700}button:disabled{opacity:.55}.secondary{background:#fff;border:1px solid #cfd6e4;color:#33415f}.danger{background:#fff5f5;border:1px solid #f0caca;color:#a33b3b}.issued{display:grid;gap:.5rem;padding:.8rem 0;border-bottom:1px solid #edf0f5}.issued button{justify-self:start}.message{color:#17884b}button:focus-visible{outline:3px solid #80aaff;outline-offset:2px}@media(max-width:520px){.child-row{align-items:flex-start;flex-direction:column}.actions{width:100%}.actions button{flex:1}}
</style>
