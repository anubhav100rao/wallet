export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
};

export type User = {
  email: string;
};

export type Wallet = {
  id: string;
  userId?: string;
  currency: string;
  totalBalance: string;
  availableBalance: string;
};

export type Hold = {
  id: string;
  transactionId: string;
  amount: string;
  expiresAt: string;
};

export type MoneyMovementResponse = {
  transactionId: string;
  status: string;
};

export type KycResponse = {
  status: string;
};
