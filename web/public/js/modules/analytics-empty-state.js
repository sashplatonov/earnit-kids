export function renderNoChildrenState() {
    const container = document.getElementById('analytics-section');
    if (!container) return;

    if (!container.dataset.originalContent) {
        container.dataset.originalContent = container.innerHTML;
    }

    container.innerHTML = `
        <div class="container">
            <div class="empty-state" style="margin-top: 4rem; text-align: center; padding: 3rem 1rem; background: var(--color-bg-card); border-radius: var(--radius-xl); border: 2px dashed rgba(255,182,107,0.3);">
                <div class="gamified-icon icon-child-link" aria-hidden="true" style="width: 64px; height: 64px; margin: 0 auto 1.5rem; opacity: 0.8;"></div>
                <h2 style="font-size: 1.5rem; margin-bottom: 0.75rem; color: var(--color-text);">Нет детей в профиле</h2>
                <p style="color: var(--color-text-muted); margin-bottom: 2rem; max-width: 400px; margin-left: auto; margin-right: auto;">
                    Добавьте первого ребенка, чтобы видеть его достижения, создавать задания и следить за прогрессом.
                </p>
                <button class="btn btn--primary" id="analytics-add-child" style="min-width: 200px;">
                    <span class="gamified-icon icon-plus" aria-hidden="true" style="width: 1.2rem; height: 1.2rem;"></span>
                    Добавить ребенка
                </button>
            </div>
        </div>
    `;

    document.getElementById('analytics-add-child')?.addEventListener('click', () => {
        if (typeof window.app?.openAddChildModal === 'function') {
            window.app.openAddChildModal();
            return;
        }

        const modal = document.getElementById('add-child-modal');
        if (modal?.tagName === 'DIALOG' && typeof modal.showModal === 'function') {
            modal.showModal();
            return;
        }

        modal?.classList.remove('hidden');
    });
}