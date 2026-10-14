import { defineConfig } from 'vite';

export default defineConfig({
  root: 'frontend', base: '/app/',
  build: { outDir: 'dist', emptyOutDir: true, sourcemap: false, target: 'es2023' }
});
