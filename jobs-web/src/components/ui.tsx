import type { ReactNode } from "react";
import type { ApiError } from "../api/client";
import { humanize } from "../lib/format";

export function ErrorBanner({ error, onDismiss }: { error?: ApiError; onDismiss?: () => void }) {
  if (!error) return null;
  return (
    <div className="banner banner-error" role="alert">
      <div>
        <strong>{error.title}</strong>
        {error.detail && <span> — {error.detail}</span>}
        {error.fieldErrors.length > 0 && (
          <ul>
            {error.fieldErrors.map((f) => (
              <li key={f.field}>
                {f.field}: {f.message}
              </li>
            ))}
          </ul>
        )}
      </div>
      {onDismiss && (
        <button className="link" onClick={onDismiss} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  );
}

export function Notice({ tone = "info", children }: { tone?: "info" | "warning" | "success"; children: ReactNode }) {
  return <div className={`banner banner-${tone}`}>{children}</div>;
}

export function Loading({ label = "Loading…" }: { label?: string }) {
  return (
    <p className="muted" role="status">
      {label}
    </p>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="empty">{children}</p>;
}

const STATUS_TONE: Record<string, string> = {
  READY_FOR_REVIEW: "accent",
  APPROVED: "accent",
  SUBMITTING: "warning",
  SUBMISSION_UNCONFIRMED: "warning",
  SUBMITTED: "success",
  FAILED: "danger",
  NOT_ELIGIBLE: "muted",
  EXPIRED: "muted",
  REJECTED_BY_USER: "muted",
  WITHDRAWN: "muted",
  OPEN: "success",
  CLOSED: "muted",
  ELIGIBLE: "success",
  BLOCKING: "danger",
  WARNING: "warning",
  SCHEDULED: "accent",
  COMPLETED: "success",
  CANCELLED: "muted",
  CONFIRMED: "success",
  IN_PROGRESS: "warning",
  UNCONFIRMED: "warning",
};

export function Badge({ value }: { value?: string }) {
  if (!value) return null;
  return <span className={`badge badge-${STATUS_TONE[value] ?? "neutral"}`}>{humanize(value)}</span>;
}

export function Card({ title, actions, children }: { title?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card-header">
          {title && <h2>{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

/** A labelled control; the hint sits outside the label so it is not part of the control's accessible name. */
export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="field">
      <label className="field">
        <span className="field-label">{label}</span>
        {children}
      </label>
      {hint && <span className="field-hint">{hint}</span>}
    </div>
  );
}
