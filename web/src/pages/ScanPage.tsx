import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { isFinished, SEVERITIES, type Issue, type Scan, type ScanFindings, type Severity, type WorkerStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorNote, Panel, ProgressBar, SeverityBadge, StatusBadge, TaskStatusBadge } from '../components';
import { ENGINE_LABEL, formatDateTime, formatDuration, SEVERITY_LABEL } from '../format';
import { errorMessage, useLoad } from '../hooks';

/** 점검이 끝날 때까지 상태와 결과를 이 간격으로 다시 불러온다 */
const POLL_MS = 3000;

export function ScanPage() {
  const { id } = useParams();
  const { api } = useAuth();
  const scan = useLoad(() => api.get<Scan>(`/api/scans/${id}`), [api, id], POLL_MS);
  const running = scan.data ? !isFinished(scan.data.status) : true;
  const findings = useLoad(() => api.get<ScanFindings>(`/api/scans/${id}/findings`), [api, id, scan.data?.status], running ? POLL_MS : 0);
  const workers = useLoad(() => api.get<WorkerStatus>('/api/workers/status'), [api], running ? 15000 : 0);
  const [error, setError] = useState<string | null>(null);

  async function cancel() {
    setError(null);
    try {
      await api.post(`/api/scans/${id}/cancel`);
      await scan.reload();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const s = scan.data;
  if (!s) {
    return scan.error ? <ErrorNote message={scan.error} /> : <div className="page-loading">불러오는 중…</div>;
  }
  const waiting = s.tasks.filter((task) => task.status === 'QUEUED');
  const offline = workers.data && waiting.some((task) => (task.engine === 'ZAP' ? !workers.data!.heavyOnline : !workers.data!.lightOnline));

  return (
    <>
      <div className="page-head">
        <Link to={`/targets/${s.targetId}`} className="back">← 대상으로</Link>
        <div className="title-row">
          <h1>점검 #{s.id}</h1>
          <StatusBadge status={s.status} />
        </div>
        <p className="muted mono">{s.baseUrl}</p>
      </div>
      <ErrorNote message={error ?? scan.error} />

      <Panel
        title="진행 상황"
        actions={
          running && (
            <button type="button" className="button-quiet" onClick={() => void cancel()}>
              점검 취소
            </button>
          )
        }
      >
        <div className="scan-progress">
          <ProgressBar value={s.progress} label="전체 진행률" />
          <span className="mono">{s.progress}%</span>
        </div>
        <p className="muted small">
          요청 {formatDateTime(s.requestedAt)} · 걸린 시간 {formatDuration(s.requestedAt, s.finishedAt)}
        </p>
        {offline && (
          <p className="note note-warn">
            이 엔진을 실행할 워커가 꺼져 있어 대기 중인 작업이 시작되지 않고 있습니다. 워커가 켜지면 이어서 진행합니다.
          </p>
        )}
        <table className="tasks">
          <thead>
            <tr>
              <th>엔진</th>
              <th>상태</th>
              <th>진행률</th>
              <th>시도</th>
              <th>걸린 시간</th>
            </tr>
          </thead>
          <tbody>
            {s.tasks.map((task) => (
              <tr key={task.engine}>
                <td>{ENGINE_LABEL[task.engine]}</td>
                <td>
                  <TaskStatusBadge status={task.status} />
                  {task.cancelRequested && task.status === 'RUNNING' && <span className="muted small"> 취소 중</span>}
                  {task.errorMessage && <div className="task-error">{task.errorMessage}</div>}
                </td>
                <td>
                  <ProgressBar value={task.progress} label={`${ENGINE_LABEL[task.engine]} 진행률`} />
                </td>
                <td className="mono">
                  {task.attempt}/{task.maxAttempts}
                </td>
                <td className="mono">{formatDuration(task.startedAt, task.finishedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>

      <Findings findings={findings.data} error={findings.error} running={running} />
    </>
  );
}

function Findings({ findings, error, running }: { findings?: ScanFindings; error: string | null; running: boolean }) {
  const [filter, setFilter] = useState<Severity | null>(null);
  if (!findings) {
    return error ? <ErrorNote message={error} /> : null;
  }
  const issues = filter ? findings.issues.filter((issue) => issue.severity === filter) : findings.issues;
  return (
    <Panel title="점검 결과">
      {running && <p className="note">끝난 엔진의 결과부터 보여 줍니다. 점검이 끝나면 더 늘어날 수 있습니다.</p>}
      <div className="summary" role="group" aria-label="위험도별 문제 수">
        {SEVERITIES.map((severity) => (
          <button
            key={severity}
            type="button"
            className={`summary-item severity-${severity.toLowerCase()}${filter === severity ? ' active' : ''}`}
            aria-pressed={filter === severity}
            onClick={() => setFilter(filter === severity ? null : severity)}
          >
            <span className="summary-count">{findings.summary[severity]}</span>
            <span>{SEVERITY_LABEL[severity]}</span>
          </button>
        ))}
      </div>
      <p className="muted small">
        문제 {findings.issues.length}개 · 위치 {findings.locationCount}곳. 여러 엔진이 같은 페이지에서 같은 문제를 찾으면 하나로 묶습니다.
      </p>
      {issues.length === 0 && (
        <p className="empty">
          {filter ? '이 위험도의 문제가 없습니다.' : running ? '아직 결과가 없습니다. 엔진이 끝나는 대로 나타납니다.' : '찾은 문제가 없습니다.'}
        </p>
      )}
      <div className="issues">
        {issues.map((issue) => (
          <IssueRow key={issue.check} issue={issue} />
        ))}
      </div>
    </Panel>
  );
}

function IssueRow({ issue }: { issue: Issue }) {
  return (
    <details className="issue">
      <summary>
        <SeverityBadge severity={issue.severity} />
        <span className="issue-title">{issue.title}</span>
        <span className="issue-meta">
          {issue.engines.map((engine) => (
            <span key={engine} className="chip">
              {ENGINE_LABEL[engine]}
            </span>
          ))}
          <span className="muted small">{issue.locations.length}곳</span>
        </span>
      </summary>
      <div className="issue-body">
        <dl className="facts">
          {issue.owaspCategory && (
            <>
              <dt>OWASP</dt>
              <dd>{issue.owaspCategory}</dd>
            </>
          )}
          {issue.cweId && (
            <>
              <dt>CWE</dt>
              <dd>
                <a href={`https://cwe.mitre.org/data/definitions/${issue.cweId}.html`} target="_blank" rel="noreferrer">
                  CWE-{issue.cweId}
                </a>
              </dd>
            </>
          )}
        </dl>
        {issue.remediation && (
          <div className="remediation">
            <h3>조치 방법</h3>
            <p>{issue.remediation}</p>
          </div>
        )}
        <table className="locations">
          <thead>
            <tr>
              <th>위치</th>
              <th>근거</th>
              <th>엔진</th>
            </tr>
          </thead>
          <tbody>
            {issue.locations.map((location) => (
              <tr key={location.fingerprint}>
                <td className="mono break">
                  {location.url}
                  {location.param && <span className="chip">{location.param}</span>}
                </td>
                <td className="mono break small">{location.evidence ?? '-'}</td>
                <td className="small">{location.engines.map((engine) => ENGINE_LABEL[engine]).join(', ')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
