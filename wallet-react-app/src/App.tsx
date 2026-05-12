import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from './layouts/AppLayout';
import { ProtectedRoute } from './routes/ProtectedRoute';
import { DashboardPage } from './pages/DashboardPage';
import { KycPage } from './pages/KycPage';
import { LoginPage } from './pages/LoginPage';
import { MoneyMovementPage } from './pages/MoneyMovementPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { RegisterPage } from './pages/RegisterPage';
import { WalletPage } from './pages/WalletPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="wallets/:walletId" element={<WalletPage />} />
          <Route path="money-movement" element={<MoneyMovementPage />} />
          <Route path="kyc" element={<KycPage />} />
          <Route path="dashboard" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
