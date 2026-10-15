import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { mockFetch } from "../test/fetchMock";
import { ProfileEditPage } from "./ProfileEditPage";

function renderNew() {
  render(
    <MemoryRouter initialEntries={["/profiles/new"]}>
      <Routes>
        <Route path="/profiles/new" element={<ProfileEditPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("New profile from a resume", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("needs explicit consent before the resume can be sent to Claude", async () => {
    mockFetch({
      "GET /api/v1/me/candidate-profiles/extractors": {
        body: [
          { id: "heuristic-resume-extractor", label: "On this machine", sendsTextExternally: false },
          { id: "claude-resume-extractor", label: "Claude (claude-opus-5-5)", sendsTextExternally: true },
        ],
      },
      "GET /api/v1/skills": { body: [] },
    });
    renderNew();
    await userEvent.upload(screen.getByLabelText("Resume file"), new File(["%PDF-1.7"], "cv.pdf", { type: "application/pdf" }));
    const read = screen.getByRole("button", { name: "Read my resume" });
    expect(read).toBeEnabled();

    await userEvent.click(await screen.findByLabelText(/Claude \(claude-opus-5-5\)/));
    expect(read).toBeDisabled();
    await userEvent.click(screen.getByLabelText(/Send the text of my resume to Anthropic/));
    expect(read).toBeEnabled();
  });

  it("shows the draft with its notes for review before anything is saved", async () => {
    const calls = mockFetch({
      "GET /api/v1/me/candidate-profiles/extractors": { body: [{ id: "heuristic-resume-extractor", label: "On this machine", sendsTextExternally: false }] },
      "GET /api/v1/skills": { body: [{ key: "java", name: "Java" }] },
      "POST /api/v1/me/candidate-profiles/drafts": {
        body: {
          extractor: "heuristic-resume-extractor",
          sentExternally: false,
          notes: ["'Delta Corp' gives only the year it started (2017); add the month."],
          unknownSkills: ["pandas"],
          snapshot: { contact: { fullName: "Jane Example" }, experiences: [], education: [], skills: [], achievements: [], attributes: [] },
        },
      },
    });
    renderNew();
    await userEvent.upload(screen.getByLabelText("Resume file"), new File(["Jane Example\nEngineer"], "cv.txt", { type: "text/plain" }));
    await userEvent.click(screen.getByRole("button", { name: "Read my resume" }));

    expect(await screen.findByText(/only the year it started \(2017\)/)).toBeInTheDocument();
    expect(screen.getByText(/Not in the skill catalog \(left out\): pandas/)).toBeInTheDocument();
    expect(screen.getByDisplayValue("Jane Example")).toBeInTheDocument();
    expect(calls.some((c) => c.method === "PUT")).toBe(false);
  });
});
