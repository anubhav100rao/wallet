import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../components/ui/ApiError';

export function LoginPage() {
  const [email, setEmail] = useState('alice@wallet.local');
  const [password, setPassword] = useState('password123');
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="card auth-card stack" onSubmit={handleSubmit}>
        <div>
          <h1 style={{ margin: 0 }}>Login</h1>
          <p className="muted">Seed users use password <span className="code">password123</span>.</p>
        </div>
        <ApiError error={error} />
        <label>
          <span className="label">Email</span>
          <input className="input" value={email} onChange={(e) => setEmail(e.target.value)} type="email" />
        </label>
        <label>
          <span className="label">Password</span>
          <input className="input" value={password} onChange={(e) => setPassword(e.target.value)} type="password" />
        </label>
        <button className="btn btn-primary" disabled={loading}>{loading ? 'Logging in...' : 'Login'}</button>
        <p className="small muted">Need a new user? <Link to="/register">Register</Link></p>
      </form>
    </div>
  );
}
