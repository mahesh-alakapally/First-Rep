import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, label } from "../api";
import type { Page, ProjectDetail, ProjectSummary } from "../types";
import { Banner, Button, Card, Empty, Field, PageHeader, StatusPill, TextArea } from "../ui";

export function AdminHome() {
  const metrics = useQuery({ queryKey: ["metrics"], queryFn: () => api<Record<string, unknown>>("/api/v1/admin/reports/pilot-metrics") });
  const requests = useQuery({ queryKey: ["deletions"], queryFn: () => api<{ id: string; userId: string; type: string; status: string }[]>("/api/v1/admin/data-requests") });
  const interest = useQuery({ queryKey: ["interest"], queryFn: () => api<{ id: string; name: string; organisation: string; message: string }[]>("/api/v1/admin/interest") });
  const client = useQueryClient();
  return (
    <div className="space-y-5">
      <PageHeader title="Platform" />
      {metrics.data ? (
        <div className="grid gap-3 md:grid-cols-3">
          {Object.entries(metrics.data).filter(([, value]) => typeof value !== "object").map(([key, value]) => (
            <Card key={key}><p className="text-xs uppercase tracking-wide text-muted">{label(key)}</p><p className="font-display text-2xl">{String(value)}</p></Card>
          ))}
        </div>
      ) : null}
      <section>
        <h2 className="mb-2 font-semibold">Open deletion requests</h2>
        {requests.data?.length === 0 ? <Empty title="None open." /> : null}
        {requests.data?.map((request) => (
          <Card key={request.id} className="mb-2">
            <p className="text-sm">{request.type} · {request.userId}</p>
            <Button className="mt-2" tone="danger" onClick={async () => {
              await api(`/api/v1/admin/data-requests/${request.id}/complete`, { method: "POST" });
              await client.invalidateQueries({ queryKey: ["deletions"] });
            }}>Complete anonymisation</Button>
          </Card>
        ))}
      </section>
      <section>
        <h2 className="mb-2 font-semibold">Pilot notes</h2>
        {interest.data?.length === 0 ? <Empty title="No notes yet." /> : null}
        {interest.data?.map((item) => <p key={item.id} className="text-sm">{item.name} · {item.organisation} · {item.message}</p>)}
      </section>
    </div>
  );
}

export function ModerationPage() {
  const projects = useQuery({ queryKey: ["moderation"], queryFn: () => api<ProjectSummary[]>("/api/v1/admin/projects") });
  return (
    <div>
      <PageHeader title="Moderation" />
      {projects.data?.length === 0 ? <Empty title="No projects outside draft." /> : null}
      <div className="space-y-3">
        {projects.data?.map((project) => (
          <Link key={project.id} to={`/admin/projects/${project.id}`} className="block">
            <Card>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="font-display text-xl">{project.title}</h2>
                  <p className="text-sm text-muted">{project.companyName}</p>
                </div>
                <StatusPill value={label(project.status)} />
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function ModerationDetail() {
  const { projectId = "" } = useParams();
  const client = useQueryClient();
  const project = useQuery({ queryKey: ["admin-project", projectId], queryFn: () => api<ProjectDetail>(`/api/v1/admin/projects/${projectId}`) });
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  if (!project.data) return <p className="text-muted">Loading…</p>;
  const item = project.data;
  return (
    <div className="space-y-4">
      <PageHeader eyebrow={item.summary.companyName} title={item.summary.title}>
        <Link to="/admin/projects" className="text-sm text-muted">Queue</Link>
      </PageHeader>
      <StatusPill value={label(item.summary.status)} />
      <p className="text-sm">{item.description}</p>
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <form className="max-w-xl space-y-3" onSubmit={async (event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        setError(null);
        try {
          await api(`/api/v1/admin/projects/${projectId}/${data.get("action")}`, {
            method: "POST",
            body: JSON.stringify({ note: data.get("note") }),
          });
          setMessage("Saved.");
          await client.invalidateQueries({ queryKey: ["admin-project", projectId] });
          await client.invalidateQueries({ queryKey: ["moderation"] });
        } catch (caught) {
          setError(caught instanceof Error ? caught.message : "Could not moderate.");
        }
      }}>
        <label className="block text-sm font-semibold">Action
          <select name="action" className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2">
            <option value="approve">Approve and publish</option>
            <option value="reject">Send back to draft</option>
            <option value="pause">Pause</option>
            <option value="close">Close and restore unused free credit</option>
            <option value="archive">Archive and restore unused free credit</option>
          </select>
        </label>
        <TextArea label="Note" name="note" />
        <Button type="submit">Apply</Button>
      </form>
    </div>
  );
}

export function PeoplePage() {
  const [q, setQ] = useState("");
  const users = useQuery({ queryKey: ["users", q], queryFn: () => api<Page<{ id: string; email: string; status: string; platformRoles: string[] }>>(`/api/v1/admin/users?q=${encodeURIComponent(q)}`) });
  const client = useQueryClient();
  return (
    <div>
      <PageHeader title="People" />
      <form className="mb-4 max-w-md" onSubmit={(event) => { event.preventDefault(); setQ(String(new FormData(event.currentTarget).get("q") ?? "")); }}>
        <Field label="Search email" name="q" />
        <Button className="mt-2" type="submit">Search</Button>
      </form>
      <div className="space-y-2">
        {users.data?.items.map((user) => (
          <Card key={user.id}>
            <p className="font-semibold">{user.email}</p>
            <p className="text-sm text-muted">{label(user.status)} · {user.platformRoles.join(", ") || "Company roles only"}</p>
            <div className="mt-2 flex gap-2">
              {["ACTIVE", "SUSPENDED"].map((status) => (
                <Button key={status} tone="quiet" onClick={async () => {
                  await api(`/api/v1/admin/users/${user.id}/status`, { method: "POST", body: JSON.stringify({ status }) });
                  await client.invalidateQueries({ queryKey: ["users", q] });
                }}>{label(status)}</Button>
              ))}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

export function AppealsPage() {
  const appeals = useQuery({ queryKey: ["appeals"], queryFn: () => api<{ id: string; submissionId: string; reason: string; status: string }[]>("/api/v1/admin/appeals") });
  const client = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="space-y-3">
      <PageHeader title="Appeals" />
      {error ? <Banner tone="danger">{error}</Banner> : null}
      {appeals.data?.length === 0 ? <Empty title="No open appeals." /> : null}
      {appeals.data?.map((appeal) => (
        <Card key={appeal.id}>
          <p className="text-sm">{appeal.reason}</p>
          <form className="mt-3 space-y-2" onSubmit={async (event) => {
            event.preventDefault();
            const data = new FormData(event.currentTarget);
            setError(null);
            try {
              await api(`/api/v1/admin/appeals/${appeal.id}/resolve`, { method: "POST", body: JSON.stringify({ status: data.get("status"), note: data.get("note") }) });
              await client.invalidateQueries({ queryKey: ["appeals"] });
            } catch (caught) {
              setError(caught instanceof Error ? caught.message : "Could not resolve.");
            }
          }}>
            <label className="block text-sm">Outcome
              <select name="status" className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2">
                <option value="UPHELD">Uphold and return to review</option>
                <option value="DENIED">Deny</option>
              </select>
            </label>
            <TextArea label="Note" name="note" required />
            <Button type="submit">Resolve</Button>
          </form>
        </Card>
      ))}
    </div>
  );
}

export function AuditPage() {
  const [action, setAction] = useState("");
  const events = useQuery({ queryKey: ["audit", action], queryFn: () => api<Page<{ id: number; action: string; resourceType: string; resourceId: string; detail: string; createdAt: string }>>(`/api/v1/admin/audit-events?action=${encodeURIComponent(action)}`) });
  const subs = useQuery({ queryKey: ["subs"], queryFn: () => api<{ id: string; plan: string; status: string; provider: string }[]>("/api/v1/admin/subscriptions") });
  const certs = useQuery({ queryKey: ["admin-certs"], queryFn: () => api<{ id: string; code: string; project: string; student: string }[]>("/api/v1/admin/certificates") });
  return (
    <div className="space-y-6">
      <PageHeader title="Audit" />
      <form onSubmit={(event) => { event.preventDefault(); setAction(String(new FormData(event.currentTarget).get("action") ?? "")); }}>
        <Field label="Action contains" name="action" placeholder="PROJECT_APPROVE" />
        <Button className="mt-2" type="submit">Filter</Button>
      </form>
      <div className="space-y-2">
        {events.data?.items.map((event) => (
          <p key={event.id} className="text-sm">{event.createdAt} · {event.action} · {event.resourceType} {event.resourceId} · {event.detail}</p>
        ))}
      </div>
      <section>
        <h2 className="mb-2 font-semibold">Subscriptions</h2>
        {subs.data?.length === 0 ? <Empty title="No subscriptions." /> : null}
        {subs.data?.map((row) => <p key={row.id} className="text-sm">{row.plan} · {row.provider} · {label(row.status)}</p>)}
      </section>
      <section>
        <h2 className="mb-2 font-semibold">Certificates</h2>
        {certs.data?.map((row) => <p key={row.id} className="text-sm">{row.student} · {row.project} · {row.code}</p>)}
      </section>
    </div>
  );
}
