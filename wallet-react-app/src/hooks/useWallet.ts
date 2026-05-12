import { useQuery } from '@tanstack/react-query';
import { getActiveHolds, getWallet } from '../api/walletApi';

export function useWallet(walletId: string) {
  return useQuery({
    queryKey: ['wallet', walletId],
    queryFn: () => getWallet(walletId),
    enabled: Boolean(walletId),
  });
}

export function useActiveHolds(walletId: string) {
  return useQuery({
    queryKey: ['wallet', walletId, 'holds'],
    queryFn: () => getActiveHolds(walletId),
    enabled: Boolean(walletId),
  });
}
