import { test, expect } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import AxeBuilder from '@axe-core/playwright';

test('personal MEGA form clears secrets and requires acknowledgement before disconnect', async ({ page, request }) => {
  const username = `mega_${randomBytes(5).toString('hex')}`;
  const password = 'workspace-mega-test-passphrase';
  const registered = await request.post('/api/auth/register', { data: {
    username, email: `${username}@example.com`, firstName: 'Анна', lastName: 'Команда', password
  } });
  expect(registered.status()).toBe(201);
  let linked = false;
  let linkCalls = 0;
  let deleteCalls = 0;
  await page.route('**/api/mega**', async route => {
    const method = route.request().method();
    if (method === 'POST') {
      linkCalls++;
      if (linkCalls === 1) {
        await route.fulfill({ status: 400, json: { code: 'MEGA_MFA_REQUIRED' } });
        return;
      }
      expect(route.request().postDataJSON().mfaCode).toBe('123456');
      linked = true;
    }
    if (method === 'DELETE') { linked = false; deleteCalls++; }
    await route.fulfill({ json: { configured: true, linked,
      accountId: linked ? 'accountA001' : null, linkedAt: linked ? '2026-11-04T00:00:00Z' : null } });
  });
  await page.goto('/app/');
  await page.getByLabel('Email или логин', { exact: true }).fill(username);
  await page.getByLabel('Пароль', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Войти', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Проекты', exact: true })).toBeVisible();
  await page.goto('/app/#/profile');
  const card = page.getByRole('region', { name: 'Личный аккаунт MEGA' });
  await card.getByLabel('Email MEGA', { exact: true }).fill('owner@mega.test');
  await card.getByLabel('Пароль MEGA', { exact: true }).fill('secret-mega-password');
  await card.getByRole('button', { name: 'Подключить личную MEGA', exact: true }).click();
  await expect(card.getByRole('alert')).toContainText('код двухфакторной');
  await expect(card.getByLabel('Пароль MEGA', { exact: true })).toHaveValue('');
  await card.getByLabel('Пароль MEGA', { exact: true }).fill('secret-mega-password');
  await card.getByLabel('Код двухфакторной аутентификации MEGA', { exact: true }).fill('123456');
  await card.getByRole('button', { name: 'Подключить личную MEGA', exact: true }).click();
  await expect(card).toContainText('accountA001');
  await expect(card.getByLabel('Пароль MEGA', { exact: true })).toHaveValue('');
  await expect(card.getByLabel('Код двухфакторной аутентификации MEGA', { exact: true })).toHaveValue('');
  await expect(card.getByRole('button', { name: 'Отключить MEGA', exact: true })).toBeDisabled();
  await card.getByRole('checkbox', { name: 'Я понимаю, что прежние файлы могут стать недоступны' }).check();
  await card.getByRole('button', { name: 'Отключить MEGA', exact: true }).click();
  await expect(card).not.toContainText('Подключён аккаунт:');
  expect(deleteCalls).toBe(1);
  const storage = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }));
  expect(storage).not.toContain('secret-mega-password');
  expect(storage).not.toContain('123456');
  expect((await new AxeBuilder({ page }).include('main').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
});
