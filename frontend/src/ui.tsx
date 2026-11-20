import type { ButtonHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes, InputHTMLAttributes } from "react";
import { applyTheme, readThemeChoice, type ThemeChoice } from "./theme";
import { useEffect, useState } from "react";

export function Button({
  tone = "accent",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: "accent" | "quiet" | "danger" }) {
  const tones = {
    accent: "bg-accent text-accent-ink hover:opacity-90",
    quiet: "bg-transparent text-ink border border-line hover:bg-paper",
    danger: "bg-transparent text-danger border border-danger/40 hover:bg-danger/10",
  };
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-3.5 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${tones[tone]} ${props.className ?? ""}`}
    />
  );
}

const fieldClass = "w-full rounded-md border border-line bg-raised px-3 py-2 text-ink outline-none focus:border-accent";

export function Field(props: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string }) {
  const { label, hint, ...input } = props;
  return (
    <label className="block space-y-1 text-sm">
      <span className="font-semibold">{label}</span>
      <input {...input} className={fieldClass} />
      {hint ? <span className="block text-muted">{hint}</span> : null}
    </label>
  );
}

export function TextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string }) {
  const { label, ...input } = props;
  return (
    <label className="block space-y-1 text-sm">
      <span className="font-semibold">{label}</span>
      <textarea {...input} className={`${fieldClass} min-h-28`} />
    </label>
  );
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement> & { label: string; children: ReactNode }) {
  const { label, children, ...input } = props;
  return (
    <label className="block space-y-1 text-sm">
      <span className="font-semibold">{label}</span>
      <select {...input} className={fieldClass}>
        {children}
      </select>
    </label>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-lg border border-line bg-raised p-5 ${className}`}>{children}</section>;
}

export function Banner({ children, tone = "quiet" }: { children: ReactNode; tone?: "quiet" | "warn" | "danger" }) {
  const tones = {
    quiet: "border-line bg-paper text-ink",
    warn: "border-warn/40 bg-warn/10 text-ink",
    danger: "border-danger/40 bg-danger/10 text-ink",
  };
  return <div className={`rounded-md border px-3 py-2 text-sm ${tones[tone]}`}>{children}</div>;
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-line px-5 py-8 text-center">
      <p className="font-display text-xl">{title}</p>
      {children ? <div className="mt-2 text-sm text-muted">{children}</div> : null}
    </div>
  );
}

export function PageHeader({ eyebrow, title, children }: { eyebrow?: string; title: string; children?: ReactNode }) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        {eyebrow ? <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">{eyebrow}</p> : null}
        <h1 className="font-display text-3xl font-medium tracking-tight">{title}</h1>
      </div>
      {children}
    </header>
  );
}

export function StatusPill({ value }: { value: string | null | undefined }) {
  return <span className="rounded-full border border-line px-2 py-0.5 text-xs font-semibold text-muted">{value ?? "—"}</span>;
}

export function ThemeToggle() {
  const [choice, setChoice] = useState<ThemeChoice>("system");
  useEffect(() => setChoice(readThemeChoice()), []);
  const next: Record<ThemeChoice, ThemeChoice> = { system: "light", light: "dark", dark: "system" };
  const names = { system: "System", light: "Light", dark: "Dark" };
  return (
    <button
      type="button"
      className="rounded-md border border-line px-2.5 py-1 text-sm"
      aria-label={`Theme ${names[choice]}. Activate to change.`}
      onClick={() => {
        const value = next[choice];
        applyTheme(value);
        setChoice(value);
      }}
    >
      {names[choice]}
    </button>
  );
}
