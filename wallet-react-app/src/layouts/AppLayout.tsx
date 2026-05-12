import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { CreditCard, Home, LogOut, ShieldCheck, WalletCards } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <div className="brand">Banking Wallet</div>
        </div>

        <nav className="nav">
          <NavLink to="/"><Home size={16} /> Dashboard</NavLink>
          <NavLink to="/wallets/10000000-0000-0000-0000-000000000001"><WalletCards size={16} /> Wallet</NavLink>
          <NavLink to="/money-movement"><CreditCard size={16} /> Money Movement</NavLink>
          <NavLink to="/kyc"><ShieldCheck size={16} /> KYC</NavLink>
        </nav>

        <div style={{ marginTop: 'auto' }} className="stack">
          <div className="small">Signed in as<br /><strong>{user?.email}</strong></div>
          <button className="btn btn-danger" onClick={handleLogout}><LogOut size={16} /> Logout</button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
