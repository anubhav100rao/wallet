import { toApiError } from '../../api/client';

export function ApiError({ error }: { error: unknown }) {
  if (!error) return null;
  return <div className="error">{toApiError(error)}</div>;
}
