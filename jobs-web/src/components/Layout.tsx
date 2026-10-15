import type { ReactNode } from "react";
import { NavLink } from "react-router";
import { useSession } from "../auth/session";

const LINKS = [
  { to: "/", label: "Overview", end: true },
  { to: "/jobs", label: "Jobs" },
  { to: "/matches", label: "Matches" },
  { to: "/applications", label: "Applications" },
  { to: "/tracking", label: "Tracking" },
  { to: "/profiles", label: "Profiles" },
];

export function Layout({ children }: { children: ReactNode }) {
  const { user, signOut } = useSession();
  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">Jobs Platform</span>
        <nav aria-label="Main">
          {LINKS.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end}>
              {l.label}
            </NavLink>
          ))}
          {user?.role === "ADMIN" && <NavLink to="/admin">Admin</NavLink>}
        </nav>
        <div className="topbar-user">
          <NavLink to="/account">{user?.displayName}</NavLink>
          <button className="secondary small" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </header>
      <main className="content">{children}</main>
    </div>
  );
}
