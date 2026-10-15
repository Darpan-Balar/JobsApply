import { useState, type FormEvent } from "react";
import { api, unwrap, type Schemas } from "../api/client";
import { formatDateTime, humanize } from "../lib/format";
import { useAction } from "../lib/useResource";
import { Badge, Card, ErrorBanner } from "./ui";

type PackageView = Schemas["PreparationViewsPackageView"];
type Answer = Schemas["PackageContentAnswer"];

/** The prepared package exactly as it will be used: issues first, then documents, answers, resume and cover note. */
export function PackageReview({ applicationId, pkg, editable, onChanged }: {
  applicationId: string;
  pkg: PackageView;
  editable: boolean;
  onChanged: () => void;
}) {
  const content = pkg.content;
  const issues = content?.issues ?? [];
  return (
    <>
      <Card title={`Package version ${pkg.versionNo ?? ""}`} actions={<span className="muted small-text">Prepared {formatDateTime(pkg.createdAt)}</span>}>
        {issues.length === 0 ? (
          <p className="success-text">No issues.</p>
        ) : (
          <ul className="list">
            {issues.map((i, n) => (
              <li key={n}>
                <Badge value={i.severity} /> {i.message} <span className="muted small-text">({i.code})</span>
              </li>
            ))}
          </ul>
        )}
        {content?.documents?.length ? (
          <p>
            Documents:{" "}
            {content.documents.map((d) => (
              <a key={d.id} className="doc-link" href={`/api/v1/me/applications/${applicationId}/documents/${d.id}`} target="_blank" rel="noreferrer">
                {humanize(d.kind)} ({d.mediaType === "application/pdf" ? "PDF" : d.mediaType === "text/html" ? "HTML" : "text"})
              </a>
            ))}
          </p>
        ) : null}
        {content?.destination?.applicationUrl && (
          <p className="muted small-text">
            Application form: <span className="mono">{content.destination.applicationUrl}</span>
          </p>
        )}
      </Card>

      <Card title="Answers">
        {content?.answers?.length ? (
          <ul className="answers">
            {content.answers.map((a) => (
              <AnswerRow key={a.question?.id} applicationId={applicationId} answer={a} editable={editable} onChanged={onChanged} />
            ))}
          </ul>
        ) : (
          <p className="muted">The form asks no questions beyond the standard fields.</p>
        )}
      </Card>

      <Card title="Resume">
        {content?.resume?.header && (
          <p>
            <strong>{content.resume.header.name}</strong>
            <span className="muted"> · {[content.resume.header.email, content.resume.header.phone, content.resume.header.location].filter(Boolean).join(" · ")}</span>
          </p>
        )}
        {content?.resume?.sections?.map((s) => (
          <div key={s.heading} className="resume-section">
            <h3>{s.heading}</h3>
            <ul>
              {s.claims?.map((c, n) => (
                <li key={n}>
                  {c.text}{" "}
                  <span className="muted small-text" title="Facts this line is based on">
                    {c.userApproved ? "(written by you)" : `(${c.sources?.length ?? 0} fact${c.sources?.length === 1 ? "" : "s"})`}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </Card>

      {content?.coverNote && (
        <Card title="Cover note">
          <p>{content.coverNote.greeting}</p>
          {content.coverNote.claims?.map((c, n) => <p key={n}>{c.text}</p>)}
          <p>{content.coverNote.closing}</p>
        </Card>
      )}
    </>
  );
}

function AnswerRow({ applicationId, answer, editable, onChanged }: {
  applicationId: string;
  answer: Answer;
  editable: boolean;
  onChanged: () => void;
}) {
  const question = answer.question;
  const draft = answer.draft;
  const [value, setValue] = useState(draft?.answer ?? "");
  const [editing, setEditing] = useState(draft?.status === "NEEDS_USER_INPUT");
  const action = useAction();
  const selfIdentification = question?.kind === "SELF_IDENTIFICATION";

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!question?.id) return;
    void action.run(async () => {
      await unwrap(
        api.PUT("/api/v1/me/applications/{id}/answers/{questionId}", {
          params: { path: { id: applicationId, questionId: question.id! } },
          body: { answer: value },
        }),
      );
      setEditing(false);
      onChanged();
    });
  };

  return (
    <li>
      <div className="answer-question">
        {question?.text}
        {question?.required && <span className="required"> *</span>}
      </div>
      {selfIdentification ? (
        <p className="muted small-text">Voluntary self-identification: answer it yourself on the form, if you want to.</p>
      ) : editing && editable ? (
        <form onSubmit={save} className="inline-form">
          <ErrorBanner error={action.error} />
          {question?.options?.length ? (
            <select value={value} onChange={(e) => setValue(e.target.value)} required>
              <option value="">Choose…</option>
              {question.options.map((o) => (
                <option key={o}>{o}</option>
              ))}
            </select>
          ) : (
            <textarea rows={2} value={value} onChange={(e) => setValue(e.target.value)} required />
          )}
          <button type="submit" className="small" disabled={action.busy}>
            Save answer
          </button>
        </form>
      ) : (
        <div className="answer-value">
          {draft?.answer ? <span>{draft.answer}</span> : <span className="warning-text">Needs your answer</span>}{" "}
          <span className="muted small-text">{draft?.note ?? humanize(draft?.origin)}</span>
          {editable && (
            <button className="link small" onClick={() => setEditing(true)}>
              Change
            </button>
          )}
        </div>
      )}
    </li>
  );
}
