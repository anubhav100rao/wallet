# Wallet React Learning App

A Vite + React + TypeScript frontend built to learn React concepts against the Banking Wallet backend.

## What this app covers

- Login/register/logout
- JWT access token storage
- Refresh-token retry flow through Axios interceptor
- Protected routes
- Dashboard layout
- Wallet details using route params
- Active holds query
- Transfer/deposit/withdraw forms
- Idempotency-Key header for money-moving requests
- KYC submit mutation
- React Query caching and invalidation
- Reusable components and custom hooks

## Backend expected

Start your backend first. The README says the backend runs locally at:

```txt
http://localhost:8081
```

Useful seeded users:

```txt
alice@wallet.local / password123
bob@wallet.local / password123
charlie@wallet.local / password123
```

Seeded wallets used by the frontend:

```txt
Alice USD source wallet:      10000000-0000-0000-0000-000000000001
Bob USD destination wallet:   10000000-0000-0000-0000-000000000002
Charlie INR wallet:           10000000-0000-0000-0000-000000000003
```

## Run frontend

```bash
cp .env.example .env
npm install
npm run dev
```

Open:

```txt
http://localhost:5173
```

## Configure API URL

By default, local development uses the Vite dev-server proxy, so `.env` can leave the API base URL empty:

```bash
VITE_API_BASE_URL=
```

Set `VITE_API_BASE_URL=http://localhost:8081` only if the backend has CORS enabled for the frontend origin.

## Project structure

```txt
src/
  api/          Axios client + API functions
  components/   Reusable UI, forms, wallet picker
  context/      Auth context
  hooks/        React Query custom hooks
  layouts/      App shell/sidebar
  pages/        Route pages
  routes/       Protected route wrapper
  types/        Shared TypeScript types
  utils/        Storage helpers
```

## Learning path

1. Start with `LoginPage.tsx` to understand controlled forms and auth state.
2. Read `AuthContext.tsx` to understand app-wide auth with Context.
3. Read `ProtectedRoute.tsx` to understand route guards.
4. Read `client.ts` to understand Axios interceptors and refresh-token retry.
5. Read `useWallet.ts` to understand query-based server state.
6. Read `MoneyMovementForm.tsx` to understand mutations and idempotency headers.
7. Read `App.tsx` to understand route composition.

## Notes

This is intentionally frontend-only. The Vite dev server proxies `/auth`, `/kyc`, and `/api` requests to the Spring Boot backend.
