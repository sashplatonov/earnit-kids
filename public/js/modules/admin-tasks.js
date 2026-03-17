/** @file Admin Tasks frontend UI module */
import { state } from './state.js';
import { renderTasks } from './ui.js';
import { showToast, closeModal, openModal, showConfirm } from './utils.js';
import { scheduleSave } from './actions.js';

let editingTaskId = null;

function getEditingTask() {
    return editingTaskId ? state.tasks.find(t => t.id == editingTaskId) : null;
}

function getTaskValidationError(name, coins) {
    if (!name) return 'Введите название задания';
    if (!coins || coins < 1) return 'Введите количество монет';
    return null;
}

function buildTaskPayload() {
    const fl = parseInt(document.getElementById('task-freq-limit').value) || 0;
    const existingTask = getEditingTask();

    return {
        name: document.getElementById('task-name').value.trim(),
        childId: existingTask?.childId ?? state.currentChildId,
        group: document.getElementById('task-group').value.trim(),
        coins: parseInt(document.getElementById('task-coins').value),
        comment: document.getElementById('task-comment').value.trim(),
        frequency: fl > 0 ? { limit: fl, period: document.getElementById('task-freq-period').value } : null
    };
}

function persistTask(data) {
    if (editingTaskId) {
        const idx = state.tasks.findIndex(t => t.id == editingTaskId);
        if (idx !== -1) state.tasks[idx] = { ...state.tasks[idx], ...data };
        return;
    }

    state.tasks.push({ id: Date.now(), ...data });
}

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
    if (taskId && typeof taskId === 'object') taskId = null;
    editingTaskId = taskId;
    const task = getEditingTask();
    if (taskId && !task) return;

    const title = document.getElementById('task-modal-title');
    if (title) title.textContent = taskId ? 'Редактировать задание' : 'Добавить задание';

    setTaskFields(task);

    const del = document.getElementById('task-delete');
    if (del) del.classList.toggle('hidden', !taskId);

    openModal('task-modal');
}

export function saveTask() {
    const data = buildTaskPayload();
    const error = getTaskValidationError(data.name, data.coins);
    if (error) return showToast(error, 'error');

    persistTask(data);

    scheduleSave(); renderTasks(); closeModal('task-modal');
    showToast(editingTaskId ? 'Задание обновлено!' : 'Задание добавлено!', 'success');
}

export function deleteTask() {
    if (!editingTaskId) return;
    showConfirm('Удалить задание?', 'Это действие нельзя отменить.', {
        onConfirm: () => {
            const task = state.tasks.find(t => t.id == editingTaskId);
            if (task) task.isDeleted = true;
            scheduleSave(); renderTasks(); closeModal('task-modal');
            showToast('Задание удалено', 'info');
        }
    });
}

export const editTask = (id) => openTaskModal(id);
