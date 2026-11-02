import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { randomBytes } from 'node:crypto';

const password = 'workspace-passphrase-2026';
function identity() { const username = `m1_${randomBytes(6).toString('hex')}`;
  return { username, email: `${username}@example.com`, firstName: 'Анна', lastName: 'Команда', password }; }
async function registerApi(request, user) {
  const response = await request.post('/api/auth/register', { data: user });
  expect(response.status()).toBe(201); return response.json();
}
async function login(page, user) {
  await page.goto('/app/');
  await page.getByLabel('Email или логин', { exact: true }).fill(user.username);
  await page.getByLabel('Пароль', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Войти', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Проекты', exact: true })).toBeVisible();
}
async function createProject(page, name) {
  await page.getByRole('link', { name: 'Новый проект' }).click();
  await page.getByLabel('Название проекта', { exact: true }).fill(name);
  await page.getByLabel('Описание', { exact: true }).fill('Контекст команды и ожидаемый результат');
  await page.getByRole('button', { name: 'Создать проект', exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  return /\/projects\/(\d+)/.exec(page.url())[1];
}
test('registration, reload, profile, shared projects, conflicts, membership and ownership use actual API', async ({ page, context, browser, baseURL }) => {
  const user = identity();
  await page.goto('/app/'); await page.getByRole('button', { name: 'Зарегистрироваться', exact: true }).click();
  await page.getByLabel('Логин', { exact: true }).fill(user.username);
  await page.getByLabel('Email', { exact: true }).fill(user.email);
  await page.getByLabel('Имя', { exact: true }).fill(user.firstName);
  await page.getByLabel('Фамилия', { exact: true }).fill(user.lastName);
  await page.getByLabel('Пароль', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Создать аккаунт', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Аккаунт создан');
  await page.getByLabel('Пароль', { exact: true }).fill('wrong-passphrase');
  await page.getByRole('button', { name: 'Войти', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Неверный логин');
  await page.getByLabel('Пароль', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Войти', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Проекты', exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByRole('heading', { name: 'Проекты', exact: true })).toBeVisible();
  const projectName = `Общий проект ${user.username}`;
  const id = await createProject(page, projectName);
  const restored = await browser.newContext({ baseURL, storageState: await context.storageState() });
  const outsider = await browser.newContext({ baseURL });
  try {
    const second = await restored.newPage(); await second.goto('/app/');
    await expect(second.getByRole('link', { name: new RegExp(projectName) })).toBeVisible();
    await second.getByRole('link', { name: new RegExp(projectName) }).click();
    await page.getByLabel('Название проекта', { exact: true }).fill('Мой сохранённый черновик');
    await second.getByLabel('Название проекта', { exact: true }).fill('Изменение другой вкладки');
    await second.getByRole('button', { name: 'Сохранить проект', exact: true }).click();
    await expect(second.getByRole('status').filter({ hasText: 'Проект сохранён' })).toBeVisible();
    await page.getByRole('button', { name: 'Сохранить проект', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Данные уже изменены');
    await expect(page.getByLabel('Название проекта', { exact: true })).toHaveValue('Мой сохранённый черновик');
    await page.getByRole('button', { name: 'Показать актуальную версию', exact: true }).click();
    await expect(page.getByLabel('Название проекта', { exact: true })).toHaveValue('Мой сохранённый черновик');
    await page.getByRole('button', { name: 'Редактировать актуальную версию', exact: true }).click();
    await expect(page.getByLabel('Название проекта', { exact: true })).toHaveValue('Изменение другой вкладки');
    await page.getByRole('button', { name: 'Обновить проект', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Изменение другой вкладки', exact: true })).toBeVisible();

    const other = identity(); const member = await registerApi(outsider.request, other);
    const otherPage = await outsider.newPage(); await login(otherPage, other);
    await expect(otherPage.getByRole('heading', { name: 'Пока нет проектов', exact: true })).toBeVisible();
    await otherPage.goto(`/app/#/projects/${id}`); await expect(otherPage.getByRole('alert')).toContainText('Проект не найден');
    await expect(otherPage.getByRole('button', { name: 'Сохранить проект', exact: true })).toHaveCount(0);
    await page.getByLabel('ID пользователя', { exact: true }).fill(String(member.id));
    await page.getByRole('button', { name: 'Добавить участника', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Участник добавлен' })).toBeVisible();
    await otherPage.reload(); await expect(otherPage.getByRole('heading', { name: 'Изменение другой вкладки', exact: true })).toBeVisible();
    await expect(otherPage.getByRole('button', { name: 'Сохранить проект', exact: true })).toHaveCount(0);
    await page.getByLabel('ID нового владельца', { exact: true }).fill(String(member.id));
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Передать владение', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Сохранить проект', exact: true })).toHaveCount(0);
    await otherPage.reload(); await expect(otherPage.getByRole('button', { name: 'Сохранить проект', exact: true })).toBeVisible();

    await page.getByRole('link', { name: 'Профиль', exact: true }).click();
    await page.getByLabel('Имя', { exact: true }).fill('Черновик моего профиля');
    await second.goto('/app/#/profile');
    await second.getByLabel('Имя', { exact: true }).fill('Профиль другой вкладки');
    await second.getByRole('button', { name: 'Сохранить профиль', exact: true }).click();
    await expect(second.getByRole('status').filter({ hasText: 'Профиль сохранён' })).toBeVisible();
    await page.getByRole('button', { name: 'Сохранить профиль', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Данные уже изменены');
    await expect(page.getByLabel('Имя', { exact: true })).toHaveValue('Черновик моего профиля');
    await page.getByRole('button', { name: 'Показать актуальную версию', exact: true }).click();
    await expect(page.getByLabel('Имя', { exact: true })).toHaveValue('Черновик моего профиля');
    await page.getByRole('button', { name: 'Редактировать актуальную версию', exact: true }).click();
    await page.getByLabel('Имя', { exact: true }).fill('Анна Обновлённая');
    await page.getByRole('button', { name: 'Сохранить профиль', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Профиль сохранён' })).toBeVisible();
    await page.reload(); await expect(page.getByLabel('Имя', { exact: true })).toHaveValue('Анна Обновлённая');
    expect(await page.evaluate(() => document.cookie)).not.toContain('__Host-pf-');
    expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain(password);
  } finally { await restored.close(); await outsider.close(); }
});

test('two tabs restore expired access once, preserve drafts and respond to logout', async ({ page, context }) => {
  const user = identity(); await registerApi(context.request, user); await login(page, user);
  const id = await createProject(page, `Сессия ${user.username}`);
  const second = await context.newPage(); await second.goto(`/app/#/projects/${id}`);
  const projectName = page.getByLabel('Название проекта', { exact: true });
  const secondName = second.getByLabel('Название проекта', { exact: true });
  // Project detail also loads task/queue APIs under the shared session lock across tabs.
  const ready = { timeout: 45_000 };
  await expect(secondName).toBeVisible(ready);
  let refreshes = 0;
  context.on('request', request => { if (request.url().endsWith('/api/session/refresh')) refreshes++; });
  await context.clearCookies({ name: '__Host-pf-access' });
  await Promise.all([page.reload(), second.reload()]);
  await expect(projectName).toBeVisible(ready);
  await expect(secondName).toBeVisible(ready);
  expect(refreshes).toBe(1);
  await projectName.fill('Сохранить после повторного входа');
  await context.clearCookies({ name: '__Host-pf-access' }); await context.clearCookies({ name: '__Host-pf-refresh' });
  await page.getByRole('button', { name: 'Сохранить проект', exact: true }).click();
  await expect(page.getByLabel('Email или логин', { exact: true })).toBeVisible(ready);
  await page.getByLabel('Пароль', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Войти', exact: true }).click();
  await expect(projectName).toHaveValue('Сохранить после повторного входа', ready);
  await second.getByRole('button', { name: 'Выйти', exact: true }).click();
  await expect(page.getByLabel('Email или логин', { exact: true })).toBeVisible(ready);
  await expect(second.getByLabel('Email или логин', { exact: true })).toBeVisible(ready);
});

test('uncertain writes never retry, password revokes sessions and project removal is explicit', async ({ page, context }) => {
  const user = identity(); await registerApi(context.request, user); await login(page, user);
  await page.getByRole('link', { name: 'Новый проект' }).click();
  await page.getByLabel('Название проекта', { exact: true }).fill('Черновик при сбое');
  let writes = 0;
  await page.route('**/api/projects', async route => {
    if (route.request().method() === 'POST') { writes++; await route.abort('failed'); }
    else await route.continue();
  });
  await page.getByRole('button', { name: 'Создать проект', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Результат записи неизвестен');
  await expect(page.getByLabel('Название проекта', { exact: true })).toHaveValue('Черновик при сбое');
  expect(writes).toBe(1); await page.unroute('**/api/projects');
  await page.getByRole('button', { name: 'Создать проект', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Черновик при сбое', exact: true })).toBeVisible();
  page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: 'Удалить проект', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Пока нет проектов', exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Профиль', exact: true }).click();
  await page.getByLabel('Текущий пароль', { exact: true }).fill(password);
  await page.getByLabel('Новый пароль', { exact: true }).fill('changed-workspace-password-2026');
  await page.getByRole('button', { name: 'Сменить пароль', exact: true }).click();
  await expect(page.getByLabel('Email или логин', { exact: true })).toBeVisible();
});

test('workspace is responsive, keyboard accessible and every theme passes WCAG', async ({ page, context }, testInfo) => {
  test.setTimeout(90000); // Eight complete WCAG audits plus desktop/mobile and keyboard acceptance.
  await page.goto('/app/');
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  const user = identity(); await registerApi(context.request, user); await login(page, user);
  await createProject(page, `Доступность ${user.username}`);
  for (const theme of ['gray', 'light', 'dark', 'graphite', 'taupe', 'forest', 'ivory', 'pearl']) {
    await page.getByRole('combobox', { name: 'Тема интерфейса', exact: true }).selectOption(theme);
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  }
  await page.getByRole('combobox', { name: 'Тема интерфейса', exact: true }).selectOption('gray');
  await page.screenshot({ path: testInfo.outputPath('workspace-desktop.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath('workspace-mobile.png'), fullPage: true });
  await page.getByRole('link', { name: 'Профиль', exact: true }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Профиль', exact: true })).toBeVisible();
});

test('revoked project access removes the cached card after a denied read', async ({ page, context, browser, baseURL }) => {
  const owner = identity(); await registerApi(context.request, owner); await login(page, owner);
  const name = `Отзыв доступа ${owner.username}`; const id = await createProject(page, name);
  const memberContext = await browser.newContext({ baseURL });
  try {
    const member = identity(); const registered = await registerApi(memberContext.request, member);
    const memberPage = await memberContext.newPage(); await login(memberPage, member);
    await page.getByLabel('ID пользователя', { exact: true }).fill(String(registered.id));
    await page.getByRole('button', { name: 'Добавить участника', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Участник добавлен' })).toBeVisible();
    await memberPage.goto(`/app/#/projects/${id}`);
    await expect(memberPage.getByRole('heading', { name, exact: true })).toBeVisible();
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: `Удалить участника ${registered.id}`, exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Участник удалён' })).toBeVisible();
    await memberPage.getByRole('button', { name: 'Обновить проект', exact: true }).click();
    await expect(memberPage.getByRole('alert')).toContainText('Проект не найден');
    await expect(memberPage.getByRole('heading', { name, exact: true })).toHaveCount(0);
    await expect(memberPage.getByText('Контекст команды и ожидаемый результат', { exact: true })).toHaveCount(0);
  } finally { await memberContext.close(); }
});
