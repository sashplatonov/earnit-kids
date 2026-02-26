/** @file Age-based theme manager */
const STORAGE_PREFIX = 'earnit-age-theme';
const THEMES = {
    mint: {
        '--color-primary': 'oklch(0.73 0.18 165)',
        '--color-secondary': 'oklch(0.78 0.19 205)',
        '--color-bg': 'oklch(0.2 0.03 180)',
        '--color-bg-light': 'oklch(0.27 0.04 185)',
        '--color-bg-card': 'oklch(0.32 0.05 190)',
        '--color-bg-hover': 'oklch(0.38 0.05 195)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    },
    ocean: {
        '--color-primary': 'oklch(0.66 0.2 245)',
        '--color-secondary': 'oklch(0.71 0.18 275)',
        '--color-bg': 'oklch(0.18 0.03 250)',
        '--color-bg-light': 'oklch(0.25 0.04 255)',
        '--color-bg-card': 'oklch(0.3 0.05 260)',
        '--color-bg-hover': 'oklch(0.35 0.05 265)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    },
    sun: {
        '--color-primary': 'oklch(0.8 0.18 85)',
        '--color-secondary': 'oklch(0.72 0.19 45)',
        '--color-bg': 'oklch(0.24 0.03 85)',
        '--color-bg-light': 'oklch(0.32 0.04 80)',
        '--color-bg-card': 'oklch(0.37 0.05 75)',
        '--color-bg-hover': 'oklch(0.42 0.06 70)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    },
    coral: {
        '--color-primary': 'oklch(0.69 0.2 28)',
        '--color-secondary': 'oklch(0.72 0.17 350)',
        '--color-bg': 'oklch(0.2 0.03 5)',
        '--color-bg-light': 'oklch(0.28 0.04 8)',
        '--color-bg-card': 'oklch(0.33 0.05 12)',
        '--color-bg-hover': 'oklch(0.38 0.06 14)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    },
    cosmos: {
        '--color-primary': 'oklch(0.64 0.2 295)',
        '--color-secondary': 'oklch(0.67 0.2 335)',
        '--color-bg': 'oklch(0.16 0.03 300)',
        '--color-bg-light': 'oklch(0.23 0.04 300)',
        '--color-bg-card': 'oklch(0.29 0.05 305)',
        '--color-bg-hover': 'oklch(0.34 0.06 310)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    }
};

const DEFAULT_THEME = 'ocean';
const THEME_LABELS = {
    mint: 'Мята',
    ocean: 'Океан',
    sun: 'Солнце',
    coral: 'Коралл',
    cosmos: 'Космос'
};

function themeKey(childId) {
    return childId ? `${STORAGE_PREFIX}:${childId}` : `${STORAGE_PREFIX}:default`;
}

function normalize(theme) {
    if (THEMES[theme]) return theme;
    return DEFAULT_THEME;
}

function persistTheme(childId, theme) {
    const normalized = normalize(theme);
    localStorage.setItem(themeKey(childId), normalized);
    return normalized;
}

function loadTheme(childId) {
    const stored = localStorage.getItem(themeKey(childId));
    return normalize(stored);
}

function markButtons(theme) {
    document.querySelectorAll('.age-theme-switch__btn').forEach((button) => {
        const matches = button.dataset.theme === theme;
        button.classList.toggle('age-theme-switch__btn--active', matches);
        button.setAttribute('aria-pressed', String(matches));
    });
}

function updateCurrentThemeUi(theme) {
    const label = document.getElementById('age-theme-current-label');
    const swatch = document.getElementById('age-theme-current-swatch');
    if (label) {
        label.textContent = `Текущая тема: ${THEME_LABELS[theme] || THEME_LABELS[DEFAULT_THEME]}`;
    }
    if (swatch) {
        swatch.className = `age-theme-switch__swatch age-theme-switch__swatch--${theme}`;
    }
}

export function applyAgeTheme(theme) {
    const normalized = normalize(theme);
    Object.entries(THEMES[normalized]).forEach(([key, value]) => {
        document.documentElement.style.setProperty(key, value);
    });
    document.documentElement.dataset.ageTheme = normalized;
    markButtons(normalized);
    updateCurrentThemeUi(normalized);
    return normalized;
}

export function useChildTheme(childId) {
    const theme = loadTheme(childId);
    applyAgeTheme(theme);
    return theme;
}

export function changeChildTheme(childId, theme) {
    const applied = persistTheme(childId, theme);
    applyAgeTheme(applied);
    return applied;
}

export function setupAgeThemeControls(getChildId = () => null) {
    const switcher = document.querySelectorAll('.age-theme-switch__btn');
    if (!switcher.length) return;

    switcher.forEach((button) => {
        button.addEventListener('click', () => {
            const theme = button.dataset.theme;
            const childId = getChildId();
            changeChildTheme(childId, theme);
        });
    });

    const initialTheme = useChildTheme(getChildId());
    markButtons(initialTheme);
}
