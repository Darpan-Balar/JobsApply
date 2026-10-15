import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { api, unwrap, type Schemas } from "../api/client";
import { Badge, Card, Empty, ErrorBanner, Field, Loading } from "../components/ui";
import { formatDate, formatDateTime, humanize, today } from "../lib/format";
import { useAction, useResource } from "../lib/useResource";

type ContactInput = Schemas["TrackingViewsContactInput"];
const EMPTY_CONTACT: ContactInput = { name: "", email: "", phone: "", title: "", profileUrl: "", notes: "" };

function clean(input: ContactInput): ContactInput {
  const blank = (v?: string) => (v && v.trim() ? v.trim() : undefined);
  return {
    name: input.name.trim(),
    email: blank(input.email),
    phone: blank(input.phone),
    title: blank(input.title),
    profileUrl: blank(input.profileUrl),
    notes: blank(input.notes),
  };
}

export function TrackingPage() {
  const [tab, setTab] = useState<"follow-ups" | "interviews" | "contacts">("follow-ups");
  return (
    <>
      <h1>Tracking</h1>
      <div className="tabs" role="tablist">
        {(["follow-ups", "interviews", "contacts"] as const).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? "tab active" : "tab"} onClick={() => setTab(t)}>
            {humanize(t.replace("-", "_"))}
          </button>
        ))}
      </div>
      {tab === "follow-ups" && <FollowUps />}
      {tab === "interviews" && <Interviews />}
      {tab === "contacts" && <Contacts />}
    </>
  );
}

function FollowUps() {
  const [includeDone, setIncludeDone] = useState(false);
  const list = useResource(() => unwrap(api.GET("/api/v1/me/follow-ups", { params: { query: { includeDone } } })), [includeDone]);
  const contacts = useResource(() => unwrap(api.GET("/api/v1/me/contacts")), []);
  const action = useAction();
  const [dueOn, setDueOn] = useState(today());
  const [note, setNote] = useState("");
  const [contactId, setContactId] = useState("");

  const add = (e: FormEvent) => {
    e.preventDefault();
    void action.run(async () => {
      await unwrap(api.POST("/api/v1/me/follow-ups", { body: { dueOn, note, contactId: contactId || undefined } }));
      setNote("");
      list.reload();
    });
  };
  const act = (call: () => Promise<unknown>) =>
    void action.run(async () => {
      await call();
      list.reload();
    });
  const contactName = (id?: string) => contacts.data?.find((c) => c.id === id)?.name;

  return (
    <Card
      title="Follow-ups"
      actions={
        <label className="inline">
          <input type="checkbox" checked={includeDone} onChange={(e) => setIncludeDone(e.target.checked)} /> Show done
        </label>
      }
    >
      <ErrorBanner error={list.error ?? action.error} onDismiss={action.clearError} />
      <form className="toolbar" onSubmit={add}>
        <input type="date" aria-label="Due on" value={dueOn} onChange={(e) => setDueOn(e.target.value)} required />
        <input aria-label="Note" placeholder="What to do" value={note} onChange={(e) => setNote(e.target.value)} required maxLength={1000} />
        <select aria-label="Contact" value={contactId} onChange={(e) => setContactId(e.target.value)}>
          <option value="">No contact</option>
          {contacts.data?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <button type="submit" disabled={action.busy}>
          Add
        </button>
      </form>
      {list.loading ? (
        <Loading />
      ) : !list.data?.length ? (
        <Empty>Nothing to follow up.</Empty>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Due</th>
              <th>What</th>
              <th>About</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {list.data.map((f) => (
              <tr key={f.id} className={f.done ? "muted" : undefined}>
                <td className={f.overdue ? "danger-text" : undefined}>{formatDate(f.dueOn)}</td>
                <td>{f.note}</td>
                <td>
                  {f.applicationId && <Link to={`/applications/${f.applicationId}`}>application</Link>} {contactName(f.contactId)}
                </td>
                <td className="button-row">
                  {f.done ? (
                    <button
                      className="secondary small"
                      onClick={() => act(() => unwrap(api.POST("/api/v1/me/follow-ups/{id}/reopen", { params: { path: { id: f.id! } } })))}
                    >
                      Reopen
                    </button>
                  ) : (
                    <button
                      className="secondary small"
                      onClick={() => act(() => unwrap(api.POST("/api/v1/me/follow-ups/{id}/done", { params: { path: { id: f.id! } } })))}
                    >
                      Done
                    </button>
                  )}
                  <button
                    className="link small danger-text"
                    onClick={() =>
                      window.confirm("Delete this follow-up?") && act(() => unwrap(api.DELETE("/api/v1/me/follow-ups/{id}", { params: { path: { id: f.id! } } })))
                    }
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}

function Interviews() {
  const list = useResource(() => unwrap(api.GET("/api/v1/me/interviews")), []);
  return (
    <Card title="Interviews">
      <p className="muted small-text">Interviews are added from a submitted application.</p>
      <ErrorBanner error={list.error} />
      {list.loading ? (
        <Loading />
      ) : !list.data?.length ? (
        <Empty>No interviews yet.</Empty>
      ) : (
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Kind</th>
              <th>Where</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {list.data.map((i) => (
              <tr key={i.id}>
                <td>{formatDateTime(i.scheduledAt)}</td>
                <td>{humanize(i.kind)}</td>
                <td>{i.location}</td>
                <td>
                  <Badge value={i.status} />
                </td>
                <td>
                  <Link to={`/applications/${i.applicationId}`}>Application</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}

function Contacts() {
  const list = useResource(() => unwrap(api.GET("/api/v1/me/contacts")), []);
  const action = useAction();
  const [editing, setEditing] = useState<{ id?: string; input: ContactInput }>();

  const save = (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    void action.run(async () => {
      const body = clean(editing.input);
      if (editing.id) {
        await unwrap(api.PUT("/api/v1/me/contacts/{id}", { params: { path: { id: editing.id } }, body }));
      } else {
        await unwrap(api.POST("/api/v1/me/contacts", { body }));
      }
      setEditing(undefined);
      list.reload();
    });
  };
  const set = (field: keyof ContactInput) => (e: { target: { value: string } }) =>
    setEditing((current) => current && { ...current, input: { ...current.input, [field]: e.target.value } });

  return (
    <Card
      title="Contacts"
      actions={
        <button className="small" onClick={() => setEditing({ input: { ...EMPTY_CONTACT } })}>
          Add contact
        </button>
      }
    >
      <ErrorBanner error={list.error ?? action.error} onDismiss={action.clearError} />
      {editing && (
        <form className="form-grid" onSubmit={save}>
          <Field label="Name">
            <input value={editing.input.name} onChange={set("name")} required maxLength={200} />
          </Field>
          <Field label="Role">
            <input value={editing.input.title ?? ""} onChange={set("title")} maxLength={200} />
          </Field>
          <Field label="E-mail">
            <input type="email" value={editing.input.email ?? ""} onChange={set("email")} maxLength={320} />
          </Field>
          <Field label="Phone">
            <input value={editing.input.phone ?? ""} onChange={set("phone")} maxLength={50} />
          </Field>
          <Field label="Profile link">
            <input value={editing.input.profileUrl ?? ""} onChange={set("profileUrl")} maxLength={500} />
          </Field>
          <Field label="Notes">
            <textarea rows={3} value={editing.input.notes ?? ""} onChange={set("notes")} maxLength={4000} />
          </Field>
          <div className="button-row">
            <button type="submit" disabled={action.busy}>
              Save
            </button>
            <button type="button" className="secondary" onClick={() => setEditing(undefined)}>
              Cancel
            </button>
          </div>
        </form>
      )}
      {list.loading ? (
        <Loading />
      ) : !list.data?.length ? (
        <Empty>No contacts yet.</Empty>
      ) : (
        <ul className="list">
          {list.data.map((c) => (
            <li key={c.id} className="row-between">
              <span>
                <strong>{c.name}</strong> {c.title && <span className="muted">· {c.title}</span>}
                <div className="small-text muted">
                  {[c.email, c.phone].filter(Boolean).join(" · ")} · added {formatDateTime(c.createdAt)}
                </div>
                {c.notes && <div className="small-text">{c.notes}</div>}
              </span>
              <span className="button-row">
                <button
                  className="secondary small"
                  onClick={() =>
                    setEditing({
                      id: c.id,
                      input: { name: c.name ?? "", email: c.email, phone: c.phone, title: c.title, profileUrl: c.profileUrl, notes: c.notes },
                    })
                  }
                >
                  Edit
                </button>
                <button
                  className="link small danger-text"
                  onClick={() =>
                    window.confirm("Delete this contact? Follow-ups about them are kept.") &&
                    void action.run(async () => {
                      await unwrap(api.DELETE("/api/v1/me/contacts/{id}", { params: { path: { id: c.id! } } }));
                      list.reload();
                    })
                  }
                >
                  Delete
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
