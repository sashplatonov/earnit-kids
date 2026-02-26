/** @file Child Switcher Ui frontend UI module */
const CHILD_SWITCHER_STYLE = `
    <style>
        .child-menu { position: relative; z-index: 2100; }
        .child-menu-btn {
            background: rgba(255, 255, 255, 0.08);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 12px;
            padding: 8px 14px;
            color: white;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 10px;
            font-family: inherit;
            font-weight: 700;
            transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        }
        .child-menu-btn:hover {
            background: rgba(255, 255, 255, 0.15);
            border-color: rgba(255, 255, 255, 0.2);
            transform: translateY(-1px);
        }
        .child-menu-btn__arrow {
            font-size: 0.7em;
            opacity: 0.6;
            transition: transform 0.25s;
        }
        .child-menu.active .child-menu-btn__arrow { transform: rotate(180deg); }
        .child-menu-dropdown {
            display: none;
            position: absolute;
            top: 100%;
            right: 0;
            margin-top: 10px;
            background: #1e1e30;
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 16px;
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
            min-width: 220px;
            z-index: 2100;
            overflow: hidden;
            animation: dropdownFade 0.2s ease-out;
        }
        @keyframes dropdownFade {
            from { opacity: 0; transform: translateY(-10px); }
            to { opacity: 1; transform: translateY(0); }
        }
        .child-menu.active .child-menu-dropdown { display: block; }
        .child-menu-item {
            padding: 12px 18px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            cursor: pointer;
            transition: all 0.2s;
            font-size: 0.95rem;
        }
        .child-menu-item:hover { background: rgba(255, 255, 255, 0.08); }
        .child-menu-item.active {
            background: rgba(255, 215, 0, 0.15);
            color: #ffd700;
        }
        .child-menu-item__name { font-weight: 600; }
        .child-menu-item__balance {
            font-size: 0.85em;
            opacity: 0.8;
            background: rgba(0,0,0,0.2);
            padding: 2px 8px;
            border-radius: 8px;
        }
        .child-menu-divider {
            height: 1px;
            background: rgba(255, 255, 255, 0.1);
            margin: 4px 0;
        }
        .child-menu-item.add-child-item {
            color: rgba(255, 255, 255, 0.5);
            font-weight: 500;
            justify-content: flex-start;
            gap: 12px;
        }
        .child-menu-item.add-child-item:hover {
            color: white;
            background: rgba(16, 185, 129, 0.1);
        }
        @media (max-width: 900px) {
            .child-menu-btn {
                padding: 6px 10px;
                gap: 6px;
                font-size: 0.85rem;
            }

            .child-menu-dropdown {
                top: auto;
                bottom: calc(100% + 8px);
                left: 0;
                right: auto;
                width: calc(100vw - 1rem);
                max-width: 420px;
                margin-top: 0;
                margin-left: -0.25rem;
                border-radius: 12px;
                z-index: 2600;
            }

            .child-menu-item {
                padding: 10px 14px;
                font-size: 0.85rem;
            }
        }
    </style>
`;

function buildChildRow(child, isActive, escapeHtml) {
    return `
        <div class="child-menu-item ${isActive ? 'active' : ''}" 
             onclick="window.app.switchChild(${child.id}); this.closest('.child-menu').classList.remove('active')">
            <span class="child-menu-item__name">${escapeHtml(child.name)}</span>
            <span class="child-menu-item__balance">${child.balance} 🪙</span>
        </div>
    `;
}

function ensureChildMenuOutsideClickListener() {
    if (window._childMenuListener) return;

    window._childMenuListener = true;
    document.addEventListener('click', (event) => {
        if (event.target.closest('.child-menu')) return;
        const activeMenus = document.querySelectorAll('.child-menu.active');
        if (!activeMenus.length) return;
        activeMenus.forEach((el) => el.classList.remove('active'));
        document.dispatchEvent(new CustomEvent('child-menu-visibility', { detail: { isActive: false } }));
    });
}

export function renderChildSwitcherUI(state, escapeHtml) {
    if (!state.isAdmin) return;

    const container = document.getElementById('child-switcher-container');
    if (!container) return;

    if (state.children.length === 0) {
        container.innerHTML = '<button class="btn btn--primary btn--small" onclick="window.app.openAddChildModal()">+ Ребенок</button>';
        return;
    }

    const currentChild = state.children.find((child) => child.id == state.currentChildId);
    const childName = currentChild ? currentChild.name : 'Выберите ребенка';
    const childRows = state.children
        .map((child) => buildChildRow(child, state.currentChildId === child.id, escapeHtml))
        .join('');

    container.innerHTML = `
        <div class="child-menu">
            <button type="button" class="child-menu-btn" data-child-toggle>
                <span class="child-menu-btn__icon">👶</span>
                <span class="child-menu-btn__name">${escapeHtml(childName)}</span>
                <span class="child-menu-btn__arrow">▼</span>
            </button>
            <div class="child-menu-dropdown">
                ${childRows}
                <div class="child-menu-divider"></div>
                <div class="child-menu-item add-child-item" onclick="window.app.openAddChildModal(); this.closest('.child-menu').classList.remove('active')">
                    <span class="child-menu-item__icon">+</span>
                    <span class="child-menu-item__name">Добавить ребенка</span>
                </div>
            </div>
        </div>
        ${CHILD_SWITCHER_STYLE}
    `;

    ensureChildMenuOutsideClickListener();

    const childMenu = container.querySelector('.child-menu');
    const toggleButton = childMenu?.querySelector('[data-child-toggle]');
    if (childMenu && toggleButton) {
        toggleButton.addEventListener('click', (event) => {
            event.stopPropagation();
            const isActive = childMenu.classList.toggle('active');
            document.dispatchEvent(new CustomEvent('child-menu-visibility', { detail: { isActive } }));
        });
    }
}
