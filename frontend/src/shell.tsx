import { NavLink, Outlet, Link } from "react-router-dom";
import { ThemeToggle } from "./ui";
import { companyId, hasRole, useSession } from "./session";

const link = "block rounded-md px-3 py-2 text-sm hover:bg-paper";
const active = "bg-paper font-semibold";

export function Shell() {
  const { me, loading } = useSession();
  const company = companyId(me);
  const student = hasRole(me, "STUDENT");
  const admin = hasRole(me, "PLATFORM_ADMIN");
  const companyRoles = me?.memberships.find((item) => item.companyId === company)?.roles ?? [];
  const canEdit = companyRoles.includes("ORG_ADMIN");
  const canReview = companyRoles.includes("ORG_ADMIN") || companyRoles.includes("REVIEWER");
  const canRecruit = companyRoles.includes("ORG_ADMIN") || companyRoles.includes("RECRUITER");
  const canManage = companyRoles.includes("ORG_ADMIN");

  return (
    <div className="min-h-screen">
      <header className="border-b border-line">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to={me ? "/home" : "/"} className="font-display text-xl">
            FirstRep
          </Link>
          <div className="flex items-center gap-3 text-sm">
            <ThemeToggle />
            {loading ? null : me ? (
              <Link to="/account" className="rounded-md border border-line px-2.5 py-1">
                {me.displayName || me.email}
              </Link>
            ) : (
              <Link to="/login" className="rounded-md bg-accent px-3 py-1.5 font-semibold text-accent-ink">
                Sign in
              </Link>
            )}
          </div>
        </div>
      </header>
      <div className="mx-auto grid max-w-6xl gap-6 px-4 py-6 md:grid-cols-[200px_1fr]">
        <nav className="space-y-4 text-sm" aria-label="Primary">
          <div>
            <p className="px-3 text-xs font-semibold uppercase tracking-[0.14em] text-muted">Explore</p>
            <NavLink to="/" className={({ isActive }) => `${link} ${isActive ? active : ""}`} end>
              About
            </NavLink>
            <NavLink to="/projects" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
              Projects
            </NavLink>
            <NavLink to="/pricing" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
              Pricing
            </NavLink>
            <NavLink to="/verify" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
              Verify a certificate
            </NavLink>
          </div>
          {me ? (
            <div>
              <p className="px-3 text-xs font-semibold uppercase tracking-[0.14em] text-muted">Your place</p>
              <NavLink to="/home" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Home
              </NavLink>
              <NavLink to="/account" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Account
              </NavLink>
              <NavLink to="/notifications" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Notices
              </NavLink>
            </div>
          ) : null}
          {student ? (
            <div>
              <p className="px-3 text-xs font-semibold uppercase tracking-[0.14em] text-muted">Student</p>
              <NavLink to="/student" className={({ isActive }) => `${link} ${isActive ? active : ""}`} end>
                My work
              </NavLink>
              <NavLink to="/student/certificates" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Certificates
              </NavLink>
              <NavLink to="/student/billing" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Billing
              </NavLink>
              <NavLink to="/student/profile" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Profile
              </NavLink>
              <NavLink to="/student/shares" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Shares
              </NavLink>
            </div>
          ) : null}
          {company ? (
            <div>
              <p className="px-3 text-xs font-semibold uppercase tracking-[0.14em] text-muted">Company</p>
              <NavLink to={`/company/${company}`} className={({ isActive }) => `${link} ${isActive ? active : ""}`} end>
                Overview
              </NavLink>
              <NavLink to={`/company/${company}/projects`} className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Projects
              </NavLink>
              {canReview ? (
                <NavLink to={`/company/${company}/review`} className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                  Review
                </NavLink>
              ) : null}
              {canRecruit ? (
                <NavLink to={`/company/${company}/candidates`} className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                  Candidates
                </NavLink>
              ) : null}
              {canManage || canEdit ? (
                <NavLink to={`/company/${company}/team`} className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                  Team
                </NavLink>
              ) : null}
            </div>
          ) : null}
          {admin ? (
            <div>
              <p className="px-3 text-xs font-semibold uppercase tracking-[0.14em] text-muted">Platform</p>
              <NavLink to="/admin" className={({ isActive }) => `${link} ${isActive ? active : ""}`} end>
                Overview
              </NavLink>
              <NavLink to="/admin/projects" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Moderation
              </NavLink>
              <NavLink to="/admin/users" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                People
              </NavLink>
              <NavLink to="/admin/appeals" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Appeals
              </NavLink>
              <NavLink to="/admin/audit" className={({ isActive }) => `${link} ${isActive ? active : ""}`}>
                Audit
              </NavLink>
            </div>
          ) : null}
        </nav>
        <main>
          <Outlet />
        </main>
      </div>
      <footer className="border-t border-line">
        <div className="mx-auto flex max-w-6xl flex-wrap gap-4 px-4 py-4 text-sm text-muted">
          <NavLink to="/privacy">Privacy</NavLink>
          <NavLink to="/terms">Terms</NavLink>
          <NavLink to="/pilot">Pilot interest</NavLink>
        </div>
      </footer>
    </div>
  );
}
