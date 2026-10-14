import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
for (const args of [
  ['node_modules/typescript/bin/tsc', '-p', 'frontend/tsconfig.json'],
  ['node_modules/vite/bin/vite.js', 'build', '--config', 'workspace-vite.config.mjs']
]) {
  const result = spawnSync(process.execPath, args, { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
