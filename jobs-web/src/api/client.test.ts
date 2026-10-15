import { afterEach, describe, expect, it, vi } from "vitest";
import { mockFetch } from "../test/fetchMock";
import { api, ApiError, csrfToken, rawRequest, unwrap, UNAUTHENTICATED_EVENT } from "./client";

describe("api client", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("sends the CSRF token on writes but not on reads", async () => {
    document.cookie = "XSRF-TOKEN=token-123";
    const calls = mockFetch({ "GET /api/v1/me/contacts": { body: [] }, "POST /api/v1/me/contacts": { status: 201, body: { id: "c1" } } });

    await unwrap(api.GET("/api/v1/me/contacts"));
    await unwrap(api.POST("/api/v1/me/contacts", { body: { name: "Rita" } }));

    expect(csrfToken()).toBe("token-123");
    expect(calls[0]!.headers.get("X-XSRF-TOKEN")).toBeNull();
    expect(calls[1]!.headers.get("X-XSRF-TOKEN")).toBe("token-123");
  });

  it("turns problem details into ApiError", async () => {
    mockFetch({ "POST /api/v1/me/contacts": { status: 409, body: { title: "Business rule violated", detail: "No" } } });
    await expect(unwrap(api.POST("/api/v1/me/contacts", { body: { name: "x" } }))).rejects.toMatchObject({
      status: 409,
      title: "Business rule violated",
      detail: "No",
    });
  });

  it("announces an expired session, except for the auth endpoints themselves", async () => {
    mockFetch({ "GET /api/v1/me/contacts": { status: 401, body: { title: "Unauthenticated" } }, "GET /api/v1/auth/session": { status: 401 } });
    const listener = vi.fn();
    window.addEventListener(UNAUTHENTICATED_EVENT, listener);

    await expect(unwrap(api.GET("/api/v1/me/contacts"))).rejects.toBeInstanceOf(ApiError);
    await expect(unwrap(api.GET("/api/v1/auth/session"))).rejects.toBeInstanceOf(ApiError);

    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(UNAUTHENTICATED_EVENT, listener);
  });

  it("raw requests carry the token and report problems", async () => {
    document.cookie = "XSRF-TOKEN=raw-token";
    const calls = mockFetch({ "POST /api/v1/auth/login": { status: 401, body: { title: "Sign-in failed", detail: "Invalid username or password" } } });
    await expect(rawRequest("/api/v1/auth/login", { method: "POST", body: "username=a&password=b" })).rejects.toMatchObject({
      detail: "Invalid username or password",
    });
    expect(calls[0]!.headers.get("X-XSRF-TOKEN")).toBe("raw-token");
  });
});
