// @ts-check
/** @typedef {import('./types').Workspace} Workspace */
/** @typedef {import('./types').Project} Project */
/** @typedef {import('./types').Task} Task */
/** @typedef {import('./types').Plan} Plan */
/** @typedef {import('./types').Preferences} Preferences */
/** @typedef {'all' | 'ready' | 'active' | 'completed'} TaskFilter */
import { selectedProject, sortedTasks } from './store.js';

/** @template {HTMLElement} T @param {string} selector @returns {T} */
export function element(selector) {
  const node = document.querySelector(selector);
  if (!(node instanceof HTMLElement)) throw new Error(`Элемент интерфейса отсутствует: ${selector}`);
  return /** @type {T} */ (node);
}

/** @param {string} tag @param {string} [text] @param {string} [className] */
function make(tag, text = '', className = '') {
  const node = document.createElement(tag);
  node.textContent = text;
  node.className = className;
  return node;
}

const priorities = { LOW: 'Низкий', MEDIUM: 'Средний', HIGH: 'Высокий', CRITICAL: 'Критический' };
const states = { NOT_STARTED: 'Не начата', IN_PROGRESS: 'В работе', COMPLETED: 'Завершена', FAILED: 'Не выполнена' };

/** @param {number | null} minutes */
function effort(minutes) {
  if (minutes === null) return 'Без оценки';
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return [hours ? `${hours} ч` : '', rest ? `${rest} мин` : ''].filter(Boolean).join(' ');
}

/** @param {string} message */
export function notify(message) {
  const notice = element('#notice');
  notice.textContent = message;
  notice.hidden = !message;
}

/** @param {Workspace} workspace @param {Plan | null} plan @param {TaskFilter} filter
 * @param {boolean} busy @param {Preferences} preferences @param {boolean} [readOnly] */
export function render(workspace, plan, filter, busy, preferences, readOnly = false) {
  const blocked = busy || readOnly;
  document.documentElement.dataset.theme = preferences.theme;
  element('#theme-toggle').textContent = preferences.theme === 'dark' ? 'Светлая тема' : 'Тёмная тема';
  const sortControl = /** @type {HTMLSelectElement} */ (element('#task-sort'));
  sortControl.value = preferences.taskSort;
  element('#task-sort-note').hidden = preferences.taskSort !== 'execution' || plan !== null;
  const project = selectedProject(workspace);
  const nav = element('#project-list');
  nav.replaceChildren();
  for (const item of workspace.projects) {
    const button = /** @type {HTMLButtonElement} */ (make('button', item.name, 'project-link'));
    button.type = 'button';
    button.dataset.project = String(item.id);
    button.disabled = blocked;
    if (item.id === project.id) button.setAttribute('aria-current', 'page');
    nav.append(button);
  }
  element('#project-name').textContent = project.name;
  element('#project-description').textContent = project.description;
  element('#storage-note').textContent = 'Альфа-версия. Проекты и задачи сохраняются только в этом браузере.';
  for (const selector of ['#new-project', '#new-task', '#refresh-plan']) {
    /** @type {HTMLButtonElement} */ (element(selector)).disabled = blocked;
  }
  const list = element('#task-list');
  list.setAttribute('aria-busy', String(busy));
  list.replaceChildren();
  const available = new Set(plan?.availableIds ?? []);
  const visible = sortedTasks(project.tasks, preferences.taskSort, plan).filter(task => filter === 'all'
    || (filter === 'ready' && available.has(task.id)) || (filter === 'active' && task.status === 'IN_PROGRESS')
    || (filter === 'completed' && task.status === 'COMPLETED'));
  element('#task-count').textContent = `${visible.length} из ${project.tasks.length}`;
  for (const task of visible) list.append(taskCard(task, project, available.has(task.id), blocked, plan !== null));
  if (!visible.length) list.append(make('p', project.tasks.length ? 'Задач с этим статусом пока нет.'
    : 'Здесь появятся задачи проекта. Добавьте первую задачу.', 'empty-state'));
  const order = element('#execution-order');
  order.replaceChildren();
  if (plan) for (const id of plan.executionOrder) {
    const task = project.tasks.find(item => item.id === id);
    if (task) order.append(make('li', task.name));
  }
  const next = project.tasks.find(task => task.id === plan?.nextId);
  element('#next-task').textContent = next?.name ?? (plan ? 'Сейчас нет доступных задач' : 'Порядок недоступен');
}

/** @param {Task} task @param {Project} project @param {boolean} ready @param {boolean} busy @param {boolean} hasPlan */
function taskCard(task, project, ready, busy, hasPlan) {
  const card = make('article', '', 'task-card');
  card.dataset.taskId = String(task.id);
  const title = make('h2', task.name, 'task-title');
  card.append(title);
  const state = task.status === 'NOT_STARTED' ? (hasPlan ? (ready ? 'Готова к запуску' : 'Ожидает зависимости')
    : 'Готовность не проверена') : states[task.status];
  const stateClass = task.status === 'NOT_STARTED' ? (ready ? 'ready' : 'blocked') : task.status;
  const meta = make('div', '', 'task-meta');
  meta.append(make('span', priorities[task.priority], `badge priority-${task.priority}`),
    make('span', state, `badge state-${stateClass}`), make('span', effort(task.estimatedMinutes)));
  if (task.dueDate) meta.append(make('span', `До ${task.dueDate.split('-').reverse().join('.')}`));
  card.append(meta);
  if (task.dependencies.length) {
    const names = task.dependencies.map(id => project.tasks.find(item => item.id === id)?.name ?? `#${id}`);
    card.append(make('div', `После: ${names.join(', ')}`, 'dependencies'));
  }
  const actions = make('div', '', 'task-actions');
  if (task.status === 'NOT_STARTED') {
    actions.append(action('Начать', 'start', task.id, busy || !ready), action('Изменить', 'edit', task.id, busy),
      action('Удалить', 'delete', task.id, busy));
  } else if (task.status === 'IN_PROGRESS') {
    actions.append(action('Завершить', 'complete', task.id, busy), action('Не выполнена', 'fail', task.id, busy));
  } else if (task.status === 'FAILED') actions.append(action('Повторить', 'retry', task.id, busy));
  card.append(actions);
  return card;
}

/** @param {string} label @param {string} kind @param {number} id @param {boolean} disabled */
function action(label, kind, id, disabled) {
  const button = /** @type {HTMLButtonElement} */ (make('button', label, 'button button-outline'));
  button.type = 'button';
  button.dataset.action = kind;
  button.dataset.id = String(id);
  button.disabled = disabled;
  return button;
}

/** @param {Project} project @param {Task | undefined} task */
export function openTaskDialog(project, task) {
  const form = /** @type {HTMLFormElement} */ (element('#task-form'));
  form.reset();
  /** @type {HTMLInputElement} */ (element('#task-form input[name="id"]')).value = task ? String(task.id) : '';
  /** @type {HTMLInputElement} */ (element('#task-name')).value = task?.name ?? '';
  /** @type {HTMLSelectElement} */ (element('#task-priority')).value = task?.priority ?? 'MEDIUM';
  /** @type {HTMLInputElement} */ (element('#task-estimate')).value = task?.estimatedMinutes ? String(task.estimatedMinutes) : '';
  /** @type {HTMLInputElement} */ (element('#task-deadline')).value = task?.dueDate ?? '';
  element('#task-dialog-title').textContent = task ? 'Изменить задачу' : 'Новая задача';
  element('#task-form-error').hidden = true;
  const options = element('#dependency-options');
  options.replaceChildren();
  for (const candidate of project.tasks.filter(item => item.id !== task?.id)) {
    const label = make('label', '', 'dependency-option');
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox'; checkbox.name = 'dependency'; checkbox.value = String(candidate.id);
    checkbox.checked = task?.dependencies.includes(candidate.id) ?? false;
    label.append(checkbox, make('span', candidate.name));
    options.append(label);
  }
  if (!options.children.length) options.append(make('p', 'В проекте пока нет других задач.', 'small muted'));
  /** @type {HTMLDialogElement} */ (element('#task-dialog')).showModal();
}
