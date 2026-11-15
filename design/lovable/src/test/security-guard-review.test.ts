import { describe, expect, it } from "vitest";

import { validateContractFile, MAX_CONTRACT_FILE_BYTES } from "@/lib/contract-file-validation";
import {
  recordedFindings,
  recordedLawSections,
  sortRecordedFindings,
  uncertainFinding,
} from "@/mocks/security-guard-review";
import bn from "@/locales/bn.json";
import en from "@/locales/en.json";

function readTranslation(dictionary: unknown, key: string): unknown {
  return key.split(".").reduce<unknown>((value, part) => {
    if (typeof value !== "object" || value === null || !(part in value)) return undefined;
    return Reflect.get(value, part) as unknown;
  }, dictionary);
}

describe("security guard recorded sample", () => {
  it("contains ten findings with legal citations and sorted severity", () => {
    expect(recordedFindings).toHaveLength(10);
    expect(recordedFindings.every((finding) => finding.sectionIds.length > 0)).toBe(true);
    expect(sortRecordedFindings(recordedFindings).map((finding) => finding.severity)).toEqual([
      "critical",
      "critical",
      "high",
      "high",
      "high",
      "high",
      "medium",
      "medium",
      "medium",
      "medium",
    ]);
  });

  it("keeps the uncertain probation-pay finding free of citations", () => {
    expect(uncertainFinding.verification).toBe("uncertain");
    expect(uncertainFinding.sectionIds).toEqual([]);
  });

  it("keeps all ten finding translations available in Bangla and English", () => {
    for (const finding of recordedFindings) {
      for (const dictionary of [bn, en]) {
        expect(readTranslation(dictionary, finding.titleKey)).toEqual(expect.any(String));
        expect(readTranslation(dictionary, finding.clauseKey)).toEqual(expect.any(String));
        expect(readTranslation(dictionary, finding.explanationKey)).toEqual(expect.any(String));
      }
    }
  });

  it("highlights text contained in every complete official section excerpt", () => {
    for (const section of Object.values(recordedLawSections)) {
      for (const dictionary of [bn, en]) {
        expect(readTranslation(dictionary, section.titleKey)).toEqual(expect.any(String));
        const text = readTranslation(dictionary, section.textKey);
        const highlight = readTranslation(dictionary, section.highlightKey);
        expect(typeof text).toBe("string");
        expect(typeof highlight).toBe("string");
        expect((text as string).includes(highlight as string)).toBe(true);
      }
    }
  });

  it("keeps all displayed model labels translated in both languages", () => {
    for (const finding of [...recordedFindings, uncertainFinding]) {
      expect(readTranslation(bn, finding.modelKey)).toEqual(expect.any(String));
      expect(readTranslation(en, finding.modelKey)).toEqual(expect.any(String));
    }
  });

  it("links every cited section to the official government law repository", () => {
    expect(
      Object.values(recordedLawSections).every(
        (section) => new URL(section.href).hostname === "bdlaws.minlaw.gov.bd",
      ),
    ).toBe(true);
  });

  it("uses exactly two critical, four high, and four medium verified findings", () => {
    expect(recordedFindings.filter((finding) => finding.severity === "critical")).toHaveLength(2);
    expect(recordedFindings.filter((finding) => finding.severity === "high")).toHaveLength(4);
    expect(recordedFindings.filter((finding) => finding.severity === "medium")).toHaveLength(4);
  });
});

describe("contract file validation", () => {
  it("accepts PDF, image and text formats at the 10 MB limit", () => {
    expect(
      validateContractFile({
        name: "contract.pdf",
        type: "application/pdf",
        size: MAX_CONTRACT_FILE_BYTES,
      }),
    ).toBeNull();
    expect(
      validateContractFile({ name: "contract.jpg", type: "image/jpeg", size: 128 }),
    ).toBeNull();
    expect(
      validateContractFile({ name: "contract.txt", type: "text/plain", size: 128 }),
    ).toBeNull();
  });

  it("rejects files above 10 MB", () => {
    expect(
      validateContractFile({
        name: "contract.pdf",
        type: "application/pdf",
        size: MAX_CONTRACT_FILE_BYTES + 1,
      }),
    ).toBe("tooLarge");
  });

  it("rejects unsupported file types", () => {
    expect(
      validateContractFile({ name: "contract.exe", type: "application/octet-stream", size: 128 }),
    ).toBe("unsupportedType");
  });
});
