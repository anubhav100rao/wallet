import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '../api/authApi';
import { ApiError } from '../components/ui/ApiError';

export function RegisterPage() {
  const [email, setEmail] = useState('demo@wallet.com');
  const [password, setPassword] = useState('password123');
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSuccess(false);
    setLoading(true);
    try {
      await register(email, password);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 800);
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
          <h1 style={{ margin: 0 }}>Register</h1>
          <p className="muted">Create a user, then login and submit KYC.</p>
        </div>
        <ApiError error={error} />
        {success ? <div className="success">Registered. Redirecting to login...</div> : null}
        <label>
          <span className="label">Email</span>
          <input className="input" value={email} onChange={(e) => setEmail(e.target.value)} type="email" />
        </label>
        <label>
          <span className="label">Password</span>
          <input className="input" value={password} onChange={(e) => setPassword(e.target.value)} type="password" />
        </label>
        <button className="btn btn-primary" disabled={loading}>{loading ? 'Registering...' : 'Register'}</button>
        <p className="small muted">Already registered? <Link to="/login">Login</Link></p>
      </form>
    </div>
  );
}
