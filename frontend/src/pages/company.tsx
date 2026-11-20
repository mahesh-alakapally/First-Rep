import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, label } from "../api";
import { useSession } from "../session";
import type { ProjectDetail, Submission } from "../types";
import { Banner, Button, Card, Empty, Field, PageHeader, StatusPill, TextArea } from "../ui";

export function CompanyHome() {
  const { companyId = "" } = useParams();
  const { me, refresh } = useSession();
  const company = useQuery({ queryKey: ["company", companyId], queryFn: () => api<{ id: string; name: string; location: string; description: string; website: string; status: string }>(`/api/v1/companies/${companyId}`) });
  const roles = me?.memberships.find((item) => item.companyId === companyId)?.roles ?? [];
  const [message, setMessage] = useState<string | null>(null);
  if (!company.data) return <p className="text-muted">Loading the company…</p>;
  return (
    <div className="space-y-4">
      <PageHeader eyebrow={roles.join(", ") || "Company"} title={company.data.name}>
        <Link to="/home" className="text-sm text-muted">Home</Link>
      </PageHeader>
      <p className="text-sm text-muted">{company.data.location}</p>
      <p className="max-w-2xl text-sm">{company.data.description}</p>
      {me && me.memberships.length > 1 ? (
        <label className="block max-w-sm text-sm font-semibold">Working company
          <select
            className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2"
            value={companyId}
            onChange={(event) => {
              localStorage.setItem("firstrep-company", event.target.value);
              window.location.assign(`/company/${event.target.value}`);
            }}
          >
            {me.memberships.map((item) => <option key={item.companyId} value={item.companyId}>{item.companyName}</option>)}
          </select>
        </label>
      ) : null}
      {roles.includes("ORG_ADMIN") ? (
        <form className="max-w-xl space-y-3" onSubmit={async (event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          await api(`/api/v1/companies/${companyId}`, {
            method: "PATCH",
            body: JSON.stringify({ name: data.get("name"), location: data.get("location"), description: data.get("description"), website: data.get("website") }),
          });
          setMessage("Company details saved.");
          await refresh();
        }}>
          <Field label="Name" name="name" defaultValue={company.data.name} required />
          <Field label="Location" name="location" defaultValue={company.data.location ?? ""} />
          <TextArea label="Description" name="description" defaultValue={company.data.description ?? ""} />
          <Field label="Website" name="website" defaultValue={company.data.website ?? ""} />
          <Button type="submit">Save company</Button>
        </form>
      ) : null}
      {message ? <Banner>{message}</Banner> : null}
    </div>
  );
}

export function CompanyProjects() {
  const { companyId = "" } = useParams();
  const projects = useQuery({ queryKey: ["company-projects", companyId], queryFn: () => api<ProjectDetail[]>(`/api/v1/companies/${companyId}/projects`) });
  return (
    <div>
      <PageHeader title="Projects">
        <Link to={`/company/${companyId}/projects/new`} className="rounded-md bg-accent px-3 py-2 text-sm font-semibold text-accent-ink">New project</Link>
      </PageHeader>
      {projects.data?.length === 0 ? <Empty title="No projects yet.">Write a brief, then send it for approval.</Empty> : null}
      <div className="space-y-3">
        {projects.data?.map((project) => (
          <Link key={project.summary.id} to={`/company/${companyId}/projects/${project.summary.id}`} className="block">
            <Card>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="font-display text-2xl">{project.summary.title}</h2>
                  <p className="text-sm text-muted">{project.summary.summary}</p>
                </div>
                <StatusPill value={label(project.summary.status)} />
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function ProjectEditor({ mode }: { mode: "new" | "edit" }) {
  const { companyId = "", projectId = "" } = useParams();
  const client = useQueryClient();
  const existing = useQuery({
    queryKey: ["company-project", projectId],
    enabled: mode === "edit",
    queryFn: () => api<ProjectDetail[]>(`/api/v1/companies/${companyId}/projects`).then((rows) => rows.find((row) => row.summary.id === projectId) ?? null),
  });
  const skills = useQuery({ queryKey: ["skills"], queryFn: () => api<{ name: string }[]>("/api/v1/skills") });
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const current = existing.data;
  if (mode === "edit" && existing.isLoading) return <p className="text-muted">Loading the project…</p>;
  if (mode === "edit" && !current) return <Banner tone="danger">That project is not in this company.</Banner>;
  return (
    <form className="max-w-2xl space-y-3" onSubmit={async (event) => {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      const firstDeliverable = { title: data.get("deliverableTitle"), description: data.get("deliverable"), required: true };
      const firstCriterion = { title: data.get("criterionTitle"), description: data.get("criterion"), maxScore: Number(data.get("maxScore")) };
      const deliverables = current && current.deliverables.length > 0
        ? current.deliverables.map((item, index) => index === 0 ? firstDeliverable : item)
        : [firstDeliverable];
      const criteria = current && current.rubric.length > 0
        ? current.rubric.map((item, index) => index === 0 ? firstCriterion : { title: item.title, description: item.description, maxScore: item.maxScore })
        : [firstCriterion];
      const extraQuestions = (current?.skillsQuestions ?? []).slice(1).map((item) => ({
        prompt: item.prompt, questionType: item.questionType, options: item.options, correctOption: item.correctOption,
      }));
      const body = {
        title: data.get("title"),
        summary: data.get("summary"),
        description: data.get("description"),
        roleTag: data.get("roleTag"),
        difficulty: data.get("difficulty"),
        effortHours: Number(data.get("effortHours")),
        eligibility: data.get("eligibility"),
        certificateConditions: data.get("certificateConditions"),
        recruitmentLinked: data.get("recruitment") === "on",
        skillNames: skills.data?.filter((skill) => data.get(`skill-${skill.name}`) === "on").map((skill) => skill.name) ?? [],
        deliverables,
        criteria,
        skillsQuestions: data.get("question")
          ? [{ prompt: data.get("question"), questionType: "MULTIPLE_CHOICE", options: [String(data.get("a")), String(data.get("b"))], correctOption: data.get("correct") }, ...extraQuestions]
          : extraQuestions,
      };
      setError(null);
      try {
        const saved = mode === "new"
          ? await api<ProjectDetail>(`/api/v1/companies/${companyId}/projects`, { method: "POST", body: JSON.stringify(body) })
          : await api<ProjectDetail>(`/api/v1/companies/${companyId}/projects/${projectId}`, { method: "PATCH", body: JSON.stringify(body) });
        if (data.get("submit") === "yes") {
          await api(`/api/v1/companies/${companyId}/projects/${saved.summary.id}/submit-for-approval`, { method: "POST" });
          setMessage("Sent for platform approval.");
        } else {
          setMessage("Saved as a draft.");
        }
        await client.invalidateQueries({ queryKey: ["company-projects", companyId] });
        if (mode === "new") window.location.assign(`/company/${companyId}/projects/${saved.summary.id}`);
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : "Could not save.");
      }
    }}>
      <PageHeader title={mode === "new" ? "New project" : current!.summary.title}>
        <Link to={`/company/${companyId}/projects`} className="text-sm text-muted">All projects</Link>
      </PageHeader>
      {current ? <StatusPill value={label(current.summary.status)} /> : null}
      {current?.moderationNote ? <Banner>{current.moderationNote}</Banner> : null}
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <Field label="Title" name="title" required defaultValue={current?.summary.title} />
      <Field label="Summary" name="summary" required defaultValue={current?.summary.summary} />
      <TextArea label="Brief" name="description" required defaultValue={current?.description} />
      <Field label="Role" name="roleTag" required defaultValue={current?.summary.roleTag ?? "Software development"} />
      <label className="block text-sm font-semibold">Difficulty
        <select name="difficulty" defaultValue={current?.summary.difficulty ?? "INTRODUCTORY"} className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2">
          <option value="INTRODUCTORY">Introductory</option>
          <option value="INTERMEDIATE">Intermediate</option>
          <option value="ADVANCED">Advanced</option>
        </select>
      </label>
      <Field label="Effort hours" name="effortHours" type="number" min={1} defaultValue={current?.summary.effortHours ?? 8} />
      <Field label="Eligibility" name="eligibility" defaultValue={current?.eligibility ?? "Open to pilot students."} />
      <Field label="Certificate conditions" name="certificateConditions" defaultValue={current?.certificateConditions ?? "Issued only after a human reviewer approves."} />
      <label className="flex gap-2 text-sm"><input type="checkbox" name="recruitment" defaultChecked={current?.summary.recruitmentLinked} /> Recruitment linked</label>
      <fieldset>
        <legend className="text-sm font-semibold">Skills</legend>
        {skills.data?.map((skill) => (
          <label key={skill.name} className="mt-1 flex gap-2 text-sm">
            <input type="checkbox" name={`skill-${skill.name}`} defaultChecked={current?.summary.skills.includes(skill.name)} /> {skill.name}
          </label>
        ))}
      </fieldset>
      <Field label="Deliverable title" name="deliverableTitle" required defaultValue={current?.deliverables[0]?.title ?? "Write-up"} />
      <TextArea label="Deliverable" name="deliverable" required defaultValue={current?.deliverables[0]?.description ?? "What the student hands in."} />
      <Field label="Criterion" name="criterionTitle" required defaultValue={current?.rubric[0]?.title ?? "Clear result"} />
      <TextArea label="What good looks like" name="criterion" required defaultValue={current?.rubric[0]?.description ?? "A reviewer can see the work."} />
      <Field label="Max score" name="maxScore" type="number" min={1} max={20} defaultValue={current?.rubric[0]?.maxScore ?? 5} />
      <TextArea label="Optional skills question" name="question" defaultValue={current?.skillsQuestions[0]?.prompt ?? ""} />
      <Field label="Option A" name="a" defaultValue={current?.skillsQuestions[0]?.options[0] ?? ""} />
      <Field label="Option B" name="b" defaultValue={current?.skillsQuestions[0]?.options[1] ?? ""} />
      <Field label="Correct option (A or B)" name="correct" defaultValue={current?.skillsQuestions[0]?.correctOption ?? "A"} />
      <div className="flex gap-2">
        <Button type="submit" name="intent" onClick={(event) => { (event.currentTarget.form!.elements.namedItem("submit") as HTMLInputElement).value = "no"; }}>Save</Button>
        {current?.summary.status === "DRAFT" || mode === "new" ? (
          <Button type="submit" tone="quiet" onClick={(event) => { (event.currentTarget.form!.elements.namedItem("submit") as HTMLInputElement).value = "yes"; }}>Send for approval</Button>
        ) : null}
      </div>
      <input type="hidden" name="submit" defaultValue="no" />
    </form>
  );
}

export function ReviewQueue() {
  const { companyId = "" } = useParams();
  const queue = useQuery({ queryKey: ["review", companyId], queryFn: () => api<{ submissionId: string; projectTitle: string; studentId: string; jobStatus: string; assessmentStatus: string; submittedAt: string }[]>(`/api/v1/companies/${companyId}/review-queue`) });
  return (
    <div>
      <PageHeader title="Review queue" />
      {queue.data?.length === 0 ? <Empty title="Nothing is waiting.">Submitted work appears here before it is released.</Empty> : null}
      <div className="space-y-3">
        {queue.data?.map((item) => (
          <Link key={item.submissionId} to={`/company/${companyId}/review/${item.submissionId}`} className="block">
            <Card>
              <h2 className="font-semibold">{item.projectTitle}</h2>
              <p className="text-sm text-muted">{label(item.assessmentStatus || item.jobStatus)} · {item.submittedAt}</p>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function ReviewDetail() {
  const { companyId = "", submissionId = "" } = useParams();
  const client = useQueryClient();
  const submission = useQuery({ queryKey: ["submission", submissionId], queryFn: () => api<Submission>(`/api/v1/submissions/${submissionId}`) });
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  if (!submission.data) return <p className="text-muted">Loading the submission…</p>;
  const item = submission.data;
  return (
    <div className="space-y-4">
      <PageHeader title="Review">
        <Link to={`/company/${companyId}/review`} className="text-sm text-muted">Queue</Link>
      </PageHeader>
      <p className="text-sm text-muted">Revision {item.revision} · {label(item.status)}</p>
      <p className="whitespace-pre-wrap text-sm">{item.notes}</p>
      <ul className="text-sm">{item.links.map((link) => <li key={link.id}><a href={link.url}>{link.url}</a></li>)}</ul>
      <ul className="text-sm">{item.assets.map((asset) => <li key={asset.id}><a href={`/api/v1/submissions/${item.id}/assets/${asset.id}`}>{asset.filename}</a> · {label(asset.scanStatus)}</li>)}</ul>
      {item.assessment?.summary ? <Card><p className="text-sm">{item.assessment.summary}</p>{item.assessment.criteria.map((row) => <p key={row.criterionId} className="mt-2 text-sm">{row.score}/{row.maxScore} · {row.feedback}</p>)}</Card> : <Banner>The draft assessment is not ready yet.</Banner>}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      {done ? <Banner>{done}</Banner> : null}
      <form className="space-y-3" onSubmit={async (event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        setError(null);
        try {
          await api(`/api/v1/submissions/${submissionId}/human-review`, {
            method: "POST",
            body: JSON.stringify({ decision: data.get("decision"), notes: data.get("notes") }),
          });
          setDone("Decision saved. The student sees it now.");
          await client.invalidateQueries({ queryKey: ["review", companyId] });
        } catch (caught) {
          setError(caught instanceof Error ? caught.message : "Could not save the decision.");
        }
      }}>
        <label className="block text-sm font-semibold">Decision
          <select name="decision" className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2">
            <option value="APPROVE">Approve and issue the certificate</option>
            <option value="REQUEST_CHANGES">Request changes</option>
            <option value="REJECT">Reject</option>
          </select>
        </label>
        <TextArea label="Notes for the student" name="notes" required />
        <Button type="submit">Save decision</Button>
      </form>
    </div>
  );
}

export function CandidatesPage() {
  const { companyId = "" } = useParams();
  const shares = useQuery({ queryKey: ["candidates", companyId], queryFn: () => api<{ id: string; projectTitle: string; purpose: string; status: string; expiresAt: string }[]>(`/api/v1/companies/${companyId}/shared-candidates`) });
  return (
    <div>
      <PageHeader title="Shared candidates" />
      <p className="mb-4 text-sm text-muted">Only packages the student has not revoked, and that have not expired.</p>
      {shares.data?.length === 0 ? <Empty title="No shared packages." /> : null}
      <div className="space-y-3">
        {shares.data?.map((share) => (
          <Link key={share.id} to={`/company/${companyId}/candidates/${share.id}`} className="block">
            <Card>
              <h2 className="font-semibold">{share.projectTitle}</h2>
              <p className="text-sm text-muted">{share.purpose} · until {share.expiresAt}</p>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function CandidatePackage() {
  const { companyId = "", shareId = "" } = useParams();
  const pack = useQuery({ queryKey: ["package", shareId], queryFn: () => api<{ share: { purpose: string; projectTitle: string }; profile: { displayName: string; headline: string; bio: string } | null; cvFilename: string | null; submissionId: string | null; assessmentSummary: string | null; skills: { score: number; maxScore: number; passed: boolean } | null; certificateCode: string | null }>(`/api/v1/companies/${companyId}/shares/${shareId}`) });
  if (pack.isError) return <Banner tone="danger">That package is not available.</Banner>;
  if (!pack.data) return <p className="text-muted">Loading the package…</p>;
  const item = pack.data;
  return (
    <div className="space-y-3">
      <PageHeader title={item.share.projectTitle}>
        <Link to={`/company/${companyId}/candidates`} className="text-sm text-muted">All candidates</Link>
      </PageHeader>
      <p className="text-sm text-muted">{item.share.purpose}</p>
      {item.profile ? <Card><h2 className="font-semibold">{item.profile.displayName}</h2><p className="text-sm">{item.profile.headline}</p><p className="mt-2 text-sm">{item.profile.bio}</p></Card> : null}
      {item.cvFilename ? <a className="text-sm font-semibold" href={`/api/v1/companies/${companyId}/shares/${shareId}/cv`}>Download CV</a> : null}
      {item.assessmentSummary ? <Card><p className="text-sm">{item.assessmentSummary}</p></Card> : null}
      {item.skills ? <p className="text-sm">Skills round {item.skills.score}/{item.skills.maxScore} · {item.skills.passed ? "passed" : "not passed"}</p> : null}
      {item.certificateCode ? <Link className="text-sm font-semibold" to={`/verify?code=${item.certificateCode}`}>Certificate {item.certificateCode}</Link> : null}
    </div>
  );
}

export function TeamPage() {
  const { companyId = "" } = useParams();
  const client = useQueryClient();
  const members = useQuery({ queryKey: ["members", companyId], queryFn: () => api<{ userId: string; email: string; status: string; roles: string[] }[]>(`/api/v1/companies/${companyId}/members`) });
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="space-y-4">
      <PageHeader title="Team" />
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <form className="grid max-w-xl gap-3 md:grid-cols-[1fr_180px_auto]" onSubmit={async (event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        setError(null);
        try {
          const body = await api<{ message?: string; devAcceptUrl?: string }>(`/api/v1/companies/${companyId}/invitations`, {
            method: "POST",
            body: JSON.stringify({ email: data.get("email"), roleName: data.get("role") }),
          });
          setMessage(body.devAcceptUrl ? `Invitation ready. Local link: ${body.devAcceptUrl}` : (body.message ?? "Invitation saved."));
          await client.invalidateQueries({ queryKey: ["members", companyId] });
        } catch (caught) {
          setError(caught instanceof Error ? caught.message : "Could not invite.");
        }
      }}>
        <Field label="Email" name="email" type="email" required />
        <label className="text-sm font-semibold">Role
          <select name="role" className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2">
            <option value="REVIEWER">Reviewer</option>
            <option value="RECRUITER">Recruiter</option>
            <option value="ORG_ADMIN">Org admin</option>
          </select>
        </label>
        <div className="flex items-end"><Button type="submit">Invite</Button></div>
      </form>
      {members.isError ? <Empty title="Team list needs an organisation admin." /> : null}
      <div className="space-y-2">
        {members.data?.map((member) => (
          <Card key={member.userId}>
            <p className="font-semibold">{member.email}</p>
            <p className="text-sm text-muted">{member.roles.join(", ")} · {label(member.status)}</p>
          </Card>
        ))}
      </div>
    </div>
  );
}
