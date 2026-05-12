import type { AuthTokens } from '../types/wallet';

const ACCESS_TOKEN_KEY = 'wallet.accessToken';
const REFRESH_TOKEN_KEY = 'wallet.refreshToken';
const EMAIL_KEY = 'wallet.email';

export function saveSession(tokens: AuthTokens, email: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  localStorage.setItem(EMAIL_KEY, email);
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getEmail() {
  return localStorage.getItem(EMAIL_KEY);
}

export function clearSession() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(EMAIL_KEY);
}
