import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { z } from "zod";
import { api, ApiError } from "../api";
import { useSession } from "../session";
import type { Me } from "../types";
import { Banner, Button, PageHeader } from "../ui";

const password = z.string().min(10).regex(/[A-Za-z]/).regex(/[0-9]/);

export function LoginPage() {
  const navigate = useNavigate();
  const { refresh } = useSession();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<{ email: string; password: string }>({ resolver: zodResolver(z.object({ email: z.string().email(), password: z.string().min(1) })) });
  return (
    <div className="mx-auto max-w-md">
      <PageHeader title="Sign in" />
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <form
        className="mt-4 space-y-3"
        onSubmit={form.handleSubmit(async (values) => {
          setError(null);
          try {
            await api<Me>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(values) });
            await refresh();
            navigate("/home");
          } catch (caught) {
            setError(caught instanceof ApiError ? caught.message : "Could not sign in.");
          }
        })}
      >
        <label className="block text-sm font-semibold">Email
          <input className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2" type="email" {...form.register("email")} />
        </label>
        <label className="block text-sm font-semibold">Password
          <input className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2" type="password" {...form.register("password")} />
        </label>
        {form.formState.errors.email ? <p className="text-sm text-danger">Enter a valid email.</p> : null}
        <Button type="submit" disabled={form.formState.isSubmitting}>Sign in</Button>
      </form>
      <p className="mt-4 text-sm text-muted"><Link to="/register">Create a student account</Link> · <Link to="/forgot-password">Forgot password</Link></p>
      <div className="mt-6 rounded-lg border border-line bg-raised p-4 text-sm">
        <p className="font-semibold">Development accounts</p>
        <p className="mt-1 text-muted">Password for every seeded account: Demo-FirstRep-2026</p>
        <ul className="mt-2 space-y-1">
          <li>student@firstrep.local</li>
          <li>orgadmin@firstrep.local</li>
          <li>recruiter@firstrep.local</li>
          <li>reviewer@firstrep.local</li>
          <li>admin@firstrep.local</li>
          <li>lakeadmin@firstrep.local</li>
        </ul>
      </div>
    </div>
  );
}

export function RegisterPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [devUrl, setDevUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<{ email: string; password: string; displayName: string }>({
    resolver: zodResolver(z.object({ email: z.string().email(), password, displayName: z.string().min(1).max(160) })),
  });
  return (
    <div className="mx-auto max-w-md">
      <PageHeader title="Create a student account" />
      <p className="text-sm text-muted">Company accounts are invited by an organisation. Public registration creates a student.</p>
      {error ? <Banner tone="danger">{error}</Banner> : null}
      {message ? <Banner>{message}</Banner> : null}
      {devUrl ? <p className="mt-3 text-sm"><Link to={devUrl.replace(/^https?:\/\/[^/]+/, "")}>Open the local verification link</Link></p> : null}
      <form
        className="mt-4 space-y-3"
        onSubmit={form.handleSubmit(async (values) => {
          setError(null);
          try {
            const body = await api<{ message: string; devVerificationUrl?: string }>("/api/v1/auth/register", { method: "POST", body: JSON.stringify(values) });
            setMessage(body.message);
            setDevUrl(body.devVerificationUrl ?? null);
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Could not register.");
          }
        })}
      >
        <label className="block text-sm font-semibold">Name<input className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2" {...form.register("displayName")} /></label>
        <label className="block text-sm font-semibold">Email<input className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2" type="email" {...form.register("email")} /></label>
        <label className="block text-sm font-semibold">Password<input className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2" type="password" {...form.register("password")} /></label>
        <p className="text-xs text-muted">At least 10 characters, with a letter and a digit.</p>
        <Button type="submit">Create account</Button>
      </form>
    </div>
  );
}

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const token = params.get("token") ?? "";
  return (
    <div className="max-w-md">
      <PageHeader title="Verify email" />
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <Button
        className="mt-4"
        disabled={!token}
        onClick={async () => {
          try {
            const body = await api<{ message: string }>("/api/v1/auth/verify-email", { method: "POST", body: JSON.stringify({ token }) });
            setMessage(body.message);
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : "That link is not valid.");
          }
        }}
      >
        Confirm this email
      </Button>
      <p className="mt-4 text-sm"><Link to="/login">Go to sign in</Link></p>
    </div>
  );
}

export function ForgotPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [devUrl, setDevUrl] = useState<string | null>(null);
  return (
    <div className="max-w-md">
      <PageHeader title="Reset a password" />
      {message ? <Banner>{message}</Banner> : null}
      {devUrl ? <p className="mt-3 text-sm"><Link to={devUrl.replace(/^https?:\/\/[^/]+/, "")}>Open the local reset link</Link></p> : null}
      <form
        className="mt-4 space-y-3"
        onSubmit={async (event) => {
          event.preventDefault();
          const email = String(new FormData(event.currentTarget).get("email") ?? "");
          const body = await api<{ message: string; devResetUrl?: string }>("/api/v1/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) });
          setMessage(body.message);
          setDevUrl(body.devResetUrl ?? null);
        }}
      >
        <label className="block text-sm font-semibold">Email<input name="email" type="email" required className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2" /></label>
        <Button type="submit">Send reset link</Button>
      </form>
    </div>
  );
}

export function ResetPage() {
  const [params] = useSearchParams();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<{ password: string }>({ resolver: zodResolver(z.object({ password })) });
  return (
    <div className="max-w-md">
      <PageHeader title="Choose a new password" />
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <form
        className="mt-4 space-y-3"
        onSubmit={form.handleSubmit(async (values) => {
          try {
            const body = await api<{ message: string }>("/api/v1/auth/reset-password", {
              method: "POST",
              body: JSON.stringify({ token: params.get("token"), password: values.password }),
            });
            setMessage(body.message);
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Could not reset.");
          }
        })}
      >
        <label className="block text-sm font-semibold">New password<input type="password" className="mt-1 w-full rounded-md border border-line bg-raised px-3 py-2" {...form.register("password")} /></label>
        <Button type="submit">Update password</Button>
      </form>
    </div>
  );
}

export function AcceptInvitePage() {
  const [params] = useSearchParams();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { refresh } = useSession();
  return (
    <div className="max-w-md">
      <PageHeader title="Accept an invitation" />
      {message ? <Banner>{message}</Banner> : null}
      {error ? <Banner tone="danger">{error}</Banner> : null}
      <Button
        onClick={async () => {
          try {
            await api("/api/v1/invitations/accept", { method: "POST", body: JSON.stringify({ token: params.get("token") }) });
            await refresh();
            setMessage("You are on the team. Open Home to continue.");
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : "Could not accept.");
          }
        }}
      >
        Accept
      </Button>
    </div>
  );
}
