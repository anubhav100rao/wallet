export function MetricCard({ label, value, helper }: { label: string; value: string; helper?: string }) {
  return (
    <div className="card metric-card">
      <div className="muted small">{label}</div>
      <div className="metric-value">{value}</div>
      {helper ? <div className="muted small">{helper}</div> : null}
    </div>
  );
}
