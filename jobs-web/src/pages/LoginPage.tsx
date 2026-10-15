import { useState, type FormEvent } from "react";
import { useSession } from "../auth/session";
import { ErrorBanner, Field } from "../components/ui";
import { useAction } from "../lib/useResource";

export function LoginPage() {
  const { signIn } = useSession();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const action = useAction();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    void action.run(() => signIn(username.trim(), password));
  };

  return (
    <main className="centered">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Sign in</h1>
        <ErrorBanner error={action.error} />
        <Field label="Username">
          <input autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
        </Field>
        <Field label="Password">
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </Field>
        <button type="submit" disabled={action.busy}>
          {action.busy ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}
