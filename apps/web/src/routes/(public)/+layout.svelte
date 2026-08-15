<script lang="ts">
    // EXPLAIN: Root +layout.server.ts already provides appConfig/i18n/locale/
    // EXPLAIN: session via data; this layout only owns the public shell.
</script>

<div class="public-shell">
    <main class="public-inner">
        <slot />
    </main>
</div>