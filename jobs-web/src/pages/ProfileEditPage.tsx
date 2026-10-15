import { useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";
import { api, rawRequest, unwrap, type Schemas } from "../api/client";
import { ProfileEditor, type Snapshot } from "../components/ProfileEditor";
import { Card, ErrorBanner, Field, Loading, Notice } from "../components/ui";
import { useAction, useResource } from "../lib/useResource";

const KEY_PATTERN = "[a-z0-9][a-z0-9-]{0,62}";

/** Edits an existing profile, or creates one from a resume file (or from scratch). */
export function ProfileEditPage() {
  const { key } = useParams();
  return key ? <EditExisting profileKey={key} /> : <CreateNew />;
}

function useSkills() {
  return useResource(() => unwrap(api.GET("/api/v1/skills")), []);
}

function EditExisting({ profileKey }: { profileKey: string }) {
  const navigate = useNavigate();
  const detail = useResource(() => unwrap(api.GET("/api/v1/me/candidate-profiles/{key}", { params: { path: { key: profileKey } } })), [profileKey]);
  const skills = useSkills();
  const save = useAction();
  if (detail.loading || skills.loading) return <Loading />;
  if (detail.error || !detail.data?.snapshot) return <ErrorBanner error={detail.error} />;
  return (
    <>
      <h1>Edit {detail.data.profile?.displayName ?? profileKey}</h1>
      <ErrorBanner error={save.error} onDismiss={save.clearError} />
      <ProfileEditor
        initial={detail.data.snapshot}
        skills={(skills.data ?? []).map((s) => ({ key: s.key!, name: s.name! }))}
        busy={save.busy}
        submitLabel="Save a new version"
        onSave={(snapshot) =>
          void save.run(async () => {
            await unwrap(
              api.PUT("/api/v1/me/candidate-profiles/{key}", {
                params: { path: { key: profileKey } },
                body: { displayName: detail.data!.profile?.displayName ?? profileKey, snapshot, note: "edited in the dashboard" },
              }),
            );
            navigate(`/profiles?selected=${encodeURIComponent(profileKey)}`);
          })
        }
      />
    </>
  );
}

function CreateNew() {
  const navigate = useNavigate();
  const extractors = useResource(() => unwrap(api.GET("/api/v1/me/candidate-profiles/extractors")), []);
  const skills = useSkills();
  const [file, setFile] = useState<File>();
  const [extractor, setExtractor] = useState("heuristic-resume-extractor");
  const [consent, setConsent] = useState(false);
  const [draft, setDraft] = useState<Schemas["ProfileDraft"]>();
  const [startBlank, setStartBlank] = useState(false);
  const [profileKey, setProfileKey] = useState("main");
  const [displayName, setDisplayName] = useState("My profile");
  const read = useAction();
  const save = useAction();
  const external = extractors.data?.find((e) => e.id === extractor)?.sendsTextExternally ?? false;

  const readResume = (e: FormEvent) => {
    e.preventDefault();
    if (!file) return;
    void read.run(async () => {
      const query = new URLSearchParams({ fileName: file.name, extractor });
      const response = await rawRequest(`/api/v1/me/candidate-profiles/drafts?${query}`, {
        method: "POST",
        body: file,
        contentType: file.type || "application/octet-stream",
      });
      setDraft((await response.json()) as Schemas["ProfileDraft"]);
    });
  };

  const saveProfile = (snapshot: Snapshot) =>
    void save.run(async () => {
      await unwrap(
        api.PUT("/api/v1/me/candidate-profiles/{key}", {
          params: { path: { key: profileKey } },
          body: { displayName, snapshot, note: draft ? `created from ${file?.name ?? "a resume"}` : "created in the dashboard" },
        }),
      );
      if (file && draft) {
        await rawRequest(`/api/v1/me/candidate-profiles/${encodeURIComponent(profileKey)}/master-resumes?fileName=${encodeURIComponent(file.name)}`, {
          method: "POST",
          body: file,
          contentType: file.type || "application/octet-stream",
        });
      }
      navigate(`/profiles?selected=${encodeURIComponent(profileKey)}`);
    });

  if (!draft && !startBlank) {
    return (
      <>
        <h1>New profile</h1>
        <Card title="Start from your resume">
          <form onSubmit={readResume} className="form-grid">
            <ErrorBanner error={read.error ?? extractors.error} onDismiss={read.clearError} />
            <Field label="Resume file" hint="PDF, Word (.docx) or text. Scanned images cannot be read.">
              <input type="file" accept=".pdf,.docx,.txt,application/pdf,text/plain" onChange={(e) => setFile(e.target.files?.[0])} />
            </Field>
            {(extractors.data?.length ?? 0) > 1 && (
              <fieldset className="choices">
                <legend className="field-label">How to read it</legend>
                {extractors.data!.map((x) => (
                  <label key={x.id} className="inline">
                    <input type="radio" name="extractor" value={x.id} checked={extractor === x.id} onChange={() => (setExtractor(x.id!), setConsent(false))} />{" "}
                    {x.label}
                    {x.sendsTextExternally ? " — better results; sends the resume text to Anthropic" : " — nothing leaves this machine"}
                  </label>
                ))}
              </fieldset>
            )}
            {external && (
              <Notice tone="warning">
                <label className="inline">
                  <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} /> Send the text of my resume to Anthropic. Your
                  name, e-mail, phone number and links are replaced with placeholders first.
                </label>
              </Notice>
            )}
            <div className="button-row">
              <button type="submit" disabled={!file || read.busy || (external && !consent)}>
                {read.busy ? "Reading…" : "Read my resume"}
              </button>
              <button type="button" className="secondary" onClick={() => setStartBlank(true)}>
                Start with an empty profile
              </button>
            </div>
            <p className="muted small-text">Nothing is saved until you review the result and press Save.</p>
          </form>
        </Card>
      </>
    );
  }

  if (skills.loading) return <Loading />;
  return (
    <>
      <h1>New profile</h1>
      <ErrorBanner error={save.error} onDismiss={save.clearError} />
      {draft && (
        <Notice tone={draft.notes?.length ? "warning" : "info"}>
          Read from {file?.name}
          {draft.sentExternally ? " with Claude" : " on this machine"}. Everything below is unconfirmed until you check it.
          {draft.notes && draft.notes.length > 0 && (
            <ul>
              {draft.notes.map((n) => (
                <li key={n}>{n}</li>
              ))}
            </ul>
          )}
          {draft.unknownSkills && draft.unknownSkills.length > 0 && (
            <p className="small-text">Not in the skill catalog (left out): {draft.unknownSkills.join(", ")}.</p>
          )}
        </Notice>
      )}
      <ProfileEditor
        initial={draft?.snapshot ?? {}}
        skills={(skills.data ?? []).map((s) => ({ key: s.key!, name: s.name! }))}
        busy={save.busy}
        submitLabel="Save profile"
        header={
          <Card title="Profile">
            <div className="grid-2">
              <Field label="Name of this profile" hint="e.g. 'Backend roles'; you can keep several">
                <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required maxLength={200} />
              </Field>
              <Field label="Short key" hint="Lower-case letters, digits and '-'">
                <input value={profileKey} onChange={(e) => setProfileKey(e.target.value)} required pattern={KEY_PATTERN} />
              </Field>
            </div>
          </Card>
        }
        onSave={saveProfile}
      />
    </>
  );
}
