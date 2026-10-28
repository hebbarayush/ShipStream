import type { ReactNode } from "react";
import { ApiError } from "../api/client";

export function ErrorBanner({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? error.message : "Something went wrong.";
  return <div className="error-banner">{message}</div>;
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty-state">{children}</div>;
}

export function Loading() {
  return <p className="muted">Loading…</p>;
}
