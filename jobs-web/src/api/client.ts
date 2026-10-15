import createClient, { type Middleware } from "openapi-fetch";
import type { components, paths } from "./schema";

export type Schemas = components["schemas"];

/** An RFC 9457 problem returned by the API, as a typed error. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail?: string,
    readonly fieldErrors: { field: string; message: string }[] = [],
  ) {
    super(detail ? `${title}: ${detail}` : title);
  }
}

export const UNAUTHENTICATED_EVENT = "jobs:unauthenticated";

/** The CSRF token the server issued as the XSRF-TOKEN cookie (SPA pattern; sent back as X-XSRF-TOKEN). */
export function csrfToken(): string {
  const match = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="));
  return match ? decodeURIComponent(match.substring("XSRF-TOKEN=".length)) : "";
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

const security: Middleware = {
  onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method)) {
      request.headers.set("X-XSRF-TOKEN", csrfToken());
    }
    return request;
  },
  onResponse({ response, request }) {
    if (response.status === 401 && !new URL(request.url).pathname.startsWith("/api/v1/auth/")) {
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT));
    }
    return response;
  },
};

/** Same origin as the page (absolute, so it also works where Request rejects relative URLs, e.g. tests). */
export const api = createClient<paths>({
  baseUrl: globalThis.location?.origin ?? "",
  credentials: "same-origin",
  // resolved per call (not captured once), so the current fetch is always used
  fetch: (request: Request) => globalThis.fetch(request),
});
api.use(security);

async function problemOf(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { title?: string; detail?: string; errors?: { field: string; message: string }[] };
    return new ApiError(response.status, body.title ?? response.statusText, body.detail, body.errors ?? []);
  } catch {
    return new ApiError(response.status, response.statusText || "Request failed");
  }
}

/** Unwraps an openapi-fetch result: the data, or an {@link ApiError}. */
export async function unwrap<T>(call: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await call;
  if (error !== undefined || !response.ok) {
    if (error && typeof error === "object" && "title" in error) {
      const p = error as { title: string; detail?: string; errors?: { field: string; message: string }[] };
      throw new ApiError(response.status, p.title, p.detail, p.errors ?? []);
    }
    throw new ApiError(response.status, response.statusText || "Request failed");
  }
  return data as T;
}

/**
 * For the few calls outside the typed contract: form sign-in/out (Spring Security) and raw YAML or file uploads.
 */
export async function rawRequest(path: string, init: RequestInit & { contentType?: string } = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  const method = (init.method ?? "GET").toUpperCase();
  if (!SAFE_METHODS.has(method)) {
    headers.set("X-XSRF-TOKEN", csrfToken());
  }
  if (init.contentType) {
    headers.set("Content-Type", init.contentType);
  }
  const response = await fetch(path, { ...init, method, headers, credentials: "same-origin" });
  if (!response.ok) {
    throw await problemOf(response);
  }
  return response;
}
