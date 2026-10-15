import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { SessionProvider } from "./auth/session";
import { mockFetch } from "./test/fetchMock";

function renderApp() {
  return render(
    <MemoryRouter>
      <SessionProvider>
        <App />
      </SessionProvider>
    </MemoryRouter>,
  );
}

const EMPTY_HOME = {
  "GET /api/v1/me/applications": { body: [] },
  "GET /api/v1/me/tracking/summary": { body: { upcomingInterviews: [], openFollowUps: [], overdueFollowUps: 0 } },
};

describe("App", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("asks for sign-in when nobody is signed in, and signs in", async () => {
    let signedIn = false;
    const calls = mockFetch({
      "GET /api/v1/auth/session": () => ({
        body: signedIn ? { user: { id: "u1", username: "jane", displayName: "Jane", role: "USER" }, setupRequired: false } : { setupRequired: false },
      }),
      "POST /api/v1/auth/login": () => {
        signedIn = true;
        return { body: {} };
      },
      ...EMPTY_HOME,
    });
    renderApp();

    await userEvent.type(await screen.findByLabelText("Username"), "jane");
    await userEvent.type(screen.getByLabelText("Password"), "a long password");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("heading", { name: "Overview" })).toBeInTheDocument();
    const login = calls.find((c) => c.path === "/api/v1/auth/login")!;
    expect(login.body).toBe("username=jane&password=a+long+password");
    expect(screen.queryByRole("link", { name: "Admin" })).not.toBeInTheDocument();
  });

  it("shows the sign-in error without revealing which part was wrong", async () => {
    mockFetch({
      "GET /api/v1/auth/session": { body: { setupRequired: false } },
      "POST /api/v1/auth/login": { status: 401, body: { title: "Sign-in failed", detail: "Invalid username or password" } },
    });
    renderApp();
    await userEvent.type(await screen.findByLabelText("Username"), "jane");
    await userEvent.type(screen.getByLabelText("Password"), "wrong");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid username or password");
  });

  it("offers the one-time setup only on this machine", async () => {
    mockFetch({ "GET /api/v1/auth/session": { body: { setupRequired: true, setupAllowedHere: true } } });
    renderApp();
    expect(await screen.findByRole("heading", { name: "Create your account" })).toBeInTheDocument();
    vi.unstubAllGlobals();

    mockFetch({ "GET /api/v1/auth/session": { body: { setupRequired: true, setupAllowedHere: false } } });
    renderApp();
    expect(await screen.findByRole("heading", { name: "Not set up yet" })).toBeInTheDocument();
  });

  it("refuses a setup whose passwords differ, before calling the server", async () => {
    const calls = mockFetch({ "GET /api/v1/auth/session": { body: { setupRequired: true, setupAllowedHere: true } } });
    renderApp();
    await userEvent.type(await screen.findByLabelText("Username"), "jane");
    await userEvent.type(screen.getByLabelText("Your name"), "Jane");
    await userEvent.type(screen.getByLabelText("Password"), "a long enough password");
    await userEvent.type(screen.getByLabelText("Repeat password"), "a different password");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("do not match");
    expect(calls.some((c) => c.path === "/api/v1/auth/setup")).toBe(false);
  });

  it("shows administration only to administrators", async () => {
    mockFetch({
      "GET /api/v1/auth/session": { body: { user: { id: "u1", username: "ada", displayName: "Ada", role: "ADMIN" }, setupRequired: false } },
      ...EMPTY_HOME,
    });
    renderApp();
    expect(await screen.findByRole("link", { name: "Admin" })).toBeInTheDocument();
  });
});
