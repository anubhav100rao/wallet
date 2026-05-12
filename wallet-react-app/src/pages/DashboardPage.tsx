import { Link } from 'react-router-dom';
import { PageHeader } from '../components/ui/PageHeader';
import { MetricCard } from '../components/ui/MetricCard';
import { useWallet } from '../hooks/useWallet';
import { seededWallets } from '../data/seededWallets';

export function DashboardPage() {
  const walletQuery = useWallet(seededWallets[0].id);
  const wallet = walletQuery.data;

  return (
    <div>
      <PageHeader title="Dashboard" description="Welcome to your Banking Wallet dashboard." />

      <div className="grid" style={{ marginTop: 18 }}>
        <div className="card section-card stack">
          <h2 style={{ margin: 0 }}>Wallet Snapshot</h2>
          {walletQuery.isLoading ? <p>Loading wallet...</p> : null}
          {wallet ? (
            <div className="grid grid-2">
              <MetricCard label="Available balance" value={`${wallet.availableBalance} ${wallet.currency}`} />
              <MetricCard label="Total balance" value={`${wallet.totalBalance} ${wallet.currency}`} />
              <Link className="btn btn-primary" style={{ gridColumn: '1 / -1' }} to={`/wallets/${wallet.id}`}>Open wallet details</Link>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
