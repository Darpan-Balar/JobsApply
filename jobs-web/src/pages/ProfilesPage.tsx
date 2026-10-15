import { useState, type ChangeEvent, type FormEvent } from "react";
import { api, rawRequest, unwrap, type Schemas } from "../api/client";
import { Card, Empty, ErrorBanner, Loading, Notice } from "../components/ui";
import { formatDateTime, humanize } from "../lib/format";
import { useAction, useResource } from "../lib/useResource";

export function ProfilesPage() {
  const profiles = useResource(() => unwrap(api.GET("/api/v1/me/candidate-profiles")), []);
  const [selected, setSelected] = useState<string>();
  const key = selected ?? profiles.data?.[0]?.key;
  return (
    <>
      <h1>Candidate profiles</h1>
      <Notice>
        Your profile holds the facts every resume line and answer is built from. It is encrypted and visible only to you. Imported facts count only
        after you confirm them.
      </Notice>
      <ErrorBanner error={profiles.error} />
      <div className="columns">
        <div>
          <Card title="Your profiles">
            {profiles.loading ? (
              <Loading />
            ) : profiles.data?.length ? (
              <ul className="list selectable">
                {profiles.data.map((p) => (
                  <li key={p.key}>
                    <button className={`link ${p.key === key ? "selected" : ""}`} onClick={() => setSelected(p.key)}>
                      {p.displayName ?? p.key} <span className="muted small-text">({p.key})</span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <Empty>No profile yet. Import one below.</Empty>
            )}
          </Card>
          <ImportProfile onImported={profiles.reload} />
        </div>
        <div>{key && <ProfileDetail profileKey={key} />}</div>
      </div>
    </>
  );
}

function ImportProfile({ onImported }: { onImported: () => void }) {
  const [yaml, setYaml] = useState("");
  const [report, setReport] = useState<Schemas["ConfigImportReport"]>();
  const action = useAction();

  const readFile = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) void file.text().then(setYaml);
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    void action.run(async () => {
      const response = await rawRequest("/api/v1/me/candidate-profiles/import?fileName=profile.yaml", { method: "POST", body: yaml, contentType: "application/yaml" });
      setReport((await response.json()) as Schemas["ConfigImportReport"]);
      onImported();
    });
  };

  return (
    <Card title="Import or update a profile (YAML)">
      <form onSubmit={submit} className="form-grid">
        <ErrorBanner error={action.error} />
        {report && (
          <Notice tone={report.issues?.length ? "warning" : "success"}>
            {report.issues?.length
              ? report.issues.map((i, n) => <div key={n}>{`${i.path ?? ""} ${i.message ?? ""}`}</div>)
              : report.outcomes?.map((o) => `${o.key}: ${humanize(o.outcome)}`).join(", ")}
          </Notice>
        )}
        <input type="file" accept=".yaml,.yml,text/yaml" aria-label="Profile file" onChange={readFile} />
        <textarea rows={8} className="mono" aria-label="Profile YAML" value={yaml} onChange={(e) => setYaml(e.target.value)} required />
        <div>
          <button type="submit" disabled={action.busy || !yaml.trim()}>
            Import
          </button>
        </div>
        <p className="muted small-text">See docs/examples/candidate-profile.example.yaml for the format.</p>
      </form>
    </Card>
  );
}

function ProfileDetail({ profileKey }: { profileKey: string }) {
  const detail = useResource(() => unwrap(api.GET("/api/v1/me/candidate-profiles/{key}", { params: { path: { key: profileKey } } })), [profileKey]);
  const resumes = useResource(
    () => unwrap(api.GET("/api/v1/me/candidate-profiles/{key}/master-resumes", { params: { path: { key: profileKey } } })),
    [profileKey],
  );
  const action = useAction();
  const facts = (detail.data?.factsByVerification ?? {}) as Record<string, number>;
  const unconfirmed = facts.UNVERIFIED ?? 0;

  const upload = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    void action.run(async () => {
      await rawRequest(`/api/v1/me/candidate-profiles/${encodeURIComponent(profileKey)}/master-resumes?fileName=${encodeURIComponent(file.name)}`, {
        method: "POST",
        body: file,
        contentType: file.type || "application/octet-stream",
      });
      resumes.reload();
    });
    e.target.value = "";
  };

  if (detail.loading) return <Loading />;
  const contact = detail.data?.snapshot?.contact;
  return (
    <>
      <ErrorBanner error={detail.error ?? resumes.error ?? action.error} onDismiss={action.clearError} />
      <Card title={detail.data?.profile?.displayName ?? profileKey} actions={<span className="muted small-text">version {detail.data?.currentVersion?.versionNo}</span>}>
        <dl className="facts">
          <dt>Name</dt>
          <dd>{contact?.fullName ?? "—"}</dd>
          <dt>E-mail</dt>
          <dd>{contact?.email ?? "—"}</dd>
          <dt>Experiences</dt>
          <dd>{detail.data?.snapshot?.experiences?.length ?? 0}</dd>
          <dt>Skills</dt>
          <dd>{detail.data?.snapshot?.skills?.length ?? 0}</dd>
          <dt>Facts</dt>
          <dd>
            {Object.entries(facts)
              .map(([k, v]) => `${v} ${humanize(k).toLowerCase()}`)
              .join(", ") || "—"}
          </dd>
        </dl>
        {unconfirmed > 0 && (
          <Notice tone="warning">
            {unconfirmed} imported fact{unconfirmed === 1 ? " is" : "s are"} not confirmed yet and will not be used.{" "}
            <button
              className="small"
              disabled={action.busy}
              onClick={() =>
                window.confirm("Confirm that every imported fact in this profile is true?") &&
                void action.run(async () => {
                  await unwrap(api.POST("/api/v1/me/candidate-profiles/{key}/verification", { params: { path: { key: profileKey } }, body: { all: true } }));
                  detail.reload();
                })
              }
            >
              I confirm they are all true
            </button>
          </Notice>
        )}
      </Card>
      <Card title="Master resume" actions={<input type="file" aria-label="Upload master resume" accept=".pdf,.docx,.txt" onChange={upload} />}>
        {resumes.data?.length ? (
          <ul className="list">
            {resumes.data.map((r) => (
              <li key={r.id}>
                <a href={`/api/v1/me/candidate-profiles/${encodeURIComponent(profileKey)}/master-resumes/${r.id}/content`} target="_blank" rel="noreferrer">
                  {r.fileName}
                </a>{" "}
                <span className="muted small-text">uploaded {formatDateTime(r.uploadedAt)} · kept exactly as uploaded</span>
              </li>
            ))}
          </ul>
        ) : (
          <Empty>No master resume uploaded.</Empty>
        )}
      </Card>
    </>
  );
}
