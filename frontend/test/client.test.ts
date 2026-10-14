import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, Client } from '../src/client.ts';
import { decodeJson, encodeJson, userId } from '../src/json.ts';

const user = { id: 1n, username: 'alice', firstName: 'Alice', lastName: 'Doe', email: 'alice@example.com', role: 'USER', version: 0n };
const response = (body: unknown, status = 200) => new Response(encodeJson(body), { status });
const csrf = { headerName: 'X-XSRF-TOKEN', token: 'masked-token' };
function mutex() {
  let pending = Promise.resolve();
  return <T>(action: () => Promise<T>): Promise<T> => {
    const result = pending.then(action); pending = result.then(() => undefined, () => undefined); return result;
  };
}

test('preserves the full server long range in bodies, routes and versions', () => {
  const text = '{"id":9223372036854775807,"ownerId":9007199254740993,"version":9007199254740995,"name":"literal 9007199254740993"}';
  const decoded = decodeJson(text) as { id: bigint; ownerId: bigint; version: bigint; name: string };
  assert.equal(decoded.id, 9223372036854775807n); assert.equal(decoded.ownerId, 9007199254740993n);
  assert.equal(encodeJson(decoded), text); assert.equal(userId('9223372036854775807'), decoded.id);
  assert.throws(() => userId('9223372036854775808')); assert.throws(() => userId('1e6'));
  assert.throws(() => decodeJson('{"version":1.5}'));
});

test('two tabs recheck me inside their shared lock and consume refresh once', async () => {
  let authenticated = false; let refreshes = 0;
  const lock = mutex();
  const fetcher: typeof fetch = async input => {
    const path = String(input);
    if (path === '/api/users/me') return authenticated ? response(user) : response({ code: 'UNAUTHORIZED' }, 401);
    if (path === '/api/session/csrf') return response(csrf);
    if (path === '/api/session/refresh') {
      assert.equal(refreshes++, 0); authenticated = true;
      return response({ user, expiresAt: '2026-10-21T00:00:00Z' });
    }
    throw new Error(`Unexpected ${path}`);
  };
  const make = () => new Client({ fetch: fetcher, lock, session: () => {}, announce: () => {} });
  const [a, b] = await Promise.all([make().restore(), make().restore()]);
  assert.equal(a?.id, 1n); assert.equal(b?.id, 1n); assert.equal(refreshes, 1);
});

test('an expired mutation is sent once and is never replayed or silently refreshed', async () => {
  const paths: string[] = []; const sessions: unknown[] = [];
  const client = new Client({ lock: mutex(), session: value => sessions.push(value), announce: () => {},
    fetch: async (input, init) => {
      const path = String(input); paths.push(path);
      assert.equal(init?.credentials, 'same-origin'); assert.equal(init?.cache, 'no-store');
      assert.equal(new Headers(init?.headers).has('Authorization'), false);
      if (path === '/api/users/me') return response(user);
      if (path === '/api/session/csrf') return response(csrf);
      assert.equal(new Headers(init?.headers).get(csrf.headerName), csrf.token);
      return response({ code: 'UNAUTHORIZED' }, 401);
    }
  });
  await assert.rejects(client.request(1n, '/api/projects', 'POST', { name: 'draft' }), ApiError);
  assert.deepEqual(paths, ['/api/users/me', '/api/session/csrf', '/api/projects']);
  assert.equal(sessions.at(-1), null);
});

test('a switched account prevents the previous user command before transmission', async () => {
  const paths: string[] = [];
  const client = new Client({ lock: mutex(), session: () => {}, announce: () => {}, fetch: async input => {
    paths.push(String(input)); return response({ ...user, id: 2n });
  } });
  await assert.rejects(client.request(1n, '/api/projects/8', 'PUT', { version: 3n }),
    error => error instanceof ApiError && error.code === 'ACCOUNT_CHANGED');
  assert.deepEqual(paths, ['/api/users/me']);
});

test('network and version failures do not replay commands; logout does not claim success on failure', async () => {
  let commands = 0; let announcements = 0; let cleared = false;
  const client = new Client({ lock: mutex(), announce: () => { announcements++; }, session: (_user, clear) => { cleared ||= Boolean(clear); },
    fetch: async input => {
      const path = String(input);
      if (path === '/api/users/me') return response(user);
      if (path === '/api/session/csrf') return response(csrf);
      commands++;
      if (path === '/api/session/logout') throw new TypeError('Network offline');
      return response({ code: 'VERSION_CONFLICT' }, 409);
    }
  });
  await assert.rejects(client.request(1n, '/api/projects/8', 'PUT', { version: 0n }), ApiError);
  await assert.rejects(client.logout(), TypeError);
  assert.equal(commands, 2); assert.equal(announcements, 0); assert.equal(cleared, false);
});
