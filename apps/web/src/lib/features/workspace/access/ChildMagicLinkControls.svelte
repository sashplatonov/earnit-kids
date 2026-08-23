<script lang="ts">
    import { appStore, type Child } from '$lib/stores/app';
    import { adminIssueChildMagicLink, adminRevokeChildMagicLink, adminGetChildMagicLinkStatus } from '$lib/services/api';
    let busy: string | number | null = null;
    let issuedLink = '';
    let issuedFor = '';
    let message = '';

    async function issue(child: Child): Promise<void> {
        busy = child.id; message = '';
        const result = await adminIssueChildMagicLink(child.id);
        busy = null;
        if (result?.link) { issuedLink = result.link; issuedFor = child.nickname; message = 'Copy this one-time link now. It will not be shown again.'; }
        else message = 'The child link could not be issued.';
    }
    async function revoke(child: Child): Promise<void> { busy = child.id; message = (await adminRevokeChildMagicLink(child.id)) ? 'Child link revoked.' : 'The child link could not be revoked.'; busy = null; }
    async function status(child: Child): Promise<void> { busy = child.id; const value = await adminGetChildMagicLinkStatus(child.id); message = value ? 'A child link is pending.' : 'No active child link.'; busy = null; }
    async function copy(): Promise<void> { try { await navigator.clipboard.writeText(issuedLink); message = 'Link copied.'; } catch { message = 'Copy failed. Use the browser copy action.'; } }
</script>

<section class="child-links" aria-labelledby="child-links-heading"><p class="eyebrow">Child access</p><h2 id="child-links-heading">One-time child links</h2><p class="hint">Links expire and can be revoked. The token is shown only after issue.</p>
    {#each $appStore.children as child (child.id)}<div class="child-row"><strong>{child.nickname}</strong><div class="actions"><button type="button" disabled={busy === child.id} on:click={() => issue(child)}>Issue</button><button type="button" class="secondary" disabled={busy === child.id} on:click={() => status(child)}>Status</button><button type="button" class="danger" disabled={busy === child.id} on:click={() => revoke(child)}>Revoke</button></div></div>{/each}
    {#if issuedLink}<div class="issued" role="status"><strong>Link for {issuedFor}</strong><button type="button" on:click={copy}>Copy one-time link</button></div>{/if}
    {#if message}<p class="message" role="status" aria-live="polite">{message}</p>{/if}
</section>

<style>
    .child-links{display:grid;gap:.7rem;color:#18243d}.eyebrow{margin:0;color:#3867d6;font-size:.72rem;font-weight:800;letter-spacing:.08em;text-transform:uppercase}h2{margin:0;font-size:1.2rem}.hint,.message{margin:0;color:#66718a;line-height:1.45}.child-row{display:flex;align-items:center;justify-content:space-between;gap:.7rem;padding:.7rem;border:1px solid #e1e6ef;border-radius:.8rem}.actions{display:flex;gap:.35rem;flex-wrap:wrap}button{min-height:2.75rem;padding:.5rem .7rem;border:0;border-radius:.65rem;background:#3867d6;color:#fff;font:inherit;font-weight:700}button:disabled{opacity:.55}.secondary{background:#fff;border:1px solid #cfd6e4;color:#33415f}.danger{background:#fff5f5;border:1px solid #f0caca;color:#a33b3b}.issued{display:grid;gap:.5rem;padding:.75rem;border:1px solid #b9e1c8;border-radius:.8rem;background:#f2fff5}.issued button{justify-self:start}.message{color:#17884b}button:focus-visible{outline:3px solid #80aaff;outline-offset:2px}@media(max-width:520px){.child-row{align-items:flex-start;flex-direction:column}.actions{width:100%}.actions button{flex:1}}
</style>
