// @ts-check
/** @typedef {import('./types').Workspace} Workspace */
/** @typedef {import('./types').Project} Project */
/** @typedef {import('./types').Task} Task */
/** @typedef {import('./types').Plan} Plan */

const STORAGE_KEY = 'projectflow.alpha.workspace.v1';
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED'];

/** @returns {Workspace} */
export function exampleWorkspace() {
  return { version: 1, selectedProjectId: 1, projects: [{
    id: 1, name: 'Запуск сайта', description: 'Первый проект: от идеи до готовой страницы.',
    tasks: [
      { id: 1, name: 'Подготовить содержание', priority: 'HIGH', estimatedMinutes: 60,
        dueDate: null, status: 'NOT_STARTED', dependencies: [] },
      { id: 2, name: 'Собрать главную страницу', priority: 'CRITICAL', estimatedMinutes: 90,
        dueDate: null, status: 'NOT_STARTED', dependencies: [1] },
      { id: 3, name: 'Подобрать иллюстрации', priority: 'MEDIUM', estimatedMinutes: 30,
        dueDate: null, status: 'NOT_STARTED', dependencies: [] },
      { id: 4, name: 'Проверить и опубликовать', priority: 'HIGH', estimatedMinutes: 45,
        dueDate: null, status: 'NOT_STARTED', dependencies: [2, 3] }
    ]
  }] };
}

/** @param {unknown} value @returns {value is Record<string, unknown>} */
function isObject(value) { return typeof value === 'object' && value !== null; }
/** @param {unknown} value @returns {value is number} */
function isId(value) { return typeof value === 'number' && Number.isSafeInteger(value) && value > 0; }
/** @param {unknown} value @returns {value is Task} */
function isTask(value) {
  return isObject(value) && isId(value.id) && typeof value.name === 'string' && value.name.trim().length > 0
    && value.name.length <= 160 && typeof value.priority === 'string' && PRIORITIES.includes(value.priority)
    && typeof value.status === 'string' && STATUSES.includes(value.status)
    && (value.estimatedMinutes === null || isId(value.estimatedMinutes))
    && (value.dueDate === null || (typeof value.dueDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value.dueDate)))
    && Array.isArray(value.dependencies) && value.dependencies.every(isId)
    && new Set(value.dependencies).size === value.dependencies.length;
}
/** @param {unknown} value @returns {value is Project} */
function isProject(value) {
  if (!isObject(value) || !isId(value.id) || typeof value.name !== 'string' || !value.name.trim()
      || value.name.length > 120 || typeof value.description !== 'string' || value.description.length > 1000
      || !Array.isArray(value.tasks) || value.tasks.length > 1000 || !value.tasks.every(isTask)) return false;
  const ids = new Set(value.tasks.map(task => task.id));
  return ids.size === value.tasks.length && value.tasks.every(task =>
    task.dependencies.every(id => ids.has(id) && id !== task.id));
}

/** @param {Storage} storage @returns {Workspace} */
export function loadWorkspace(storage) {
  const saved = storage.getItem(STORAGE_KEY);
  if (saved === null) return exampleWorkspace();
  /** @type {unknown} */
  let value;
  try { value = JSON.parse(saved); }
  catch { throw new Error('Сохранённые данные не удалось прочитать. Исходная запись сохранена без изменений.'); }
  if (!isObject(value) || value.version !== 1 || !isId(value.selectedProjectId)
      || !Array.isArray(value.projects) || !value.projects.length || !value.projects.every(isProject)
      || new Set(value.projects.map(project => project.id)).size !== value.projects.length
      || !value.projects.some(project => project.id === value.selectedProjectId)) {
    throw new Error('Сохранённые данные не удалось прочитать. Исходная запись сохранена без изменений.');
  }
  return { version: 1, selectedProjectId: value.selectedProjectId, projects: value.projects };
}

/** @param {Storage} storage @param {Workspace} workspace */
export function saveWorkspace(storage, workspace) {
  try { storage.setItem(STORAGE_KEY, JSON.stringify(workspace)); }
  catch { throw new Error('Не удалось сохранить изменения. Разрешите хранение данных для этого сайта.'); }
}

/** @param {Workspace} workspace */
export function selectedProject(workspace) {
  const project = workspace.projects.find(item => item.id === workspace.selectedProjectId);
  if (!project) throw new Error('Проект не найден.');
  return project;
}

/** @param {{id: number}[]} items */
export function nextId(items) { return Math.max(0, ...items.map(item => item.id)) + 1; }

/** @param {Project} project @param {AbortSignal} [signal] @returns {Promise<Plan>} */
export async function calculatePlan(project, signal) {
  const response = await fetch('/alpha/plan', {
    method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      tasks: project.tasks.map(task => ({ id: task.id, priority: task.priority,
        dueDate: task.dueDate, estimatedMinutes: task.estimatedMinutes })),
      dependencies: project.tasks.flatMap(task => task.dependencies.map(predecessorId =>
        ({ predecessorId, successorId: task.id }))),
      completedIds: project.tasks.filter(task => task.status === 'COMPLETED').map(task => task.id),
      candidateIds: project.tasks.filter(task => task.status === 'NOT_STARTED').map(task => task.id)
    })
  }).catch(() => { throw new Error('Приложение недоступно. Изменения не сохранены.'); });
  if (!response.ok) {
    /** @type {{ code?: string }} */
    const error = await response.json().catch(() => ({}));
    if (error.code === 'CYCLIC_DEPENDENCY') throw new Error('Эта зависимость создаёт цикл. Измените выбранные задачи.');
    throw new Error('Не удалось рассчитать порядок. Проверьте задачи, зависимости и доступность приложения.');
  }
  return response.json();
}
