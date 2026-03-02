/** @file Child Switcher Ui frontend UI module */
/* eslint-disable max-lines, max-statements */
const CHILD_SWITCHER_STYLE = `
<style>
    .child-menu {position: relative; z-index: var(--layer-dropdown); }
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
        font - size: 0.7em;
    opacity: 0.6;
    transition: transform 0.25s;
        }
    .child-menu.active .child-menu-btn__arrow {transform: rotate(180deg); }
    .child-menu-dropdown {
        display: none;
    position: absolute;
    top: 100%;
    bottom: auto;
    right: 0;
    margin-top: 10px;
    background: #1e1e30;
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 16px;
    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
    min-width: 220px;
    z-index: var(--layer-dropdown);
    overflow: hidden;
    animation: dropdownFadeIn 0.2s ease-out;
        }
    .child-menu-dropdown.child-menu-dropdown--flipped {
        top: auto;
    bottom: calc(100% + 8px);
        }
    @keyframes dropdownFadeIn {
        from {opacity: 0; }
    to {opacity: 1; }
        }
    .child-menu.active .child-menu-dropdown {display: block; }
    .child-menu-dropdown::before {
        position: absolute;
    top: -6px;
    right: 1rem;
    border: 6px solid transparent;
    border-bottom-color: var(--color-bg-card);
        }
    .child-menu-dropdown.child-menu-dropdown--flipped::before {
        top: auto;
    bottom: -6px;
    border-bottom-color: transparent;
    border-top-color: var(--color-bg-card);
        }
    .child-menu-item {
        padding: 12px 18px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    cursor: pointer;
    transition: all 0.2s;
    font-size: 0.95rem;
        }
    .child-menu-item:hover {background: rgba(255, 255, 255, 0.08); }
    .child-menu-item.active {
        background: rgba(255, 215, 0, 0.15);
    color: #ffd700;
        }
    .child-menu-item__name {font - weight: 600; }
    .child-menu-item__balance {
        font - size: 0.85em;
    opacity: 0.8;
    background: rgba(0,0,0,0.2);
    padding: 2px 8px;
    border-radius: 8px;
    display: inline-flex;
    align-items: center;
    gap: 0.2rem;
        }
    .child-menu-item__balance .gamified-icon,
    .child-menu-btn__icon .gamified-icon {
        width: 0.9rem;
    height: 0.9rem;
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
    @media (max-width: 900px), (hover: none) and (pointer: coarse) {
            .child-menu-btn {
        padding: 6px 10px;
    gap: 6px;
    font-size: 0.85rem;
            }

            .child-menu-dropdown {
                position: fixed !important;
                left: 50% !important;
                right: auto !important;
                transform: translateX(-50%) !important;
                width: calc(100vw - 1rem) !important;
                max-width: 480px !important;
                max-height: 60vh;
                bottom: 78px !important;
                top: auto !important;
                margin: 0 !important;
                overflow-y: auto;
                border-radius: 16px;
                box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.3);
                z-index: 99999 !important;
                animation: dropdownSlideUp 0.2s ease-out !important;
            }

            .child-menu-dropdown.child-menu-dropdown--flipped,
            .child-menu-dropdown:not(.child-menu-dropdown--flipped) {
                top: auto !important;
                bottom: 78px !important;
            }

    .child-menu-item {
        padding: 10px 14px;
    font-size: 0.85rem;
            }
        }
    @keyframes dropdownSlideUp {
        from { opacity: 0; transform: translateX(-50%) translateY(10px); }
    to { opacity: 1; transform: translateX(-50%) translateY(0); }
        }
</style>
`;

const MOBILE_LAYOUT_QUERY = '(max-width: 900px)';
const DROPDOWN_GAP = 8;
const TOUCH_LAYOUT_QUERY = '(hover: none) and (pointer: coarse)';
const MOBILE_DROPDOWN_EDGE_PADDING = 8;
const MOBILE_DROPDOWN_MAX_WIDTH = 480;

function isMobileChildMenuLayout() {
    return window.matchMedia(MOBILE_LAYOUT_QUERY).matches || window.matchMedia(TOUCH_LAYOUT_QUERY).matches;
}

function buildChildRow(child, isActive, escapeHtml) {
    return `
        <div class="child-menu-item ${isActive ? 'active' : ''}" 
             onclick="window.app.switchChild(${child.id}); this.closest('.child-menu').classList.remove('active')">
            <span class="child-menu-item__name">${escapeHtml(child.name)}</span>
            <span class="child-menu-item__balance">${child.balance}<span class="gamified-icon icon-coin-stack" aria-hidden="true"></span></span>
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
        activeMenus.forEach((el) => {
            el.classList.remove('active');
            const dropdown = el.querySelector('.child-menu-dropdown');
            dropdown?.classList.remove('child-menu-dropdown--flipped');
        });
        document.dispatchEvent(new CustomEvent('child-menu-visibility', { detail: { isActive: false } }));
    });
}

function parseSafeInset(varName) {
    const value = getComputedStyle(document.documentElement).getPropertyValue(varName).trim();
    return value ? parseFloat(value) || 0 : 0;
}

function getViewportMetrics() {
    const visualViewport = window.visualViewport;
    return {
        width: Math.max(visualViewport?.width || 0, window.innerWidth || 0),
        height: Math.max(visualViewport?.height || 0, window.innerHeight || 0),
        offsetTop: visualViewport?.offsetTop || 0
    };
}

function shouldFlipDropdown({ isMobileLayout, spaceAbove, spaceBelow, dropdownHeight, rectTop, viewportHeight }) {
    if (rectTop > viewportHeight / 1.5) {
        return true;
    }
    if (isMobileLayout) {
        return true;
    }
    return spaceBelow < dropdownHeight && spaceAbove > spaceBelow;
}

function applyDropdownPosition({
    dropdown,
    rect,
    isMobileLayout,
    shouldFlip,
    dropdownHeight,
    viewportWidth,
    viewportHeight,
    safeTop,
    safeBottom
}) {
    // Force mobile styles via simple inline JS to bypass any WebKit/Orion CSS calc/variable parsing bugs.
    const isMobile = isMobileLayout || viewportWidth <= 900;
    if (isMobile) {
        dropdown.style.position = 'fixed';
        dropdown.style.left = '50%';
        dropdown.style.right = 'auto';
        dropdown.style.transform = 'translateX(-50%)';
        dropdown.style.width = 'calc(100vw - 16px)';
        dropdown.style.maxWidth = '480px';
        dropdown.style.bottom = '78px';
        dropdown.style.top = 'auto';
        dropdown.style.margin = '0';
        dropdown.style.zIndex = '99999';
        return;
    }

    dropdown.style.left = '';
    dropdown.style.right = '';
    dropdown.style.width = '';
    dropdown.style.maxWidth = '';
    dropdown.style.position = 'absolute';
    if (shouldFlip) {
        dropdown.style.top = 'auto';
        dropdown.style.bottom = `calc(100% + ${DROPDOWN_GAP}px)`;
        return;
    }

    dropdown.style.top = `${Math.round(rect.bottom + DROPDOWN_GAP)}px`;
    dropdown.style.bottom = 'auto';
}

function positionChildMenuDropdown(childMenu) {
    const dropdown = childMenu.querySelector('.child-menu-dropdown');
    const toggleButton = childMenu.querySelector('[data-child-toggle]');
    if (!dropdown || !toggleButton) return;

    if (!childMenu.classList.contains('active')) {
        dropdown.classList.remove('child-menu-dropdown--flipped');
        dropdown.style.top = '';
        dropdown.style.bottom = '';
        dropdown.style.left = '';
        dropdown.style.right = '';
        dropdown.style.width = '';
        dropdown.style.maxWidth = '';
        return;
    }

    const {
        width: viewportWidth,
        height: viewportHeight,
        offsetTop: viewportOffsetTop
    } = getViewportMetrics();
    const rect = toggleButton.getBoundingClientRect();
    const adjustedRectTop = rect.top - viewportOffsetTop;
    const adjustedRectBottom = rect.bottom - viewportOffsetTop;
    const dropdownHeight = dropdown.getBoundingClientRect().height || dropdown.scrollHeight;
    const safeTop = parseSafeInset('--safe-top');
    const safeBottom = parseSafeInset('--safe-bottom');
    const spaceAbove = adjustedRectTop - safeTop - 12;
    const spaceBelow = viewportHeight - adjustedRectBottom - safeBottom - 12;
    const isMobileLayout = isMobileChildMenuLayout();
    const shouldFlip = shouldFlipDropdown({
        isMobileLayout,
        spaceAbove,
        spaceBelow,
        dropdownHeight,
        rectTop: rect.top,
        viewportHeight
    });
    dropdown.classList.toggle('child-menu-dropdown--flipped', shouldFlip);
    applyDropdownPosition({
        dropdown,
        rect,
        isMobileLayout,
        shouldFlip,
        dropdownHeight,
        viewportWidth,
        viewportHeight,
        safeTop,
        safeBottom
    });
}

function refreshActiveChildDropdowns() {
    const menus = document.querySelectorAll('.child-menu.active');
    menus.forEach(positionChildMenuDropdown);
    if (!window._childMenuResizeListener) {
        window._childMenuResizeListener = true;
        window.addEventListener('resize', refreshActiveChildDropdowns);
        window.addEventListener('orientationchange', refreshActiveChildDropdowns);
    }
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
                <span class="child-menu-btn__icon"><span class="gamified-icon icon-child" aria-hidden="true"></span></span>
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
            if (isActive) {
                requestAnimationFrame(() => positionChildMenuDropdown(childMenu));
            } else {
                positionChildMenuDropdown(childMenu);
            }
            refreshActiveChildDropdowns();
            document.dispatchEvent(new CustomEvent('child-menu-visibility', { detail: { isActive } }));
        });
    }
}
