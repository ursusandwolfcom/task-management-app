// @ts-check
/** @typedef {import('./types').Workspace} Workspace */
/** @typedef {import('./types').Task} Task */
/** @typedef {import('./types').Priority} Priority */
/** @typedef {import('./types').Plan} Plan */
/** @typedef {import('./types').Preferences} Preferences */
/** @typedef {import('./ui').TaskFilter} TaskFilter */
import { addDemoProjects, calculatePlan, defaultPreferences, exampleWorkspace, isTaskSort, isTheme, isTaskView, isDensity,
  loadCoordinatedWorkspace, loadPreferences, loadWorkspace,
  nextId, nextTheme, savePreferences, saveWorkspace, selectedProject, STORAGE_KEY } from './store.js';
import { element, notify, openTaskDialog, render } from './ui.js';

/** @type {Workspace} */
let workspace;
let storageWarning = '';
try { workspace = loadWorkspace(localStorage); }
catch (error) { workspace = exampleWorkspace(); storageWarning = message(error); }
if (!storageWarning) {
  try { workspace = await loadCoordinatedWorkspace(localStorage); }
  catch (error) { storageWarning = message(error); }
}
let preferences = defaultPreferences();
try { preferences = loadPreferences(localStorage); } catch { /* Keep usable defaults if storage access is blocked. */ }
/** @type {Plan | null} */
let plan = null;
/** @type {TaskFilter} */
let filter = 'all';
let search = '';
let busy = false;
if (!navigator.locks) storageWarning = 'Для сохранения нужен браузер с поддержкой Web Locks. Обновите браузер.';
let externalChange = false;

window.addEventListener('storage', event => {
  if (event.storageArea !== localStorage || (event.key !== STORAGE_KEY && event.key !== null)) return;
  externalChange = true;
  plan = null;
  notify('Данные изменены в другой вкладке. Перезагрузите страницу; введённые поля пока сохранены в форме.');
  draw();
});

/** @param {unknown} error */
function message(error) { return error instanceof Error ? error.message : 'Не удалось выполнить действие.'; }
function draw() { render(workspace, plan, filter, busy, preferences, Boolean(storageWarning), search); }
/** @param {Preferences} candidate */
function changePreferences(candidate) {
  try {
    savePreferences(localStorage, candidate);
    preferences = candidate;
    notify(externalChange ? 'Данные изменены в другой вкладке. Перезагрузите страницу.' : storageWarning);
  } catch (error) { notify(message(error)); draw(); return false; }
  draw();
  return true;
}

element('#task-sort').addEventListener('change', event => {
  if (!(event.target instanceof HTMLSelectElement) || !isTaskSort(event.target.value)) return;
  changePreferences({ ...preferences, taskSort: event.target.value });
});
element('#task-search').addEventListener('input', event => {
  if (!(event.target instanceof HTMLInputElement)) return;
  search = event.target.value;
  draw();
});
element('#theme-toggle').addEventListener('click', () => {
  changePreferences({ ...preferences, theme: nextTheme(preferences.theme) });
});
element('#task-view').addEventListener('click', event => {
  if (!(event.target instanceof HTMLButtonElement) || !isTaskView(event.target.dataset.view)) return;
  changePreferences({ ...preferences, taskView: event.target.dataset.view });
});
element('#open-settings').addEventListener('click', () => {
  /** @type {HTMLInputElement} */ (element(`#settings-form input[value="${preferences.theme}"]`)).checked = true;
  /** @type {HTMLSelectElement} */ (element('#settings-view')).value = preferences.taskView;
  /** @type {HTMLSelectElement} */ (element('#settings-density')).value = preferences.density;
  element('#settings-form-error').hidden = true;
  /** @type {HTMLDialogElement} */ (element('#settings-dialog')).showModal();
});
element('#settings-form').addEventListener('submit', event => {
  event.preventDefault();
  const data = new FormData(/** @type {HTMLFormElement} */ (event.currentTarget));
  const theme = data.get('theme'), taskView = data.get('taskView'), density = data.get('density');
  if (!isTheme(theme) || !isTaskView(taskView) || !isDensity(density)) {
    formError('#settings-form-error', new Error('Выберите тему, представление и плотность интерфейса.')); return;
  }
  if (changePreferences({ ...preferences, theme, taskView, density })) {
    /** @type {HTMLDialogElement} */ (element('#settings-dialog')).close();
  } else formError('#settings-form-error', new Error('Не удалось сохранить настройки. Проверьте хранилище браузера.'));
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
  try {
    const result = await calculatePlan(selectedProject(workspace));
    if (!externalChange) { plan = result; notify(storageWarning); }
  }
  catch (error) { notify(message(error)); }
  finally { setBusy(false); }
}

/** @param {Workspace} candidate */
async function commit(candidate) {
  if (busy || storageWarning) throw new Error('Изменения пока недоступны.');
  if (externalChange) throw new Error('Данные изменены в другой вкладке. Изменения не сохранены. Перезагрузите страницу.');
  setBusy(true);
  try {
    const result = await calculatePlan(selectedProject(candidate));
    workspace = await saveWorkspace(localStorage, candidate);
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
element('#load-demo').addEventListener('click', async () => {
  if (busy || storageWarning || externalChange) return;
  try { await commit(addDemoProjects(workspace)); }
  catch (error) { notify(message(error)); }
});

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
  if (Number(button.dataset.project) === workspace.selectedProjectId) { void refresh(); return; }
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
  let id;
  try { id = nextId(candidate.projects); }
  catch (error) { formError('#project-form-error', error); return; }
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
  let id;
  try { id = existing?.id ?? nextId(project.tasks); }
  catch (error) { formError('#task-form-error', error); return; }
  /** @type {Task} */
  const task = { id, name,
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
