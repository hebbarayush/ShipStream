import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

const LINKS = [
  { to: "/app", label: "Shipments", end: true },
  { to: "/app/shipments/new", label: "New shipment" },
  { to: "/app/hubs", label: "Hubs & routes" },
];

export function Layout() {
  const { user, logout } = useAuth();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          Ship<span className="brand-mark">Stream</span>
        </div>
        <nav className="nav">
          {LINKS.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) => "nav-link" + (isActive ? " active" : "")}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span>{user?.username}</span>
          <button onClick={logout}>Log out</button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
