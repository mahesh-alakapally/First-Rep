import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, label, money } from "../api";
import type { Enrollment, Entitlements, Plan, Profile, Submission } from "../types";
import { Banner, Button, Card, Empty, Field, PageHeader, StatusPill, TextArea } from "../ui";

export function StudentHome() {
  const enrollments = useQuery({ queryKey: ["enrollments"], queryFn: () => api<Enrollment[]>("/api/v1/students/me/enrollments") });
  const entitlements = useQuery({ queryKey: ["entitlements"], queryFn: () => api<Entitlements>("/api/v1/me/entitlements") });
  return (
    <div>
      <PageHeader eyebrow="Student" title="My work">
        <Link to="/projects" className="text-sm font-semibold">Find a project</Link>
      </PageHeader>
      {entitlements.data ? (
        <p className="mb-4 text-sm text-muted">
          Free projects left: {entitlements.data.freeRemaining} of {entitlements.data.freeAllowance}.
          {entitlements.data.premiumActive ? " Premium is active." : " Premium is not active."}
        </p>
      ) : null}
      {enrollments.data?.length === 0 ? <Empty title="You have not enrolled yet."><Link to="/projects">Browse published projects</Link></Empty> : null}
      <div className="space-y-3">
        {enrollments.data?.map((item) => (
          <Link key={item.id} to={`/student/enrollments/${item.id}`} className="block">
            <Card>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="font-display text-2xl">{item.projectTitle}</h2>
                  <p className="text-sm text-muted">{item.companyName} · {label(item.accessGrantType)}</p>
                </div>
                <StatusPill value={label(item.status)} />
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function WorkspacePage() {
  const { enrollmentId = "" } = useParams();
  const client = useQueryClient();
  const enrollment = useQuery({ queryKey: ["enrollment", enrollmentId], queryFn: () => api<Enrollment>(`/api/v1/enrollments/${enrollmentId}`) });
  const submissions = useQuery({ queryKey: ["submissions", enrollmentId], queryFn: () => api<Submission[]>(`/api/v1/enrollments/${enrollmentId}/submissions`) });
  const [error, setError] = useState<string | null>(null);
  const current = submissions.data?.[0];
  return (
    <div className="space-y-5">
      <PageHeader eyebrow={enrollment.data?.companyName} title={enrollment.data?.projectTitle ?? "Work"}>
        <Link to="/student" className="text-sm text-muted">Back to my work</Link>
      </PageHeader>
      {error ? <Banner tone="danger">{error}</Banner> : null}
      {!current || current.status === "SUBMITTED" ? (
        <Button
          onClick={async () => {
            setError(null);
            try {
              await api(`/api/v1/enrollments/${enrollmentId}/submissions`, { method: "POST", body: JSON.stringify({ notes: "" }) });
              await client.invalidateQueries({ queryKey: ["submissions", enrollmentId] });
            } catch (caught) {
              setError(caught instanceof Error ? caught.message : "Could not open a draft.");
            }
          }}
        >
          {current ? "Start another revision" : "Start a draft"}
        </Button>
      ) : null}
      {submissions.data?.length === 0 ? <Empty title="No draft yet.">Start one, then add a file, a public link, or notes.</Empty> : null}
      {submissions.data?.map((submission) => (
        <SubmissionCard key={submission.id} submission={submission} projectId={enrollment.data?.projectId} onChange={() => client.invalidateQueries({ queryKey: ["submissions", enrollmentId] })} />
      ))}
    </div>
  );
}

function SubmissionCard({ submission, projectId, onChange }: { submission: Submission; projectId?: string; onChange: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [notes, setNotes] = useState(submission.notes ?? "");
  const draft = submission.status !== "SUBMITTED";
  return (
    <Card className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold">Revision {submission.revision}</h2>
        <StatusPill value={`${label(submission.status)}${submission.jobStatus ? " · " + label(submission.jobStatus) : ""}`} />
      </div>
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <TextArea label="Notes" value={notes} onChange={(event) => setNotes(event.target.value)} disabled={!draft} />
      {draft ? (
        <div className="flex flex-wrap gap-3">
          <Button tone="quiet" onClick={async () => {
            await api(`/api/v1/enrollments/${submission.enrollmentId}/submissions`, { method: "POST", body: JSON.stringify({ notes }) });
            onChange();
          }}>Save notes</Button>
        </div>
      ) : null}
      <form className="grid gap-3 md:grid-cols-[1fr_auto]" onSubmit={async (event) => {
        event.preventDefault();
        const url = String(new FormData(event.currentTarget).get("url") ?? "");
        setError(null);
        try {
          await api(`/api/v1/submissions/${submission.id}/links`, { method: "POST", body: JSON.stringify({ url }) });
          onChange();
        } catch (caught) {
          setError(caught instanceof Error ? caught.message : "Link was rejected.");
        }
      }}>
        <Field label="Public https link" name="url" placeholder="https://github.com/owner/repo" disabled={!draft} />
        <div className="flex items-end"><Button type="submit" tone="quiet" disabled={!draft}>Add link</Button></div>
      </form>
      <ul className="text-sm">
        {submission.links.map((link) => <li key={link.id}>{link.kind}: {link.url}</li>)}
      </ul>
      <form onSubmit={async (event) => {
        event.preventDefault();
        const file = (event.currentTarget.elements.namedItem("file") as HTMLInputElement).files?.[0];
        if (!file) return;
        const body = new FormData();
        body.set("file", file);
        setError(null);
        try {
          await api(`/api/v1/submissions/${submission.id}/assets`, { method: "POST", body });
          onChange();
        } catch (caught) {
          setError(caught instanceof Error ? caught.message : "Upload failed.");
        }
      }}>
        <label className="block text-sm font-semibold">File
          <input name="file" type="file" disabled={!draft} className="mt-1 block text-sm" />
        </label>
        <Button className="mt-2" type="submit" tone="quiet" disabled={!draft}>Upload</Button>
      </form>
      <ul className="text-sm">
        {submission.assets.map((asset) => (
          <li key={asset.id}>
            <a href={`/api/v1/submissions/${submission.id}/assets/${asset.id}`}>{asset.filename}</a>
            <span className="text-muted"> · {label(asset.scanStatus)}</span>
          </li>
        ))}
      </ul>
      {draft ? (
        <Button onClick={async () => {
          setError(null);
          try {
            await api(`/api/v1/enrollments/${submission.enrollmentId}/submissions`, { method: "POST", body: JSON.stringify({ notes }) });
            await api(`/api/v1/submissions/${submission.id}/submit`, { method: "POST" });
            onChange();
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Could not submit.");
          }
        }}>Submit for review</Button>
      ) : null}
      <Assessment submission={submission} onChange={onChange} />
      {projectId ? <SkillsRound projectId={projectId} /> : null}
      {submission.status === "SUBMITTED" ? <ShareForm submissionId={submission.id} /> : null}
    </Card>
  );
}

function Assessment({ submission, onChange }: { submission: Submission; onChange: () => void }) {
  const assessment = submission.assessment;
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  if (!assessment) return <p className="text-sm text-muted">No assessment yet. After you submit, a reviewer sees a draft before you do.</p>;
  return (
    <div className="space-y-3 border-t border-line pt-4">
      <h3 className="font-semibold">Assessment · {label(assessment.status)}</h3>
      {assessment.summary ? <p className="text-sm">{assessment.summary}</p> : <p className="text-sm text-muted">Scores stay hidden until a person releases them.</p>}
      {assessment.criteria.map((row) => (
        <div key={row.criterionId} className="text-sm">
          <p className="font-semibold">{row.score} / {row.maxScore}</p>
          <p>{row.feedback}</p>
          <p className="text-muted">Evidence: {row.evidence}</p>
        </div>
      ))}
      {assessment.reviews.map((review) => (
        <p key={review.createdAt} className="text-sm">{label(review.decision)}: {review.notes}</p>
      ))}
      {assessment.status === "RELEASED" ? (
        <form className="space-y-2" onSubmit={async (event) => {
          event.preventDefault();
          const body = await api<{ id: string }>(`/api/v1/submissions/${submission.id}/appeals`, { method: "POST", body: JSON.stringify({ reason }) });
          setMessage(`Appeal ${body.id} is open.`);
          onChange();
        }}>
          <TextArea label="Appeal" value={reason} onChange={(event) => setReason(event.target.value)} />
          <Button type="submit" tone="quiet">Send appeal</Button>
          {message ? <Banner>{message}</Banner> : null}
        </form>
      ) : null}
    </div>
  );
}

function SkillsRound({ projectId }: { projectId: string }) {
  const client = useQueryClient();
  const round = useQuery({
    queryKey: ["skills", projectId],
    queryFn: () => api<{ id: string; title: string; instructions: string; questions: { id: string; prompt: string; questionType: string; options: string[] }[]; latestAttempt: { id: string; status: string; score: number | null; maxScore: number | null; passed: boolean | null } | null }>(`/api/v1/projects/${projectId}/skills-round`),
    retry: false,
  });
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [attemptId, setAttemptId] = useState<string | null>(null);
  if (round.isError) return null;
  if (!round.data) return null;
  return (
    <div className="space-y-3 border-t border-line pt-4">
      <h3 className="font-semibold">{round.data.title}</h3>
      <p className="text-sm text-muted">{round.data.instructions}</p>
      {round.data.latestAttempt ? <p className="text-sm">Latest: {label(round.data.latestAttempt.status)} {round.data.latestAttempt.score ?? "—"}/{round.data.latestAttempt.maxScore ?? "—"}</p> : null}
      {round.data.questions.map((question) => (
        <div key={question.id} className="text-sm">
          <p className="font-semibold">{question.prompt}</p>
          {question.questionType === "MULTIPLE_CHOICE" ? (
            <div className="mt-1 space-y-1">
              {question.options.map((option, index) => {
                const letter = String.fromCharCode(65 + index);
                return (
                  <label key={letter} className="flex gap-2">
                    <input type="radio" name={question.id} checked={answers[question.id] === letter} onChange={() => setAnswers({ ...answers, [question.id]: letter })} />
                    {letter}. {option}
                  </label>
                );
              })}
            </div>
          ) : (
            <textarea className="mt-1 w-full rounded-md border border-line bg-paper px-3 py-2" value={answers[question.id] ?? ""} onChange={(event) => setAnswers({ ...answers, [question.id]: event.target.value })} />
          )}
        </div>
      ))}
      <div className="flex gap-2">
        <Button tone="quiet" onClick={async () => {
          const attempt = await api<{ id: string }>(`/api/v1/skills-rounds/${round.data!.id}/attempts`, { method: "POST" });
          setAttemptId(attempt.id);
        }}>Start attempt</Button>
        <Button disabled={!attemptId} onClick={async () => {
          await api(`/api/v1/skills-round-attempts/${attemptId}/submit`, {
            method: "POST",
            body: JSON.stringify({ answers: Object.entries(answers).map(([questionId, answer]) => ({ questionId, answer })) }),
          });
          await client.invalidateQueries({ queryKey: ["skills", projectId] });
        }}>Submit answers</Button>
      </div>
    </div>
  );
}

function ShareForm({ submissionId }: { submissionId: string }) {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  return (
    <form className="space-y-2 border-t border-line pt-4" onSubmit={async (event) => {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      setError(null);
      try {
        await api(`/api/v1/submissions/${submissionId}/share-with-company`, {
          method: "POST",
          body: JSON.stringify({
            purpose: data.get("purpose"),
            expiresInDays: Number(data.get("days") || 30),
            includeProfile: data.get("profile") === "on",
            includeCv: data.get("cv") === "on",
            includeSubmission: true,
            includeAssessment: data.get("assessment") === "on",
            includeSkills: data.get("skills") === "on",
            includeCertificate: data.get("certificate") === "on",
          }),
        });
        setMessage("Shared. You can revoke it from Shares.");
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : "Could not share.");
      }
    }}>
      <h3 className="font-semibold">Share with the company</h3>
      <Field label="Purpose" name="purpose" required placeholder="Vaasa pilot introduction" />
      <Field label="Days" name="days" type="number" min={1} max={90} defaultValue={30} />
      <label className="flex gap-2 text-sm"><input type="checkbox" name="profile" /> Profile</label>
      <label className="flex gap-2 text-sm"><input type="checkbox" name="cv" /> CV</label>
      <label className="flex gap-2 text-sm"><input type="checkbox" name="assessment" defaultChecked /> Released assessment</label>
      <label className="flex gap-2 text-sm"><input type="checkbox" name="skills" /> Skills round</label>
      <label className="flex gap-2 text-sm"><input type="checkbox" name="certificate" /> Certificate</label>
      <Button type="submit" tone="quiet">Share</Button>
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
    </form>
  );
}

export function CertificatesPage() {
  const certs = useQuery({ queryKey: ["certs"], queryFn: () => api<{ id: string; verificationCode: string; projectTitle: string; companyName: string; issuedAt: string }[]>("/api/v1/me/certificates") });
  return (
    <div>
      <PageHeader title="Certificates" />
      {certs.data?.length === 0 ? <Empty title="No certificate yet.">A certificate is issued only after a reviewer approves the project.</Empty> : null}
      <div className="space-y-3">
        {certs.data?.map((cert) => (
          <Card key={cert.id}>
            <h2 className="font-display text-2xl">{cert.projectTitle}</h2>
            <p className="text-sm text-muted">{cert.companyName} · {cert.issuedAt}</p>
            <p className="mt-2 text-sm">Code {cert.verificationCode}</p>
            <div className="mt-3 flex gap-3 text-sm">
              <a className="font-semibold" href={`/api/v1/certificates/${cert.id}/download`}>Download PDF</a>
              <Link to={`/verify?code=${cert.verificationCode}`}>Public check</Link>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

export function BillingPage() {
  const client = useQueryClient();
  const plans = useQuery({ queryKey: ["plans"], queryFn: () => api<Plan[]>("/api/v1/billing/plans") });
  const entitlements = useQuery({ queryKey: ["entitlements"], queryFn: () => api<Entitlements>("/api/v1/me/entitlements") });
  const history = useQuery({ queryKey: ["payments"], queryFn: () => api<{ id: string; amountCents: number; currency: string; status: string; provider: string; createdAt: string }[]>("/api/v1/billing/history") });
  const [checkoutId, setCheckoutId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="space-y-4">
      <PageHeader title="Billing" />
      <Banner tone="warn">Local billing simulator. No card is charged. Premium starts only after you confirm the simulated payment.</Banner>
      {entitlements.data ? <p className="text-sm">Free remaining {entitlements.data.freeRemaining}. Premium {entitlements.data.premiumActive ? "active" : "inactive"}.</p> : null}
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <div className="grid gap-3 md:grid-cols-2">
        {plans.data?.map((plan) => (
          <Card key={plan.code}>
            <h2 className="font-display text-xl">{plan.name}</h2>
            <p className="mt-1">{money(plan.priceCents, plan.currency)} / {plan.interval.toLowerCase()}</p>
            <Button className="mt-3" onClick={async () => {
              setError(null);
              try {
                const body = await api<{ checkoutId: string; mode: string; message?: string; url?: string }>("/api/v1/billing/checkout-session", { method: "POST", body: JSON.stringify({ planCode: plan.code }) });
                if (body.mode === "simulator") {
                  setCheckoutId(body.checkoutId);
                  setMessage(body.message ?? "Confirm to apply the simulated payment.");
                } else if (body.url) {
                  window.location.assign(body.url);
                }
              } catch (caught) {
                setError(caught instanceof Error ? caught.message : "Checkout failed.");
              }
            }}>Start checkout</Button>
          </Card>
        ))}
      </div>
      {checkoutId ? (
        <Button onClick={async () => {
          await api("/api/v1/billing/simulator/confirm", { method: "POST", body: JSON.stringify({ checkoutId }) });
          setMessage("Premium is active for this period.");
          setCheckoutId(null);
          await client.invalidateQueries({ queryKey: ["entitlements"] });
          await client.invalidateQueries({ queryKey: ["payments"] });
        }}>Confirm simulated payment</Button>
      ) : null}
      {entitlements.data?.premiumActive ? (
        <Button tone="quiet" onClick={async () => {
          await api("/api/v1/billing/cancel", { method: "POST" });
          setMessage("Premium stays until the end of the period.");
          await client.invalidateQueries({ queryKey: ["entitlements"] });
        }}>Cancel at period end</Button>
      ) : null}
      <div className="space-y-2">
        {history.data?.map((payment) => (
          <p key={payment.id} className="text-sm">{money(payment.amountCents, payment.currency)} · {payment.provider} · {label(payment.status)} · {payment.createdAt}</p>
        ))}
      </div>
    </div>
  );
}

export function ProfilePage() {
  const client = useQueryClient();
  const profile = useQuery({ queryKey: ["profile"], queryFn: () => api<Profile>("/api/v1/students/me") });
  const skills = useQuery({ queryKey: ["skills"], queryFn: () => api<{ name: string; slug: string }[]>("/api/v1/skills") });
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  if (!profile.data) return <p className="text-muted">Loading profile…</p>;
  const data = profile.data;
  return (
    <form className="max-w-xl space-y-3" onSubmit={async (event) => {
      event.preventDefault();
      const form = new FormData(event.currentTarget);
      const selected = skills.data?.filter((skill) => form.get(`skill-${skill.slug}`) === "on").map((skill) => skill.name) ?? [];
      setError(null);
      try {
        await api("/api/v1/students/me", {
          method: "PUT",
          body: JSON.stringify({
            displayName: form.get("displayName"),
            headline: form.get("headline"),
            education: form.get("education"),
            targetRoles: form.get("targetRoles"),
            bio: form.get("bio"),
            portfolioUrl: form.get("portfolioUrl"),
            githubUrl: form.get("githubUrl"),
            shareProfileInPackages: form.get("share") === "on",
            skillNames: selected,
          }),
        });
        setMessage("Profile saved.");
        await client.invalidateQueries({ queryKey: ["profile"] });
        await client.invalidateQueries({ queryKey: ["me"] });
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : "Could not save.");
      }
    }}>
      <PageHeader title="Profile" />
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <Field label="Name" name="displayName" defaultValue={data.displayName} required />
      <Field label="Headline" name="headline" defaultValue={data.headline ?? ""} />
      <Field label="Education" name="education" defaultValue={data.education ?? ""} />
      <Field label="Target roles" name="targetRoles" defaultValue={data.targetRoles ?? ""} />
      <TextArea label="Bio" name="bio" defaultValue={data.bio ?? ""} />
      <Field label="Portfolio URL" name="portfolioUrl" defaultValue={data.portfolioUrl ?? ""} />
      <Field label="GitHub URL" name="githubUrl" defaultValue={data.githubUrl ?? ""} />
      <label className="flex gap-2 text-sm"><input type="checkbox" name="share" defaultChecked={data.shareProfileInPackages} /> Allow profile fields in a share package</label>
      <fieldset>
        <legend className="text-sm font-semibold">Skills</legend>
        <div className="mt-2 grid gap-1">
          {skills.data?.map((skill) => (
            <label key={skill.slug} className="flex gap-2 text-sm">
              <input type="checkbox" name={`skill-${skill.slug}`} defaultChecked={data.skillNames.includes(skill.name)} /> {skill.name}
            </label>
          ))}
        </div>
      </fieldset>
      <Button type="submit">Save profile</Button>
      <div>
        <label className="block text-sm font-semibold">CV
          <input className="mt-1 block" type="file" onChange={async (event) => {
            const file = event.target.files?.[0];
            if (!file) return;
            const body = new FormData();
            body.set("file", file);
            await api("/api/v1/students/me/cv", { method: "POST", body });
            setMessage("CV stored.");
            await client.invalidateQueries({ queryKey: ["profile"] });
          }} />
        </label>
        {data.cvFilename ? <p className="text-sm text-muted">Current file: {data.cvFilename}</p> : null}
      </div>
    </form>
  );
}

export function SharesPage() {
  const client = useQueryClient();
  const shares = useQuery({ queryKey: ["shares"], queryFn: () => api<{ id: string; companyName: string; projectTitle: string; purpose: string; status: string; expiresAt: string }[]>("/api/v1/me/shares") });
  return (
    <div>
      <PageHeader title="Shares" />
      {shares.data?.length === 0 ? <Empty title="Nothing is shared.">Share from a submitted project when you want a company to see it.</Empty> : null}
      <div className="space-y-3">
        {shares.data?.map((share) => (
          <Card key={share.id}>
            <h2 className="font-semibold">{share.projectTitle}</h2>
            <p className="text-sm text-muted">{share.companyName} · {share.purpose}</p>
            <p className="text-sm">{label(share.status)} · until {share.expiresAt}</p>
            {share.status === "ACTIVE" ? (
              <Button className="mt-3" tone="danger" onClick={async () => {
                await api(`/api/v1/shares/${share.id}`, { method: "DELETE" });
                await client.invalidateQueries({ queryKey: ["shares"] });
              }}>Revoke</Button>
            ) : null}
          </Card>
        ))}
      </div>
    </div>
  );
}
