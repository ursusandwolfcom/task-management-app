// @ts-check
/** @typedef {import('./types').Workspace} Workspace */
/** @typedef {import('./types').Task} Task */
/** @typedef {import('./types').Priority} Priority */
/** @typedef {import('./types').Plan} Plan */
/** @typedef {import('./types').Preferences} Preferences */
/** @typedef {import('./ui').TaskFilter} TaskFilter */
import { calculatePlan, defaultPreferences, exampleWorkspace, isTaskSort, loadPreferences, loadWorkspace,
  nextId, savePreferences, saveWorkspace, selectedProject } from './store.js';
import { element, notify, openTaskDialog, render } from './ui.js';

/** @type {Workspace} */
let workspace;
let storageWarning = '';
try { workspace = loadWorkspace(localStorage); }
catch (error) { workspace = exampleWorkspace(); storageWarning = message(error); }
let preferences = defaultPreferences();
try { preferences = loadPreferences(localStorage); } catch { /* Keep usable defaults if storage access is blocked. */ }
/** @type {Plan | null} */
let plan = null;
/** @type {TaskFilter} */
let filter = 'all';
let busy = false;

/** @param {unknown} error */
function message(error) { return error instanceof Error ? error.message : 'Не удалось выполнить действие.'; }
function draw() { render(workspace, plan, filter, busy || Boolean(storageWarning), preferences); }
/** @param {Preferences} candidate */
function changePreferences(candidate) {
  try {
    savePreferences(localStorage, candidate);
    preferences = candidate;
    notify(storageWarning);
  } catch (error) { notify(message(error)); }
  draw();
}

element('#task-sort').addEventListener('change', event => {
  if (!(event.target instanceof HTMLSelectElement) || !isTaskSort(event.target.value)) return;
  changePreferences({ ...preferences, taskSort: event.target.value });
});
element('#theme-toggle').addEventListener('click', () => {
  changePreferences({ ...preferences, theme: preferences.theme === 'dark' ? 'light' : 'dark' });
});
/** @param {boolean} value */
function setBusy(value) {
  busy = value;
  for (const control of document.querySelectorAll('dialog input, dialog select, dialog textarea, dialog button')) {
    if (control instanceof HTMLInputElement || control instanceof HTMLSelectElement
      || control instanceof HTMLTextAreaElement || control instanceof HTMLButtonElement) control.disabled = value;
  }
  draw();
}

async function refresh() {
  if (busy) return;
  setBusy(true);
  plan = null;
  try { plan = await calculatePlan(selectedProject(workspace)); notify(storageWarning); }
  catch (error) { notify(message(error)); }
  finally { setBusy(false); }
}

/** @param {Workspace} candidate */
async function commit(candidate) {
  if (busy || storageWarning) throw new Error('Изменения пока недоступны.');
  setBusy(true);
  try {
    const result = await calculatePlan(selectedProject(candidate));
    saveWorkspace(localStorage, candidate);
    workspace = candidate;
    plan = result;
    notify('');
  } finally { setBusy(false); }
}

/** @param {string} selector @param {unknown} error */
function formError(selector, error) {
  const target = element(selector);
  target.textContent = message(error);
  target.hidden = false;
}

element('#new-project').addEventListener('click', () => {
  /** @type {HTMLFormElement} */ (element('#project-form')).reset();
  element('#project-form-error').hidden = true;
  /** @type {HTMLDialogElement} */ (element('#project-dialog')).showModal();
});
element('#new-task').addEventListener('click', () => openTaskDialog(selectedProject(workspace), undefined));
element('#refresh-plan').addEventListener('click', () => { void refresh(); });

for (const button of document.querySelectorAll('[data-close]')) button.addEventListener('click', () => {
  if (button instanceof HTMLElement && button.dataset.close) {
    /** @type {HTMLDialogElement} */ (element(`#${button.dataset.close}`)).close();
  }
});

for (const dialog of document.querySelectorAll('dialog')) dialog.addEventListener('cancel', event => {
  if (busy) event.preventDefault();
});

element('#project-list').addEventListener('click', async event => {
  if (!(event.target instanceof Element) || busy) return;
  const button = event.target.closest('button[data-project]');
  if (!(button instanceof HTMLButtonElement) || button.disabled) return;
  const candidate = structuredClone(workspace);
  candidate.selectedProjectId = Number(button.dataset.project);
  try { await commit(candidate); }
  catch (error) { notify(message(error)); }
});

element('#task-filters').addEventListener('click', event => {
  if (!(event.target instanceof HTMLButtonElement)) return;
  const value = event.target.dataset.filter;
  if (value !== 'all' && value !== 'ready' && value !== 'active' && value !== 'completed') return;
  filter = value;
  for (const button of document.querySelectorAll('#task-filters button')) {
    button.setAttribute('aria-pressed', String(button === event.target));
  }
  draw();
});

element('#project-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = /** @type {HTMLFormElement} */ (event.currentTarget);
  const data = new FormData(form);
  const name = String(data.get('name') ?? '').trim();
  if (!name) { formError('#project-form-error', new Error('Укажите название проекта.')); return; }
  const candidate = structuredClone(workspace);
  const id = nextId(candidate.projects);
  candidate.projects.push({ id, name, description: String(data.get('description') ?? '').trim(), tasks: [] });
  candidate.selectedProjectId = id;
  try {
    await commit(candidate);
    /** @type {HTMLDialogElement} */ (element('#project-dialog')).close();
  } catch (error) { formError('#project-form-error', error); }
});

element('#task-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = new FormData(/** @type {HTMLFormElement} */ (event.currentTarget));
  const candidate = structuredClone(workspace);
  const project = selectedProject(candidate);
  const editingId = Number(data.get('id'));
  const name = String(data.get('name') ?? '').trim();
  const estimate = String(data.get('estimate') ?? '');
  const minutes = estimate ? Number(estimate) : null;
  if (!name || (minutes !== null && (!Number.isSafeInteger(minutes) || minutes <= 0))) {
    formError('#task-form-error', new Error('Укажите название и положительное целое число минут либо оставьте оценку пустой.'));
    return;
  }
  const existing = project.tasks.find(task => task.id === editingId);
  if (existing && existing.status !== 'NOT_STARTED') {
    formError('#task-form-error', new Error('Изменение уже начатой задачи недоступно в альфа-версии.')); return;
  }
  /** @type {Task} */
  const task = { id: existing?.id ?? nextId(project.tasks), name,
    priority: /** @type {Priority} */ (String(data.get('priority'))), estimatedMinutes: minutes,
    dueDate: String(data.get('dueDate') ?? '') || null,
    status: existing?.status ?? 'NOT_STARTED', dependencies: data.getAll('dependency').map(value => Number(value)) };
  if (existing) project.tasks = project.tasks.map(item => item.id === existing.id ? task : item);
  else project.tasks.push(task);
  try {
    await commit(candidate);
    /** @type {HTMLDialogElement} */ (element('#task-dialog')).close();
  } catch (error) { formError('#task-form-error', error); }
});

element('#task-list').addEventListener('click', async event => {
  if (!(event.target instanceof Element) || busy) return;
  const button = event.target.closest('button[data-action]');
  if (!(button instanceof HTMLButtonElement) || button.disabled) return;
  const id = Number(button.dataset.id);
  const original = selectedProject(workspace).tasks.find(task => task.id === id);
  if (!original) return;
  if (button.dataset.action === 'edit') { openTaskDialog(selectedProject(workspace), original); return; }
  const candidate = structuredClone(workspace);
  const project = selectedProject(candidate);
  const task = project.tasks.find(item => item.id === id);
  if (!task) return;
  try {
    switch (button.dataset.action) {
      case 'start':
        if (task.status !== 'NOT_STARTED' || !plan?.availableIds.includes(id)) throw new Error('Зависимости ещё не завершены.');
        task.status = 'IN_PROGRESS'; break;
      case 'complete':
      case 'fail':
        if (task.status !== 'IN_PROGRESS') throw new Error('Сначала начните задачу.');
        task.status = button.dataset.action === 'complete' ? 'COMPLETED' : 'FAILED'; break;
      case 'retry':
        if (task.status !== 'FAILED') throw new Error('Повторить можно только невыполненную задачу.');
        task.status = 'NOT_STARTED'; break;
      case 'delete':
        if (task.status !== 'NOT_STARTED') throw new Error('Удаление начатой задачи недоступно.');
        if (project.tasks.some(item => item.dependencies.includes(id))) {
          throw new Error('От этой задачи зависят другие. Сначала измените их зависимости.');
        }
        if (!window.confirm(`Удалить задачу «${task.name}»?`)) return;
        project.tasks = project.tasks.filter(item => item.id !== id); break;
      default: return;
    }
    await commit(candidate);
  } catch (error) { notify(message(error)); }
});

draw();
void refresh();
