import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { type Schemas } from "../api/client";
import { humanize } from "../lib/format";
import { Badge, Card, Field, Notice } from "./ui";

export type Snapshot = Schemas["CandidateSnapshot"];
/** While editing, every field may still be empty; the API types are applied after validation. */
type Row<T> = { [K in keyof T]?: T[K] } & { id: string };
type Experience = Row<Schemas["CandidateExperience"]>;
type Education = Row<Schemas["CandidateEducation"]>;
type Skill = Row<Schemas["CandidateSkill"]>;
type Achievement = Row<Schemas["CandidateAchievement"]>;
type Attribute = Row<Schemas["CandidateAttribute"]>;
type Contact = Schemas["CandidateContact"];
type Verification = Schemas["CandidateExperience"]["verification"];
type Level = Schemas["CandidateEducation"]["level"];

const LEVELS: Level[] = ["HIGH_SCHOOL", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTORATE"];
const PROFICIENCY = ["BASIC", "WORKING", "PROFICIENT", "EXPERT"] as const;
/** Attribute keys the platform uses (matching rules and answer drafting). */
const ATTRIBUTE_SUGGESTIONS = ["work-authorization.de", "work-authorization.us", "work-authorization.gb", "work-authorization.in",
  "work-authorization.nl", "notice-period-days", "preferred-city", "salary.expectation"];
const AUTHORIZATION_VALUES = ["citizen", "permanent-resident", "authorized", "not-required", "requires-sponsorship", "not-authorized"];

export interface SkillOption {
  key: string;
  name: string;
}

interface Props {
  initial: Snapshot;
  skills: SkillOption[];
  busy: boolean;
  header?: ReactNode;
  submitLabel: string;
  onSave: (snapshot: Snapshot) => void;
}

function newId(prefix: string, taken: Set<string>): string {
  for (let i = 1; ; i++) {
    const id = `${prefix}-new-${i}`;
    if (!taken.has(id)) return id;
  }
}

const blank = (v?: string | null) => (v && v.trim() ? v.trim() : undefined);

/**
 * Edits a whole profile. Facts you add or change become your confirmed statements (VERIFIED); facts you leave as they
 * were keep their status, so an imported fact stays unconfirmed until you edit it or confirm everything.
 */
export function ProfileEditor({ initial, skills, busy, header, submitLabel, onSave }: Props) {
  const [contact, setContact] = useState<Contact>(initial.contact ?? {});
  const [experiences, setExperiences] = useState<Experience[]>((initial.experiences ?? []) as Experience[]);
  const [achievements, setAchievements] = useState<Achievement[]>((initial.achievements ?? []) as Achievement[]);
  const [education, setEducation] = useState<Education[]>((initial.education ?? []) as Education[]);
  const [skillRows, setSkillRows] = useState<Skill[]>((initial.skills ?? []) as Skill[]);
  const [attributes, setAttributes] = useState<Attribute[]>((initial.attributes ?? []) as Attribute[]);
  const [touched, setTouched] = useState<Set<string>>(new Set());
  const [confirmAll, setConfirmAll] = useState(false);
  const [problems, setProblems] = useState<string[]>([]);

  const original = useMemo(() => {
    const ids = new Map<string, Verification>();
    [...(initial.experiences ?? []), ...(initial.education ?? []), ...(initial.skills ?? []), ...(initial.achievements ?? []), ...(initial.attributes ?? [])]
      .forEach((f) => f.id && f.verification && ids.set(f.id, f.verification));
    return ids;
  }, [initial]);
  const takenIds = () =>
    new Set([...experiences, ...education, ...skillRows, ...achievements, ...attributes].map((f) => f.id ?? "").filter(Boolean));
  const touch = (id?: string) => id && setTouched((t) => (t.has(id) ? t : new Set(t).add(id)));
  const skillName = (key?: string) => skills.find((s) => s.key === key)?.name ?? key;

  function update<T extends { id?: string }>(setter: (fn: (rows: T[]) => T[]) => void, index: number, patch: Partial<T>) {
    setter((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  /** The status a fact is saved with. */
  const status = (id: string | undefined, fallback: Verification, preference = false): Verification => {
    if (preference) return "USER_PREFERENCE";
    if (confirmAll || !id || !original.has(id) || touched.has(id)) return "VERIFIED";
    return original.get(id) ?? fallback;
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const found: string[] = [];
    experiences.forEach((x, i) => {
      if (!blank(x.title) || !blank(x.employer) || !x.start) found.push(`Position ${i + 1}: title, employer and start month are required.`);
    });
    education.forEach((x, i) => {
      if (!blank(x.institution) || !x.level) found.push(`Education ${i + 1}: institution and degree level are required.`);
    });
    skillRows.forEach((x, i) => !x.skill && found.push(`Skill ${i + 1}: choose a skill.`));
    attributes.forEach((x, i) => (!blank(x.key) || !blank(x.value)) && found.push(`Other fact ${i + 1}: a key and a value are required.`));
    setProblems(found);
    if (found.length) return;
    const experienceIds = new Set(experiences.map((x) => x.id));
    const saved: Snapshot = {
      contact: {
        fullName: blank(contact.fullName),
        email: blank(contact.email),
        phone: blank(contact.phone),
        city: blank(contact.city),
        countryCode: blank(contact.countryCode)?.toUpperCase(),
        links: (contact.links ?? []).map((l) => l.trim()).filter(Boolean),
      },
      experiences: experiences.map((x) => ({ ...x, title: x.title!.trim(), employer: x.employer!.trim(), start: x.start!, location: blank(x.location),
        end: x.end || undefined, verification: status(x.id, "UNVERIFIED") })),
      achievements: achievements.filter((a) => blank(a.text) && (!a.experienceId || experienceIds.has(a.experienceId)))
        .map((a) => ({ ...a, text: a.text!.trim(), verification: status(a.id, "UNVERIFIED") })),
      education: education.map((x) => ({ ...x, institution: x.institution!.trim(), level: x.level!, field: blank(x.field), start: x.start || undefined,
        end: x.end || undefined, verification: status(x.id, "UNVERIFIED") })),
      skills: skillRows.map((s) => ({ ...s, skill: s.skill!, verification: status(s.id, "UNVERIFIED") })),
      attributes: attributes.map((a) => ({ ...a, key: a.key!.trim(), value: a.value!.trim(),
        verification: status(a.id, "UNVERIFIED", a.key!.trim().startsWith("preferred-")) })),
    };
    onSave(saved);
  };

  const unconfirmedBadge = (id?: string) =>
    id && original.get(id) === "UNVERIFIED" && !touched.has(id) && !confirmAll ? <Badge value="UNCONFIRMED" /> : null;

  return (
    <form onSubmit={submit}>
      {header}
      <Card title="Contact">
        <div className="grid-2">
          <Field label="Full name">
            <input value={contact.fullName ?? ""} onChange={(e) => setContact({ ...contact, fullName: e.target.value })} />
          </Field>
          <Field label="E-mail">
            <input type="email" value={contact.email ?? ""} onChange={(e) => setContact({ ...contact, email: e.target.value })} />
          </Field>
          <Field label="Phone">
            <input value={contact.phone ?? ""} onChange={(e) => setContact({ ...contact, phone: e.target.value })} />
          </Field>
          <Field label="City">
            <input value={contact.city ?? ""} onChange={(e) => setContact({ ...contact, city: e.target.value })} />
          </Field>
          <Field label="Country code" hint="Two letters, e.g. DE">
            <input value={contact.countryCode ?? ""} maxLength={2} onChange={(e) => setContact({ ...contact, countryCode: e.target.value.toUpperCase() })} />
          </Field>
          <Field label="Links" hint="One per line (LinkedIn, GitHub, website)">
            <textarea rows={2} value={(contact.links ?? []).join("\n")} onChange={(e) => setContact({ ...contact, links: e.target.value.split("\n") })} />
          </Field>
        </div>
      </Card>

      <Card
        title="Experience"
        actions={
          <button type="button" className="secondary small" onClick={() => setExperiences([...experiences, { id: newId("exp", takenIds()), employer: "", title: "", start: "" }])}>
            Add position
          </button>
        }
      >
        {experiences.length === 0 && <p className="muted">No positions yet.</p>}
        {experiences.map((x, i) => {
          const own = achievements.map((a, index) => ({ a, index })).filter(({ a }) => a.experienceId === x.id);
          return (
            <fieldset key={x.id} className="fact">
              <legend>
                {x.title || "New position"} {x.employer && `· ${x.employer}`} {unconfirmedBadge(x.id)}
              </legend>
              <div className="grid-2">
                <Field label="Job title">
                  <input value={x.title ?? ""} onChange={(e) => (update(setExperiences, i, { title: e.target.value }), touch(x.id))} />
                </Field>
                <Field label="Employer">
                  <input value={x.employer ?? ""} onChange={(e) => (update(setExperiences, i, { employer: e.target.value }), touch(x.id))} />
                </Field>
                <Field label="Start">
                  <input type="month" value={x.start ?? ""} onChange={(e) => (update(setExperiences, i, { start: e.target.value }), touch(x.id))} />
                </Field>
                <Field label="End" hint="Leave empty if this is your current position">
                  <input type="month" value={x.end ?? ""} onChange={(e) => (update(setExperiences, i, { end: e.target.value }), touch(x.id))} />
                </Field>
                <Field label="Location">
                  <input value={x.location ?? ""} onChange={(e) => (update(setExperiences, i, { location: e.target.value }), touch(x.id))} />
                </Field>
                <Field label="Skills used">
                  <SkillPicker value={x.skills ?? []} skills={skills} onChange={(v) => (update(setExperiences, i, { skills: v }), touch(x.id))} />
                </Field>
              </div>
              <div className="field-label">Achievements</div>
              {own.map(({ a, index }) => (
                <div className="row-between" key={a.id}>
                  <input
                    className="grow"
                    aria-label="Achievement"
                    value={a.text ?? ""}
                    onChange={(e) => (update(setAchievements, index, { text: e.target.value }), touch(a.id))}
                  />
                  {unconfirmedBadge(a.id)}
                  <button type="button" className="link small danger-text" onClick={() => setAchievements(achievements.filter((_, j) => j !== index))}>
                    Remove
                  </button>
                </div>
              ))}
              <div className="button-row">
                <button
                  type="button"
                  className="secondary small"
                  onClick={() => setAchievements([...achievements, { id: newId(`${x.id}-a`, takenIds()), text: "", experienceId: x.id, skills: [] }])}
                >
                  Add achievement
                </button>
                <button
                  type="button"
                  className="link small danger-text"
                  onClick={() => {
                    setExperiences(experiences.filter((_, j) => j !== i));
                    setAchievements(achievements.filter((a) => a.experienceId !== x.id));
                  }}
                >
                  Remove position
                </button>
              </div>
            </fieldset>
          );
        })}
      </Card>

      <Card
        title="Education"
        actions={
          <button type="button" className="secondary small" onClick={() => setEducation([...education, { id: newId("edu", takenIds()), institution: "" }])}>
            Add education
          </button>
        }
      >
        {education.length === 0 && <p className="muted">No education yet.</p>}
        {education.map((x, i) => (
          <fieldset key={x.id} className="fact">
            <legend>
              {x.institution || "New entry"} {unconfirmedBadge(x.id)}
            </legend>
            <div className="grid-2">
              <Field label="Institution">
                <input value={x.institution ?? ""} onChange={(e) => (update(setEducation, i, { institution: e.target.value }), touch(x.id))} />
              </Field>
              <Field label="Degree level">
                <select value={x.level ?? ""} onChange={(e) => (update(setEducation, i, { level: (e.target.value || undefined) as Level }), touch(x.id))}>
                  <option value="">Choose…</option>
                  {LEVELS.map((l) => (
                    <option key={l} value={l}>
                      {humanize(l)}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Field of study">
                <input value={x.field ?? ""} onChange={(e) => (update(setEducation, i, { field: e.target.value }), touch(x.id))} />
              </Field>
              <Field label="Completed">
                <select
                  value={x.completed ? "yes" : "no"}
                  onChange={(e) => (update(setEducation, i, { completed: e.target.value === "yes" }), touch(x.id))}
                >
                  <option value="yes">Yes</option>
                  <option value="no">Not (yet)</option>
                </select>
              </Field>
              <Field label="Start">
                <input type="month" value={x.start ?? ""} onChange={(e) => (update(setEducation, i, { start: e.target.value }), touch(x.id))} />
              </Field>
              <Field label="End">
                <input type="month" value={x.end ?? ""} onChange={(e) => (update(setEducation, i, { end: e.target.value }), touch(x.id))} />
              </Field>
            </div>
            <button type="button" className="link small danger-text" onClick={() => setEducation(education.filter((_, j) => j !== i))}>
              Remove
            </button>
          </fieldset>
        ))}
      </Card>

      <Card
        title="Skills"
        actions={
          <button type="button" className="secondary small" onClick={() => setSkillRows([...skillRows, { id: newId("sk", takenIds()), skill: "" }])}>
            Add skill
          </button>
        }
      >
        <p className="muted small-text">Years are optional; leave them empty unless you know them.</p>
        <table>
          <tbody>
            {skillRows.map((s, i) => (
              <tr key={s.id}>
                <td>
                  <select aria-label="Skill" value={s.skill ?? ""} onChange={(e) => (update(setSkillRows, i, { skill: e.target.value }), touch(s.id))}>
                    <option value="">Choose…</option>
                    {skills.map((o) => (
                      <option key={o.key} value={o.key}>
                        {o.name}
                      </option>
                    ))}
                  </select>{" "}
                  {unconfirmedBadge(s.id)}
                </td>
                <td>
                  <input
                    type="number"
                    aria-label={`Years of ${skillName(s.skill)}`}
                    min={0}
                    max={60}
                    step={0.5}
                    value={s.years ?? ""}
                    onChange={(e) => (update(setSkillRows, i, { years: e.target.value === "" ? undefined : Number(e.target.value) }), touch(s.id))}
                  />
                </td>
                <td>
                  <select
                    aria-label="Proficiency"
                    value={s.proficiency ?? ""}
                    onChange={(e) => (update(setSkillRows, i, { proficiency: (e.target.value || undefined) as Skill["proficiency"] }), touch(s.id))}
                  >
                    <option value="">Proficiency…</option>
                    {PROFICIENCY.map((p) => (
                      <option key={p} value={p}>
                        {humanize(p)}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <button type="button" className="link small danger-text" onClick={() => setSkillRows(skillRows.filter((_, j) => j !== i))}>
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card
        title="Other facts"
        actions={
          <button type="button" className="secondary small" onClick={() => setAttributes([...attributes, { id: newId("attr", takenIds()), key: "", value: "" }])}>
            Add fact
          </button>
        }
      >
        <p className="muted small-text">
          Things a resume rarely says but applications ask: work authorization per country (e.g. <span className="mono">work-authorization.de</span> ={" "}
          <span className="mono">citizen</span>), notice period, preferred city. Only you can state these.
        </p>
        <datalist id="attribute-keys">
          {ATTRIBUTE_SUGGESTIONS.map((k) => (
            <option key={k} value={k} />
          ))}
        </datalist>
        <datalist id="authorization-values">
          {AUTHORIZATION_VALUES.map((v) => (
            <option key={v} value={v} />
          ))}
        </datalist>
        {attributes.map((a, i) => (
          <div className="row-between" key={a.id}>
            <input aria-label="Fact" list="attribute-keys" placeholder="key" value={a.key ?? ""} onChange={(e) => (update(setAttributes, i, { key: e.target.value }), touch(a.id))} />
            <input
              aria-label="Value"
              className="grow"
              list={a.key?.startsWith("work-authorization.") ? "authorization-values" : undefined}
              placeholder="value"
              value={a.value ?? ""}
              onChange={(e) => (update(setAttributes, i, { value: e.target.value }), touch(a.id))}
            />
            {unconfirmedBadge(a.id)}
            <button type="button" className="link small danger-text" onClick={() => setAttributes(attributes.filter((_, j) => j !== i))}>
              Remove
            </button>
          </div>
        ))}
      </Card>

      <Card>
        {problems.length > 0 && (
          <Notice tone="warning">
            <ul>
              {problems.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          </Notice>
        )}
        <label className="inline">
          <input type="checkbox" checked={confirmAll} onChange={(e) => setConfirmAll(e.target.checked)} /> I have checked everything above and it is
          true
        </label>
        <p className="muted small-text">
          Only confirmed facts are used in matching and in your applications. What you typed or changed counts as confirmed; anything left as it
          was imported stays unconfirmed unless you tick the box.
        </p>
        <button type="submit" disabled={busy}>
          {busy ? "Saving…" : submitLabel}
        </button>
      </Card>
    </form>
  );
}

function SkillPicker({ value, skills, onChange }: { value: string[]; skills: SkillOption[]; onChange: (v: string[]) => void }) {
  return (
    <div className="chips">
      {value.map((k) => (
        <span key={k} className="chip">
          {skills.find((s) => s.key === k)?.name ?? k}
          <button type="button" className="link" aria-label={`Remove ${k}`} onClick={() => onChange(value.filter((v) => v !== k))}>
            ×
          </button>
        </span>
      ))}
      <select aria-label="Add skill" value="" onChange={(e) => e.target.value && onChange([...value, e.target.value])}>
        <option value="">+ skill</option>
        {skills
          .filter((s) => !value.includes(s.key))
          .map((s) => (
            <option key={s.key} value={s.key}>
              {s.name}
            </option>
          ))}
      </select>
    </div>
  );
}
