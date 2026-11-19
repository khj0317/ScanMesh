import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiClient } from '../api/client';
import type { Member } from '../api/types';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8190';

interface AuthState {
  api: ApiClient;
  /** null이면 로그인하지 않음, undefined면 저장된 로그인을 되살리는 중 */
  member: Member | null | undefined;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [member, setMember] = useState<Member | null | undefined>(undefined);
  const api = useMemo(
    () => new ApiClient({ baseUrl: API_URL, onLoggedOut: () => setMember(null) }),
    [],
  );

  useEffect(() => {
    api
      .restore()
      .then((restored) => (restored ? api.get<Member>('/api/members/me') : null))
      .then(setMember)
      .catch(() => setMember(null));
  }, [api]);

  const login = useCallback(
    async (email: string, password: string) => {
      await api.login(email, password);
      setMember(await api.get<Member>('/api/members/me'));
    },
    [api],
  );

  const logout = useCallback(async () => {
    await api.logout();
    setMember(null);
  }, [api]);

  const value = useMemo(() => ({ api, member, login, logout }), [api, member, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error('AuthProvider 안에서만 쓸 수 있습니다.');
  }
  return auth;
}
