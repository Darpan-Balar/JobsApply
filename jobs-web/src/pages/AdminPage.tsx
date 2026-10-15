import { useState, type FormEvent } from "react";
import { api, rawRequest, unwrap, type Schemas } from "../api/client";
import { useSession } from "../auth/session";
import { Badge, Card, ErrorBanner, Field, Loading, Notice } from "../components/ui";
import { formatDateTime, humanize } from "../lib/format";
import { useAction, useResource } from "../lib/useResource";

/** Accounts and the shared catalog. Administrators never see other users' personal data. */
export function AdminPage() {
  return (
    <>
      <h1>Administration</h1>
      <Notice>Changes here affect everyone: the shared job catalog and who can sign in. No one's personal data is visible here.</Notice>
      <div className="columns">
        <Accounts />
        <div>
          <SystemCard />
          <Catalog />
          <SyncRuns />
        </div>
      </div>
    </>
  );
}

function Accounts() {
  const { user } = useSession();
  const users = useResource(() => unwrap(api.GET("/api/v1/admin/users")), []);
  const action = useAction();
  const [form, setForm] = useState({ username: "", displayName: "", email: "", password: "", role: "USER" as "USER" | "ADMIN" });

  const create = (e: FormEvent) => {
    e.preventDefault();
    void action.run(async () => {
      await unwrap(
        api.POST("/api/v1/admin/users", {
          body: {
            username: form.username.trim(),
            displayName: form.displayName.trim(),
            email: form.email.trim() || undefined,
            password: form.password,
            role: form.role,
          },
        }),
      );
      setForm({ username: "", displayName: "", email: "", password: "", role: "USER" });
      users.reload();
    });
  };

  const reset = (account: Schemas["UserSummary"]) => {
    const password = window.prompt(`New password for ${account.username} (at least 12 characters):`);
    if (!password) return;
    void action.run(() => unwrap(api.POST("/api/v1/admin/users/{id}/password", { params: { path: { id: account.id! } }, body: { newPassword: password } })));
  };

  return (
    <Card title="Accounts">
      <ErrorBanner error={users.error ?? action.error} onDismiss={action.clearError} />
      {users.loading ? (
        <Loading />
      ) : (
        <table>
          <thead>
            <tr>
              <th>User</th>
              <th>Role</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.data?.map((u) => (
              <tr key={u.id}>
                <td>
                  {u.displayName} <span className="muted small-text">({u.username})</span>
                  {!u.canSignIn && <div className="small-text muted">cannot sign in (no password)</div>}
                </td>
                <td>{humanize(u.role)}</td>
                <td>
                  <Badge value={u.status} />
                </td>
                <td className="button-row">
                  <button className="secondary small" onClick={() => reset(u)}>
                    Set password
                  </button>
                  {u.id !== user?.id && u.status === "ACTIVE" && (
                    <button
                      className="link small danger-text"
                      onClick={() =>
                        window.confirm(`Disable ${u.username}? They are signed out and cannot sign in.`) &&
                        void action.run(async () => {
                          await unwrap(api.POST("/api/v1/admin/users/{id}/disable", { params: { path: { id: u.id! } } }));
                          users.reload();
                        })
                      }
                    >
                      Disable
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <h3>Add someone</h3>
      <form className="form-grid" onSubmit={create}>
        <Field label="Username">
          <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required />
        </Field>
        <Field label="Name">
          <input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} required />
        </Field>
        <Field label="E-mail (optional)">
          <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Initial password" hint="At least 12 characters; they can change it under Account">
          <input
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required
            minLength={12}
          />
        </Field>
        <Field label="Role">
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as "USER" | "ADMIN" })}>
            <option value="USER">User</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </Field>
        <div>
          <button type="submit" disabled={action.busy}>
            Create account
          </button>
        </div>
      </form>
    </Card>
  );
}

function Catalog() {
  const action = useAction();
  const [message, setMessage] = useState<string>();
  return (
    <Card title="Shared catalog">
      <ErrorBanner error={action.error} onDismiss={action.clearError} />
      {message && <Notice tone="success">{message}</Notice>}
      <div className="button-row">
        <button
          disabled={action.busy}
          onClick={() =>
            void action.run(async () => {
              const report = await unwrap(api.POST("/api/v1/config/import", { params: { query: { dryRun: false } } }));
              setMessage(
                report.issues?.length
                  ? `${report.issues.length} configuration issue(s); nothing applied.`
                  : `Configuration imported (${report.outcomes?.length ?? 0} documents).`,
              );
            })
          }
        >
          Import configuration directory
        </button>
        <button
          className="secondary"
          disabled={action.busy}
          onClick={() =>
            void action.run(async () => {
              const result = await unwrap(api.POST("/api/v1/admin/system/sync"));
              setMessage(result.ran ? "Synchronization finished; see the runs below." : "A synchronization is already running.");
            })
          }
        >
          {action.busy ? "Working…" : "Synchronize all employers now"}
        </button>
      </div>
    </Card>
  );
}

interface Health {
  status: string;
  components?: Record<string, { status: string; details?: Record<string, unknown> }>;
}

function SystemCard() {
  const system = useResource(() => unwrap(api.GET("/api/v1/admin/system")), []);
  const health = useResource(async () => (await (await rawRequest("/actuator/health")).json()) as Health, []);
  const action = useAction();
  const [message, setMessage] = useState<string>();
  const s = system.data;
  return (
    <Card
      title="System"
      actions={
        <button className="secondary small" onClick={() => (system.reload(), health.reload())}>
          Refresh
        </button>
      }
    >
      <ErrorBanner error={system.error ?? health.error ?? action.error} onDismiss={action.clearError} />
      {message && <Notice tone="success">{message}</Notice>}
      {health.data && (
        <p>
          Health: <Badge value={health.data.status === "UP" ? "COMPLETED" : health.data.status} />{" "}
          {Object.entries(health.data.components ?? {})
            .filter(([, c]) => c.status !== "UP")
            .map(([name, c]) => `${name}: ${c.status}`)
            .join(", ")}
        </p>
      )}
      {s && (
        <dl className="facts">
          <dt>Database schema</dt>
          <dd>version {s.schemaVersion}</dd>
          <dt>Scheduled sync</dt>
          <dd>
            {s.scheduledSyncEnabled ? `on (${s.syncSchedule})` : "off"} · last {formatDateTime(s.lastScheduledSync)}
          </dd>
          <dt>Retention</dt>
          <dd>{s.retentionEnabled ? `on (${s.retentionSchedule}), sync runs kept ${s.syncRunRetention}` : "off"}</dd>
          <dt>Undelivered events</dt>
          <dd className={s.pendingEvents ? "warning-text" : undefined}>{s.pendingEvents ?? 0}</dd>
        </dl>
      )}
      <div className="button-row">
        <button
          className="secondary small"
          disabled={action.busy}
          onClick={() =>
            void action.run(async () => {
              const result = await unwrap(api.POST("/api/v1/admin/system/retention"));
              setMessage(result.ran ? "Retention applied." : "Retention is already running.");
              system.reload();
            })
          }
        >
          Apply retention now
        </button>
      </div>
    </Card>
  );
}

function SyncRuns() {
  const runs = useResource(() => unwrap(api.GET("/api/v1/sync-runs", { params: { query: { limit: 15 } } })), []);
  return (
    <Card
      title="Recent synchronizations"
      actions={
        <button className="secondary small" onClick={runs.reload}>
          Refresh
        </button>
      }
    >
      <ErrorBanner error={runs.error} />
      {runs.loading ? (
        <Loading />
      ) : (
        <ul className="list compact">
          {runs.data?.map((r) => (
            <li key={r.id}>
              <Badge value={r.status === "SUCCEEDED" ? "COMPLETED" : r.status} /> {r.employerKey ?? r.sourceKey} · {formatDateTime(r.startedAt)}
              {r.errorSummary && <div className="small-text danger-text">{r.errorSummary}</div>}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
