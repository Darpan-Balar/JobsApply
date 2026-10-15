import { useState, type FormEvent } from "react";
import { api, unwrap, type Schemas } from "../api/client";
import { formatDateTime, humanize, newIdempotencyKey } from "../lib/format";
import { useAction, useResource } from "../lib/useResource";
import { Badge, Card, Empty, ErrorBanner, Field, Notice } from "./ui";

type Detail = Schemas["PreparationViewsApplicationDetail"];
type Attempt = Schemas["ApplicationServiceAttemptView"];
type EvidenceKind = Schemas["SubmissionEvidence"]["kind"];

/**
 * Remembers the Idempotency-Key of a submission start per application (session storage), so that retrying after a
 * network error or reopening the assistance window reuses it instead of being refused.
 */
function startKey(applicationId: string): string {
  const name = `jobs:start-key:${applicationId}`;
  try {
    const existing = sessionStorage.getItem(name);
    if (existing) return existing;
    const created = newIdempotencyKey();
    sessionStorage.setItem(name, created);
    return created;
  } catch {
    return newIdempotencyKey();
  }
}

const REJECTABLE = new Set(["DISCOVERED", "MATCHED", "NOT_ELIGIBLE", "PREPARED", "READY_FOR_REVIEW", "APPROVED", "FAILED"]);

export function SubmissionPanel({ detail, onChanged }: { detail: Detail; onChanged: () => void }) {
  const application = detail.application!;
  const id = application.id!;
  const status = application.status;
  const pkg = detail.currentPackage;
  const action = useAction();
  const attempts = useResource(() => unwrap(api.GET("/api/v1/me/applications/{id}/attempts", { params: { path: { id } } })), [id, status]);
  const inProgress = attempts.data?.find((a) => a.outcome === "IN_PROGRESS");

  const run = (call: () => Promise<unknown>) =>
    void action.run(async () => {
      await call();
      onChanged();
    });

  return (
    <Card title="Submission" actions={<Badge value={status} />}>
      <ErrorBanner error={action.error} onDismiss={action.clearError} />
      {status === "READY_FOR_REVIEW" && pkg?.id && <ApproveStep applicationId={id} packageId={pkg.id} onChanged={onChanged} />}
      {status === "PREPARED" && (
        <Notice tone="warning">This package has blocking issues (see above). Answer what is missing, or prepare again.</Notice>
      )}
      {status === "APPROVED" && (
        <>
          <Notice>
            You approved version {pkg?.versionNo}. The approval allows exactly one submission. Choose how you want to submit:
          </Notice>
          <div className="button-row">
            <button
              disabled={action.busy}
              onClick={() =>
                run(() =>
                  unwrap(api.POST("/api/v1/me/applications/{id}/assist", { params: { path: { id }, header: { "Idempotency-Key": startKey(id) } } })),
                )
              }
            >
              Open the form with browser assistance
            </button>
            <button
              className="secondary"
              disabled={action.busy}
              onClick={() =>
                run(() =>
                  unwrap(
                    api.POST("/api/v1/me/applications/{id}/submission", { params: { path: { id }, header: { "Idempotency-Key": startKey(id) } } }),
                  ),
                )
              }
            >
              I will submit it myself
            </button>
            <button className="secondary" disabled={action.busy} onClick={() => run(() => unwrap(api.DELETE("/api/v1/me/applications/{id}/approval", { params: { path: { id } } })))}>
              Revoke approval
            </button>
          </div>
          <p className="muted small-text">Browser assistance fills only what your approved package makes certain. You always press Submit yourself.</p>
        </>
      )}
      {status === "SUBMITTING" && inProgress && (
        inProgress.channel === "BROWSER_ASSISTED" ? (
          <AssistPanel applicationId={id} attempt={inProgress} onChanged={onChanged} />
        ) : (
          <>
            {pkg?.content?.destination?.applicationUrl && (
              <p>
                Submit at{" "}
                <a href={pkg.content.destination.applicationUrl} target="_blank" rel="noreferrer noopener">
                  the application form
                </a>
                , then report how it went.
              </p>
            )}
            <OutcomeForm mode="report" applicationId={id} attemptId={inProgress.id!} onDone={onChanged} />
          </>
        )
      )}
      {status === "SUBMISSION_UNCONFIRMED" && (
        <>
          <Notice tone="warning">
            Nobody knows yet whether this submission went through. Check your e-mail or the employer's portal, then record what you found. Nothing is
            retried until you do.
          </Notice>
          <OutcomeForm mode="verify" applicationId={id} onDone={onChanged} />
        </>
      )}
      {status === "SUBMITTED" && (
        <div className="button-row">
          <button className="secondary" disabled={action.busy} onClick={() => run(() => unwrap(api.POST("/api/v1/me/applications/{id}/withdrawal", { params: { path: { id } } })))}>
            Withdraw application
          </button>
        </div>
      )}
      {status === "FAILED" && pkg?.content?.candidate?.key && pkg.content.role?.key && (
        <>
          <Notice tone="warning">The submission failed. Prepare again to review the package and approve a fresh submission.</Notice>
          <button
            disabled={action.busy}
            onClick={() =>
              run(() =>
                unwrap(
                  api.POST("/api/v1/me/applications/prepare", {
                    body: { postingId: application.postingId!, candidateProfile: pkg.content!.candidate!.key!, roleProfile: pkg.content!.role!.key! },
                  }),
                ),
              )
            }
          >
            Prepare again
          </button>
        </>
      )}
      {status && REJECTABLE.has(status) && (
        <p>
          <button
            className="link danger-text"
            disabled={action.busy}
            onClick={() => window.confirm("Stop pursuing this application?") && run(() => unwrap(api.POST("/api/v1/me/applications/{id}/reject", { params: { path: { id } }, body: {} })))}
          >
            Not interested — reject this application
          </button>
        </p>
      )}
      <AttemptList applicationId={id} attempts={attempts.data ?? []} />
    </Card>
  );
}

function ApproveStep({ applicationId, packageId, onChanged }: { applicationId: string; packageId: string; onChanged: () => void }) {
  const blockers = useResource(
    () => unwrap(api.GET("/api/v1/me/applications/{id}/approval/blockers", { params: { path: { id: applicationId } } })),
    [applicationId, packageId],
  );
  const action = useAction();
  const blocked = (blockers.data?.length ?? 0) > 0;
  return (
    <>
      <ErrorBanner error={blockers.error ?? action.error} />
      {blocked ? (
        <Notice tone="warning">
          Before you can approve:
          <ul>
            {blockers.data!.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
        </Notice>
      ) : (
        <Notice>Review the package above. Approving allows one submission of exactly this version.</Notice>
      )}
      <button
        disabled={blocked || blockers.loading || action.busy}
        onClick={() =>
          void action.run(async () => {
            await unwrap(api.POST("/api/v1/me/applications/{id}/approval", { params: { path: { id: applicationId } }, body: { packageId } }));
            onChanged();
          })
        }
      >
        Approve this version
      </button>
    </>
  );
}

const EVIDENCE_KINDS: { value: Exclude<EvidenceKind, "NONE">; label: string }[] = [
  { value: "CONFIRMATION_PAGE", label: "Confirmation page" },
  { value: "CONFIRMATION_EMAIL", label: "Confirmation e-mail" },
  { value: "EMPLOYER_PORTAL", label: "Employer portal shows it" },
  { value: "SCREENSHOT", label: "Screenshot" },
];

/** Reporting (or verifying) how a submission ended. CONFIRMED needs evidence; FAILED needs a description. */
export function OutcomeForm({ mode, applicationId, attemptId, onDone }: {
  mode: "report" | "verify";
  applicationId: string;
  attemptId?: string;
  onDone: () => void;
}) {
  const [outcome, setOutcome] = useState<"CONFIRMED" | "FAILED" | "UNCONFIRMED">("CONFIRMED");
  const [kind, setKind] = useState<Exclude<EvidenceKind, "NONE">>("CONFIRMATION_EMAIL");
  const [detail, setDetail] = useState("");
  const [pageUrl, setPageUrl] = useState("");
  const action = useAction();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const evidence =
      outcome === "CONFIRMED"
        ? { kind, detail, pageUrl: pageUrl || undefined }
        : outcome === "FAILED"
          ? { kind: "NONE" as const, detail }
          : undefined;
    void action.run(async () => {
      if (mode === "report") {
        await unwrap(
          api.POST("/api/v1/me/applications/{id}/submission/{attemptId}/outcome", {
            params: { path: { id: applicationId, attemptId: attemptId! } },
            body: { outcome, evidence },
          }),
        );
      } else {
        await unwrap(api.POST("/api/v1/me/applications/{id}/verification", { params: { path: { id: applicationId } }, body: { result: outcome, evidence } }));
      }
      onDone();
    });
  };

  return (
    <form onSubmit={submit} className="form-grid">
      <ErrorBanner error={action.error} />
      <Field label={mode === "report" ? "How did it go?" : "What did you find?"}>
        <select value={outcome} onChange={(e) => setOutcome(e.target.value as typeof outcome)}>
          <option value="CONFIRMED">It was submitted — I have evidence</option>
          <option value="FAILED">It failed — nothing was submitted</option>
          {mode === "report" && <option value="UNCONFIRMED">I am not sure</option>}
        </select>
      </Field>
      {outcome === "CONFIRMED" && (
        <Field label="Evidence">
          <select value={kind} onChange={(e) => setKind(e.target.value as typeof kind)}>
            {EVIDENCE_KINDS.map((k) => (
              <option key={k.value} value={k.value}>
                {k.label}
              </option>
            ))}
          </select>
        </Field>
      )}
      {outcome !== "UNCONFIRMED" && (
        <Field label={outcome === "CONFIRMED" ? "What it says (e.g. the e-mail subject)" : "What went wrong"}>
          <textarea rows={2} value={detail} onChange={(e) => setDetail(e.target.value)} required maxLength={4000} />
        </Field>
      )}
      {outcome === "CONFIRMED" && (
        <Field label="Address of the confirmation page (optional)">
          <input value={pageUrl} onChange={(e) => setPageUrl(e.target.value)} maxLength={2048} />
        </Field>
      )}
      <div>
        <button type="submit" disabled={action.busy}>
          Record
        </button>
      </div>
    </form>
  );
}

function AssistPanel({ applicationId, attempt, onChanged }: { applicationId: string; attempt: Attempt; onChanged: () => void }) {
  const view = useResource(() => unwrap(api.GET("/api/v1/me/applications/{id}/assist", { params: { path: { id: applicationId } } })), [applicationId, attempt.id]);
  const [current, setCurrent] = useState<Schemas["AssistView"]>();
  const action = useAction();
  const shown = current ?? view.data;

  const call = (request: () => Promise<Schemas["AssistView"]>) =>
    void action.run(async () => {
      const next = await request();
      setCurrent(next);
      if (next.state === "CONFIRMED" || next.state === "FAILED" || next.state === "FINISHED") onChanged();
    });
  const path = { params: { path: { id: applicationId } } };

  return (
    <div className="assist">
      <ErrorBanner error={view.error ?? action.error} onDismiss={action.clearError} />
      {shown && (
        <>
          <p>
            <Badge value={shown.state} /> {shown.instructions}
          </p>
          {shown.blockers?.length ? <Notice tone="warning">Paused: {shown.blockers.map(humanize).join(", ")}. Nothing was filled.</Notice> : null}
          {shown.humanSteps?.map((s) => (
            <Notice key={s} tone="warning">
              {s}
            </Notice>
          ))}
          {shown.filled?.length ? (
            <details open>
              <summary>Filled for you ({shown.filled.length})</summary>
              <ul className="list compact">
                {shown.filled.map((f) => (
                  <li key={f.fieldId}>
                    {f.label} <span className="muted small-text">— {f.note}</span>
                  </li>
                ))}
              </ul>
            </details>
          ) : null}
          {shown.leftToYou?.length ? (
            <details open>
              <summary>Left to you ({shown.leftToYou.length})</summary>
              <ul className="list compact">
                {shown.leftToYou.map((f) => (
                  <li key={f.fieldId}>
                    {f.label}
                    {f.required && <span className="required"> *</span>} <span className="muted small-text">— {f.note}</span>
                  </li>
                ))}
              </ul>
            </details>
          ) : null}
        </>
      )}
      <div className="button-row">
        {shown?.state === "PAUSED_FOR_YOU" && (
          <button disabled={action.busy} onClick={() => call(() => unwrap(api.POST("/api/v1/me/applications/{id}/assist/refresh", path)))}>
            I resolved it — continue
          </button>
        )}
        {(shown?.state === "READY_FOR_YOU" || shown?.state === "PAUSED_FOR_YOU") && (
          <>
            <button
              disabled={action.busy}
              onClick={() =>
                call(() =>
                  unwrap(api.POST("/api/v1/me/applications/{id}/assist/confirmation", { params: { path: { id: applicationId }, query: { waitSeconds: 30 } } })),
                )
              }
            >
              {action.busy ? "Looking for the confirmation…" : "I pressed Submit — record the confirmation"}
            </button>
            <button className="secondary" disabled={action.busy} onClick={() => call(() => unwrap(api.DELETE("/api/v1/me/applications/{id}/assist", path)))}>
              Close the window
            </button>
          </>
        )}
        {shown?.state === "WINDOW_NOT_OPEN" && (
          <button
            className="secondary"
            disabled={action.busy}
            onClick={() =>
              call(() => unwrap(api.POST("/api/v1/me/applications/{id}/assist", { params: { path: { id: applicationId }, header: { "Idempotency-Key": startKey(applicationId) } } })))
            }
          >
            Reopen the window
          </button>
        )}
      </div>
      {shown?.state === "WINDOW_NOT_OPEN" && attempt.id && <OutcomeForm mode="report" applicationId={applicationId} attemptId={attempt.id} onDone={onChanged} />}
    </div>
  );
}

function AttemptList({ applicationId, attempts }: { applicationId: string; attempts: Attempt[] }) {
  if (attempts.length === 0) return null;
  return (
    <details>
      <summary>Submission attempts ({attempts.length})</summary>
      {attempts.length === 0 ? (
        <Empty>None.</Empty>
      ) : (
        <ul className="list">
          {attempts.map((a) => (
            <li key={a.id}>
              <Badge value={a.outcome} /> {humanize(a.channel)} · started {formatDateTime(a.startedAt)}
              {a.finishedAt && <> · ended {formatDateTime(a.finishedAt)}</>}
              {a.evidence && a.evidence.kind !== "NONE" && (
                <div className="small-text">
                  {humanize(a.evidence.kind)}: {a.evidence.detail}
                  {a.evidence.artifactRef && (
                    <>
                      {" "}
                      ·{" "}
                      <a href={`/api/v1/me/applications/${applicationId}/attempts/${a.id}/screenshot`} target="_blank" rel="noreferrer">
                        screenshot
                      </a>
                    </>
                  )}
                </div>
              )}
              {a.evidence?.kind === "NONE" && a.evidence.detail && <div className="small-text muted">{a.evidence.detail}</div>}
            </li>
          ))}
        </ul>
      )}
    </details>
  );
}
