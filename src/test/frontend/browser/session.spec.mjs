import { test, expect } from '@playwright/test';
import { randomBytes } from 'node:crypto';

async function request(page, path, method = 'GET', body, csrf) {
  return page.evaluate(async ({ path, method, body, csrf }) => {
    const response = await fetch(path, {
      method, credentials: 'same-origin',
      headers: { ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(csrf ? { [csrf.headerName]: csrf.token } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {})
    });
    return { status: response.status, body: response.status === 204 ? null : await response.json() };
  }, { path, method, body, csrf });
}

test('secure HttpOnly session survives reload and browser restoration, protects writes and revokes on logout',
  async ({ page, context, browser, baseURL }) => {
    const username = `cookie_${randomBytes(8).toString('hex')}`;
    const password = 'browser-cookie-passphrase-2026';
    await page.goto('/alpha/');
    const registered = await request(page, '/api/auth/register', 'POST', {
      username, email: `${username}@example.com`, password, firstName: 'Cookie', lastName: 'Test'
    });
    expect(registered.status).toBe(201);
    const unprotected = await request(page, '/api/session/login', 'POST', { identifier: username, password });
    expect(unprotected.status).toBe(403);
    const csrf = (await request(page, '/api/session/csrf')).body;
    const login = await request(page, '/api/session/login', 'POST', { identifier: username, password }, csrf);
    expect(login.status).toBe(200);
    expect(login.body.user.username).toBe(username);
    expect(Object.keys(login.body).sort()).toEqual(['expiresAt', 'user']);
    const authCookies = (await context.cookies()).filter(cookie => cookie.name !== '__Host-pf-csrf');
    expect(authCookies.map(cookie => cookie.name).sort()).toEqual(['__Host-pf-access', '__Host-pf-refresh']);
    for (const cookie of authCookies) {
      expect(cookie.httpOnly).toBe(true);
      expect(cookie.secure).toBe(true);
      expect(cookie.sameSite).toBe('Strict');
      expect(cookie.path).toBe('/');
      expect(cookie.expires).toBeGreaterThan(Date.now() / 1000);
    }
    expect(await page.evaluate(() => document.cookie)).not.toContain('__Host-pf-');
    const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }));
    for (const cookie of authCookies) expect(storage).not.toContain(cookie.value);
    await page.reload();
    expect((await request(page, '/api/users/me')).body.username).toBe(username);

    const savedState = await context.storageState();
    const restored = await browser.newContext({ baseURL, storageState: savedState });
    try {
      const restoredPage = await restored.newPage();
      await restoredPage.goto('/alpha/');
      expect((await request(restoredPage, '/api/users/me')).body.username).toBe(username);
      const renewedCsrf = (await request(restoredPage, '/api/session/csrf')).body;
      const project = { name: 'Cookie transport project', description: '', startDate: '2026-10-13',
        endDate: '2026-10-14', timezone: 'UTC' };
      expect((await request(restoredPage, '/api/projects', 'POST', project)).status).toBe(403);
      const created = await request(restoredPage, '/api/projects', 'POST', project, renewedCsrf);
      expect(created.status).toBe(201);
      const refresh = await request(restoredPage, '/api/session/refresh', 'POST', undefined, renewedCsrf);
      expect(refresh.status).toBe(200);
      expect(refresh.body.expiresAt).toBe(login.body.expiresAt);
      expect((await request(restoredPage, '/api/session/logout', 'POST', undefined, renewedCsrf)).status).toBe(204);
      expect((await restored.cookies()).filter(cookie => cookie.name.startsWith('__Host-pf-'))).toEqual([]);
      expect((await request(restoredPage, '/api/users/me')).status).toBe(401);
      // The original browser still holds the previous JWT: database revocation must reject it too.
      expect((await request(page, '/api/users/me')).status).toBe(401);
      const newCsrf = (await request(restoredPage, '/api/session/csrf')).body;
      expect((await request(restoredPage, '/api/session/logout', 'POST', undefined, newCsrf)).status).toBe(204);
    } finally {
      await restored.close();
    }
  });
