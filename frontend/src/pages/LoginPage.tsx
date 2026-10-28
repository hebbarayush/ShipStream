import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { ErrorBanner } from "../components/RequestState";

export function LoginPage() {
  const { user, login, loginError, loginPending } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  if (user) {
    const from = (location.state as { from?: Location })?.from?.pathname ?? "/app";
    return <Navigate to={from} replace />;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    await login(username, password);
    navigate("/app", { replace: true });
  }

  return (
    <div className="auth-page">
      <div className="card auth-card">
        <div className="brand" style={{ marginBottom: "var(--sp-6)" }}>
          Ship<span className="brand-mark">Stream</span>
        </div>
        <h1 style={{ marginBottom: "var(--sp-1)" }}>Ops login</h1>
        <p className="subtitle" style={{ marginBottom: "var(--sp-6)" }}>
          Internal access only. Looking to track a package? <a href="/">Go back</a>.
        </p>

        <div className="hint-box" style={{ marginBottom: "var(--sp-6)" }}>
          Demo access: <strong className="mono">demo</strong> / <strong className="mono">demo1234</strong>
          <button
            type="button"
            className="link"
            style={{ marginLeft: "var(--sp-2)" }}
            onClick={() => {
              setUsername("demo");
              setPassword("demo1234");
            }}
          >
            fill in
          </button>
        </div>

        {loginError ? <ErrorBanner error={loginError} /> : null}

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input id="username" required value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={loginPending} style={{ width: "100%" }}>
            {loginPending ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </div>
    </div>
  );
}
