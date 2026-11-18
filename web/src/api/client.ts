import type { TokenResponse } from './types';

/** 서버의 에러 응답 형식 { status, code, message } */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

interface ClientOptions {
  baseUrl: string;
  fetch?: typeof fetch;
  /** 리프레시까지 실패해서 다시 로그인해야 할 때 */
  onLoggedOut?: () => void;
}

/**
 * API 클라이언트. 액세스 토큰은 메모리에만 두고, 만료되면(401) 새로 받아 요청을 한 번 다시 보낸다.
 * 리프레시 토큰은 서버가 HttpOnly 쿠키로 주고받으므로 이 코드는 값을 볼 수 없다. XSS가 생겨도 빼 갈 수 없게 하기 위해서다.
 *
 * 서버는 리프레시 토큰을 쓸 때마다 바꾸고, 이미 버린 토큰이 다시 오면 탈취로 보고 모든 로그인을 끊는다.
 * 그래서 여러 요청이 동시에 401을 받아도 리프레시는 한 번만 보내고, 탭이 여러 개면 Web Locks로 차례를 정한다.
 * 쿠키는 탭끼리 공유되므로, 차례를 기다린 탭은 앞 탭이 바꿔 둔 새 쿠키로 요청하게 된다.
 */
export class ApiClient {
  private accessToken: string | null = null;
  private refreshing: Promise<boolean> | null = null;
  private readonly fetchImpl: typeof fetch;

  constructor(private readonly options: ClientOptions) {
    this.fetchImpl = options.fetch ?? ((input, init) => fetch(input, init));
  }

  get loggedIn(): boolean {
    return this.accessToken !== null;
  }

  async login(email: string, password: string): Promise<void> {
    this.accept(await this.send<TokenResponse>('POST', '/api/auth/login', { email, password }, false));
  }

  async logout(): Promise<void> {
    this.accessToken = null;
    // 서버가 쿠키를 지우지 못해도 이 화면에서는 이미 로그아웃했다
    await this.send('POST', '/api/auth/logout', undefined, false).catch(() => undefined);
  }

  /** 새로 고침한 뒤 쿠키에 남은 리프레시 토큰으로 로그인 상태를 되살린다. 로그인한 적이 없으면 false */
  restore(): Promise<boolean> {
    return this.refresh(false);
  }

  get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path);
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('POST', path, body);
  }

  delete(path: string): Promise<void> {
    return this.request<void>('DELETE', path);
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    try {
      return await this.send<T>(method, path, body, true);
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401 || this.accessToken === null) {
        throw error;
      }
      if (!(await this.refresh(true))) {
        throw error;
      }
      return this.send<T>(method, path, body, true);
    }
  }

  /** @param notifyOnFailure 로그인한 상태에서 만료된 경우에만 로그아웃을 알린다 */
  private refresh(notifyOnFailure: boolean): Promise<boolean> {
    // 동시에 들어온 401들은 같은 리프레시를 기다린다
    this.refreshing ??= this.withLock(() => this.rotate(notifyOnFailure)).finally(() => {
      this.refreshing = null;
    });
    return this.refreshing;
  }

  private async rotate(notifyOnFailure: boolean): Promise<boolean> {
    try {
      this.accept(await this.send<TokenResponse>('POST', '/api/auth/refresh', undefined, false));
      return true;
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) {
        throw error;
      }
      this.accessToken = null;
      if (notifyOnFailure) {
        this.options.onLoggedOut?.();
      }
      return false;
    }
  }

  private withLock<T>(task: () => Promise<T>): Promise<T> {
    const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
    return locks ? locks.request('scanmesh-refresh', task) : task();
  }

  private accept(tokens: TokenResponse): void {
    this.accessToken = tokens.accessToken;
  }

  private async send<T>(method: string, path: string, body: unknown, authorize: boolean): Promise<T> {
    const headers: Record<string, string> = {};
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (authorize && this.accessToken) {
      headers.Authorization = `Bearer ${this.accessToken}`;
    }
    const response = await this.fetchImpl(this.options.baseUrl + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      // 리프레시 토큰 쿠키를 주고받는다. 쿠키 경로가 /api/auth라 다른 API 요청에는 실리지 않는다
      credentials: 'include',
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : undefined;
    if (!response.ok) {
      throw new ApiError(response.status, data?.code ?? 'UNKNOWN', data?.message ?? `요청이 실패했습니다 (${response.status}).`);
    }
    return data as T;
  }
}
