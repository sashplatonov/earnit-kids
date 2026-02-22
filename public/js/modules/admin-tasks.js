import { state } from './state.js';
import { renderTasks } from './ui.js';
import { showToast, closeModal, openModal, showConfirm } from './utils.js';
import { scheduleSave } from './actions.js';

let editingTaskId = null;

function setTaskFields(task = null) {
    const fields = ['name', 'group', 'coins', 'comment', 'freq-limit', 'freq-period'];
    const vals = task ? [
        task.name, task.group || '', task.coins, task.comment || '',
        task.frequency?.limit || '', task.frequency?.period || 'day'
    ] : ['', '', '', '', '', 'day'];

    fields.forEach((f, i) => {
        const el = document.getElementById(`task-${f}`);
        if (el) el.value = vals[i];
    });
}

export function openTaskModal(taskId = null) {
    editingTaskId = taskId;
    const task = taskId ? state.tasks.find(t => t.id == taskId) : null;
    if (taskId && !task) return;

    const title = document.getElementById('task-modal-title');
    if (title) title.textContent = taskId ? 'Редактировать задание' : 'Добавить задание';

    setTaskFields(task);

    const del = document.getElementById('task-delete');
    if (del) del.classList.toggle('hidden', !taskId);

    openModal('task-modal');
}

export function saveTask() {
    const name = document.getElementById('task-name').value.trim();
    const coins = parseInt(document.getElementById('task-coins').value);
    const fl = parseInt(document.getElementById('task-freq-limit').value) || 0;

    if (!name) return showToast('Введите название задания', 'error');
    if (!coins || coins < 1) return showToast('Введите количество монет', 'error');

    const data = {
        name, childId: state.currentChildId,
        group: document.getElementById('task-group').value.trim(),
        coins, comment: document.getElementById('task-comment').value.trim(),
        frequency: fl > 0 ? { limit: fl, period: document.getElementById('task-freq-period').value } : null
    };

    if (editingTaskId) {
        const idx = state.tasks.findIndex(t => t.id == editingTaskId);
        if (idx !== -1) state.tasks[idx] = { ...state.tasks[idx], ...data };
    } else {
        state.tasks.push({ id: Date.now(), ...data });
    }

    scheduleSave(); renderTasks(); closeModal('task-modal');
    showToast(editingTaskId ? 'Задание обновлено!' : 'Задание добавлено!', 'success');
}

export function deleteTask() {
    if (!editingTaskId) return;
    showConfirm('Удалить задание?', 'Это действие нельзя отменить.', () => {
        state.tasks = state.tasks.filter(t => t.id != editingTaskId);
        scheduleSave(); renderTasks(); closeModal('task-modal');
        showToast('Задание удалено', 'info');
    });
}

export const editTask = (id) => openTaskModal(id);
