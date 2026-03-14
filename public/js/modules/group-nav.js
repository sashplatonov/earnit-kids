/** @file Group Navigation frontend UI module */
import { escapeHtml } from './utils.js';

/**
 * Initializes or updates the group navigation tab bar.
 * @param {string} containerId - ID of the container element for tabs (e.g. 'tasks-group-nav')
 * @param {Object} options - Navigation configuration options
 * @param {Array<string>} [options.groups=[]] - Array of group names
 * @param {string} [options.activeGroup='Все'] - The currently active group
 * @param {Function} [options.onChange] - Callback when a new tab is selected
 */
export function renderGroupNav(containerId, options = {}) {
    const { groups = [], activeGroup = 'Все', onChange } = options;
    const container = document.getElementById(containerId);
    if (!container) return;

    if (!groups || groups.length <= 1) {
        container.innerHTML = '';
        container.classList.add('hidden');
        return;
    }

    container.classList.remove('hidden');

    const finalGroups = ['Все', ...groups];

    // Build the HTML
    const tabsHtml = finalGroups.map((group) => {
        const isActive = group === activeGroup;
        const activeClass = isActive ? ' group-nav__tab--active' : '';
        const pressedState = isActive ? ' aria-pressed="true"' : ' aria-pressed="false"';
        return `<button type="button" class="group-nav__tab${activeClass}" data-group="${escapeHtml(group)}"${pressedState}>${escapeHtml(group)}</button>`;
    }).join('');

    container.innerHTML = `<div class="group-nav__scroll" id="${containerId}-scroll">${tabsHtml}</div>`;

    // Attach click handlers
    const scrollContainer = document.getElementById(`${containerId}-scroll`);
    if (scrollContainer) {
        const tabs = scrollContainer.querySelectorAll('.group-nav__tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const groupName = tab.dataset.group;
                if (groupName !== activeGroup && onChange) {
                    onChange(groupName);
                }
            });
        });
    }
}
