import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ProfileEditor, type Snapshot } from "./ProfileEditor";

const SKILLS = [
  { key: "java", name: "Java" },
  { key: "kafka", name: "Kafka" },
];

const DRAFT: Snapshot = {
  contact: { fullName: "Jane Example", email: "jane@example.com" },
  experiences: [{ id: "exp-beta-ag", verification: "UNVERIFIED", title: "Senior Backend Engineer", employer: "Beta AG", start: "2023-01", skills: ["kafka"] }],
  achievements: [
    { id: "exp-beta-ag-a1", verification: "UNVERIFIED", text: "Built an event pipeline on Kafka.", experienceId: "exp-beta-ag", skills: ["kafka"] },
  ],
  education: [{ id: "edu-example-university", verification: "UNVERIFIED", institution: "Example University", level: "BACHELOR", completed: true }],
  skills: [{ id: "sk-java", verification: "UNVERIFIED", skill: "java" }],
  attributes: [],
};

function renderEditor(onSave = vi.fn(), initial: Snapshot = DRAFT) {
  render(<ProfileEditor initial={initial} skills={SKILLS} busy={false} submitLabel="Save profile" onSave={onSave} />);
  return onSave;
}

describe("ProfileEditor", () => {
  it("keeps untouched imported facts unconfirmed and confirms what you edit or add", async () => {
    const onSave = renderEditor();
    expect(screen.getAllByText("Unconfirmed").length).toBeGreaterThan(0);

    const achievement = screen.getByLabelText("Achievement");
    await userEvent.clear(achievement);
    await userEvent.type(achievement, "Built an event pipeline on Kafka processing 2M events per day.");
    await userEvent.click(screen.getByRole("button", { name: "Add fact" }));
    await userEvent.type(screen.getByLabelText("Fact"), "work-authorization.de");
    await userEvent.type(screen.getByLabelText("Value"), "citizen");
    await userEvent.click(screen.getByRole("button", { name: "Save profile" }));

    const saved = onSave.mock.calls[0]![0] as Snapshot;
    expect(saved.experiences![0]!.verification).toBe("UNVERIFIED");
    expect(saved.education![0]!.verification).toBe("UNVERIFIED");
    expect(saved.achievements![0]).toMatchObject({ verification: "VERIFIED", text: "Built an event pipeline on Kafka processing 2M events per day." });
    expect(saved.attributes![0]).toMatchObject({ key: "work-authorization.de", value: "citizen", verification: "VERIFIED" });
  });

  it("confirms everything when you say all of it is true", async () => {
    const onSave = renderEditor();
    await userEvent.click(screen.getByLabelText(/I have checked everything above/));
    await userEvent.click(screen.getByRole("button", { name: "Save profile" }));
    const saved = onSave.mock.calls[0]![0] as Snapshot;
    const all = [...saved.experiences!, ...saved.achievements!, ...saved.education!, ...saved.skills!];
    expect(all.every((f) => f.verification === "VERIFIED")).toBe(true);
  });

  it("will not save a position without its start month", async () => {
    const onSave = renderEditor(vi.fn(), { experiences: [{ id: "exp-x", verification: "UNVERIFIED", title: "Engineer", employer: "Acme", start: "" }] } as Snapshot);
    await userEvent.click(screen.getByRole("button", { name: "Save profile" }));
    expect(screen.getByText(/Position 1: title, employer and start month are required/)).toBeInTheDocument();
    expect(onSave).not.toHaveBeenCalled();
  });

  it("adds skills from the catalog to a position", async () => {
    const onSave = renderEditor();
    const position = screen.getByText(/Senior Backend Engineer · Beta AG/).closest("fieldset")!;
    await userEvent.selectOptions(within(position as HTMLElement).getByLabelText("Add skill"), "java");
    await userEvent.click(screen.getByRole("button", { name: "Save profile" }));
    const saved = onSave.mock.calls[0]![0] as Snapshot;
    expect(saved.experiences![0]).toMatchObject({ skills: ["kafka", "java"], verification: "VERIFIED" });
  });
});
