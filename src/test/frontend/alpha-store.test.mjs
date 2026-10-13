import assert from 'node:assert/strict';
import test from 'node:test';
import { exampleWorkspace, loadWorkspace, saveWorkspace, selectedProject, nextId } from '../../main/resources/alpha/store.js';
import { defaultPreferences, loadPreferences, savePreferences, sortedTasks } from '../../main/resources/alpha/store.js';
import { addDemoProjects } from '../../main/resources/alpha/store.js';

function serialLocks() {
  let tail = Promise.resolve();
  return { request: (_name, callback) => {
    const result = tail.then(callback);
    tail = result.catch(() => {});
    return result;
  } };
}
const locks = serialLocks();
async function persist(storage, workspace) {
  const saved = await saveWorkspace(storage, workspace, locks);
  Object.assign(workspace, saved);
}
function writeFixture(storage, workspace) {
  storage.setItem('projectflow.alpha.workspace.v1', JSON.stringify(workspace));
}

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
    snapshot: () => [...values.entries()]
  };
}

test('demo scenarios populate fresh storage and are added once without replacing user projects or edits', async () => {
  const storage = memoryStorage();
  const fresh = loadWorkspace(storage);
  assert.equal(fresh.projects.filter(project => project.demoKey).length, 3);
  assert.equal(storage.getItem('projectflow.alpha.workspace.v1'), null);
  const user = exampleWorkspace();
  user.projects[0].id = user.selectedProjectId = 20;
  const original = structuredClone(user);
  const candidate = addDemoProjects(user);
  assert.deepEqual(user, original);
  assert.deepEqual(candidate.projects[0], user.projects[0]);
  assert.equal(new Set(candidate.projects.map(project => project.id)).size, 4);
  const portal = selectedProject(candidate);
  portal.tasks[2].name = 'Customer edit';
  await persist(storage, candidate);
  const repeated = addDemoProjects(loadWorkspace(storage));
  assert.deepEqual(repeated, candidate);
  assert.equal(selectedProject(repeated).tasks[2].name, 'Customer edit');
});

test('persists projects, minute estimates, execution state and dependency links across reload', async () => {
  const storage = memoryStorage();
  const workspace = exampleWorkspace();
  workspace.projects[0].tasks[0].status = 'COMPLETED';
  workspace.projects.push({ id: 2, name: 'Другой проект', description: '', tasks: [] });
  workspace.selectedProjectId = 2;
  await persist(storage, workspace);
  assert.deepEqual(loadWorkspace(storage), workspace);
  assert.equal(selectedProject(loadWorkspace(storage)).id, 2);
});

test('does not silently replace corrupted, unknown-version or inconsistent stored data', async () => {
  for (const mutate of [
    value => { value.version = 2; },
    value => { value.selectedProjectId = 100; },
    value => { value.projects[0].tasks[0].estimatedMinutes = 0.5; },
    value => { value.projects[0].tasks[0].dependencies = [999]; },
    value => { value.projects[0].tasks[0].dependencies = [1]; },
    value => { value.projects[0].tasks[0].status = 'UNKNOWN'; },
    value => { value.projects[0].tasks.push(value.projects[0].tasks[0]); }
  ]) {
    const storage = memoryStorage();
    const value = exampleWorkspace();
    mutate(value);
    writeFixture(storage, value);
    const previous = storage.snapshot();
    assert.throws(() => loadWorkspace(storage));
    assert.deepEqual(storage.snapshot(), previous);
  }
});

test('storage quota failure leaves the previous workspace intact', async () => {
  const storage = memoryStorage();
  const previous = exampleWorkspace();
  await persist(storage, previous);
  const failing = { getItem: storage.getItem, setItem: () => { throw new Error('quota'); } };
  const next = structuredClone(previous);
  next.projects[0].name = 'Unsaved';
  await assert.rejects(saveWorkspace(failing, next, locks), /сохранить/);
  assert.deepEqual(loadWorkspace(storage), previous);
});

test('default workspace copies and new IDs are independent', async () => {
  const first = exampleWorkspace();
  first.projects[0].name = 'Changed';
  assert.equal(exampleWorkspace().projects[0].name, 'Запуск сайта');
  assert.equal(nextId([{ id: 4 }, { id: 1 }, { id: 2 }]), 5);
  assert.equal(nextId([]), 1);
});

test('newest first follows creation order and editing does not move a task', async () => {
  const tasks = exampleWorkspace().projects[0].tasks;
  const original = structuredClone(tasks);
  assert.deepEqual(sortedTasks(tasks, 'newest', null).map(task => task.id), [4, 3, 2, 1]);
  assert.deepEqual(tasks, original);
  tasks.push({ ...tasks[0], id: 5, name: 'New task' });
  tasks[1] = { ...tasks[1], name: 'Edited old task' };
  assert.deepEqual(sortedTasks(tasks, 'newest', null).map(task => task.id), [5, 4, 3, 2, 1]);
  assert.deepEqual(sortedTasks(tasks, 'oldest', null).map(task => task.id), [1, 2, 3, 4, 5]);
});

test('execution display follows supplied server order without changing tasks or plan', async () => {
  const tasks = exampleWorkspace().projects[0].tasks;
  const plan = { executionOrder: [1, 3, 2, 4], availableIds: [1, 3], nextId: 1 };
  const previous = structuredClone({ tasks, plan });
  assert.deepEqual(sortedTasks(tasks, 'execution', plan).map(task => task.id), plan.executionOrder);
  assert.deepEqual(sortedTasks(tasks, 'execution', null).map(task => task.id), [4, 3, 2, 1]);
  assert.deepEqual({ tasks, plan }, previous);
});

test('theme and task order default to dark/newest and persist independently of workspace', async () => {
  const storage = memoryStorage();
  const workspace = exampleWorkspace();
  await persist(storage, workspace);
  assert.deepEqual(loadPreferences(storage), defaultPreferences());
  for (const taskSort of ['newest', 'oldest', 'execution']) for (const theme of ['dark', 'light', 'gray'])
    for (const taskView of ['list', 'kanban']) for (const density of ['comfortable', 'compact']) {
    const preferences = { taskSort, theme, taskView, density };
    savePreferences(storage, preferences);
    assert.deepEqual(loadPreferences(storage), preferences);
    assert.deepEqual(loadWorkspace(storage), workspace);
  }
  const defaults = defaultPreferences();
  defaults.theme = 'light';
  assert.equal(defaultPreferences().theme, 'dark');
});

test('legacy preferences retain theme/order and new fields use defaults without rewriting storage', () => {
  const storage = memoryStorage();
  for (const fields of [{}, { taskView: 'unknown', density: null }]) {
    storage.setItem('projectflow.alpha.preferences.v1', JSON.stringify({ version: 1,
      taskSort: 'oldest', theme: 'light', ...fields }));
    const original = storage.snapshot();
    assert.deepEqual(loadPreferences(storage), { taskSort: 'oldest', theme: 'light',
      taskView: 'list', density: 'comfortable' });
    assert.deepEqual(storage.snapshot(), original);
  }
});

test('invalid or unavailable preferences use safe defaults without overwriting stored data', async () => {
  for (const value of ['{', 'null', '{"version":2,"theme":"light"}',
    '{"version":1,"taskSort":"unknown","theme":"unknown"}']) {
    const storage = memoryStorage();
    storage.setItem('projectflow.alpha.preferences.v1', value);
    const previous = storage.snapshot();
    assert.deepEqual(loadPreferences(storage), defaultPreferences());
    assert.deepEqual(storage.snapshot(), previous);
  }
  assert.deepEqual(loadPreferences({ getItem: () => { throw new Error('blocked'); } }), defaultPreferences());
});

test('preferences quota failure preserves existing preference and workspace records', async () => {
  const storage = memoryStorage();
  await persist(storage, exampleWorkspace());
  savePreferences(storage, { taskSort: 'oldest', theme: 'light' });
  const previous = storage.snapshot();
  const failing = { getItem: storage.getItem, setItem: () => { throw new Error('quota'); } };
  assert.throws(() => savePreferences(failing, defaultPreferences()), /сохранить настройку/);
  assert.deepEqual(storage.snapshot(), previous);
});

test('stored due dates must be real Gregorian dates and invalid records remain untouched', async () => {
  for (const dueDate of ['2026-02-29', '1900-02-29', '2026-04-31', '2026-00-12', '2026-13-01', '2026-01-00']) {
    const storage = memoryStorage();
    const workspace = exampleWorkspace();
    workspace.projects[0].tasks[0].dueDate = dueDate;
    writeFixture(storage, workspace);
    const previous = storage.snapshot();
    assert.throws(() => loadWorkspace(storage), dueDate);
    assert.deepEqual(storage.snapshot(), previous);
  }
  for (const dueDate of ['2024-02-29', '2000-02-29', '2026-12-31']) {
    const storage = memoryStorage();
    const workspace = exampleWorkspace();
    workspace.projects[0].tasks[0].dueDate = dueDate;
    await persist(storage, workspace);
    assert.equal(loadWorkspace(storage).projects[0].tasks[0].dueDate, dueDate);
  }
});

test('ID allocation rejects exhausted safe integer space instead of producing rounded identifiers', async () => {
  assert.equal(nextId([{ id: Number.MAX_SAFE_INTEGER - 1 }]), Number.MAX_SAFE_INTEGER);
  assert.throws(() => nextId([{ id: Number.MAX_SAFE_INTEGER }]), /идентификатор/);
});

test('stale tab selection cannot erase another tab task', async () => {
  const storage = memoryStorage();
  const first = loadWorkspace(storage), stale = loadWorkspace(storage);
  first.projects[0].tasks.push({ ...first.projects[0].tasks[0], id: 5, name: 'Z' });
  const saved = await saveWorkspace(storage, first, locks);
  await assert.rejects(saveWorkspace(storage, stale, locks), /другой вкладке/);
  assert.deepEqual(loadWorkspace(storage), saved);
  assert.equal(saved.projects[0].tasks.at(-1).name, 'Z');
});

test('simultaneous writes from the same revision have one winner under the shared lock', async () => {
  const storage = memoryStorage();
  const clients = [loadWorkspace(storage), loadWorkspace(storage)];
  clients.forEach((value, index) => { value.projects[0].name = `Client ${index}`; });
  const results = await Promise.allSettled(clients.map(value => saveWorkspace(storage, value, locks)));
  assert.equal(results.filter(value => value.status === 'fulfilled').length, 1);
  assert.equal(results.filter(value => value.status === 'rejected').length, 1);
  assert.equal(loadWorkspace(storage).revision, 1);
});

test('legacy workspace upgrades without rewriting on read and invalid revisions stay intact', async () => {
  const storage = memoryStorage();
  const legacy = exampleWorkspace();
  delete legacy.revision;
  writeFixture(storage, legacy);
  const original = storage.snapshot();
  const loaded = loadWorkspace(storage);
  assert.equal(loaded.revision, 0);
  assert.deepEqual(storage.snapshot(), original);
  const saved = await saveWorkspace(storage, loaded, locks);
  assert.equal(saved.revision, 1);
  assert.deepEqual(saved.projects, legacy.projects);
  for (const revision of [-1, 0.5, '1', null, Number.MAX_SAFE_INTEGER + 1]) {
    writeFixture(storage, { ...saved, revision });
    const previous = storage.snapshot();
    assert.throws(() => loadWorkspace(storage));
    assert.deepEqual(storage.snapshot(), previous);
  }
});

test('missing locks, corrupt current storage and exhausted revisions reject without writing', async () => {
  const storage = memoryStorage();
  await assert.rejects(saveWorkspace(storage, exampleWorkspace(), null), /Web Locks/);
  assert.deepEqual(storage.snapshot(), []);
  storage.setItem('projectflow.alpha.workspace.v1', '{');
  await assert.rejects(saveWorkspace(storage, exampleWorkspace(), locks), /Исходная запись/);
  assert.equal(storage.getItem('projectflow.alpha.workspace.v1'), '{');
  const exhausted = { ...exampleWorkspace(), revision: Number.MAX_SAFE_INTEGER };
  writeFixture(storage, exhausted);
  await assert.rejects(saveWorkspace(storage, exhausted, locks), /ревизий/);
  assert.deepEqual(loadWorkspace(storage), exhausted);
});
