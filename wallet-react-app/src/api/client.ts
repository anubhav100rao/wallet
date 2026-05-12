import axios from 'axios';
import { clearSession, getAccessToken, getRefreshToken, saveSession, getEmail } from '../utils/storage';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  const email = getEmail();
  if (!refreshToken || !email) return null;

  const response = await axios.post(
    `${API_BASE_URL}/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } },
  );

  const tokens = response.data;
  saveSession(tokens, email);
  return tokens.accessToken;
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const isUnauthorized = error.response?.status === 401;

    if (!isUnauthorized || originalRequest?._retry) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      refreshPromise ??= refreshAccessToken().finally(() => {
        refreshPromise = null;
      });

      const newToken = await refreshPromise;
      if (!newToken) throw new Error('Refresh token missing');

      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return api(originalRequest);
    } catch (refreshError) {
      clearSession();
      window.location.href = '/login';
      return Promise.reject(refreshError);
    }
  },
);

export function idempotencyHeaders() {
  return {
    'Idempotency-Key': crypto.randomUUID(),
  };
}

export function toApiError(error: unknown) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.detail ?? error.response?.data?.message ?? error.message;
  }
  if (error instanceof Error) return error.message;
  return 'Something went wrong';
}
