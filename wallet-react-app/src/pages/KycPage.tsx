import { useMutation } from '@tanstack/react-query';
import { submitKyc } from '../api/kycApi';
import { ApiError } from '../components/ui/ApiError';
import { PageHeader } from '../components/ui/PageHeader';

export function KycPage() {
  const mutation = useMutation({ mutationFn: submitKyc });

  return (
    <div>
      <PageHeader title="KYC" description="Submit your KYC details." />
      <div className="card section-card stack">
        <div>
          <h2 style={{ margin: 0 }}>Identity Verification</h2>
          <p className="muted">Submit your information to unlock all wallet features.</p>
        </div>
        <ApiError error={mutation.error} />
        {mutation.isSuccess ? <div className="success">KYC submitted successfully.</div> : null}
        <button className="btn btn-primary" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
          {mutation.isPending ? 'Submitting...' : 'Submit KYC'}
        </button>
      </div>
    </div>
  );
}
