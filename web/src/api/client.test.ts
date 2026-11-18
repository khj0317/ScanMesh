import { describe, expect, it, vi } from 'vitest';
import { ApiClient, ApiError } from './client';

function json(status: number, body: unknown): Response {
  return new Response(body === undefined ? '' : JSON.stringify(body), { status });
}

const LOGIN = { accessToken: 'access-1', tokenType: 'Bearer', expiresIn: 1800 };
const REFRESHED = { accessToken: 'access-2', tokenType: 'Bearer', expiresIn: 1800 };

/** 브라우저가 쿠키를 붙여 보내는 것처럼, 쿠키를 함께 보내라고 한 요청만 리프레시에 성공하는 가짜 서버 */
function fakeServer(options: { refreshDelayMs?: number; refreshFails?: boolean } = {}) {
  const calls = { refresh: 0 };
  const fetch = vi.fn(async (url: string, init?: RequestInit) => {
    if (url.endsWith('/api/auth/login')) {
      return json(200, LOGIN);
    }
    if (url.endsWith('/api/auth/refresh')) {
      calls.refresh++;
      await new Promise((resolve) => setTimeout(resolve, options.refreshDelayMs ?? 0));
      return options.refreshFails || init?.credentials !== 'include'
        ? json(401, { code: 'INVALID_REFRESH_TOKEN', message: '로그인이 만료되었습니다. 다시 로그인해주세요.' })
        : json(200, REFRESHED);
    }
    const auth = (init?.headers as Record<string, string>)?.Authorization;
    return auth === 'Bearer access-2' ? json(200, url) : json(401, { code: 'INVALID_TOKEN', message: 'x' });
  });
  return { fetch: fetch as unknown as typeof globalThis.fetch, calls };
}

describe('ApiClient', () => {
  it('액세스 토큰이 만료되면 쿠키로 리프레시하고 요청을 한 번 다시 보낸다', async () => {
    const server = fakeServer();
    const client = new ApiClient({ baseUrl: '', fetch: server.fetch });
    await client.login('a@example.com', 'password123');

    await expect(client.get('/api/targets')).resolves.toBe('/api/targets');
    expect(server.calls.refresh).toBe(1);
  });

  it('동시에 401을 받아도 리프레시는 한 번만 보낸다 (버린 토큰을 다시 보내면 서버가 모든 로그인을 끊는다)', async () => {
    const server = fakeServer({ refreshDelayMs: 10 });
    const client = new ApiClient({ baseUrl: '', fetch: server.fetch });
    await client.login('a@example.com', 'password123');

    const results = await Promise.all([client.get('/a'), client.get('/b'), client.get('/c')]);

    expect(results).toEqual(['/a', '/b', '/c']);
    expect(server.calls.refresh).toBe(1);
  });

  it('리프레시도 실패하면 로그아웃을 알린다', async () => {
    const server = fakeServer({ refreshFails: true });
    const onLoggedOut = vi.fn();
    const client = new ApiClient({ baseUrl: '', fetch: server.fetch, onLoggedOut });
    await client.login('a@example.com', 'password123');

    await expect(client.get('/api/targets')).rejects.toBeInstanceOf(ApiError);
    expect(client.loggedIn).toBe(false);
    expect(onLoggedOut).toHaveBeenCalledOnce();
  });

  it('새로 고침 뒤 쿠키가 없으면 조용히 로그인하지 않은 상태가 된다', async () => {
    const server = fakeServer({ refreshFails: true });
    const onLoggedOut = vi.fn();
    const client = new ApiClient({ baseUrl: '', fetch: server.fetch, onLoggedOut });

    await expect(client.restore()).resolves.toBe(false);
    expect(onLoggedOut).not.toHaveBeenCalled();
  });

  it('서버 에러 메시지를 그대로 전한다', async () => {
    const fetch = vi.fn(async () => json(409, { status: 409, code: 'DUPLICATE_TARGET', message: '이미 등록한 도메인입니다.' }));
    const client = new ApiClient({ baseUrl: '', fetch: fetch as unknown as typeof globalThis.fetch });

    await expect(client.post('/api/targets', {})).rejects.toMatchObject({ status: 409, code: 'DUPLICATE_TARGET', message: '이미 등록한 도메인입니다.' });
  });
});
