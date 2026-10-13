import { test as base, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { THEMES } from '../../../main/resources/alpha/store.js';

const themeLabels = { dark: 'Тёмная', light: 'Светлая', gray: 'Серая', ivory: 'Слоновая кость',
  pearl: 'Жемчужная', graphite: 'Графит', taupe: 'Тауп', forest: 'Тёмный лес' };

const test = base.extend({
  pageErrors: [async ({ page }, use) => {
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await use(errors);
    expect(errors).toEqual([]);
  }, { auto: true }]
});

const settled = page => expect(page.locator('#task-list')).toHaveAttribute('aria-busy', 'false');
const card = (page, name) => page.locator('.task-card').filter({ has: page.getByRole('heading', { name, exact: true }) });
const taskIds = page => page.locator('.task-card').evaluateAll(cards => cards.map(item => Number(item.dataset.taskId)));

async function createTask(page, name, dependency) {
  await page.getByRole('button', { name: '+ Добавить задачу', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Название задачи', { exact: true }).fill(name);
  if (dependency) await dialog.getByLabel(dependency, { exact: true }).check();
  await dialog.getByRole('button', { name: 'Сохранить задачу', exact: true }).click();
  await expect(dialog).toBeHidden();
  await settled(page);
}

async function applySettings(page, theme, taskView = 'list', density = 'comfortable') {
  await page.getByRole('button', { name: 'Настройки интерфейса', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('radio', { name: themeLabels[theme], exact: true }).check();
  await dialog.getByLabel('Представление задач', { exact: true }).selectOption(taskView);
  await dialog.getByLabel('Плотность интерфейса', { exact: true }).selectOption(density);
  await dialog.getByRole('button', { name: 'Применить настройки', exact: true }).click();
  await expect(dialog).toBeHidden();
}

test('new tasks, sort and theme preferences survive reload without graph recalculation', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  expect(await taskIds(page)).toEqual([4, 3, 2, 1]);
  await createTask(page, 'Newest');
  expect(await taskIds(page)).toEqual([5, 4, 3, 2, 1]);
  let requests = 0;
  page.on('request', request => { if (request.url().endsWith('/alpha/plan')) requests++; });
  await page.getByRole('combobox', { name: 'Порядок задач', exact: true }).selectOption('oldest');
  await page.getByRole('button', { name: 'Светлая тема', exact: true }).click();
  expect(requests).toBe(0);
  await page.reload();
  await settled(page);
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  expect(await taskIds(page)).toEqual([1, 2, 3, 4, 5]);
  await page.getByRole('combobox', { name: 'Порядок задач', exact: true }).selectOption('execution');
  expect(await page.locator('.task-title').allTextContents()).toEqual(await page.locator('#execution-order li').allTextContents());
});

test('actual completion unlocks dependencies and cyclic editing preserves the workspace and form', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  const predecessor = card(page, 'Подготовить содержание');
  const successor = card(page, 'Собрать главную страницу');
  await expect(successor.getByRole('button', { name: 'Начать', exact: true })).toBeDisabled();
  await predecessor.getByRole('button', { name: 'Начать', exact: true }).click();
  await predecessor.getByRole('button', { name: 'Завершить', exact: true }).click();
  await settled(page);
  await expect(successor.getByRole('button', { name: 'Начать', exact: true })).toBeEnabled();
  const previous = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
  await successor.getByRole('button', { name: 'Изменить', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Проверить и опубликовать', { exact: true }).check();
  await dialog.getByRole('button', { name: 'Сохранить задачу', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('цикл');
  await expect(dialog.getByLabel('Проверить и опубликовать', { exact: true })).toBeChecked();
  expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(previous);
});

test('quota and network failures preserve stored data and form input', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  const previous = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
  await page.getByRole('button', { name: '+ Добавить задачу', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Название задачи', { exact: true }).fill('Keep this input');
  await page.route('**/alpha/plan', route => route.abort());
  await dialog.getByRole('button', { name: 'Сохранить задачу', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('недоступно');
  await expect(dialog.getByLabel('Название задачи', { exact: true })).toHaveValue('Keep this input');
  await page.unroute('**/alpha/plan');
  await page.evaluate(() => { Storage.prototype.setItem = () => { throw new Error('quota'); }; });
  await dialog.getByRole('button', { name: 'Сохранить задачу', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('сохранить');
  expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(previous);
  await page.reload();
  await settled(page);
  await createTask(page, 'Recovered after quota');
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('projectflow.alpha.workspace.v1')).revision)).toBe(1);
});

for (const kind of ['task', 'project', 'date']) {
  test(`corrupt or exhausted ${kind} state remains intact and does not leave UI loading`, async ({ page, context }) => {
    const workspace = { version: 1, selectedProjectId: 1, projects: [{ id: 1, name: 'Boundary', description: '',
      tasks: [{ id: kind === 'task' ? Number.MAX_SAFE_INTEGER : 1, name: 'Existing', priority: 'LOW',
        estimatedMinutes: 1, dueDate: kind === 'date' ? '2026-02-29' : null, status: 'NOT_STARTED', dependencies: [] }] }] };
    if (kind === 'project') workspace.selectedProjectId = workspace.projects[0].id = Number.MAX_SAFE_INTEGER;
    await context.addInitScript(value => localStorage.setItem('projectflow.alpha.workspace.v1', JSON.stringify(value)), workspace);
    await page.goto('/alpha/');
    await settled(page);
    const previous = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
    if (kind === 'date') {
      await expect(page.getByRole('button', { name: '+ Добавить задачу', exact: true })).toBeDisabled();
      await expect(page.getByRole('status')).toContainText('Исходная запись сохранена');
    } else {
      await page.getByRole('button', { name: kind === 'task' ? '+ Добавить задачу' : '+ Создать проект', exact: true }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByLabel(kind === 'task' ? 'Название задачи' : 'Название проекта', { exact: true }).fill('Unsaved');
      await dialog.getByRole('button', { name: kind === 'task' ? 'Сохранить задачу' : 'Создать проект', exact: true }).click();
      await expect(dialog.getByRole('alert')).toContainText('идентификатор');
    }
    expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(previous);
  });
}

test('eight themes, both views and dialogs meet WCAG checks on populated desktop and mobile', async ({ page }) => {
  test.setTimeout(180000);
  await page.goto('/alpha/');
  await settled(page);
  await page.locator('#load-demo').click();
  await settled(page);
  for (const theme of THEMES) for (const taskView of ['list', 'kanban']) {
    await applySettings(page, theme, taskView);
    await page.setViewportSize({ width: 1440, height: 1000 });
    for (const dialog of [false, true]) {
      if (dialog) await page.getByRole('button', { name: '+ Добавить задачу', exact: true }).click();
      const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      expect(audit.violations).toEqual([]);
      if (dialog) await page.getByRole('dialog').getByRole('button', { name: 'Отмена', exact: true }).click();
    }
    await page.setViewportSize({ width: 390, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const mobileAudit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(mobileAudit.violations).toEqual([]);
  }
});

test('all eight theme choices survive reload without workspace writes or graph calculation', async ({ page }) => {
  test.setTimeout(60000);
  await page.goto('/alpha/');
  await settled(page);
  const workspace = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
  for (const theme of THEMES) {
    let requests = 0;
    const onRequest = request => { if (request.url().endsWith('/alpha/plan')) requests++; };
    page.on('request', onRequest);
    await applySettings(page, theme, 'kanban', 'compact');
    expect(requests).toBe(0);
    page.off('request', onRequest);
    expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(workspace);
    await page.reload();
    await settled(page);
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
    await expect(page.locator('html')).toHaveAttribute('data-density', 'compact');
    await expect(page.locator('.kanban-column')).toHaveCount(4);
    await page.locator('#open-settings').click();
    await expect(page.getByRole('radio', { name: themeLabels[theme], exact: true })).toBeChecked();
    await page.getByRole('dialog').getByRole('button', { name: 'Отмена', exact: true }).click();
  }
  await applySettings(page, 'dark');
  for (const theme of THEMES.slice(1).concat(THEMES[0])) {
    await page.locator('#theme-toggle').click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
  }
});

test('Swagger renders the real local alpha API and XSS-like task names remain plain text', async ({ page, request }) => {
  await page.goto('/swagger-ui/index.html');
  await expect(page.getByText('Calculate dependency order and actual readiness', { exact: true })).toBeVisible();
  const specification = await (await request.get('/v3/api-docs')).json();
  expect(Object.keys(specification.paths)).toEqual(['/alpha/plan']);
  await page.goto('/alpha/');
  await settled(page);
  const name = '<img src=x onerror="window.alphaInjected=true">';
  await createTask(page, name);
  await expect(card(page, name)).toBeVisible();
  expect(await page.locator('#task-list img').count()).toBe(0);
  expect(await page.evaluate(() => window.alphaInjected)).toBeUndefined();
});

test('another tab task survives stale project selection and form submission', async ({ page, context }) => {
  await page.goto('/alpha/');
  await settled(page);
  const stale = await context.newPage();
  await stale.goto('/alpha/');
  await settled(stale);
  await stale.getByRole('button', { name: '+ Добавить задачу', exact: true }).click();
  const dialog = stale.getByRole('dialog');
  await dialog.getByLabel('Название задачи', { exact: true }).fill('Unsaved in B');
  await createTask(page, 'Z saved in A');
  await expect(stale.getByRole('status')).toContainText('другой вкладке');
  await dialog.getByRole('button', { name: 'Сохранить задачу', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('другой вкладке');
  await expect(dialog.getByLabel('Название задачи', { exact: true })).toHaveValue('Unsaved in B');
  await dialog.getByRole('button', { name: 'Отмена', exact: true }).click();
  const saved = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
  await stale.locator('#project-list button[data-project]').first().click();
  await settled(stale);
  expect(await stale.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(saved);
  await page.reload();
  await settled(page);
  await expect(card(page, 'Z saved in A')).toBeVisible();
  await stale.reload();
  await settled(stale);
  await expect(card(stale, 'Z saved in A')).toBeVisible();
  await createTask(stale, 'B after reload');
});

for (const staleCache of [false, true]) test(`simultaneous tab submissions retain one write and reject the other (stale cache: ${staleCache})`, async ({ page, context }) => {
  await page.goto('/alpha/');
  await settled(page);
  const other = await context.newPage();
  await other.goto('/alpha/');
  await settled(other);
  const tabs = [page, other];
  let arrivals = 0;
  let release;
  const bothReady = new Promise(resolve => { release = resolve; });
  await context.route('**/alpha/plan', async route => {
    arrivals++;
    if (arrivals === 2) release();
    await bothReady;
    await route.continue();
  });
  for (const [index, tab] of tabs.entries()) {
    await tab.getByRole('button', { name: '+ Добавить задачу', exact: true }).click();
    await tab.getByRole('dialog').getByLabel('Название задачи', { exact: true }).fill(`Concurrent ${index}`);
  }
  if (staleCache) await Promise.all(tabs.map(tab => tab.evaluate(() => {
    const original = Storage.prototype.getItem;
    const cached = localStorage.getItem('projectflow.alpha.workspace.v1');
    window.alphaOriginalGetItem = original;
    Storage.prototype.getItem = function (key) {
      return key === 'projectflow.alpha.workspace.v1' ? cached : original.call(this, key);
    };
  })));
  await Promise.all(tabs.map(tab => tab.getByRole('dialog')
    .getByRole('button', { name: 'Сохранить задачу', exact: true }).click()));
  await Promise.all(tabs.map(settled));
  if (staleCache) await Promise.all(tabs.map(tab => tab.evaluate(() => {
    Storage.prototype.getItem = window.alphaOriginalGetItem;
    delete window.alphaOriginalGetItem;
  })));
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('projectflow.alpha.workspace.v1')).revision)).toBe(1);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('projectflow.alpha.workspace.v1'))
    .projects[0].tasks.filter(task => task.name.startsWith('Concurrent ')).length)).toBe(1);
  const losers = [];
  for (const tab of tabs) if (await tab.getByRole('dialog').isVisible()) losers.push(tab);
  const dialogStates = await Promise.all(tabs.map(tab => tab.locator('#task-dialog').evaluate(dialog => ({
    open: dialog.open, visible: dialog.checkVisibility(),
    busy: document.querySelector('#task-list').getAttribute('aria-busy'),
    error: document.querySelector('#task-form-error').textContent
  }))));
  expect(losers, JSON.stringify(dialogStates)).toHaveLength(1);
  await expect(losers[0].getByRole('dialog').getByRole('alert')).toContainText('другой вкладке');
});

test('unavailable IndexedDB preserves existing legacy workspace and permits reading only', async ({ page, context }) => {
  const workspace = { version: 1, revision: 3, selectedProjectId: 9,
    projects: [{ id: 9, name: 'Существующий проект', description: '', tasks: [] }] };
  await context.addInitScript(value => {
    localStorage.setItem('projectflow.alpha.workspace.v1', JSON.stringify(value));
    Object.defineProperty(window, 'indexedDB', { value: undefined });
  }, workspace);
  await page.goto('/alpha/');
  await settled(page);
  await expect(page.locator('#project-name')).toHaveText('Существующий проект');
  await expect(page.locator('#new-task')).toBeDisabled();
  await expect(page.getByRole('status')).toContainText('Общее хранилище браузера недоступно');
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('projectflow.alpha.workspace.v1')))).toEqual(workspace);
});

test('aborted canonical transaction rolls back the mirror and retains the form for retry', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  const previous = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
  await page.evaluate(() => {
    const put = IDBObjectStore.prototype.put;
    window.alphaOriginalPut = put;
    IDBObjectStore.prototype.put = function (...args) {
      const result = put.apply(this, args);
      queueMicrotask(() => this.transaction.abort());
      return result;
    };
  });
  await page.locator('#new-task').click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Название задачи', { exact: true }).fill('Retry after aborted transaction');
  await dialog.getByRole('button', { name: 'Сохранить задачу', exact: true }).click();
  await settled(page);
  await expect(dialog.getByRole('alert')).toContainText('сохранить');
  await expect(dialog.getByLabel('Название задачи', { exact: true })).toHaveValue('Retry after aborted transaction');
  expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(previous);
  await page.evaluate(() => {
    IDBObjectStore.prototype.put = window.alphaOriginalPut;
    delete window.alphaOriginalPut;
  });
  await dialog.getByRole('button', { name: 'Сохранить задачу', exact: true }).click();
  await expect(dialog).toBeHidden();
  await settled(page);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('projectflow.alpha.workspace.v1')).revision)).toBe(1);
  await page.reload();
  await settled(page);
  await expect(card(page, 'Retry after aborted transaction')).toBeVisible();
});

test('browser without Web Locks permits reading and prevents unsafe saving', async ({ page, context }) => {
  await context.addInitScript(() => Object.defineProperty(navigator, 'locks', { value: undefined }));
  await page.goto('/alpha/');
  await settled(page);
  await expect(card(page, 'Подготовить содержание')).toBeVisible();
  await expect(page.getByRole('button', { name: '+ Добавить задачу', exact: true })).toBeDisabled();
  await expect(page.getByRole('status')).toContainText('Web Locks');
  expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBeNull();
});

test('gray theme, kanban and compact density persist without workspace writes or plan calls', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  const previous = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
  let requests = 0;
  page.on('request', request => { if (request.url().endsWith('/alpha/plan')) requests++; });
  await applySettings(page, 'gray', 'kanban', 'compact');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'gray');
  await expect(page.locator('html')).toHaveAttribute('data-density', 'compact');
  await expect(page.locator('.kanban-column')).toHaveCount(4);
  expect(requests).toBe(0);
  expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(previous);
  await page.reload();
  await settled(page);
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'gray');
  await expect(page.locator('html')).toHaveAttribute('data-density', 'compact');
  await expect(page.getByRole('button', { name: 'Канбан', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.column-NOT_STARTED .task-card')).toHaveCount(4);
});

test('kanban actions keep dependency guards and update project summary', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  await applySettings(page, 'gray', 'kanban');
  const first = card(page, 'Подготовить содержание');
  const dependent = card(page, 'Собрать главную страницу');
  await expect(dependent.getByRole('button', { name: 'Начать', exact: true })).toBeDisabled();
  await first.getByRole('button', { name: 'Начать', exact: true }).click();
  await settled(page);
  await expect(page.locator('.column-IN_PROGRESS .task-card')).toHaveCount(1);
  await expect(page.locator('#summary-active')).toHaveText('1');
  await first.getByRole('button', { name: 'Завершить', exact: true }).click();
  await settled(page);
  await expect(page.locator('.column-COMPLETED .task-card')).toHaveCount(1);
  await expect(page.locator('#summary-completed')).toHaveText('1');
  await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '25');
  await expect(dependent.getByRole('button', { name: 'Начать', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Список', exact: true }).click();
  await expect(page.locator('.kanban-column')).toHaveCount(0);
  await expect(card(page, 'Подготовить содержание')).toContainText('Завершена');
});

test('task search works in list and kanban without changing workspace or recalculating the plan', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  const previous = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
  let requests = 0;
  page.on('request', request => { if (request.url().endsWith('/alpha/plan')) requests++; });
  await page.getByRole('searchbox', { name: 'Поиск задач', exact: true }).fill(' СОДЕРЖАНИЕ ');
  await expect(page.locator('.task-card')).toHaveCount(1);
  await expect(page.locator('#summary-total')).toHaveText('4');
  await page.getByRole('button', { name: 'Канбан', exact: true }).click();
  await expect(page.locator('.task-card')).toHaveCount(1);
  await page.getByRole('searchbox', { name: 'Поиск задач', exact: true }).fill('Нет такой задачи');
  await expect(page.locator('.task-card')).toHaveCount(0);
  await expect(page.locator('.kanban-column')).toHaveCount(4);
  expect(requests).toBe(0);
  expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(previous);
});

test('settings dialog supports keyboard close and preserves choices on storage failure', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  const trigger = page.getByRole('button', { name: 'Настройки интерфейса', exact: true });
  await trigger.click();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(trigger).toBeFocused();
  await trigger.click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('radio', { name: 'Серая', exact: true }).check();
  const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations).toEqual([]);
  await page.evaluate(() => { Storage.prototype.setItem = () => { throw new Error('quota'); }; });
  await dialog.getByRole('button', { name: 'Применить настройки', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('сохранить настройки');
  await expect(dialog.getByRole('radio', { name: 'Серая', exact: true })).toBeChecked();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

test('view and theme controls share the upper-right toolbar and gray surfaces stay dark', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  await applySettings(page, 'gray', 'kanban');
  for (const id of ['task-view', 'theme-toggle', 'open-settings']) {
    await expect(page.locator(`.app-topbar .workspace-controls #${id}`)).toHaveCount(1);
  }
  await page.locator('#open-settings').click();
  const colors = await page.locator('.app-topbar, .sidebar, .summary-card, .task-card, .kanban-column, dialog, dialog select, .button, .badge')
    .evaluateAll(elements => elements.filter(element => element.checkVisibility()).map(element => {
      const style = getComputedStyle(element);
      return { background: style.backgroundColor, foreground: style.color };
    }));
  for (const color of colors) {
    if (color.background !== 'rgba(0, 0, 0, 0)') {
      expect(color.background.match(/\d+/g).slice(0, 3).map(Number).every(channel => channel <= 102)).toBe(true);
    }
    expect(color.foreground).not.toBe('rgb(255, 255, 255)');
  }
});

test('demo reload preserves user data, edits and all scenarios use the actual Java plan', async ({ page, context, request }) => {
  const workspace = { version: 1, revision: 0, selectedProjectId: 10,
    projects: [{ id: 10, name: 'Проект заказчика', description: 'Сохранить', tasks: [] }] };
  await context.addInitScript(value => {
    if (!localStorage.getItem('projectflow.alpha.workspace.v1')) {
      localStorage.setItem('projectflow.alpha.workspace.v1', JSON.stringify(value));
    }
  }, workspace);
  await page.goto('/alpha/');
  await settled(page);
  const original = await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'));
  await page.route('**/alpha/plan', route => route.abort());
  await page.getByRole('button', { name: 'Открыть демо-сценарии', exact: true }).click();
  await settled(page);
  await expect(page.getByRole('status')).toContainText('недоступно');
  expect(await page.evaluate(() => localStorage.getItem('projectflow.alpha.workspace.v1'))).toBe(original);
  await page.unroute('**/alpha/plan');
  await page.getByRole('button', { name: 'Открыть демо-сценарии', exact: true }).click();
  await settled(page);
  await expect(page.locator('#project-name')).toHaveText('Демо · Клиентский портал');
  await expect(page.locator('#demo-guide')).toBeVisible();
  await applySettings(page, 'gray', 'kanban');
  for (const state of ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED']) {
    expect(await page.locator(`.column-${state} .task-card`).count()).toBeGreaterThan(0);
  }
  await card(page, 'Реализовать личный кабинет').getByRole('button', { name: 'Начать', exact: true }).click();
  await settled(page);
  await card(page, 'Реализовать личный кабинет').getByRole('button', { name: 'Завершить', exact: true }).click();
  await settled(page);
  await expect(card(page, 'Проверить безопасность').getByRole('button', { name: 'Начать', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Открыть демо-сценарии', exact: true }).click();
  await settled(page);
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('projectflow.alpha.workspace.v1')));
  expect(saved.projects).toHaveLength(4);
  expect(saved.projects[0]).toEqual(workspace.projects[0]);
  expect(saved.projects.find(project => project.demoKey === 'portal').tasks[2].status).toBe('COMPLETED');
  for (const project of saved.projects.filter(project => project.demoKey)) {
    const result = await request.post('/alpha/plan', { data: {
      tasks: project.tasks.map(({ id, priority, dueDate, estimatedMinutes }) => ({ id, priority, dueDate, estimatedMinutes })),
      dependencies: project.tasks.flatMap(task => task.dependencies.map(predecessorId => ({ predecessorId, successorId: task.id }))),
      completedIds: project.tasks.filter(task => task.status === 'COMPLETED').map(task => task.id),
      candidateIds: project.tasks.filter(task => task.status === 'NOT_STARTED').map(task => task.id)
    } });
    expect(result.ok()).toBe(true);
    const plan = await result.json();
    expect(plan.executionOrder).toHaveLength(project.tasks.length);
    if (project.demoKey === 'priorities') expect(plan.executionOrder).toEqual([4, 2, 3, 7, 1, 5, 6]);
    if (project.demoKey === 'recovery') expect(plan.availableIds).toEqual([6]);
  }
  await page.getByRole('button', { name: 'Демо · Восстановление сервиса', exact: true }).click();
  await settled(page);
  const recovery = card(page, 'Восстановить платёжный шлюз');
  await expect(card(page, 'Проверить обработку заказов').getByRole('button', { name: 'Начать', exact: true })).toBeDisabled();
  await recovery.getByRole('button', { name: 'Повторить', exact: true }).click();
  await settled(page);
  await recovery.getByRole('button', { name: 'Начать', exact: true }).click();
  await settled(page);
  await recovery.getByRole('button', { name: 'Завершить', exact: true }).click();
  await settled(page);
  await expect(card(page, 'Проверить обработку заказов').getByRole('button', { name: 'Начать', exact: true })).toBeEnabled();
  const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations).toEqual([]);
  await page.reload();
  await settled(page);
  await expect(card(page, 'Восстановить платёжный шлюз')).toContainText('Завершена');
});
