# Local OCR for contract intake and OcrIT (FR-27, ADR-0014): Tesseract 5 with Bangla and English
# data. It reads a page image from stdin and writes the word table to stdout; it has no volume and
# needs no network at run time. Build: docker build -t shromikai-tesseract docker/tesseract
FROM debian:bookworm-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-ben tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/*
ENTRYPOINT ["tesseract"]
