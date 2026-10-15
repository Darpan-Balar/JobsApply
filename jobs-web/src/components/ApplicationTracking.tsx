import { useState, type FormEvent } from "react";
import { api, unwrap, type Schemas } from "../api/client";
import { formatDate, formatDateTime, humanize, today } from "../lib/format";
import { useAction, useResource } from "../lib/useResource";
import { Badge, Card, Empty, ErrorBanner } from "./ui";

type Kind = Schemas["TrackingViewsInterviewInput"]["kind"];
const KINDS: Kind[] = ["PHONE_SCREEN", "TECHNICAL", "BEHAVIORAL", "ONSITE", "FINAL", "OTHER"];

/** Interviews and follow-ups of one submitted application. */
export function ApplicationTracking({ applicationId, submitted }: { applicationId: string; submitted: boolean }) {
  const interviews = useResource(() => unwrap(api.GET("/api/v1/me/interviews", { params: { query: { applicationId } } })), [applicationId]);
  const followUps = useResource(() => unwrap(api.GET("/api/v1/me/follow-ups", { params: { query: { applicationId, includeDone: true } } })), [applicationId]);
  const action = useAction();
  const [when, setWhen] = useState("");
  const [kind, setKind] = useState<Kind>("PHONE_SCREEN");
  const [location, setLocation] = useState("");
  const [dueOn, setDueOn] = useState(today());
  const [note, setNote] = useState("");

  const schedule = (e: FormEvent) => {
    e.preventDefault();
    void action.run(async () => {
      await unwrap(
        api.POST("/api/v1/me/applications/{id}/interviews", {
          params: { path: { id: applicationId } },
          body: { scheduledAt: new Date(when).toISOString(), kind, location: location || undefined, durationMinutes: 60 },
        }),
      );
      setWhen("");
      setLocation("");
      interviews.reload();
    });
  };

  const addFollowUp = (e: FormEvent) => {
    e.preventDefault();
    void action.run(async () => {
      await unwrap(api.POST("/api/v1/me/follow-ups", { body: { dueOn, note, applicationId } }));
      setNote("");
      followUps.reload();
    });
  };

  return (
    <Card title="Interviews and follow-ups">
      <ErrorBanner error={interviews.error ?? followUps.error ?? action.error} onDismiss={action.clearError} />
      <h3>Interviews</h3>
      {interviews.data?.length ? (
        <ul className="list">
          {interviews.data.map((i) => (
            <li key={i.id} className="row-between">
              <span>
                {formatDateTime(i.scheduledAt)} · {humanize(i.kind)} {i.location && <span className="muted">· {i.location}</span>} <Badge value={i.status} />
              </span>
              {i.status === "SCHEDULED" && (
                <span className="button-row">
                  <button
                    className="secondary small"
                    onClick={() =>
                      void action.run(async () => {
                        await unwrap(api.POST("/api/v1/me/interviews/{id}/status", { params: { path: { id: i.id! } }, body: { status: "COMPLETED" } }));
                        interviews.reload();
                      })
                    }
                  >
                    Completed
                  </button>
                  <button
                    className="secondary small"
                    onClick={() =>
                      void action.run(async () => {
                        await unwrap(api.POST("/api/v1/me/interviews/{id}/status", { params: { path: { id: i.id! } }, body: { status: "CANCELLED" } }));
                        interviews.reload();
                      })
                    }
                  >
                    Cancelled
                  </button>
                </span>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <Empty>No interviews yet.</Empty>
      )}
      {submitted && (
        <form className="toolbar" onSubmit={schedule}>
          <input type="datetime-local" aria-label="Interview time" value={when} onChange={(e) => setWhen(e.target.value)} required />
          <select aria-label="Interview kind" value={kind} onChange={(e) => setKind(e.target.value as Kind)}>
            {KINDS.map((k) => (
              <option key={k} value={k}>
                {humanize(k)}
              </option>
            ))}
          </select>
          <input aria-label="Where" placeholder="Where or link" value={location} onChange={(e) => setLocation(e.target.value)} maxLength={500} />
          <button type="submit" disabled={action.busy}>
            Add interview
          </button>
        </form>
      )}
      <h3>Follow-ups</h3>
      {followUps.data?.length ? (
        <ul className="list">
          {followUps.data.map((f) => (
            <li key={f.id} className={f.done ? "muted" : undefined}>
              <span className={f.overdue ? "danger-text" : undefined}>{formatDate(f.dueOn)}</span> · {f.note} {f.done && "(done)"}
            </li>
          ))}
        </ul>
      ) : (
        <Empty>No follow-ups.</Empty>
      )}
      <form className="toolbar" onSubmit={addFollowUp}>
        <input type="date" aria-label="Due on" value={dueOn} onChange={(e) => setDueOn(e.target.value)} required />
        <input aria-label="Follow-up note" placeholder="e.g. Ask the recruiter for an update" value={note} onChange={(e) => setNote(e.target.value)} required maxLength={1000} />
        <button type="submit" disabled={action.busy}>
          Add follow-up
        </button>
      </form>
    </Card>
  );
}
