import { useState, type FormEvent } from "react";
import { api, ApiError, unwrap } from "../api/client";
import { useSession } from "../auth/session";
import { Card, ErrorBanner, Field, Notice } from "../components/ui";
import { useAction } from "../lib/useResource";

export function AccountPage() {
  const { user } = useSession();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [done, setDone] = useState(false);
  const action = useAction();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setDone(false);
    void action.run(async () => {
      if (next !== confirm) throw new ApiError(400, "The new passwords do not match");
      await unwrap(api.POST("/api/v1/me/password", { body: { currentPassword: current, newPassword: next } }));
      setCurrent("");
      setNext("");
      setConfirm("");
      setDone(true);
    });
  };

  return (
    <>
      <h1>Account</h1>
      <Card title="You">
        <dl className="facts">
          <dt>Username</dt>
          <dd>{user?.username}</dd>
          <dt>Name</dt>
          <dd>{user?.displayName}</dd>
          <dt>Role</dt>
          <dd>{user?.role === "ADMIN" ? "Administrator" : "User"}</dd>
        </dl>
      </Card>
      <Card title="Change password">
        <form onSubmit={submit} className="form-grid">
          <ErrorBanner error={action.error} />
          {done && <Notice tone="success">Your password was changed.</Notice>}
          <Field label="Current password">
            <input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
          </Field>
          <Field label="New password" hint="At least 12 characters">
            <input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required minLength={12} />
          </Field>
          <Field label="Repeat new password">
            <input type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
          </Field>
          <div>
            <button type="submit" disabled={action.busy}>
              Change password
            </button>
          </div>
        </form>
      </Card>
    </>
  );
}
