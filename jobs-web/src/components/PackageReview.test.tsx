import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Schemas } from "../api/client";
import { PackageReview } from "./PackageReview";

const pkg: Schemas["PreparationViewsPackageView"] = {
  id: "pkg-1",
  versionNo: 1,
  content: {
    issues: [{ severity: "BLOCKING", code: "ANSWER_REQUIRED", message: "a required question needs your answer" }],
    answers: [
      { question: { id: "q1", text: "Will you require sponsorship?", required: true, kind: "SCREENING", options: ["Yes", "No"] }, draft: { status: "NEEDS_USER_INPUT" } },
      { question: { id: "q2", text: "Gender", kind: "SELF_IDENTIFICATION" }, draft: { status: "NEEDS_USER_INPUT" } },
    ],
    resume: { sections: [{ heading: "Experience", claims: [{ text: "Built a Kafka pipeline", sources: [{ candidateProfileVersionId: "v", factId: "f" }] }] }] },
    documents: [{ id: "d1", kind: "RESUME", mediaType: "application/pdf" }],
  },
};

describe("PackageReview", () => {
  it("shows issues, asks for missing answers, and never asks for self-identification", () => {
    render(<PackageReview applicationId="app-1" pkg={pkg} editable onChanged={() => {}} />);
    expect(screen.getByText("a required question needs your answer")).toBeInTheDocument();
    expect(screen.getAllByRole("combobox")).toHaveLength(1);
    expect(screen.getByText(/Voluntary self-identification/)).toBeInTheDocument();
    expect(screen.getByText("Built a Kafka pipeline")).toBeInTheDocument();
    expect(screen.getByText("(1 fact)")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Resume \(PDF\)/ })).toHaveAttribute("href", "/api/v1/me/applications/app-1/documents/d1");
  });
});
