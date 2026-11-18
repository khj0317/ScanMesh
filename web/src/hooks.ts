import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from './api/client';

interface Loaded<T> {
  data: T | undefined;
  error: string | null;
  reload: () => Promise<void>;
}

/**
 * 화면에 필요한 데이터를 불러온다. intervalMs를 주면 그 간격으로 다시 불러오고, 0이면 멈춘다.
 * 다시 불러오다 실패하면 이전 데이터는 그대로 두고 에러만 보여 준다.
 */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[], intervalMs = 0): Loaded<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string | null>(null);
  const loadRef = useRef(load);
  loadRef.current = load;

  const reload = useCallback(async () => {
    try {
      setData(await loadRef.current());
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  // 불러오는 함수는 매번 새로 만들어지므로, 무엇이 바뀌면 다시 불러올지는 deps로 받는다
  useEffect(() => {
    void reload();
  }, deps);

  useEffect(() => {
    if (intervalMs <= 0) {
      return undefined;
    }
    const timer = setInterval(() => void reload(), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs, reload]);

  return { data, error, reload };
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof TypeError) {
    return 'API 서버에 연결할 수 없습니다.';
  }
  return error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.';
}
