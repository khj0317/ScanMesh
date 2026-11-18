import { Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { useAuth } from './auth/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { ScanPage } from './pages/ScanPage';
import { TargetPage } from './pages/TargetPage';
import { TargetsPage } from './pages/TargetsPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage mode="login" />} />
      <Route path="/signup" element={<LoginPage mode="signup" />} />
      <Route element={<RequireLogin />}>
        <Route path="/" element={<TargetsPage />} />
        <Route path="/targets/:id" element={<TargetPage />} />
        <Route path="/scans/:id" element={<ScanPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function RequireLogin() {
  const { member, logout } = useAuth();
  const location = useLocation();
  if (member === undefined) {
    return <div className="page-loading">불러오는 중…</div>;
  }
  if (member === null) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden="true" />
          ScanMesh
        </Link>
        <nav className="topbar-nav">
          <Link to="/">점검 대상</Link>
        </nav>
        <div className="topbar-user">
          <span>{member.name}</span>
          <button type="button" className="button-quiet" onClick={() => void logout()}>
            로그아웃
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
