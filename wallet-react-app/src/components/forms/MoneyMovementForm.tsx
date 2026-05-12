import { useState } from 'react';
import { ApiError } from '../ui/ApiError';
import { WalletPicker } from '../wallet/WalletPicker';
import { useDeposit, useTransfer, useWithdrawal } from '../../hooks/useMoneyMovement';
import { seededWallets } from '../../data/seededWallets';

type Mode = 'transfer' | 'deposit' | 'withdrawal';

export function MoneyMovementForm() {
  const [mode, setMode] = useState<Mode>('transfer');
  const [fromWalletId, setFromWalletId] = useState(seededWallets[0].id);
  const [toWalletId, setToWalletId] = useState(seededWallets[1].id);
  const [amount, setAmount] = useState(25);
  const [currency, setCurrency] = useState('USD');
  const transfer = useTransfer();
  const deposit = useDeposit();
  const withdrawal = useWithdrawal();

  const activeMutation = mode === 'transfer' ? transfer : mode === 'deposit' ? deposit : withdrawal;
  const result = activeMutation.data;

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const normalizedAmount = Number(amount);

    if (mode === 'transfer') {
      await transfer.mutateAsync({ fromWalletId, toWalletId, amount: normalizedAmount, currency });
    } else if (mode === 'deposit') {
      await deposit.mutateAsync({ toWalletId, amount: normalizedAmount, currency });
    } else {
      await withdrawal.mutateAsync({ fromWalletId, amount: normalizedAmount, currency });
    }
  }

  return (
    <form className="card section-card stack" onSubmit={handleSubmit}>
      <div className="row">
        {(['transfer', 'deposit', 'withdrawal'] as Mode[]).map((item) => (
          <button
            key={item}
            type="button"
            className={`btn ${mode === item ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setMode(item)}
          >
            {item[0].toUpperCase() + item.slice(1)}
          </button>
        ))}
      </div>

      <ApiError error={activeMutation.error} />
      {result ? (
        <div className="success">
          Request {result.status.toLowerCase()}. Transaction <span className="code">{result.transactionId}</span>
        </div>
      ) : null}

      {(mode === 'transfer' || mode === 'withdrawal') ? (
        <WalletPicker label="From wallet" value={fromWalletId} onChange={setFromWalletId} />
      ) : null}

      {(mode === 'transfer' || mode === 'deposit') ? (
        <WalletPicker label="To wallet" value={toWalletId} onChange={setToWalletId} />
      ) : null}

      <div className="grid grid-2">
        <label>
          <span className="label">Amount</span>
          <input className="input" value={amount} onChange={(e) => setAmount(Number(e.target.value))} type="number" min="1" step="0.01" />
        </label>
        <label>
          <span className="label">Currency</span>
          <select className="input" value={currency} onChange={(e) => setCurrency(e.target.value)}>
            <option value="USD">USD</option>
            <option value="INR">INR</option>
          </select>
        </label>
      </div>

      <button className="btn btn-primary" disabled={activeMutation.isPending}>
        {activeMutation.isPending ? 'Submitting...' : `Create ${mode}`}
      </button>

      <p className="small muted">
        Every submit sends a fresh <span className="code">Idempotency-Key</span> header using <span className="code">crypto.randomUUID()</span>.
      </p>
    </form>
  );
}
