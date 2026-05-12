export function PageHeader({ title, description }: { title: string; description?: string }) {
  return (
    <div className="between" style={{ marginBottom: 22 }}>
      <div>
        <h1 style={{ margin: 0 }}>{title}</h1>
        {description ? <p className="muted" style={{ margin: '6px 0 0' }}>{description}</p> : null}
      </div>
    </div>
  );
}
