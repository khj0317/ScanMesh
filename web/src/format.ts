import type { Engine, ScanStatus, Severity, TaskStatus } from './api/types';

export const SEVERITY_LABEL: Record<Severity, string> = {
  CRITICAL: '심각',
  HIGH: '높음',
  MEDIUM: '중간',
  LOW: '낮음',
  INFO: '정보',
};

export const ENGINE_LABEL: Record<Engine, string> = {
  CUSTOM: '자체 규칙',
  NUCLEI: 'Nuclei',
  ZAP: 'ZAP',
};

export const SCAN_STATUS_LABEL: Record<ScanStatus, string> = {
  QUEUED: '대기 중',
  RUNNING: '점검 중',
  COMPLETED: '완료',
  PARTIAL: '일부 완료',
  FAILED: '실패',
  CANCELLED: '취소됨',
};

export const TASK_STATUS_LABEL: Record<TaskStatus, string> = {
  QUEUED: '대기 중',
  RUNNING: '실행 중',
  SUCCEEDED: '성공',
  FAILED: '실패',
  CANCELLED: '취소됨',
};

const dateTime = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' });

export function formatDateTime(value: string | null): string {
  return value ? dateTime.format(new Date(value)) : '-';
}

/** 시작과 끝 사이 시간. 끝나지 않았으면 지금까지 */
export function formatDuration(from: string | null, to: string | null): string {
  if (!from) {
    return '-';
  }
  const seconds = Math.max(0, Math.round(((to ? new Date(to) : new Date()).getTime() - new Date(from).getTime()) / 1000));
  return seconds < 60 ? `${seconds}초` : `${Math.floor(seconds / 60)}분 ${seconds % 60}초`;
}
