import { api, idempotencyHeaders } from './client';
import type { MoneyMovementResponse } from '../types/wallet';

export type TransferRequest = {
  fromWalletId: string;
  toWalletId: string;
  amount: number;
  currency: string;
};

export type DepositRequest = {
  toWalletId: string;
  amount: number;
  currency: string;
};

export type WithdrawalRequest = {
  fromWalletId: string;
  amount: number;
  currency: string;
};

export async function createTransfer(payload: TransferRequest): Promise<MoneyMovementResponse> {
  const { data } = await api.post('/api/transfers', payload, { headers: idempotencyHeaders() });
  return data;
}

export async function createDeposit(payload: DepositRequest): Promise<MoneyMovementResponse> {
  const { data } = await api.post('/api/deposits', payload, { headers: idempotencyHeaders() });
  return data;
}

export async function createWithdrawal(payload: WithdrawalRequest): Promise<MoneyMovementResponse> {
  const { data } = await api.post('/api/withdrawals', payload, { headers: idempotencyHeaders() });
  return data;
}
