import type { ReactNode } from 'react';
import type { ScanStatus, Severity, TaskStatus } from './api/types';
import { SCAN_STATUS_LABEL, SEVERITY_LABEL, TASK_STATUS_LABEL } from './format';

export function SeverityBadge({ severity }: { severity: Severity }) {
  return <span className={`badge severity-${severity.toLowerCase()}`}>{SEVERITY_LABEL[severity]}</span>;
}

export function StatusBadge({ status }: { status: ScanStatus }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{SCAN_STATUS_LABEL[status]}</span>;
}

/** 작업 상태는 점검 상태와 이름이 겹치는 값(QUEUED, RUNNING 등)이 있어서 따로 둔다 */
export function TaskStatusBadge({ status }: { status: TaskStatus }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{TASK_STATUS_LABEL[status]}</span>;
}

export function ProgressBar({ value, label }: { value: number; label: string }) {
  return (
    <div className="progress" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={value}>
      <div className="progress-fill" style={{ width: `${value}%` }} />
    </div>
  );
}

export function ErrorNote({ message }: { message: string | null }) {
  return message ? (
    <p className="note note-error" role="alert">
      {message}
    </p>
  ) : null;
}

export function Panel({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>{title}</h2>
        {actions}
      </div>
      {children}
    </section>
  );
}

/** 값을 눌러서 복사한다. DNS 레코드 값처럼 손으로 옮겨 적기 어려운 값에 쓴다 */
export function CopyValue({ value }: { value: string }) {
  return (
    <span className="copy">
      <code>{value}</code>
      <button type="button" className="button-quiet" onClick={() => void navigator.clipboard?.writeText(value)}>
        복사
      </button>
    </span>
  );
}
