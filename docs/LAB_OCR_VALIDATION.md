# Laboratory PDF/OCR validation matrix

This document records public, non-user sample layouts used to design parser regression tests. No patient files or downloaded sample PDFs are stored in this repository. The test rows use synthetic values and do not represent medical advice.

## Public layout references

| Reference | Publicly illustrated column layout | Regression coverage |
|---|---|---|
| [Sample Laboratory Report — The Body](https://img.thebody.com/legacyAssets/44/76/lab_results.pdf) | TEST NAME, RESULT ABNORMAL, RESULT NORMAL, UNITS, REFERENCE RANGE | Both result columns; only one populated result is accepted. A row with both populated is rejected as ambiguous. |
| [CB Laboratory sample report](https://hikma.edu.ye/upfiles/library/Hikma-2025-08-12-31484.pdf) | PROCEDURE, RESULT VALUES, ABN, REFERENCE RANGES, UNITS | Reordered header mapping, abnormal flag, and qualitative values. |
| [Cumulative sample report with notes](https://labtests.ir/labfa/sites/aacc-dlab/files/inline-files/Cumulative%20sample%20report%20with%20notes.pdf) | Test, Normal, Abnormal, Flag, Units, Reference Range | Separate normal/abnormal result columns and flags. |
| [UK government sample laboratory form](https://assets.publishing.service.gov.uk/media/57a089ceed915d622c0003ed/Working-paper-5.pdf) | Test, Result, Units, Reference Range; CBC, U&E, and liver-function groups | Conventional test/result/unit/range mapping and common abbreviated test names. |

## Implemented parser coverage

- Header-driven mapping independent of column order.
- Separate normal and abnormal result columns; rows with multiple populated result candidates are not guessed.
- Reference intervals in one column or split low/high columns.
- Pipe-, tab-, and whitespace-aligned text rows.
- Arabic and Latin analyte names, Arabic-Indic digits, decimal comma/point, common medical units, flags, and common qualitative results.
- Conservative parsing of unlabelled delimited rows and vertical four-cell groups.
- Original extracted text retained for human review; candidates are not saved automatically.

## Spatial extraction

- Text-based PDFs use PDF text coordinates to preserve large gaps as column separators.
- Scanned PDFs use Tesseract hOCR word boxes for Arabic and ML Kit word boxes for Latin/digits; the two streams are merged by their on-page positions.
- A visible table border alone is not treated as a semantic field. Column headings and cell alignment remain necessary to identify which value is a result, unit, flag, or reference interval.

## Required acceptance tests before enabling automatic import

1. Run parser unit tests across all layouts above.
2. Run the four-page user-supplied report locally on an Android device; do not commit it to this repository.
3. Run at least one text-based PDF and one scanned-image PDF from separate public sample layouts on a camera-capable Android API 36 device.
4. Verify the extracted row-by-row candidates against each page image, especially rows with low/high columns, multiple result columns, qualitative cells, and Arabic comments.
5. Confirm ambiguous rows remain unselected for human review, and the source PDF remains linked to the correct family member.

Passing parser unit tests does not establish OCR accuracy on real-world scans. Device-level sample runs and human comparison against the original pages remain release gates.
