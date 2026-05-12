import { useMemo, useState } from 'react';
import { login as loginRequest, logout as logoutRequest } from '../api/authApi';
import { AuthContext } from './auth-state';
import { clearSession, getEmail, getRefreshToken, saveSession } from '../utils/storage';
import type { User } from '../types/wallet';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    const email = getEmail();
    return email ? { email } : null;
  });

  async function login(email: string, password: string) {
    const tokens = await loginRequest(email, password);
    saveSession(tokens, email);
    setUser({ email });
  }

  async function logout() {
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) await logoutRequest(refreshToken);
    } finally {
      clearSession();
      setUser(null);
    }
  }

  const value = useMemo(
    () => ({ user, isAuthenticated: Boolean(user), login, logout }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
