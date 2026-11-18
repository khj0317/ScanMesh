import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ENGINES, type Engine, type Scan, type Target, type VerificationResult } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { CopyValue, ErrorNote, Panel, ProgressBar, StatusBadge } from '../components';
import { ENGINE_LABEL, formatDateTime, formatDuration } from '../format';
import { errorMessage, useLoad } from '../hooks';

const ENGINE_DESCRIPTION: Record<Engine, string> = {
  CUSTOM: '보안 헤더, 쿠키, 인증서, 민감 파일 노출',
  NUCLEI: '알려진 CVE와 설정 실수 (속도 제한)',
  ZAP: '사이트를 둘러보며 응답 점검 (공격 요청 없음)',
};

export function TargetPage() {
  const { id } = useParams();
  const { api } = useAuth();
  const navigate = useNavigate();
  const target = useLoad(() => api.get<Target>(`/api/targets/${id}`), [api, id]);
  const scans = useLoad(() => api.get<Scan[]>(`/api/targets/${id}/scans`), [api, id]);
  const [engines, setEngines] = useState<Engine[]>(ENGINES);
  const [verification, setVerification] = useState<VerificationResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function act(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const verify = () =>
    act(async () => {
      const result = await api.post<VerificationResult>(`/api/targets/${id}/verify`);
      setVerification(result);
      await target.reload();
    });

  const requestScan = () =>
    act(async () => {
      const scan = await api.post<Scan>(`/api/targets/${id}/scans`, { engines });
      navigate(`/scans/${scan.id}`);
    });

  const remove = () => {
    if (!window.confirm('이 대상과 지금까지의 점검 결과를 모두 지웁니다. 계속할까요?')) {
      return;
    }
    void act(async () => {
      await api.delete(`/api/targets/${id}`);
      navigate('/');
    });
  };

  if (target.error && !target.data) {
    return <ErrorNote message={target.error} />;
  }
  const t = target.data;
  if (!t) {
    return <div className="page-loading">불러오는 중…</div>;
  }

  return (
    <>
      <div className="page-head">
        <Link to="/" className="back">← 점검 대상</Link>
        <h1>{t.host}</h1>
        <p className="muted mono">{t.baseUrl}</p>
      </div>
      <ErrorNote message={error} />

      {!t.verified && (
        <Panel title="소유 인증">
          <p>이 사이트의 주인인지 확인한 뒤에만 점검할 수 있습니다. 아래 값을 올린 뒤 <strong>인증 확인</strong>을 누르세요.</p>
          {t.verification.method === 'DNS_TXT' ? (
            <dl className="facts">
              <dt>레코드 종류</dt>
              <dd>TXT</dd>
              <dt>이름</dt>
              <dd><CopyValue value={t.verification.dnsRecordName} /></dd>
              <dt>값</dt>
              <dd><CopyValue value={t.verification.token} /></dd>
            </dl>
          ) : (
            <dl className="facts">
              <dt>파일 주소</dt>
              <dd><CopyValue value={t.verification.fileUrl} /></dd>
              <dt>파일 내용</dt>
              <dd><CopyValue value={t.verification.token} /></dd>
            </dl>
          )}
          {verification && !verification.verified && <p className="note note-warn">{verification.detail}</p>}
          <button type="button" className="button-primary" onClick={() => void verify()} disabled={busy}>
            인증 확인
          </button>
        </Panel>
      )}

      {t.verified && (
        <Panel title="점검 요청">
          {verification?.verified && <p className="note note-ok">{verification.detail}</p>}
          <div className="engine-picks">
            {ENGINES.map((engine) => (
              <label key={engine} className="engine-pick">
                <input
                  type="checkbox"
                  checked={engines.includes(engine)}
                  onChange={(e) => setEngines(e.target.checked ? [...engines, engine] : engines.filter((x) => x !== engine))}
                />
                <span>
                  <strong>{ENGINE_LABEL[engine]}</strong>
                  <span className="muted small">{ENGINE_DESCRIPTION[engine]}</span>
                </span>
              </label>
            ))}
          </div>
          <button type="button" className="button-primary" onClick={() => void requestScan()} disabled={busy || engines.length === 0}>
            점검 시작
          </button>
        </Panel>
      )}

      <Panel title="점검 기록">
        <ErrorNote message={scans.error} />
        {scans.data?.length === 0 && <p className="empty">아직 점검한 적이 없습니다.</p>}
        <ul className="list">
          {scans.data?.map((scan) => (
            <li key={scan.id}>
              <Link to={`/scans/${scan.id}`} className="list-row">
                <span className="list-main">
                  <strong>점검 #{scan.id}</strong>
                  <span className="muted small">
                    {formatDateTime(scan.requestedAt)} · {scan.tasks.map((task) => ENGINE_LABEL[task.engine]).join(', ')}
                  </span>
                </span>
                <span className="list-progress">
                  <ProgressBar value={scan.progress} label={`점검 #${scan.id} 진행률`} />
                </span>
                <StatusBadge status={scan.status} />
                <span className="muted small">{formatDuration(scan.requestedAt, scan.finishedAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      </Panel>

      <div className="danger-zone">
        <button type="button" className="button-danger" onClick={remove} disabled={busy}>
          대상 삭제
        </button>
      </div>
    </>
  );
}
