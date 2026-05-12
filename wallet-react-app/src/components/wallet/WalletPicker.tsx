import { seededWallets } from '../../data/seededWallets';

export function WalletPicker({ value, onChange, label = 'Wallet' }: { value: string; onChange: (value: string) => void; label?: string }) {
  return (
    <label>
      <span className="label">{label}</span>
      <select className="input" value={value} onChange={(event) => onChange(event.target.value)}>
        {seededWallets.map((wallet) => (
          <option key={wallet.id} value={wallet.id}>{wallet.label}</option>
        ))}
      </select>
    </label>
  );
}
