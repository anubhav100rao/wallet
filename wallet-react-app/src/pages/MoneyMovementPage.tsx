import { MoneyMovementForm } from '../components/forms/MoneyMovementForm';
import { PageHeader } from '../components/ui/PageHeader';

export function MoneyMovementPage() {
  return (
    <div>
      <PageHeader title="Money movement" description="Transfer, deposit, or withdraw funds." />
      <MoneyMovementForm />
    </div>
  );
}
