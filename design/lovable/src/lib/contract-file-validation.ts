export const MAX_CONTRACT_FILE_BYTES = 10 * 1024 * 1024;

export type ContractFileError = "tooLarge" | "unsupportedType";

export function validateContractFile(
  file: Pick<File, "name" | "size" | "type">,
): ContractFileError | null {
  if (file.size > MAX_CONTRACT_FILE_BYTES) return "tooLarge";

  const extension = file.name.split(".").pop()?.toLocaleLowerCase();
  const mimeSupported =
    file.type === "application/pdf" ||
    file.type.startsWith("image/") ||
    file.type === "text/plain" ||
    file.type === "text/markdown";
  const extensionSupported = ["pdf", "txt", "md", "markdown"].includes(extension ?? "");
  const supported = mimeSupported || (file.type.length === 0 && extensionSupported);

  return supported ? null : "unsupportedType";
}
