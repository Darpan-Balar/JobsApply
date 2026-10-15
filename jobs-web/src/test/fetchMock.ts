import { vi } from "vitest";

export interface Call {
  method: string;
  path: string;
  headers: Headers;
  body?: unknown;
}

type Handler = (call: Call) => { status?: number; body?: unknown } | undefined;

/** A tiny fetch double: routes "METHOD /path" (query ignored) to handlers and records every call. */
export function mockFetch(routes: Record<string, Handler | { status?: number; body?: unknown }>) {
  const calls: Call[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = input instanceof Request ? input : new Request(new URL(String(input), "http://localhost"), init);
    const url = new URL(request.url, "http://localhost");
    const text = request.method === "GET" || request.method === "HEAD" ? "" : await request.clone().text();
    let body: unknown = text;
    try {
      body = text ? JSON.parse(text) : undefined;
    } catch {
      // form or raw body
    }
    const call: Call = { method: request.method, path: url.pathname, headers: request.headers, body };
    calls.push(call);
    const route = routes[`${request.method} ${url.pathname}`];
    const result = typeof route === "function" ? route(call) : route;
    if (!result) {
      return new Response(JSON.stringify({ title: "Not found" }), { status: 404, headers: { "Content-Type": "application/problem+json" } });
    }
    const status = result.status ?? 200;
    return new Response(status === 204 ? null : JSON.stringify(result.body ?? {}), {
      status,
      headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
    });
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}
