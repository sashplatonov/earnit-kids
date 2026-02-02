import { state } from './state.js';
import { scheduleSave } from './actions.js';
import { openModal, closeModal, showToast } from './utils.js';

const DEFAULT_RULES = `## 🎯 Цели системы
* 🎒 стабильно ходить в школу
* 🧺 помогать по дому
* 💪 учиться преодолевать сложности
* 📚 закрыть проект: сдача предметов за прошлый год
* 🤝 получать поддержку от родителей

_💵 Финансовый лимит: до **10,000 RSD в месяц**_

## 🧭 Главная идея
* ✅ Монеты даются **за взрослые действия**, не за оценки
* 🏆 Проект "сдать предметы" — главный большой трек
* 🧾 Покупки только в рамках 10,000 RSD/месяц
* 🤝 Поддержка словами важнее монет

## ⚖️ Лимиты монет в день
_Максимум: **10 🪙 в день**_

| Категория | Лимит |
|---|---|
| 🎒 Школа | макс 2 🪙/день |
| 🧺 Дом | макс 6 🪙/день |
| 🏃‍♀️ Шаги | макс 2 🪙/день |
| 💪 Преодоление | макс 5 🪙/день, до 3 раз в неделю |
| 📚 Сдача предмета | бонус, не входит в лимит |

## 🪙 Таблица заработка

| Категория | Действие | 🪙 | Лимит |
|---|---|---|---|
| 🎒 Школа | Полный день без пропусков | 2 | 1 раз/день |
| 🧺 Дом | Малое дело 15-20 мин | 2 | до 2 раз/день |
| 🧺 Дом | Среднее дело 30-45 мин | 4 | 1 раз/день |
| 🧺 Дом | Большое дело 60+ мин | 6 | до 3 раз/неделю |
| 🏃‍♀️ Шаги | 10,000 шагов за день | 2 | 1 раз/день |
| 💪 Преодоление | Сложный шаг | 5 | до 3 раз/неделю |
| 📚 Проект | Сдала один предмет | 60 | по факту |

## 🛍️ Правила магазина
_⚖️ **1 крупная покупка в месяц** (маникюр / ресницы / гаджет / одежда)_
При покупке проверяется: хватает монет 🪙 + укладываемся в 10,000 RSD 💵

## 🧩 Мини-правила
* **Засчитываем без придирок:** реально помогает + сделано до конца = монеты начисляются
* **Преодоление без торга:** важный шаг, который обычно избегала
* **Не решаем на эмоциях:** "Ок, вернемся вечером"
* **Монеты не заменяют поддержку:** поддержка словами даётся всегда 🤝

## 💬 Шаблоны поддержки
* "Я вижу твои усилия. Спасибо." 🤝
* "Классно, что ты помогла по дому." 🧺
* "Ты сделала сложный шаг. Я горжусь тобой." 💪
* "Давай вместе выберем следующий маленький шаг." 🧩
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
