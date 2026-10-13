import { test as base, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

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
  const labels = { dark: 'Тёмная', light: 'Светлая', gray: 'Серая' };
  await dialog.getByRole('radio', { name: labels[theme], exact: true }).check();
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

test('three themes, both views and dialogs meet WCAG checks on desktop and mobile', async ({ page }) => {
  test.setTimeout(90000);
  await page.goto('/alpha/');
  await settled(page);
  for (const theme of ['dark', 'light', 'gray']) for (const taskView of ['list', 'kanban']) {
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
  await stale.locator('#project-list button[data-project]').click();
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

test('simultaneous tab submissions retain one write and explicitly reject the other', async ({ page, context }) => {
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
  await Promise.all(tabs.map(tab => tab.getByRole('dialog')
    .getByRole('button', { name: 'Сохранить задачу', exact: true }).click()));
  await Promise.all(tabs.map(settled));
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('projectflow.alpha.workspace.v1')).revision)).toBe(1);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('projectflow.alpha.workspace.v1'))
    .projects[0].tasks.filter(task => task.name.startsWith('Concurrent ')).length)).toBe(1);
  const losers = [];
  for (const tab of tabs) if (await tab.getByRole('dialog').isVisible()) losers.push(tab);
  expect(losers).toHaveLength(1);
  await expect(losers[0].getByRole('dialog').getByRole('alert')).toContainText('другой вкладке');
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
