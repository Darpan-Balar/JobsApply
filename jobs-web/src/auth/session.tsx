import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api, rawRequest, unwrap, UNAUTHENTICATED_EVENT, type Schemas } from "../api/client";

export type Me = Schemas["SessionViewMe"];

interface SessionState {
  loading: boolean;
  user?: Me;
  setupRequired: boolean;
  setupAllowedHere: boolean;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  refresh: () => Promise<void>;
}

const SessionContext = createContext<SessionState | undefined>(undefined);

/** The signed-in user, loaded from the server (the session cookie is HttpOnly; nothing is stored in the browser). */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ loading: boolean; user?: Me; setupRequired: boolean; setupAllowedHere: boolean }>({
    loading: true,
    setupRequired: false,
    setupAllowedHere: false,
  });

  const refresh = useCallback(async () => {
    const session = await unwrap(api.GET("/api/v1/auth/session"));
    setState({
      loading: false,
      user: session.user ?? undefined,
      setupRequired: session.setupRequired ?? false,
      setupAllowedHere: session.setupAllowedHere ?? false,
    });
  }, []);

  useEffect(() => {
    refresh().catch(() => setState((s) => ({ ...s, loading: false })));
    const expired = () => setState((s) => ({ ...s, user: undefined }));
    window.addEventListener(UNAUTHENTICATED_EVENT, expired);
    return () => window.removeEventListener(UNAUTHENTICATED_EVENT, expired);
  }, [refresh]);

  const signIn = useCallback(
    async (username: string, password: string) => {
      const body = new URLSearchParams({ username, password });
      await rawRequest("/api/v1/auth/login", { method: "POST", body, contentType: "application/x-www-form-urlencoded" });
      await refresh();
    },
    [refresh],
  );

  const signOut = useCallback(async () => {
    try {
      await rawRequest("/api/v1/auth/logout", { method: "POST" });
    } finally {
      await refresh().catch(() => setState((s) => ({ ...s, user: undefined })));
    }
  }, [refresh]);

  return <SessionContext.Provider value={{ ...state, signIn, signOut, refresh }}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionState {
  const session = useContext(SessionContext);
  if (!session) throw new Error("useSession outside SessionProvider");
  return session;
}
