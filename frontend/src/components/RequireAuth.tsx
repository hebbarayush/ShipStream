import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

export function RequireAuth() {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) return null; // avoid a login-page flash while the first /auth/me call is in flight
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />;

  return <Outlet />;
}
