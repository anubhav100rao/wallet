import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createDeposit, createTransfer, createWithdrawal } from '../api/transactionApi';

export function useTransfer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createTransfer,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['wallet'] }),
  });
}

export function useDeposit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createDeposit,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['wallet'] }),
  });
}

export function useWithdrawal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createWithdrawal,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['wallet'] }),
  });
}
