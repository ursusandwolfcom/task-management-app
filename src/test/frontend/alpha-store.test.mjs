import assert from 'node:assert/strict';
import test from 'node:test';
import { exampleWorkspace, loadWorkspace, saveWorkspace, selectedProject, nextId } from '../../main/resources/alpha/store.js';
import { defaultPreferences, loadPreferences, savePreferences, sortedTasks } from '../../main/resources/alpha/store.js';

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
    snapshot: () => [...values.entries()]
  };
}

test('persists projects, minute estimates, execution state and dependency links across reload', () => {
  const storage = memoryStorage();
  const workspace = exampleWorkspace();
  workspace.projects[0].tasks[0].status = 'COMPLETED';
  workspace.projects.push({ id: 2, name: 'Другой проект', description: '', tasks: [] });
  workspace.selectedProjectId = 2;
  saveWorkspace(storage, workspace);
  assert.deepEqual(loadWorkspace(storage), workspace);
  assert.equal(selectedProject(loadWorkspace(storage)).id, 2);
});

test('does not silently replace corrupted, unknown-version or inconsistent stored data', () => {
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
    saveWorkspace(storage, value);
    const previous = storage.snapshot();
    assert.throws(() => loadWorkspace(storage));
    assert.deepEqual(storage.snapshot(), previous);
  }
});

test('storage quota failure leaves the previous workspace intact', () => {
  const storage = memoryStorage();
  const previous = exampleWorkspace();
  saveWorkspace(storage, previous);
  const failing = { getItem: storage.getItem, setItem: () => { throw new Error('quota'); } };
  const next = structuredClone(previous);
  next.projects[0].name = 'Unsaved';
  assert.throws(() => saveWorkspace(failing, next), /сохранить/);
  assert.deepEqual(loadWorkspace(storage), previous);
});

test('default workspace copies and new IDs are independent', () => {
  const first = exampleWorkspace();
  first.projects[0].name = 'Changed';
  assert.equal(exampleWorkspace().projects[0].name, 'Запуск сайта');
  assert.equal(nextId([{ id: 4 }, { id: 1 }, { id: 2 }]), 5);
  assert.equal(nextId([]), 1);
});

test('newest first follows creation order and editing does not move a task', () => {
  const tasks = exampleWorkspace().projects[0].tasks;
  const original = structuredClone(tasks);
  assert.deepEqual(sortedTasks(tasks, 'newest', null).map(task => task.id), [4, 3, 2, 1]);
  assert.deepEqual(tasks, original);
  tasks.push({ ...tasks[0], id: 5, name: 'New task' });
  tasks[1] = { ...tasks[1], name: 'Edited old task' };
  assert.deepEqual(sortedTasks(tasks, 'newest', null).map(task => task.id), [5, 4, 3, 2, 1]);
  assert.deepEqual(sortedTasks(tasks, 'oldest', null).map(task => task.id), [1, 2, 3, 4, 5]);
});

test('execution display follows supplied server order without changing tasks or plan', () => {
  const tasks = exampleWorkspace().projects[0].tasks;
  const plan = { executionOrder: [1, 3, 2, 4], availableIds: [1, 3], nextId: 1 };
  const previous = structuredClone({ tasks, plan });
  assert.deepEqual(sortedTasks(tasks, 'execution', plan).map(task => task.id), plan.executionOrder);
  assert.deepEqual(sortedTasks(tasks, 'execution', null).map(task => task.id), [4, 3, 2, 1]);
  assert.deepEqual({ tasks, plan }, previous);
});

test('theme and task order default to dark/newest and persist independently of workspace', () => {
  const storage = memoryStorage();
  const workspace = exampleWorkspace();
  saveWorkspace(storage, workspace);
  assert.deepEqual(loadPreferences(storage), { taskSort: 'newest', theme: 'dark' });
  for (const taskSort of ['newest', 'oldest', 'execution']) for (const theme of ['dark', 'light']) {
    savePreferences(storage, { taskSort, theme });
    assert.deepEqual(loadPreferences(storage), { taskSort, theme });
    assert.deepEqual(loadWorkspace(storage), workspace);
  }
  const defaults = defaultPreferences();
  defaults.theme = 'light';
  assert.equal(defaultPreferences().theme, 'dark');
});

test('invalid or unavailable preferences use safe defaults without overwriting stored data', () => {
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

test('preferences quota failure preserves existing preference and workspace records', () => {
  const storage = memoryStorage();
  saveWorkspace(storage, exampleWorkspace());
  savePreferences(storage, { taskSort: 'oldest', theme: 'light' });
  const previous = storage.snapshot();
  const failing = { getItem: storage.getItem, setItem: () => { throw new Error('quota'); } };
  assert.throws(() => savePreferences(failing, defaultPreferences()), /сохранить настройку/);
  assert.deepEqual(storage.snapshot(), previous);
});

test('stored due dates must be real Gregorian dates and invalid records remain untouched', () => {
  for (const dueDate of ['2026-02-29', '1900-02-29', '2026-04-31', '2026-00-12', '2026-13-01', '2026-01-00']) {
    const storage = memoryStorage();
    const workspace = exampleWorkspace();
    workspace.projects[0].tasks[0].dueDate = dueDate;
    saveWorkspace(storage, workspace);
    const previous = storage.snapshot();
    assert.throws(() => loadWorkspace(storage), dueDate);
    assert.deepEqual(storage.snapshot(), previous);
  }
  for (const dueDate of ['2024-02-29', '2000-02-29', '2026-12-31']) {
    const storage = memoryStorage();
    const workspace = exampleWorkspace();
    workspace.projects[0].tasks[0].dueDate = dueDate;
    saveWorkspace(storage, workspace);
    assert.equal(loadWorkspace(storage).projects[0].tasks[0].dueDate, dueDate);
  }
});

test('ID allocation rejects exhausted safe integer space instead of producing rounded identifiers', () => {
  assert.equal(nextId([{ id: Number.MAX_SAFE_INTEGER - 1 }]), Number.MAX_SAFE_INTEGER);
  assert.throws(() => nextId([{ id: Number.MAX_SAFE_INTEGER }]), /идентификатор/);
});
