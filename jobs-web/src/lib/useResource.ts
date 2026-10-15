import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../api/client";

export interface Resource<T> {
  data?: T;
  error?: ApiError;
  loading: boolean;
  reload: () => void;
}

/** Loads data when the dependencies change; `reload` fetches again. Late answers of superseded loads are ignored. */
export function useResource<T>(load: () => Promise<T>, deps: unknown[]): Resource<T> {
  const [state, setState] = useState<{ data?: T; error?: ApiError; loading: boolean }>({ loading: true });
  const generation = useRef(0);
  const [tick, setTick] = useState(0);
  const stableLoad = useCallback(load, deps);

  useEffect(() => {
    const current = ++generation.current;
    setState((s) => ({ ...s, loading: true }));
    stableLoad().then(
      (data) => current === generation.current && setState({ data, loading: false }),
      (error: unknown) =>
        current === generation.current &&
        setState({ error: error instanceof ApiError ? error : new ApiError(0, String(error)), loading: false }),
    );
  }, [stableLoad, tick]);

  return { ...state, reload: () => setTick((t) => t + 1) };
}

/** Runs one action at a time and remembers its error, for buttons and forms. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError>();
  const run = useCallback(async <T,>(action: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true);
    setError(undefined);
    try {
      return await action();
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)));
      return undefined;
    } finally {
      setBusy(false);
    }
  }, []);
  return { busy, error, run, clearError: () => setError(undefined) };
}
