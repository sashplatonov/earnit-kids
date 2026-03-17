/** @file Age-based theme manager */
import { saveChildTheme } from './api.js';

const STORAGE_PREFIX = 'earnit-age-theme';
const THEMES = {
    mint: {
        '--color-primary': 'oklch(0.76 0.13 160)',
        '--color-secondary': 'oklch(0.81 0.11 204)',
        '--color-bg': 'oklch(0.985 0.01 160)',
        '--color-bg-light': 'oklch(0.965 0.02 180)',
        '--color-bg-card': 'oklch(0.995 0.006 170)',
        '--color-bg-hover': 'oklch(0.95 0.02 185)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    },
    ocean: {
        '--color-primary': 'oklch(0.74 0.13 228)',
        '--color-secondary': 'oklch(0.8 0.11 258)',
        '--color-bg': 'oklch(0.985 0.01 230)',
        '--color-bg-light': 'oklch(0.965 0.02 240)',
        '--color-bg-card': 'oklch(0.995 0.006 220)',
        '--color-bg-hover': 'oklch(0.95 0.02 245)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    },
    sun: {
        '--color-primary': 'oklch(0.83 0.13 90)',
        '--color-secondary': 'oklch(0.76 0.13 48)',
        '--color-bg': 'oklch(0.988 0.012 88)',
        '--color-bg-light': 'oklch(0.972 0.02 82)',
        '--color-bg-card': 'oklch(0.998 0.006 90)',
        '--color-bg-hover': 'oklch(0.955 0.022 80)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    },
    coral: {
        '--color-primary': 'oklch(0.74 0.14 28)',
        '--color-secondary': 'oklch(0.8 0.1 356)',
        '--color-bg': 'oklch(0.987 0.012 18)',
        '--color-bg-light': 'oklch(0.97 0.02 12)',
        '--color-bg-card': 'oklch(0.997 0.006 18)',
        '--color-bg-hover': 'oklch(0.954 0.022 16)',
        '--gradient-primary': 'linear-gradient(135deg, var(--color-primary), var(--color-secondary))'
    },
    cosmos: {
        '--color-primary': 'oklch(0.73 0.12 300)',
        '--color-secondary': 'oklch(0.78 0.11 338)',
        '--color-bg': 'oklch(0.985 0.01 305)',
        '--color-bg-light': 'oklch(0.965 0.02 300)',
        '--color-bg-card': 'oklch(0.995 0.006 304)',
        '--color-bg-hover': 'oklch(0.95 0.02 308)',
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
    if (childId) {
        saveChildTheme(childId, normalized);
    }
    return normalized;
}

function loadTheme(childId, serverTheme) {
    if (serverTheme && THEMES[serverTheme]) return serverTheme;
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

export function useChildTheme(childId, serverTheme) {
    const theme = loadTheme(childId, serverTheme);
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
