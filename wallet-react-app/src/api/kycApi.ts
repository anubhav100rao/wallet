import { api } from './client';
import type { KycResponse } from '../types/wallet';

export async function submitKyc(): Promise<KycResponse> {
  const { data } = await api.post<KycResponse>('/kyc/submit');
  return data;
}
