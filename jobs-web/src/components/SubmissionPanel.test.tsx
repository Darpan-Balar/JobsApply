import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Schemas } from "../api/client";
import { mockFetch } from "../test/fetchMock";
import { OutcomeForm, SubmissionPanel } from "./SubmissionPanel";

function detail(status: string): Schemas["PreparationViewsApplicationDetail"] {
  return {
    application: { id: "app-1", status: status as Schemas["ApplicationView"]["status"], postingId: "post-1", history: [] },
    currentPackage: { id: "pkg-2", versionNo: 2, content: { destination: { applicationUrl: "https://jobs.example/apply" } } },
  };
}

describe("SubmissionPanel", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("cannot approve while something blocks", async () => {
    mockFetch({
      "GET /api/v1/me/applications/app-1/attempts": { body: [] },
      "GET /api/v1/me/applications/app-1/approval/blockers": { body: ["required question unanswered: Visa?"] },
    });
    render(<SubmissionPanel detail={detail("READY_FOR_REVIEW")} onChanged={() => {}} />);
    expect(await screen.findByText("required question unanswered: Visa?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve this version" })).toBeDisabled();
  });

  it("approves exactly the version on screen", async () => {
    const calls = mockFetch({
      "GET /api/v1/me/applications/app-1/attempts": { body: [] },
      "GET /api/v1/me/applications/app-1/approval/blockers": { body: [] },
      "POST /api/v1/me/applications/app-1/approval": { body: { id: "a1" } },
    });
    const changed = vi.fn();
    render(<SubmissionPanel detail={detail("READY_FOR_REVIEW")} onChanged={changed} />);
    const approve = await screen.findByRole("button", { name: "Approve this version" });
    await vi.waitFor(() => expect(approve).toBeEnabled());
    await userEvent.click(approve);
    await vi.waitFor(() => expect(changed).toHaveBeenCalled());
    expect(calls.find((c) => c.method === "POST")!.body).toEqual({ packageId: "pkg-2" });
  });

  it("starts a submission with an idempotency key and reuses it on retry", async () => {
    let attempts = 0;
    const calls = mockFetch({
      "GET /api/v1/me/applications/app-1/attempts": { body: [] },
      "POST /api/v1/me/applications/app-1/submission": () => (++attempts === 1 ? { status: 503, body: { title: "Unavailable" } } : { body: {} }),
    });
    render(<SubmissionPanel detail={detail("APPROVED")} onChanged={() => {}} />);
    const manual = await screen.findByRole("button", { name: "I will submit it myself" });
    await userEvent.click(manual);
    await screen.findByRole("alert");
    await userEvent.click(manual);
    const starts = calls.filter((c) => c.path.endsWith("/submission"));
    expect(starts).toHaveLength(2);
    expect(starts[0]!.headers.get("Idempotency-Key")).toMatch(/^ui-/);
    expect(starts[1]!.headers.get("Idempotency-Key")).toBe(starts[0]!.headers.get("Idempotency-Key"));
  });
});

describe("OutcomeForm", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends evidence for a confirmed submission", async () => {
    const calls = mockFetch({ "POST /api/v1/me/applications/app-1/submission/att-1/outcome": { body: {} } });
    render(<OutcomeForm mode="report" applicationId="app-1" attemptId="att-1" onDone={() => {}} />);
    await userEvent.type(screen.getByLabelText("What it says (e.g. the e-mail subject)"), "Thanks for applying");
    await userEvent.click(screen.getByRole("button", { name: "Record" }));
    await vi.waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]!.body).toEqual({ outcome: "CONFIRMED", evidence: { kind: "CONFIRMATION_EMAIL", detail: "Thanks for applying" } });
  });

  it("an uncertain outcome sends no evidence, and verification cannot be uncertain", async () => {
    const calls = mockFetch({ "POST /api/v1/me/applications/app-1/submission/att-1/outcome": { body: {} } });
    const { unmount } = render(<OutcomeForm mode="report" applicationId="app-1" attemptId="att-1" onDone={() => {}} />);
    await userEvent.selectOptions(screen.getByLabelText("How did it go?"), "UNCONFIRMED");
    await userEvent.click(screen.getByRole("button", { name: "Record" }));
    await vi.waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]!.body).toEqual({ outcome: "UNCONFIRMED" });
    unmount();

    render(<OutcomeForm mode="verify" applicationId="app-1" onDone={() => {}} />);
    expect(screen.queryByRole("option", { name: "I am not sure" })).not.toBeInTheDocument();
  });
});
