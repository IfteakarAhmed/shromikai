import { describe, expect, it } from "vitest";

import { reviewFindings, verificationStatuses } from "@/mocks/contract-review";

describe("contract review mock citations", () => {
  it("only allows the three named verification states", () => {
    expect(verificationStatuses).toEqual(["verified", "uncertain", "rejected"]);
  });

  it("links every displayed legal citation to the official Bangladesh law source", () => {
    const citations = reviewFindings.flatMap((finding) => finding.citations);

    expect(citations.length).toBeGreaterThan(0);
    expect(
      citations.every((citation) => new URL(citation.href).hostname === "bdlaws.minlaw.gov.bd"),
    ).toBe(true);
  });

  it("does not show a legal citation for an uncertain finding", () => {
    const uncertainFinding = reviewFindings.find((finding) => finding.verification === "uncertain");

    expect(uncertainFinding?.citations).toEqual([]);
  });
});
