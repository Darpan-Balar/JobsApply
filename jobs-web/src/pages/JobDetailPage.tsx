import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";
import { api, unwrap, type Schemas } from "../api/client";
import { Badge, Card, ErrorBanner, Field, Loading, Notice } from "../components/ui";
import { formatDateTime, humanize } from "../lib/format";
import { useAction, useResource } from "../lib/useResource";

/** Lines of "question text", with a trailing "*" for required ones. */
function parseQuestions(text: string): Schemas["ApplicationControllerQuestion"][] {
  return text
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => (l.endsWith("*") ? { text: l.slice(0, -1).trim(), required: true } : { text: l, required: false }));
}

export function JobDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const posting = useResource(() => unwrap(api.GET("/api/v1/postings/{id}", { params: { path: { id } } })), [id]);
  const profiles = useResource(() => unwrap(api.GET("/api/v1/me/candidate-profiles")), []);
  const roles = useResource(() => unwrap(api.GET("/api/v1/role-profiles")), []);
  const [candidate, setCandidate] = useState("");
  const [role, setRole] = useState("");
  const [questions, setQuestions] = useState("");
  const [result, setResult] = useState<Schemas["PreparationViewsResult"]>();
  const prepare = useAction();
  const recheck = useAction();

  useEffect(() => {
    if (!candidate && profiles.data?.[0]?.key) setCandidate(profiles.data[0].key);
  }, [profiles.data, candidate]);
  useEffect(() => {
    if (!role && roles.data?.[0]?.key) setRole(roles.data[0].key);
  }, [roles.data, role]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    void prepare.run(async () => {
      const prepared = await unwrap(
        api.POST("/api/v1/me/applications/prepare", {
          body: { postingId: id, candidateProfile: candidate, roleProfile: role, additionalQuestions: parseQuestions(questions) },
        }),
      );
      setResult(prepared);
      if (prepared.applicationId && prepared.outcome !== "NOT_ELIGIBLE" && prepared.outcome !== "EXPIRED") {
        navigate(`/applications/${prepared.applicationId}`);
      }
    });
  };

  if (posting.loading) return <Loading />;
  if (posting.error) return <ErrorBanner error={posting.error} />;
  const detail = posting.data;
  const summary = detail?.summary;
  return (
    <>
      <h1>{summary?.title}</h1>
      <p className="subtitle">
        {summary?.employerName} · {humanize(summary?.workplaceType)} · <Badge value={summary?.availability} />
      </p>
      <div className="columns">
        <Card title="Description">
          {detail?.descriptionText ? <div className="prose">{detail.descriptionText}</div> : <p className="muted">No description was published.</p>}
        </Card>
        <div>
          <Card title="Prepare an application">
            {profiles.data?.length === 0 ? (
              <Notice tone="warning">Import a candidate profile first (Profiles).</Notice>
            ) : (
              <form onSubmit={submit} className="form-grid">
                <ErrorBanner error={prepare.error} />
                {result?.outcome === "NOT_ELIGIBLE" && (
                  <Notice tone="warning">
                    Not eligible: a hard requirement of this role profile is not met. Nothing was generated.
                    <ul>
                      {result.issues?.map((i) => (
                        <li key={i.code + (i.message ?? "")}>{i.message}</li>
                      ))}
                    </ul>
                  </Notice>
                )}
                {result?.outcome === "EXPIRED" && <Notice tone="warning">The source reports this job closed.</Notice>}
                <Field label="Candidate profile">
                  <select value={candidate} onChange={(e) => setCandidate(e.target.value)} required>
                    {profiles.data?.map((p) => (
                      <option key={p.key} value={p.key}>
                        {p.displayName ?? p.key}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Role profile">
                  <select value={role} onChange={(e) => setRole(e.target.value)} required>
                    {roles.data?.map((r) => (
                      <option key={r.key} value={r.key}>
                        {r.key}
                        {r.systemTemplate ? " (template)" : ""}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Extra questions from the form" hint="One per line; end with * if required. Greenhouse questions are fetched automatically.">
                  <textarea rows={3} value={questions} onChange={(e) => setQuestions(e.target.value)} />
                </Field>
                <div>
                  <button type="submit" disabled={prepare.busy || !candidate || !role}>
                    {prepare.busy ? "Preparing…" : "Prepare"}
                  </button>
                </div>
                <p className="muted small-text">Preparing never submits anything. You review every package first.</p>
              </form>
            )}
          </Card>
          <Card
            title="Where it is listed"
            actions={
              <button
                className="secondary small"
                disabled={recheck.busy}
                onClick={() =>
                  void recheck.run(async () => {
                    await unwrap(api.POST("/api/v1/postings/{id}/availability-check", { params: { path: { id } } }));
                    posting.reload();
                  })
                }
              >
                {recheck.busy ? "Checking…" : "Recheck availability"}
              </button>
            }
          >
            <ErrorBanner error={recheck.error} />
            <ul className="list">
              {detail?.listings?.map((l) => (
                <li key={l.id}>
                  <strong>{l.sourceKey}</strong> <Badge value={l.availability} />
                  <div className="muted small-text">
                    Last checked {formatDateTime(l.lastCheckedAt)}
                    {l.availabilityEvidence && ` · ${l.availabilityEvidence}`}
                  </div>
                  {l.listingUrl && (
                    <a href={l.listingUrl} target="_blank" rel="noreferrer noopener">
                      Open listing
                    </a>
                  )}
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>
    </>
  );
}
