/* Helper module for child switcher positioning and dropdown placement */
const MOBILE_LAYOUT_QUERY = '(max-width: 900px)';
const DROPDOWN_GAP = 8;
const TOUCH_LAYOUT_QUERY = '(hover: none) and (pointer: coarse)';

function isMobileChildMenuLayout() {
    return window.matchMedia(MOBILE_LAYOUT_QUERY).matches || window.matchMedia(TOUCH_LAYOUT_QUERY).matches;
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
    const isMobile = isMobileLayout || viewportWidth <= 900;
    if (isMobile) {
        applyMobileDropdownPosition(dropdown);
        return;
    }

    applyDesktopDropdownPosition(dropdown, shouldFlip, rect);
}

function applyMobileDropdownPosition(dropdown) {
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
}

function applyDesktopDropdownPosition(dropdown, shouldFlip, rect) {
    clearDropdownBoxStyles(dropdown);
    dropdown.style.position = 'absolute';
    if (shouldFlip) {
        dropdown.style.top = 'auto';
        dropdown.style.bottom = `calc(100% + ${DROPDOWN_GAP}px)`;
        return;
    }
    dropdown.style.top = `${Math.round(rect.bottom + DROPDOWN_GAP)}px`;
    dropdown.style.bottom = 'auto';
}

function clearDropdownBoxStyles(dropdown) {
    dropdown.style.left = '';
    dropdown.style.right = '';
    dropdown.style.width = '';
    dropdown.style.maxWidth = '';
}

function resetDropdownPosition(dropdown) {
    dropdown.classList.remove('child-menu-dropdown--flipped');
    dropdown.style.top = '';
    dropdown.style.bottom = '';
    dropdown.style.left = '';
    dropdown.style.right = '';
    dropdown.style.width = '';
    dropdown.style.maxWidth = '';
}

function getDropdownPositionContext(dropdown, toggleButton) {
    const viewportMetrics = getViewportMetrics();
    const rect = toggleButton.getBoundingClientRect();
    const adjustedRectTop = rect.top - viewportMetrics.offsetTop;
    const adjustedRectBottom = rect.bottom - viewportMetrics.offsetTop;
    const safeTop = parseSafeInset('--safe-top');
    const safeBottom = parseSafeInset('--safe-bottom');
    const isMobileLayout = isMobileChildMenuLayout();

    return {
        rect,
        isMobileLayout,
        viewportWidth: viewportMetrics.width,
        viewportHeight: viewportMetrics.height,
        dropdownHeight: dropdown.getBoundingClientRect().height || dropdown.scrollHeight,
        safeTop,
        safeBottom,
        spaceAbove: adjustedRectTop - safeTop - 12,
        spaceBelow: viewportMetrics.height - adjustedRectBottom - safeBottom - 12
    };
}

function positionChildMenuDropdown(childMenu) {
    const dropdown = childMenu.querySelector('.child-menu-dropdown');
    const toggleButton = childMenu.querySelector('[data-child-toggle]');
    if (!dropdown || !toggleButton) return;

    if (!childMenu.classList.contains('active')) {
        resetDropdownPosition(dropdown);
        return;
    }

    const positionContext = getDropdownPositionContext(dropdown, toggleButton);
    const shouldFlip = shouldFlipDropdown({
        isMobileLayout: positionContext.isMobileLayout,
        spaceAbove: positionContext.spaceAbove,
        spaceBelow: positionContext.spaceBelow,
        dropdownHeight: positionContext.dropdownHeight,
        rectTop: positionContext.rect.top,
        viewportHeight: positionContext.viewportHeight
    });
    dropdown.classList.toggle('child-menu-dropdown--flipped', shouldFlip);
    applyDropdownPosition({
        dropdown,
        rect: positionContext.rect,
        isMobileLayout: positionContext.isMobileLayout,
        shouldFlip,
        dropdownHeight: positionContext.dropdownHeight,
        viewportWidth: positionContext.viewportWidth,
        viewportHeight: positionContext.viewportHeight,
        safeTop: positionContext.safeTop,
        safeBottom: positionContext.safeBottom
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

export { positionChildMenuDropdown, refreshActiveChildDropdowns };
