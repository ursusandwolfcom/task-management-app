// @ts-check
import { demoProjects, demoGuides } from './demo.js';
/** @typedef {import('./types').Workspace} Workspace */
/** @typedef {import('./types').Project} Project */
/** @typedef {import('./types').Task} Task */
/** @typedef {import('./types').Plan} Plan */
/** @typedef {import('./types').Preferences} Preferences */
/** @typedef {import('./types').TaskSort} TaskSort */
/** @typedef {import('./types').Theme} Theme */
/** @typedef {import('./types').TaskView} TaskView */
/** @typedef {import('./types').Density} Density */

export const STORAGE_KEY = 'projectflow.alpha.workspace.v1';
const PREFERENCES_KEY = 'projectflow.alpha.preferences.v1';
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED'];
export const THEMES = Object.freeze(/** @type {Theme[]} */ (
  ['dark', 'light', 'gray', 'ivory', 'pearl', 'graphite', 'taupe', 'forest']));

/** @returns {Preferences} */
export function defaultPreferences() {
  return { taskSort: 'newest', theme: 'dark', taskView: 'list', density: 'comfortable' };
}
/** @param {unknown} value @returns {value is TaskSort} */
export function isTaskSort(value) { return value === 'newest' || value === 'oldest' || value === 'execution'; }
/** @param {unknown} value @returns {value is Theme} */
export function isTheme(value) { return THEMES.some(theme => theme === value); }
/** @param {Theme} theme @returns {Theme} */
export function nextTheme(theme) { return THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length]; }
/** @param {unknown} value @returns {value is TaskView} */
export function isTaskView(value) { return value === 'list' || value === 'kanban'; }
/** @param {unknown} value @returns {value is Density} */
export function isDensity(value) { return value === 'comfortable' || value === 'compact'; }
/** @param {Storage} storage @returns {Preferences} */
export function loadPreferences(storage) {
  const defaults = defaultPreferences();
  try {
    /** @type {unknown} */
    const saved = JSON.parse(storage.getItem(PREFERENCES_KEY) ?? 'null');
    if (!isObject(saved) || saved.version !== 1) return defaults;
    return { taskSort: isTaskSort(saved.taskSort) ? saved.taskSort : defaults.taskSort,
      theme: isTheme(saved.theme) ? saved.theme : defaults.theme,
      taskView: isTaskView(saved.taskView) ? saved.taskView : defaults.taskView,
      density: isDensity(saved.density) ? saved.density : defaults.density };
  } catch { return defaults; }
}
/** @param {Storage} storage @param {Preferences} preferences */
export function savePreferences(storage, preferences) {
  try { storage.setItem(PREFERENCES_KEY, JSON.stringify({ version: 1, ...preferences })); }
  catch { throw new Error('Не удалось сохранить настройку. Проверьте доступ к хранилищу браузера.'); }
}
/** Display order only; insertion order represents creation in the browser prototype.
 * @param {Task[]} tasks @param {TaskSort} sort @param {Plan | null} plan @returns {Task[]} */
export function sortedTasks(tasks, sort, plan) {
  const copy = [...tasks];
  if (sort === 'oldest') return copy;
  if (sort === 'newest' || plan === null) return copy.reverse();
  const ranks = new Map(plan.executionOrder.map((id, index) => [id, index]));
  return copy.sort((left, right) => (ranks.get(left.id) ?? Infinity) - (ranks.get(right.id) ?? Infinity));
}

/** @returns {Workspace} */
export function exampleWorkspace() {
  return { version: 1, revision: 0, selectedProjectId: 1, projects: [{
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
/** @param {unknown} value */
function isDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1];
}
/** @param {unknown} value @returns {value is Task} */
function isTask(value) {
  return isObject(value) && isId(value.id) && typeof value.name === 'string' && value.name.trim().length > 0
    && value.name.length <= 160 && typeof value.priority === 'string' && PRIORITIES.includes(value.priority)
    && typeof value.status === 'string' && STATUSES.includes(value.status)
    && (value.estimatedMinutes === null || isId(value.estimatedMinutes))
    && (value.dueDate === null || isDate(value.dueDate))
    && Array.isArray(value.dependencies) && value.dependencies.every(isId)
    && new Set(value.dependencies).size === value.dependencies.length;
}
/** @param {unknown} value @returns {value is Project} */
function isProject(value) {
  if (!isObject(value) || !isId(value.id) || typeof value.name !== 'string' || !value.name.trim()
      || value.name.length > 120 || typeof value.description !== 'string' || value.description.length > 1000
      || (value.demoKey !== undefined && (typeof value.demoKey !== 'string' || !Object.hasOwn(demoGuides, value.demoKey)))
      || !Array.isArray(value.tasks) || value.tasks.length > 1000 || !value.tasks.every(isTask)) return false;
  const ids = new Set(value.tasks.map(task => task.id));
  return ids.size === value.tasks.length && value.tasks.every(task =>
    task.dependencies.every(id => ids.has(id) && id !== task.id));
}

/** @param {Storage} storage @returns {Workspace} */
export function loadWorkspace(storage) {
  const saved = storage.getItem(STORAGE_KEY);
  if (saved === null) return addDemoProjects(exampleWorkspace(), false);
  /** @type {unknown} */
  let value;
  try { value = JSON.parse(saved); }
  catch { throw new Error('Сохранённые данные не удалось прочитать. Исходная запись сохранена без изменений.'); }
  return validatedWorkspace(value);
}

/** @param {unknown} value @returns {Workspace} */
function validatedWorkspace(value) {
  if (!isObject(value) || value.version !== 1 || !isId(value.selectedProjectId)
      || (value.revision !== undefined && (!Number.isSafeInteger(value.revision)
        || typeof value.revision !== 'number' || value.revision < 0))
      || !Array.isArray(value.projects) || !value.projects.length || !value.projects.every(isProject)
      || new Set(value.projects.map(project => project.id)).size !== value.projects.length
      || !value.projects.some(project => project.id === value.selectedProjectId)) {
    throw new Error('Сохранённые данные не удалось прочитать. Исходная запись сохранена без изменений.');
  }
  return { version: 1, revision: typeof value.revision === 'number' ? value.revision : 0,
    selectedProjectId: value.selectedProjectId, projects: value.projects };
}

/** IndexedDB is shared across renderers; localStorage caches can lag even under a Web Lock.
 * Existing local records are imported on first access. The mirror remains for legacy readers.
 * @param {Storage} storage @param {LockManager | undefined} [locks]
 * @param {IDBFactory | undefined} [database] @returns {Promise<Workspace>} */
export async function loadCoordinatedWorkspace(storage, locks = globalThis.navigator?.locks, database = globalThis.indexedDB) {
  if (!locks) return loadWorkspace(storage);
  if (!database) throw new Error('Общее хранилище браузера недоступно. Сохранение отключено; существующие данные сохранены.');
  return locks.request(STORAGE_KEY, () => workspaceTransaction(database, (store, record) => {
    const legacy = loadWorkspace(storage);
    if (record && legacy.revision > record.revision) {
      throw new Error('Локальная запись новее общего хранилища. Исходная запись сохранена; сохранение отключено. Перезагрузите страницу.');
    }
    const current = record ?? legacy;
    if (current !== record) store.put(current, STORAGE_KEY);
    return current;
  }));
}

/** @param {IDBFactory} database @returns {Promise<IDBDatabase>} */
function openWorkspaceDatabase(database) {
  return new Promise((resolve, reject) => {
    const request = database.open('projectflow.alpha.workspace.v1', 1);
    let blocked = false;
    request.onupgradeneeded = () => request.result.createObjectStore('workspace');
    request.onblocked = () => {
      blocked = true;
      reject(new Error('Общее хранилище занято. Закройте другие вкладки и перезагрузите страницу.'));
    };
    request.onerror = () => reject(new Error('Не удалось открыть общее хранилище браузера. Данные сохранены без изменений.'));
    request.onsuccess = () => {
      if (blocked) { request.result.close(); return; }
      request.result.onversionchange = () => request.result.close();
      resolve(request.result);
    };
  });
}

/** Keep the revision check and canonical write in one cross-renderer transaction.
 * @param {IDBFactory} database
 * @param {(store: IDBObjectStore, current: Workspace | undefined) => Workspace} action
 * @param {() => void} [rollbackMirror] @returns {Promise<Workspace>} */
async function workspaceTransaction(database, action, rollbackMirror = () => {}) {
  const connection = await openWorkspaceDatabase(database);
  try {
    return await new Promise((resolve, reject) => {
      const transaction = connection.transaction('workspace', 'readwrite');
      const store = transaction.objectStore('workspace');
      /** @type {Workspace | undefined} */
      let result;
      /** @type {unknown} */
      let failure;
      const request = store.get(STORAGE_KEY);
      request.onsuccess = () => {
        try { result = action(store, request.result === undefined ? undefined : validatedWorkspace(request.result)); }
        catch (error) { failure = error; transaction.abort(); }
      };
      transaction.oncomplete = () => {
        if (result) resolve(result);
        else reject(new Error('Не удалось прочитать общее хранилище браузера.'));
      };
      transaction.onabort = () => {
        try { rollbackMirror(); } catch { /* Canonical data remains intact if the mirror becomes unavailable. */ }
        reject(failure ?? new Error('Не удалось сохранить изменения в общем хранилище браузера.'));
      };
    });
  } finally { connection.close(); }
}

/** The read/check/write sequence runs under an exclusive lock and an IndexedDB transaction.
 * Legacy records have revision zero and are upgraded only on a successful save.
 * @param {Storage} storage @param {Workspace} workspace
 * @param {LockManager | undefined} [locks] @param {IDBFactory | undefined} [database]
 * @returns {Promise<Workspace>} */
export async function saveWorkspace(storage, workspace, locks = globalThis.navigator?.locks, database = globalThis.indexedDB) {
  if (!locks) throw new Error('Для сохранения нужен браузер с поддержкой Web Locks. Обновите браузер.');
  if (!database && typeof window !== 'undefined') {
    throw new Error('Общее хранилище браузера недоступно. Изменения не сохранены.');
  }
  return locks.request(STORAGE_KEY, () => {
    let mirrorWritten = false;
    /** @type {Workspace | undefined} */
    let previous;
    const previousMirror = storage.getItem(STORAGE_KEY);
    /** @param {IDBObjectStore | undefined} store @param {Workspace | undefined} record */
    const write = (store, record) => {
      const legacy = loadWorkspace(storage);
      const current = record ?? legacy;
      if (record && legacy.revision > record.revision) {
        throw new Error('Данные изменены в другой вкладке. Изменения не сохранены. Перезагрузите страницу.');
      }
      if (current.revision !== workspace.revision) {
        throw new Error('Данные изменены в другой вкладке. Изменения не сохранены. Перезагрузите страницу.');
      }
      if (!Number.isSafeInteger(workspace.revision) || workspace.revision >= Number.MAX_SAFE_INTEGER) {
        throw new Error('Не удалось сохранить изменения: диапазон ревизий исчерпан.');
      }
      previous = current;
      const saved = { ...workspace, revision: workspace.revision + 1 };
      try { storage.setItem(STORAGE_KEY, JSON.stringify(saved)); }
      catch { throw new Error('Не удалось сохранить изменения. Разрешите хранение данных для этого сайта.'); }
      mirrorWritten = true;
      store?.put(saved, STORAGE_KEY);
      return saved;
    };
    if (!database) return write(undefined, undefined); // Non-browser storage unit checks.
    return workspaceTransaction(database, write, () => {
      if (!mirrorWritten) return;
      if (previousMirror === null && previous?.revision === 0) storage.removeItem(STORAGE_KEY);
      else if (previous) storage.setItem(STORAGE_KEY, JSON.stringify(previous));
    });
  });
}

/** @param {Workspace} workspace */
export function selectedProject(workspace) {
  const project = workspace.projects.find(item => item.id === workspace.selectedProjectId);
  if (!project) throw new Error('Проект не найден.');
  return project;
}

/** @param {{id: number}[]} items */
export function nextId(items) {
  const id = Math.max(0, ...items.map(item => item.id)) + 1;
  if (!Number.isSafeInteger(id)) throw new Error('Не удалось создать идентификатор: диапазон значений исчерпан.');
  return id;
}

/** Add missing scenarios without replacing projects or resetting edited demo tasks.
 * @param {Workspace} workspace @param {boolean} [selectDemo] @returns {Workspace} */
export function addDemoProjects(workspace, selectDemo = true) {
  const candidate = structuredClone(workspace);
  for (const project of demoProjects(1)) {
    if (!candidate.projects.some(existing => existing.demoKey === project.demoKey)) {
      project.id = nextId(candidate.projects);
      candidate.projects.push(project);
    }
  }
  if (selectDemo) candidate.selectedProjectId = candidate.projects.find(project => project.demoKey === 'portal')?.id ?? candidate.selectedProjectId;
  return candidate;
}

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
