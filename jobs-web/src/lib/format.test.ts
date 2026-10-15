import { describe, expect, it } from "vitest";
import { formatDate, humanize, newIdempotencyKey } from "./format";

describe("format", () => {
  it("humanizes enum values", () => {
    expect(humanize("READY_FOR_REVIEW")).toBe("Ready for review");
    expect(humanize(undefined)).toBe("—");
  });

  it("shows calendar dates without shifting them across time zones", () => {
    expect(formatDate("2026-10-15")).toContain("15");
  });

  it("creates idempotency keys the API accepts", () => {
    expect(newIdempotencyKey()).toMatch(/^[A-Za-z0-9._:-]{8,100}$/);
  });
});
