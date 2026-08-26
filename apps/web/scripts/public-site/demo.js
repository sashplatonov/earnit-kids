import { messages, resolveDocumentLocale } from './i18n.js';
import { DEMO_TABS, demoData, normalizeDemoTab } from './demo-data.js';

export { DEMO_TABS, normalizeDemoTab } from './demo-data.js';

const sign = (entry) => entry.kind === 'earned' ? '+' : '-';

export function formatDemoDate(value, locale) {
    return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export function formatDemoAmount(amount, locale) {
    return new Intl.NumberFormat(locale, { signDisplay: 'always' }).format(amount);
}

function text(value) {
    return document.createTextNode(String(value));
}

function element(tag, className, children = []) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    node.append(...children);
    return node;
}

function label(message, value) {
    return element('span', 'demo-meta', [text(`${message}: ${value}`)]);
}

function fixture(copy, section, key) {
    return copy.fixture[section][key];
}

function renderTasks(copy) {
    return demoData.tasks.length ? element('ul', 'demo-list', demoData.tasks.map((task) => element('li', 'demo-row', [
        element('div', 'demo-row-main', [element('h3', null, [text(fixture(copy, 'taskNames', task.nameKey))]), label(copy.task.group, fixture(copy, 'groups', task.groupKey)), label(copy.task.repeat, fixture(copy, 'repeats', task.repeatKey))]),
        element('strong', 'demo-amount demo-amount-positive', [text(`+${task.coins} ${copy.coins}`)]),
    ]))) : element('p', 'demo-empty', [text(copy.empty)]);
}

function renderRewards(copy) {
    return demoData.rewards.length ? element('ul', 'demo-list', demoData.rewards.map((reward) => element('li', 'demo-row', [
        element('div', 'demo-row-main', [element('h3', null, [text(fixture(copy, 'rewardNames', reward.nameKey))]), label(copy.reward.group, fixture(copy, 'groups', reward.groupKey)), label(copy.reward.available, fixture(copy, 'availability', reward.available ? 'yes' : 'no'))]),
        element('strong', 'demo-amount demo-amount-negative', [text(`${reward.price} ${copy.coins}`)]),
    ]))) : element('p', 'demo-empty', [text(copy.empty)]);
}

function renderHistory(locale, copy) {
    return demoData.history.length ? element('ul', 'demo-list', demoData.history.map((entry) => element('li', 'demo-row', [
        element('div', 'demo-row-main', [element('h3', null, [text(fixture(copy, 'taskNames', entry.labelKey) ?? fixture(copy, 'rewardNames', entry.labelKey))]), element('time', 'demo-meta', [text(formatDemoDate(entry.date, locale))])]),
        element('span', `demo-amount demo-amount-${entry.kind}`, [text(`${sign(entry)}${entry.amount} ${copy.coins}`), text(` · ${entry.kind === 'earned' ? copy.historyEntry.earned : copy.historyEntry.spent}`)]),
    ]))) : element('p', 'demo-empty', [text(copy.empty)]);
}

function renderRequests(locale, copy) {
    return demoData.requests.length ? element('ul', 'demo-list', demoData.requests.map((request) => element('li', 'demo-row', [
        element('div', 'demo-row-main', [element('h3', null, [text(fixture(copy, 'taskNames', request.labelKey) ?? fixture(copy, 'rewardNames', request.labelKey))]), label(copy.request.submitted, request.kind === 'task' ? copy.request.task : copy.request.reward), element('time', 'demo-meta', [text(formatDemoDate(request.date, locale))])]),
        element('span', `demo-status demo-status-${request.status}`, [text(copy.request[request.status]), text(` · ${formatDemoAmount(request.amount, locale)} ${copy.coins}`)]),
    ]))) : element('p', 'demo-empty', [text(copy.empty)]);
}

export function renderDemo(documentRef = document, windowRef = window) {
    const root = documentRef.querySelector('[data-demo-root]');
    if (!root) return;
    const locale = resolveDocumentLocale(documentRef);
    const copy = messages[locale].demo;
    const tab = normalizeDemoTab(new URL(windowRef.location.href).searchParams.get('tab'));
    root.replaceChildren();

    const title = element('h1', null, [text(messages[locale].pageTitles.demo)]);
    title.id = 'demo-title';
    const notice = element('p', 'demo-notice', [text(copy.notice)]);
    const summary = element('div', 'demo-summary', [element('div', null, [element('span', 'demo-eyebrow', [text(copy.sampleChild)]), element('strong', null, [text(copy.sampleChildName)])]), element('div', null, [element('span', 'demo-eyebrow', [text(copy.balance)]), element('strong', null, [text(`${demoData.child.balance} ${copy.coins}`)])])]);
    const tabs = element('div', 'demo-tabs', []);
    const panels = element('div', 'demo-panels', []);
    const renderers = { tasks: () => renderTasks(copy), rewards: () => renderRewards(copy), history: () => renderHistory(locale, copy), requests: () => renderRequests(locale, copy) };

    DEMO_TABS.forEach((id) => {
        const selected = id === tab;
        const button = element('button', `demo-tab${selected ? ' is-active' : ''}`, [text(copy[id])]);
        const panel = element('section', 'demo-panel', [element('h2', null, [text(copy[id])]), renderers[id]()]);
        button.type = 'button'; button.id = `demo-tab-${id}`; button.setAttribute('role', 'tab'); button.setAttribute('aria-selected', String(selected)); button.setAttribute('aria-controls', `demo-panel-${id}`); button.tabIndex = selected ? 0 : -1;
        panel.id = `demo-panel-${id}`; panel.setAttribute('role', 'tabpanel'); panel.setAttribute('aria-labelledby', button.id); panel.tabIndex = 0; panel.hidden = !selected;
        button.addEventListener('click', () => selectDemoTab(documentRef, windowRef, id));
        button.addEventListener('keydown', (event) => {
            const index = DEMO_TABS.indexOf(id);
            let next;
            if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = DEMO_TABS[(index + 1) % DEMO_TABS.length];
            if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = DEMO_TABS[(index - 1 + DEMO_TABS.length) % DEMO_TABS.length];
            if (event.key === 'Home') next = DEMO_TABS[0];
            if (event.key === 'End') next = DEMO_TABS.at(-1);
            if (event.key === 'Enter' || event.key === ' ') next = id;
            if (next) { event.preventDefault(); selectDemoTab(documentRef, windowRef, next, true); }
        });
        tabs.append(button); panels.append(panel);
    });
    const actions = element('div', 'demo-actions', [element('a', 'btn btn-secondary', [text(copy.signIn)]), element('a', 'btn btn-primary', [text(copy.shop)])]);
    actions.children[0].href = '/api/login-google/start?continue=%2Fapp';
    actions.children[1].href = locale === 'ru' ? '/ru/app?context=rewards' : '/app?context=rewards';
    root.append(element('div', 'container demo-container', [title, notice, summary, element('div', 'demo-tablist', [tabs]), panels, actions]));
}

export function selectDemoTab(documentRef, windowRef, tab, focus = false) {
    const next = normalizeDemoTab(tab);
    const url = new URL(windowRef.location.href);
    url.searchParams.set('tab', next);
    windowRef.history.replaceState({}, '', url);
    renderDemo(documentRef, windowRef);
    if (focus) documentRef.querySelector(`#demo-tab-${next}`)?.focus();
}

if (typeof window !== 'undefined' && typeof document !== 'undefined') renderDemo(document, window);
