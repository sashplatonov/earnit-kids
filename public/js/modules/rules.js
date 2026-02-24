/** @file Rules frontend UI module */
import { state } from './state.js';
import { scheduleSave } from './actions.js';
import { openModal, closeModal, showToast } from './utils.js';

const DEFAULT_RULES = `## 🎯 Цели системы
* 🏆 Развитие ответственности и самостоятельности
* 🧺 Поощрение помощи по дому
* 💪 Поддержка в преодолении сложностей
* 📚 Мотивация к обучению и чтению
* 🤝 Укрепление доверия между родителями и детьми

## 🧭 Главная идея
* ✅ Монеты даются за **старания и вклад в жизнь семьи**
* 🏆 Крупные достижения заслуживают больших бонусов
* 🧾 Покупки планируются вместе, исходя из накоплений
* 🤝 Поддержка и похвала важнее любых монет

## 🪙 Рекомендации
* **Прозрачность**: Ребенок должен понимать, за что он получает монеты.
* **Регулярность**: Подтверждайте выполнение заданий своевременно.
* **Гибкость**: Систему можно адаптировать под текущие задачи семьи.
* **Позитивный настрой**: Монеты — это поощрение, а не способ наказания.

## 🛍️ Магазин и покупки
* Магазин помогает ребенку учиться распределять ресурсы и копить на важные цели.
* При крупных покупках рекомендуется обсуждать их значимость и необходимость.

## 🧩 Маленькие советы
* **Хвалите за процесс**, а не только за результат.
* **Обсуждайте цели** на неделю вместе.
* **Празднуйте** достижение крупных целей!
`;

// Render Rules
export function renderRules() {
    const container = document.getElementById('rules-display');
    if (!container) return;

    // Use stored rules or default
    const rulesMarkdown = state.rules || DEFAULT_RULES;

    // Use marked library if available, otherwise simple fallback
    if (window.marked) {
        container.innerHTML = window.marked.parse(rulesMarkdown);
    } else {
        // Fallback simple renderer (basic headers and lists)
        container.innerHTML = `<pre>${rulesMarkdown}</pre>`;
    }
}

// Open Edit Modal
export function openEditRules() {
    const textarea = document.getElementById('rules-text');
    if (textarea) {
        textarea.value = state.rules || DEFAULT_RULES;
        openModal('rules-modal');
    }
}

// Save Rules
export function saveRules() {
    const textarea = document.getElementById('rules-text');
    if (textarea) {
        state.rules = textarea.value;
        scheduleSave();
        renderRules();
        closeModal('rules-modal');
        showToast('Правила обновлены', 'success');
    }
}
