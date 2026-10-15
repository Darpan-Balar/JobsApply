import { useState, type FormEvent } from "react";
import { api, ApiError, unwrap } from "../api/client";
import { useSession } from "../auth/session";
import { ErrorBanner, Field, Notice } from "../components/ui";
import { useAction } from "../lib/useResource";

/** One-time creation of the first (administrator) account, offered only on the machine the platform runs on. */
export function SetupPage() {
  const { setupAllowedHere, signIn } = useSession();
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const action = useAction();

  if (!setupAllowedHere) {
    return (
      <main className="centered">
        <div className="card auth-card">
          <h1>Not set up yet</h1>
          <p>Open the dashboard on the machine the platform runs on to create the first account.</p>
        </div>
      </main>
    );
  }

  const submit = (e: FormEvent) => {
    e.preventDefault();
    void action.run(async () => {
      if (password !== confirm) {
        throw new ApiError(400, "The passwords do not match");
      }
      await unwrap(api.POST("/api/v1/auth/setup", { body: { username: username.trim(), displayName: displayName.trim(), password } }));
      await signIn(username.trim(), password);
    });
  };

  return (
    <main className="centered">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Create your account</h1>
        <Notice>
          This first account becomes the administrator. Anything you already stored (profiles, applications) stays with it.
        </Notice>
        <ErrorBanner error={action.error} />
        <Field label="Username" hint="Lower-case letters, digits, '.', '_' or '-'">
          <input autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
        </Field>
        <Field label="Your name">
          <input autoComplete="name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
        </Field>
        <Field label="Password" hint="At least 12 characters; a passphrase works well">
          <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={12} />
        </Field>
        <Field label="Repeat password">
          <input type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </Field>
        <button type="submit" disabled={action.busy}>
          {action.busy ? "Creating…" : "Create account"}
        </button>
      </form>
    </main>
  );
}
