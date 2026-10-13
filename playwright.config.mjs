import { defineConfig } from '@playwright/test';
import { randomBytes } from 'node:crypto';

const port = Number(process.env.ALPHA_TEST_PORT ?? 18082);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid ALPHA_TEST_PORT');
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './src/test/frontend/browser',
  fullyParallel: true,
  workers: 2,
  retries: 0,
  timeout: 30000,
  reporter: 'list',
  outputDir: './target/playwright-results',
  use: {
    baseURL,
    browserName: 'chromium',
    channel: process.env.PLAYWRIGHT_CHANNEL || undefined,
    trace: 'retain-on-failure',
    viewport: { width: 1440, height: 1000 }
  },
  webServer: {
    env: { JWT_SIGNING_KEY_BASE64: randomBytes(32).toString('base64') },
    command: 'java -jar target/task-management-app-0.0.1-SNAPSHOT.jar '
      + `--spring.profiles.active=alpha --server.port=${port}`,
    url: `${baseURL}/actuator/health`,
    reuseExistingServer: false,
    timeout: 90000,
    stdout: 'ignore',
    stderr: 'pipe'
  }
});
