import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { companyId, hasRole, useSession } from "../session";
import { Banner, Button, Card, Empty, Field, PageHeader, TextArea } from "../ui";

export function HomePage() {
  const { me, loading } = useSession();
  if (loading) return <p className="text-muted">Loading your place…</p>;
  if (!me) {
    return (
      <div>
        <PageHeader title="Sign in to see your work." />
        <Link to="/login" className="rounded-md bg-accent px-4 py-2 text-sm font-semibold text-accent-ink">Sign in</Link>
      </div>
    );
  }
  const company = companyId(me);
  return (
    <div>
      <PageHeader eyebrow="Home" title={me.displayName ? `Hello, ${me.displayName}` : "Your FirstRep home"} />
      <div className="grid gap-4 md:grid-cols-2">
        {hasRole(me, "STUDENT") ? (
          <Card>
            <h2 className="font-display text-xl">Student work</h2>
            <p className="mt-2 text-sm text-muted">Enrolments, submissions, certificates, and billing.</p>
            <Link to="/student" className="mt-3 inline-block text-sm font-semibold">Open my work</Link>
          </Card>
        ) : null}
        {!company ? <CreateCompany /> : null}
        {company ? (
          <Card>
            <h2 className="font-display text-xl">{me.memberships.find((item) => item.companyId === company)?.companyName}</h2>
            <p className="mt-2 text-sm text-muted">{me.memberships.find((item) => item.companyId === company)?.roles.join(", ")}</p>
            <Link to={`/company/${company}`} className="mt-3 inline-block text-sm font-semibold">Open the company</Link>
          </Card>
        ) : null}
        {hasRole(me, "PLATFORM_ADMIN") ? (
          <Card>
            <h2 className="font-display text-xl">Platform</h2>
            <p className="mt-2 text-sm text-muted">Moderation, people, appeals, and the audit trail.</p>
            <Link to="/admin" className="mt-3 inline-block text-sm font-semibold">Open admin</Link>
          </Card>
        ) : null}
        <Card>
          <h2 className="font-display text-xl">Account</h2>
          <p className="mt-2 text-sm text-muted">{me.email}</p>
          <Link to="/account" className="mt-3 inline-block text-sm font-semibold">Password, export, deletion</Link>
        </Card>
      </div>
    </div>
  );
}

function CreateCompany() {
  const { refresh } = useSession();
  const [error, setError] = useState<string | null>(null);
  return (
    <Card>
      <h2 className="font-display text-xl">Start a company</h2>
      <p className="mt-2 text-sm text-muted">This makes you the organisation admin. Students stay students as well.</p>
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <form
        className="mt-3 space-y-3"
        onSubmit={async (event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          setError(null);
          try {
            const company = await api<{ id: string }>("/api/v1/companies", {
              method: "POST",
              body: JSON.stringify({ name: data.get("name"), location: data.get("location"), description: data.get("description"), website: "" }),
            });
            localStorage.setItem("firstrep-company", company.id);
            await refresh();
            window.location.assign(`/company/${company.id}`);
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Could not create the company.");
          }
        }}
      >
        <Field label="Company name" name="name" required />
        <Field label="Location" name="location" />
        <TextArea label="Description" name="description" />
        <Button type="submit">Create company</Button>
      </form>
    </Card>
  );
}

export function AccountPage() {
  const { me, refresh } = useSession();
  const navigate = useNavigate();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  if (!me) return <Banner>Sign in to manage the account.</Banner>;
  return (
    <div className="max-w-xl space-y-5">
      <PageHeader title="Account" />
      <p className="text-sm text-muted">{me.email} · {me.status}</p>
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <form
        className="space-y-3"
        onSubmit={async (event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          setError(null);
          try {
            const body = await api<{ message: string }>("/api/v1/me/password", {
              method: "POST",
              body: JSON.stringify({ currentPassword: data.get("current"), newPassword: data.get("next") }),
            });
            setMessage(body.message);
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Could not update the password.");
          }
        }}
      >
        <Field label="Current password" name="current" type="password" required />
        <Field label="New password" name="next" type="password" required hint="At least 10 characters, with a letter and a digit." />
        <Button type="submit">Update password</Button>
      </form>
      <div className="flex flex-wrap gap-3">
        <Button
          tone="quiet"
          onClick={async () => {
            const data = await api<unknown>("/api/v1/me/export");
            const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = "firstrep-export.json";
            anchor.click();
            URL.revokeObjectURL(url);
          }}
        >
          Download my data
        </Button>
        <Button
          tone="danger"
          onClick={async () => {
            const body = await api<{ status: string }>("/api/v1/me/deletion-requests", { method: "POST" });
            setMessage(`Deletion request ${body.status}. An admin completes it; financial rows stay.`);
          }}
        >
          Request deletion
        </Button>
        <Button
          tone="quiet"
          onClick={async () => {
            await api("/api/v1/auth/logout", { method: "POST" });
            await refresh();
            navigate("/");
          }}
        >
          Sign out
        </Button>
      </div>
    </div>
  );
}

export function NoticesPage() {
  const client = useQueryClient();
  const notes = useQuery({
    queryKey: ["notes"],
    queryFn: () => api<{ id: string; title: string; body: string; readAt: string | null; createdAt: string }[]>("/api/v1/notifications"),
  });
  return (
    <div>
      <PageHeader title="Notices" />
      {notes.data?.length === 0 ? <Empty title="No notices yet." /> : null}
      <div className="space-y-3">
        {notes.data?.map((note) => (
          <Card key={note.id}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="font-semibold">{note.title}</h2>
                <p className="mt-1 text-sm">{note.body}</p>
                <p className="mt-1 text-xs text-muted">{note.createdAt}</p>
              </div>
              {note.readAt ? null : (
                <Button
                  tone="quiet"
                  onClick={async () => {
                    await api(`/api/v1/notifications/${note.id}/read`, { method: "POST" });
                    await client.invalidateQueries({ queryKey: ["notes"] });
                  }}
                >
                  Mark read
                </Button>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
