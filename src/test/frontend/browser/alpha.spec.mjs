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

test('both themes and dialogs meet selected WCAG checks on desktop and mobile', async ({ page }) => {
  await page.goto('/alpha/');
  await settled(page);
  for (const theme of ['dark', 'light']) {
    if (theme === 'light') await page.getByRole('button', { name: 'Светлая тема', exact: true }).click();
    await page.setViewportSize({ width: 1440, height: 1000 });
    for (const dialog of [false, true]) {
      if (dialog) await page.getByRole('button', { name: '+ Добавить задачу', exact: true }).click();
      const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      expect(audit.violations).toEqual([]);
      if (dialog) await page.getByRole('dialog').getByRole('button', { name: 'Отмена', exact: true }).click();
    }
    await page.setViewportSize({ width: 390, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
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
