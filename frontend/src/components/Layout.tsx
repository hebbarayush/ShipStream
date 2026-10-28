import { NavLink, Outlet } from "react-router-dom";

const LINKS = [
  { to: "/", label: "Shipments", end: true },
  { to: "/shipments/new", label: "New shipment" },
  { to: "/hubs", label: "Hubs & routes" },
];

export function Layout() {
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
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
