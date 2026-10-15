import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "../auth/session";
import { mockFetch } from "../test/fetchMock";
import { AdminPage } from "./AdminPage";

describe("AdminPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("shows the system state and reports a sync that is already running", async () => {
    const calls = mockFetch({
      "GET /api/v1/auth/session": { body: { user: { id: "a1", username: "ada", displayName: "Ada", role: "ADMIN" } } },
      "GET /api/v1/admin/users": { body: [] },
      "GET /api/v1/sync-runs": { body: [] },
      "GET /api/v1/admin/system": {
        body: { schemaVersion: "14", scheduledSyncEnabled: true, syncSchedule: "0 0 6 * * *", retentionEnabled: true, pendingEvents: 2 },
      },
      "GET /actuator/health": { body: { status: "WARNING", components: { eventPublications: { status: "WARNING" }, db: { status: "UP" } } } },
      "POST /api/v1/admin/system/sync": { body: { ran: false } },
    });
    render(
      <SessionProvider>
        <AdminPage />
      </SessionProvider>,
    );

    expect(await screen.findByText("version 14")).toBeInTheDocument();
    expect(await screen.findByText(/eventPublications: WARNING/)).toBeInTheDocument();
    expect(screen.getByText("2")).toHaveClass("warning-text");

    await userEvent.click(screen.getByRole("button", { name: "Synchronize all employers now" }));
    expect(await screen.findByText("A synchronization is already running.")).toBeInTheDocument();
    expect(calls.some((c) => c.method === "POST" && c.path === "/api/v1/admin/system/sync")).toBe(true);
  });
});
