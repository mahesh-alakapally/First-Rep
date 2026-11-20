import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { api, money } from "../api";
import type { Page, Plan, ProjectDetail, ProjectSummary } from "../types";
import { Banner, Button, Card, Empty, Field, PageHeader, StatusPill, TextArea } from "../ui";

export function AboutPage() {
  return (
    <div className="space-y-8">
      <PageHeader eyebrow="Vaasa pilot" title="Evidence of work, read by a person." />
      <p className="max-w-2xl text-lg text-muted">
        FirstRep is a small place for early-career candidates to do a real project, receive a rubric-based review, and choose what a company may see.
        A certificate is a record of that review. It is not a job offer, a grade from a model, or a rank against other people.
      </p>
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <h2 className="font-display text-xl">Do the work</h2>
          <p className="mt-2 text-sm text-muted">Enrol in a published project, submit a file or a public link, and keep the draft until you are ready.</p>
        </Card>
        <Card>
          <h2 className="font-display text-xl">A person decides</h2>
          <p className="mt-2 text-sm text-muted">A draft assessment can help a reviewer. You see the scores only after a human releases them.</p>
        </Card>
        <Card>
          <h2 className="font-display text-xl">You choose the share</h2>
          <p className="mt-2 text-sm text-muted">A company sees a package only after you consent, and only until you revoke it.</p>
        </Card>
      </div>
      <div className="flex flex-wrap gap-3">
        <Link to="/projects" className="rounded-md bg-accent px-4 py-2 text-sm font-semibold text-accent-ink">Browse projects</Link>
        <Link to="/register" className="rounded-md border border-line px-4 py-2 text-sm font-semibold">Create a student account</Link>
      </div>
    </div>
  );
}

export function PricingPage() {
  const plans = useQuery({ queryKey: ["plans"], queryFn: () => api<Plan[]>("/api/v1/billing/plans") });
  return (
    <div>
      <PageHeader eyebrow="Billing" title="Two free projects, then a subscription." />
      <p className="mb-6 max-w-2xl text-muted">
        Each student can enrol in two projects without paying. A further enrolment waits for an active subscription. In this local stack the payment step is a clearly marked simulator: the browser return does not grant access, the confirm action does.
      </p>
      <div className="grid gap-4 md:grid-cols-2">
        {(plans.data ?? []).map((plan) => (
          <Card key={plan.code}>
            <h2 className="font-display text-2xl">{plan.name}</h2>
            <p className="mt-2 text-3xl">{money(plan.priceCents, plan.currency)}</p>
            <p className="text-sm text-muted">per {plan.interval.toLowerCase()}</p>
          </Card>
        ))}
      </div>
      {plans.isError ? <Banner tone="danger">Plans could not be loaded.</Banner> : null}
    </div>
  );
}

export function CataloguePage() {
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const difficulty = params.get("difficulty") ?? "";
  const projects = useQuery({
    queryKey: ["projects", q, difficulty],
    queryFn: () => api<Page<ProjectSummary>>(`/api/v1/projects?q=${encodeURIComponent(q)}&difficulty=${encodeURIComponent(difficulty)}&size=20`),
  });
  return (
    <div>
      <PageHeader eyebrow="Catalogue" title="Published projects" />
      <form
        className="mb-5 grid gap-3 md:grid-cols-[1fr_180px_auto]"
        onSubmit={(event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          setParams({ q: String(data.get("q") ?? ""), difficulty: String(data.get("difficulty") ?? "") });
        }}
      >
        <Field label="Search" name="q" defaultValue={q} placeholder="Role, title, or skill" />
        <label className="block space-y-1 text-sm">
          <span className="font-semibold">Difficulty</span>
          <select name="difficulty" defaultValue={difficulty} className="w-full rounded-md border border-line bg-raised px-3 py-2">
            <option value="">Any</option>
            <option value="INTRODUCTORY">Introductory</option>
            <option value="INTERMEDIATE">Intermediate</option>
            <option value="ADVANCED">Advanced</option>
          </select>
        </label>
        <div className="flex items-end"><Button type="submit">Search</Button></div>
      </form>
      {projects.data?.items.length === 0 ? <Empty title="No published projects match.">Try a broader search, or check back after moderation.</Empty> : null}
      <div className="space-y-3">
        {projects.data?.items.map((project) => (
          <Link key={project.id} to={`/projects/${project.id}`} className="block">
            <Card>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="font-display text-2xl">{project.title}</h2>
                  <p className="text-sm text-muted">{project.companyName} · {project.roleTag} · {project.effortHours} hours</p>
                </div>
                <StatusPill value={project.difficulty} />
              </div>
              <p className="mt-3 text-sm">{project.summary}</p>
              <p className="mt-2 text-xs text-muted">{project.skills.join(" · ")}{project.recruitmentLinked ? " · Recruitment linked" : ""}</p>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function ProjectPage() {
  const { projectId = "" } = useParams();
  const project = useQuery({ queryKey: ["project", projectId], queryFn: () => api<ProjectDetail>(`/api/v1/projects/${projectId}`) });
  if (project.isError) return <Banner tone="danger">That project is not published.</Banner>;
  if (!project.data) return <p className="text-muted">Loading the brief…</p>;
  const item = project.data;
  return (
    <div className="space-y-5">
      <PageHeader eyebrow={item.summary.companyName} title={item.summary.title}>
        <Link to="/projects" className="text-sm text-muted">Back to projects</Link>
      </PageHeader>
      <p>{item.summary.summary}</p>
      <p className="whitespace-pre-wrap text-sm text-muted">{item.description}</p>
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <h2 className="font-semibold">What to hand in</h2>
          <ul className="mt-2 space-y-2 text-sm">
            {item.deliverables.map((row) => (
              <li key={row.title}><span className="font-semibold">{row.title}.</span> {row.description}</li>
            ))}
          </ul>
        </Card>
        <Card>
          <h2 className="font-semibold">How it is read</h2>
          <ul className="mt-2 space-y-2 text-sm">
            {item.rubric.map((row) => (
              <li key={row.id}><span className="font-semibold">{row.title}</span> · up to {row.maxScore}. {row.description}</li>
            ))}
          </ul>
        </Card>
      </div>
      <p className="text-sm text-muted">{item.eligibility}</p>
      <p className="text-sm text-muted">{item.certificateConditions}</p>
      {item.enrollmentId ? (
        <Link to={`/student/enrollments/${item.enrollmentId}`} className="inline-block rounded-md bg-accent px-4 py-2 text-sm font-semibold text-accent-ink">Open your work</Link>
      ) : (
        <Enroll projectId={item.summary.id} />
      )}
    </div>
  );
}

function Enroll({ projectId }: { projectId: string }) {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="space-y-3">
      {error ? <Banner tone="warn">{error}</Banner> : null}
      {message ? <Banner>{message}</Banner> : null}
      <Button
        onClick={async () => {
          setError(null);
          try {
            const enrollment = await api<{ id: string }>(`/api/v1/projects/${projectId}/enroll`, { method: "POST" });
            window.location.assign(`/student/enrollments/${enrollment.id}`);
          } catch (caught) {
            const text = caught instanceof Error ? caught.message : "Could not enrol.";
            setError(text);
            if (text.toLowerCase().includes("premium") || text.toLowerCase().includes("subscription")) {
              setMessage("Two free enrolments are already used. Billing can grant access after the simulator confirm, not from this page alone.");
            }
          }
        }}
      >
        Enrol
      </Button>
    </div>
  );
}

export function VerifyPage() {
  const [params, setParams] = useSearchParams();
  const code = params.get("code") ?? "";
  const result = useQuery({
    queryKey: ["verify", code],
    enabled: code.length > 4,
    queryFn: () => api<Record<string, string>>(`/api/v1/certificates/${encodeURIComponent(code)}/verify`),
  });
  return (
    <div>
      <PageHeader title="Check a certificate" />
      <form className="mb-4 max-w-md" onSubmit={(event) => { event.preventDefault(); setParams({ code: String(new FormData(event.currentTarget).get("code") ?? "") }); }}>
        <Field label="Verification code" name="code" defaultValue={code} />
        <div className="mt-3"><Button type="submit">Check</Button></div>
      </form>
      {result.data ? (
        <Card>
          <p className="font-display text-2xl">{result.data.studentDisplayName}</p>
          <p className="mt-1">{result.data.projectTitle} · {result.data.companyName}</p>
          <p className="mt-2 text-sm text-muted">Issued {result.data.issuedAt}</p>
          <p className="mt-3 text-sm">{result.data.statement}</p>
        </Card>
      ) : null}
      {result.isError ? <Banner tone="danger">No certificate matches that code.</Banner> : null}
    </div>
  );
}

export function LegalPage({ title, children }: { title: string; children: string }) {
  return (
    <div>
      <PageHeader title={title} />
      <p className="max-w-2xl whitespace-pre-wrap text-sm leading-6 text-muted">{children}</p>
    </div>
  );
}

export function PilotPage() {
  const [done, setDone] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="max-w-xl">
      <PageHeader title="Pilot interest" />
      {done ? <Banner>{done}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <form
        className="space-y-3"
        onSubmit={async (event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          setError(null);
          try {
            const body = await api<{ message: string }>("/api/v1/pilot-interest", {
              method: "POST",
              body: JSON.stringify({
                name: data.get("name"),
                email: data.get("email"),
                organisation: data.get("organisation"),
                message: data.get("message"),
              }),
            });
            setDone(body.message);
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Could not save that.");
          }
        }}
      >
        <Field label="Name" name="name" required />
        <Field label="Email" name="email" type="email" required />
        <Field label="Organisation" name="organisation" required />
        <TextArea label="Note" name="message" />
        <Button type="submit">Send</Button>
      </form>
    </div>
  );
}
