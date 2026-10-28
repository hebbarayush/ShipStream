import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, type ReactNode } from "react";
import { api, ApiError } from "../api/client";
import type { AuthUser } from "../api/types";

interface AuthContextValue {
  user: AuthUser | undefined;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<AuthUser>;
  loginError: unknown;
  loginPending: boolean;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  // A 401 here just means "not logged in" - that's a normal state for this query, not an error to retry.
  // Cached value is AuthUser | null, never undefined: TanStack Query treats setQueryData(key, undefined)
  // as a no-op (it does *not* clear the cache), so logout couldn't use undefined to mean "logged out".
  const meQuery = useQuery<AuthUser | null>({
    queryKey: ["auth", "me"],
    queryFn: api.me,
    retry: false,
    throwOnError: false,
  });

  const loginMutation = useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) => api.login(username, password),
    onSuccess: (user) => queryClient.setQueryData(["auth", "me"], user),
  });

  const logoutMutation = useMutation({
    mutationFn: api.logout,
    onSuccess: () => queryClient.setQueryData(["auth", "me"], null),
  });

  const user = meQuery.error instanceof ApiError ? undefined : (meQuery.data ?? undefined);

  const value: AuthContextValue = {
    user,
    isLoading: meQuery.isLoading,
    login: (username, password) => loginMutation.mutateAsync({ username, password }),
    loginError: loginMutation.error,
    loginPending: loginMutation.isPending,
    logout: () => logoutMutation.mutate(),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
