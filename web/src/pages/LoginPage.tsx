import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { ErrorNote } from '../components';
import { errorMessage } from '../hooks';

export function LoginPage({ mode }: { mode: 'login' | 'signup' }) {
  const { api, member, login } = useAuth();
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (member) {
    return <Navigate to={from} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'signup') {
        await api.post('/api/auth/signup', { email, password, name });
      }
      await login(email, password);
      navigate(from, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const signup = mode === 'signup';
  return (
    <div className="auth">
      <form className="auth-card" onSubmit={submit}>
        <div className="brand brand-large">
          <span className="brand-mark" aria-hidden="true" />
          ScanMesh
        </div>
        <p className="muted">소유를 인증한 웹사이트를 여러 점검 엔진으로 한 번에 점검합니다.</p>
        <h1>{signup ? '회원가입' : '로그인'}</h1>
        {signup && (
          <label>
            이름
            <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={50} autoComplete="name" />
          </label>
        )}
        <label>
          이메일
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label>
          비밀번호
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={signup ? 8 : undefined}
            autoComplete={signup ? 'new-password' : 'current-password'}
          />
          {signup && <span className="hint">8~64자, 영문과 숫자를 모두 포함</span>}
        </label>
        <ErrorNote message={error} />
        <button type="submit" className="button-primary" disabled={busy}>
          {busy ? '처리 중…' : signup ? '가입하고 로그인' : '로그인'}
        </button>
        <p className="muted center">
          {signup ? (
            <>이미 계정이 있나요? <Link to="/login">로그인</Link></>
          ) : (
            <>처음이신가요? <Link to="/signup">회원가입</Link></>
          )}
        </p>
      </form>
    </div>
  );
}
