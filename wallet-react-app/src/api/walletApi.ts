import { api } from './client';
import type { Hold, Wallet } from '../types/wallet';

export async function getWallet(walletId: string): Promise<Wallet> {
  const { data } = await api.get<Wallet>(`/api/wallets/${walletId}`);
  return data;
}

export async function getActiveHolds(walletId: string): Promise<Hold[]> {
  const { data } = await api.get<Hold[]>(`/api/wallets/${walletId}/holds/active`);
  return data;
}
