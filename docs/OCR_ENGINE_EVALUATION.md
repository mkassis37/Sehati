# Sehati OCR engine evaluation and medical-result extraction

Status: evaluation branch only — no production engine is replaced by this document.
Base: Android API 36 candidate, branch `experiment/lab-ocr-engine-evaluation`.
Privacy: all benchmark inputs must be synthetic or public non-identifying samples. Never commit patient PDFs, OCR output containing patient identifiers, or health data.

## Decision summary

Do not add PaddleOCR's Android SDK to the production app yet.

Why:
- The official Android SDK is a real on-device ONNX Runtime pipeline with text boxes, confidence scores, timings, and a separable SDK module.
- The current official Android deployment guide documents PP-OCRv6 Android assets and PP-OCRv5 mobile assets. It does not document an Arabic recognition model for that Android SDK release.
- PaddleOCR's documented Arabic recognizer is the older `arabic_PP-OCRv3_mobile_rec`, not PP-OCRv5/v6. The model list reports 73.55 average recognition accuracy on its benchmark and 17.3 MB in one current module table (an older model-list page reports 9.6 MB, so the exact converted asset size must be measured from the selected download).
- PP-StructureV3 supports Arabic in its language appendix and provides layout/table/multicolumn processing, but its default recognizer is not Arabic. Its documented table-recognition modules do not establish Arabic clinical-table accuracy or an Android offline bundle for Arabic.
- The SDK example uses ONNX Runtime 1.21.1 and OpenCV 4.5.3, while Sehati already uses OpenCV 4.14.0. Adding the demo dependency as-is risks duplicate/native ABI conflicts and an unnecessary downgrade. Any integration must first prove compatibility without changing the existing OpenCV, SQLCipher, camera, or signing setup.

Therefore PaddleOCR is a strong isolated research candidate, not a production-ready drop-in replacement. Keep the existing Tesseract Arabic + ML Kit Latin pipeline as the baseline until an Arabic-compatible detector/recognizer pair and table strategy pass the same-device corpus.

## Candidate comparison

| Candidate | Strength | Important limitation | Decision |
|---|---|---|---|
| PDFBox Android (already used) | Extracts positioned text from text-based PDFs; preserves source coordinates | Cannot OCR scanned pages; PDF reading order can be irregular | Keep for digital PDFs |
| Tesseract 4 Android + Arabic traineddata (already used) | Offline Arabic OCR; hOCR word boxes can preserve geometry | Recognition quality varies with scan quality/fonts; does not understand lab table semantics | Keep as Arabic baseline |
| Google ML Kit Text Recognition Latin (already used) | On-device Latin/number recognition with element boxes | Latin script recognizer is not an Arabic recognizer | Keep for Latin/digits baseline |
| PaddleOCR Android SDK | Official Android ONNX SDK, detection + recognition, confidence, boxes, performance timings | Current Android guide's documented models do not establish Arabic support; Arabic PP-OCRv3 model is a separate older model and must be checked for ONNX conversion/API compatibility | Prototype only |
| PP-StructureV3 | Layout analysis, table structure, multi-column reading order, Markdown output; Arabic is listed as a supported OCR language | Main documented integration is Python/pipeline; Arabic table structure and on-device Android deployment are not proven by the docs | Offline reference benchmark / future conversion candidate |
| Tabula Java | Extracts tables from text-based PDFs | Not OCR; poor fit for scanned PDFs and Android runtime needs separate compatibility testing | Optional PDF-only comparator, not production dependency |

## Target architecture

1. **Source intake** — preserve original PDF/image URI and page count; enforce current size/page limits.
2. **Digital PDF path** — PDFBox text + character coordinates. Avoid OCR when a reliable text layer exists, but retain page rendering for visual verification.
3. **Scanned/image path** — pluggable on-device OCR providers. Baseline: Tesseract Arabic + ML Kit Latin. Experimental providers must return tokens/lines with bounding boxes and confidence, not a flattened text blob.
4. **Layout normalization** — group tokens into rows by vertical overlap, preserve left/right coordinates, detect column boundaries, distinguish RTL/LTR segments per row. Do not assume that a whole page has one reading direction.
5. **Table interpretation** — use detected headers and column geometry to identify analyte, result, unit, reference interval, flag, specimen, and comments. Reject ambiguous rows instead of guessing.
6. **Medical normalization** — retain the original label and unit; attach LOINC only when an exact, defensible mapping is known; validate units against UCUM only when a valid code exists. Never infer a clinical code from a vague name.
7. **Human review** — show original page, bounding-box highlight, raw OCR text, parsed candidate, confidence, and uncertainty. Require explicit user confirmation before writing a LabResult.
8. **Persistence** — preserve the original encrypted report and family-member ownership. No OCR provider may upload data or call a remote API.

## Medical terminology rules

- LOINC identifies observations/tests; it does not determine the patient's result or reference interval.
- UCUM represents units; preserve the laboratory's displayed unit string even when a UCUM mapping is unavailable.
- HL7 FHIR Observation is a useful interoperability model for code, value, unit, reference range, interpretation, specimen, and effective time. It is not a reason to force an uncertain parse into a structured record.
- Existing Room LabResult schema must not be changed until a separately reviewed migration is designed and tested. Do not backfill guessed codes.

## Benchmark corpus

Use public sample layouts only after checking their redistribution/license terms. Where the PDF itself cannot be redistributed, record its URL and create synthetic, de-identified fixtures that reproduce only the column layout.

Required strata:
- Digital PDF with embedded text and positioned columns.
- Scanned PDF at 150, 200, and 300 DPI.
- English CBC and chemistry reports.
- Arabic or bilingual urine/CBC/chemistry reports.
- Separate NORMAL and ABNORMAL result columns.
- Split LOW/HIGH reference columns.
- Borderless whitespace-aligned tables and boxed tables.
- Multi-column pages, repeated headers, page breaks, footnotes, stamps, and Arabic comments.
- Arabic-Indic and Western digits; decimal dot/comma; signs <, <=, >, >=; qualitative results (Nil, Trace, Hazy, Positive, Negative); units such as mg/dL, mmol/L, ng/mL, g/dL, /HPF.

For every fixture keep expected values as synthetic test data: analyte, raw result, numeric/qualitative value, unit, low/high range, flag, source page, and expected ambiguity/review state.

## Metrics and release gates

Report each engine separately; do not combine metrics into one vague accuracy score.

- Character Error Rate (CER) separately for Arabic, Latin, and digits.
- Word Error Rate (WER) separately for Arabic and English.
- Table cell exact-match accuracy for analyte/result/unit/range/flag.
- Row association accuracy: whether every result remains attached to its correct analyte.
- False-accept rate: ambiguous or incorrect rows incorrectly presented as safe candidates.
- Confidence calibration: compare confidence with observed correctness.
- Cold model load, per-page latency, peak memory, APK/AAB size delta, battery/thermal behavior.
- Offline proof: block network during OCR and confirm successful local processing.
- Device matrix: API 26 baseline, API 36, and 16 KB page-size environment; inspect native ELF PT_LOAD alignment for all bundled .so files.

Hard safety gates:
- No wrong analyte/result/unit/range association in the accepted validation corpus.
- Ambiguous rows are withheld for review, never auto-saved.
- Original report remains attached to the correct family member.
- No network transmission of report bytes or OCR text.
- No regression to camera, scanner, SQLCipher, backup/restore, signing, or existing data.
- All unit tests, lint, debug build, instrumentation, and native compatibility checks pass.

## Execution sequence

### Phase A — baseline and failing tests
1. Capture the exact failing Gradle test names/assertions and test XML for the current API 36 candidate.
2. Fix parser logic from the failing examples; never weaken assertions merely to get green CI.
3. Add regression fixtures for each fixed case.
4. Keep PR #12 open and unmerged until its complete existing CI is green.

### Phase B — independent engine prototype
1. Download candidate model assets only from official release/model links and record exact version, SHA-256, license, and size.
2. Verify whether the Arabic PP-OCRv3 recognition model is compatible with the current Android ONNX SDK's model input/output, dictionary YAML, and detection model. Do not assume compatibility from the language name alone.
3. Prototype the SDK in this separate branch/module, not in the main app dependency graph.
4. If dependency conflicts arise with OpenCV 4.14.0 or native libraries, stop and resolve in the prototype; do not downgrade production OpenCV.
5. Run identical synthetic/public page fixtures through baseline and candidate on the same device.

### Phase C — layout/table evaluation
1. Compare PP-StructureV3 output against PDFBox coordinates and the current hOCR/ML Kit geometry.
2. Determine whether table structure recognition can handle bilingual Arabic/English lab tables. If not, use OCR boxes plus explicit column/header inference; do not claim table AI support.
3. Preserve raw source text and boxes for every candidate.

### Phase D — standards and review UX
1. Add optional terminology metadata only after exact LOINC/UCUM mapping sources and licensing are reviewed.
2. Keep source label/unit and source snippet even when a standard code is attached.
3. Display page crop/highlight and require confirmation before saving.

### Phase E — rollout
1. Keep new OCR behind an internal feature flag.
2. Compare results without changing existing saved records.
3. Enable only after all gates pass and a migration/rollback plan is reviewed.
4. Produce a signed candidate only after the separate API 36, 16 KB, privacy, and device acceptance gates pass.

## Research references

- PaddleOCR Android deployment: https://www.paddleocr.ai/latest/en/version3.x/inference_deployment/cross_platform/android_deployment.html
- PaddleOCR PP-StructureV3 usage and supported languages: https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/pipeline_usage/PP-StructureV3.en.md
- PaddleOCR PP-StructureV3 introduction: https://www.paddleocr.ai/main/en/version3.x/algorithm/PP-StructureV3/PP-StructureV3.html
- PaddleOCR text recognition model list: https://www.paddleocr.ai/main/en/version3.x/module_usage/text_recognition.html
- PaddleOCR Android SDK source: https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android
- Tesseract: https://github.com/tesseract-ocr/tesseract
- Tabula Java: https://github.com/tabulapdf/tabula-java
- LOINC: https://loinc.org/
- UCUM: https://ucum.org/
- HL7 FHIR Observation: https://hl7.org/fhir/observation.html

## Current evidence / not yet run

- Source inspection confirms Sehati already uses PDFBox Android, Tesseract4Android with local Arabic traineddata, and ML Kit Latin text recognition.
- Source inspection confirms the current OCR flow is local and returns word/element geometry for the two OCR streams.
- Source inspection confirms the app uses OpenCV 4.14.0; PaddleOCR's official Android SDK sample declares OpenCV 4.5.3 and ONNX Runtime 1.21.1.
- Public documentation supports PP-StructureV3 Arabic OCR language selection, but this does not prove Arabic table structure accuracy or an offline Android Arabic model bundle.
- No Paddle model has yet been downloaded, converted, run against Sehati reports, or integrated.
- No real-world OCR accuracy claim is made until the benchmark and device runs are completed.
