import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="auth-page">
      <div className="card auth-card stack">
        <h1 style={{ margin: 0 }}>404</h1>
        <p className="muted">This page does not exist.</p>
        <Link className="btn btn-primary" to="/">Go home</Link>
      </div>
    </div>
  );
}
