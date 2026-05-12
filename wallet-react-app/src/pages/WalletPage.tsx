import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../components/ui/ApiError';
import { MetricCard } from '../components/ui/MetricCard';
import { PageHeader } from '../components/ui/PageHeader';
import { WalletPicker } from '../components/wallet/WalletPicker';
import { useActiveHolds, useWallet } from '../hooks/useWallet';

export function WalletPage() {
  const params = useParams();
  const navigate = useNavigate();
  const walletId = params.walletId ?? '10000000-0000-0000-0000-000000000001';
  const [selectedWalletId, setSelectedWalletId] = useState(walletId);
  const walletQuery = useWallet(walletId);
  const holdsQuery = useActiveHolds(walletId);
  const wallet = walletQuery.data;

  function handleWalletChange(nextWalletId: string) {
    setSelectedWalletId(nextWalletId);
    navigate(`/wallets/${nextWalletId}`);
  }

  return (
    <div>
      <PageHeader title="Wallet details" description="View your wallet balances and active holds." />
      <div className="card section-card stack" style={{ marginBottom: 18 }}>
        <WalletPicker value={selectedWalletId} onChange={handleWalletChange} />
      </div>

      <ApiError error={walletQuery.error} />
      {walletQuery.isLoading ? <div className="card section-card">Loading wallet...</div> : null}

      {wallet ? (
        <>
          <div className="grid grid-3">
            <MetricCard label="Available balance" value={`${wallet.availableBalance} ${wallet.currency}`} />
            <MetricCard label="Total balance" value={`${wallet.totalBalance} ${wallet.currency}`} />
            <MetricCard label="Currency" value={wallet.currency} helper="Wallet currency" />
          </div>

          <div className="card section-card" style={{ marginTop: 18 }}>
            <div className="between">
              <h2 style={{ marginTop: 0 }}>Active holds</h2>
              <span className="badge">{holdsQuery.data?.length ?? 0} active</span>
            </div>
            <ApiError error={holdsQuery.error} />
            {holdsQuery.isLoading ? <p>Loading holds...</p> : null}
            {holdsQuery.data?.length ? (
              <table className="table">
                <thead>
                  <tr><th>Amount</th><th>Transaction</th><th>Expires</th></tr>
                </thead>
                <tbody>
                  {holdsQuery.data.map((hold) => (
                    <tr key={hold.id}>
                      <td>{hold.amount}</td>
                      <td><span className="code">{hold.transactionId}</span></td>
                      <td>{new Date(hold.expiresAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : !holdsQuery.isLoading ? <p className="muted">No active holds.</p> : null}
          </div>
        </>
      ) : null}
    </div>
  );
}
