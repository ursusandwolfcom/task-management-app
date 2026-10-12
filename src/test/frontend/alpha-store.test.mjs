import assert from 'node:assert/strict';
import test from 'node:test';
import { exampleWorkspace, loadWorkspace, saveWorkspace, selectedProject, nextId } from '../../main/resources/alpha/store.js';

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
