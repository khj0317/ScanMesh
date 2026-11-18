import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import type { Target, VerificationMethod } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorNote, Panel } from '../components';
import { formatDateTime } from '../format';
import { errorMessage, useLoad } from '../hooks';

export function TargetsPage() {
  const { api } = useAuth();
  const navigate = useNavigate();
  const targets = useLoad(() => api.get<Target[]>('/api/targets'), [api]);
  const [url, setUrl] = useState('');
  const [method, setMethod] = useState<VerificationMethod>('DNS_TXT');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function register(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const target = await api.post<Target>('/api/targets', { url, verificationMethod: method });
      navigate(`/targets/${target.id}`);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>점검 대상</h1>
        <p className="muted">점검하려면 먼저 사이트를 등록하고 소유를 인증하세요. 인증한 사이트만 점검할 수 있습니다.</p>
      </div>

      <Panel title="새 대상 등록">
        <form className="register" onSubmit={register}>
          <label className="grow">
            사이트 주소
            <input
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://example.com"
              required
              maxLength={2048}
              inputMode="url"
            />
          </label>
          <fieldset className="segmented">
            <legend>소유 인증 방법</legend>
            <label>
              <input type="radio" name="method" checked={method === 'DNS_TXT'} onChange={() => setMethod('DNS_TXT')} />
              DNS TXT 레코드
            </label>
            <label>
              <input type="radio" name="method" checked={method === 'WELL_KNOWN_FILE'} onChange={() => setMethod('WELL_KNOWN_FILE')} />
              인증 파일
            </label>
          </fieldset>
          <button type="submit" className="button-primary" disabled={busy}>
            {busy ? '확인 중…' : '등록'}
          </button>
        </form>
        <ErrorNote message={error} />
      </Panel>

      <Panel title={`등록한 대상${targets.data ? ` ${targets.data.length}개` : ''}`}>
        <ErrorNote message={targets.error} />
        {targets.data?.length === 0 && <p className="empty">아직 등록한 대상이 없습니다.</p>}
        <ul className="list">
          {targets.data?.map((target) => (
            <li key={target.id}>
              <Link to={`/targets/${target.id}`} className="list-row">
                <span className="list-main">
                  <strong>{target.host}</strong>
                  <span className="muted mono">{target.baseUrl}</span>
                </span>
                <span className={`badge ${target.verified ? 'status-completed' : 'status-queued'}`}>
                  {target.verified ? '인증 완료' : '인증 필요'}
                </span>
                <span className="muted small">{formatDateTime(target.createdAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      </Panel>
    </>
  );
}
