import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * React only offers error boundaries as class components - no hook equivalent exists for catching
 * render errors. Without this, any uncaught render-time error white-screens the whole app with
 * nothing on screen and nothing in the DOM to debug from (we've hit that exact confusing blank-page
 * failure mode more than once during development, for other reasons - this at least guarantees it's
 * never silent).
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Unhandled error in render tree:", error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="auth-page">
          <div className="card auth-card">
            <h1 style={{ marginBottom: "var(--sp-2)" }}>Something went wrong</h1>
            <p className="subtitle" style={{ marginBottom: "var(--sp-4)" }}>
              This page hit an unexpected error. Reloading usually fixes it.
            </p>
            <button className="btn btn-primary" onClick={() => window.location.reload()}>
              Reload
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
