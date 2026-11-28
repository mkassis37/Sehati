# Sehati stabilization and acceptance plan — 2026-11-28

## Safety rules
- Work on upgrade/android-api-36 / PR #12; do not merge until all checks pass.
- Preserve application ID, release signing key, SQLCipher, Android Keystore, backup formats, and existing records.
- Do not clear app data or uninstall the data-bearing app during diagnosis.
- Use synthetic family records for destructive tests.
- Use the supplied four-page laboratory PDF locally only; it contains personal health information and must not be committed.
- Screenshot capture remains explicit opt-in; default protection stays enabled.

## 1. Measurements crash — P0
Code finding: the measurements tab placed a RecordListPanel containing a LazyColumn inside an outer verticalScroll. Nested vertical scrollables can be measured with unbounded height and throw during Compose layout. The outer scroll was removed and the records list is constrained to the remaining height below the trend charts.
Tests: open the measurements tab with zero, one, and many records; repeat through summary shortcut and timeline; test both charts, add/edit/delete, decimal input, process recreation, member switching, and Back.
Pass: no crash; list and charts render; CRUD persists; only the selected member's records appear.

## 2. Navigation and emergency card — P0
Test entry routes from member details, summary, emergency card, allergies, labs, medications, appointments, documents, reports, search, and timeline. Test system Back, in-app Back, predictive Back on API 36, correct origin restoration, emergency-card persistence, visibility settings, and life-threatening allergy warning behavior.
Pass: no unexpected finish, blank screen, wrong member, stale tab, or lost edit; lock-screen and recent-app previews remain protected.

## 3. Startup and family files — P0
Test first launch, warm launch, process recreation, Keystore-unavailable and database-open failure handling. Create two synthetic members with distinguishable records; switch repeatedly; edit/delete/recreate; verify no stale selected member and no cross-member records.
Pass: safe startup or actionable error; each member sees only their own records; data persists after restart.

## 4. Screenshot privacy — P1
Verify secure by default; opt in from Security settings; test screenshot and recording; opt out and verify FLAG_SECURE is restored immediately and after restart. Check recent-app previews, lock/unlock, background/foreground, PDF preview, document picker, and scanner. Scanner's independent secure-window behavior must be reviewed, not removed blindly.
Pass: explicit opt-in only; opt-out restores protection; no sensitive preview leak.

## 5. Laboratory PDF and OCR — P1
Use the supplied four-page report locally: page 1 chemistry/lipids/HbA1c; page 2 hormones/tumor markers; page 3 urine analysis; page 4 CBC. Test embedded text and scanned-image OCR. Check English analyte names, numeric values, units, reference intervals, flags, Arabic comments, Arabic/Western numerals, decimals, inequality signs, ranges, and qualitative values such as Nil, Hazy, and Trace. Verify every candidate against its page, prevent row/column mismatches, require human review before saving, and retain the original PDF with the correct member. Test cancel, retry, malformed/oversized file, and process recreation.
Pass: no silent value changes; units and ranges remain attached to the correct analyte; no unreviewed automatic save; source PDF remains recoverable.

## 6. Encryption, backup, restore — P0
Run existing SQLCipher/Room, encrypted attachment, backup round-trip, and preview-security tests. Export a password-protected backup with multiple synthetic members, records, and attachments; restore separately; compare ownership, counts, IDs, and attachment hashes. Test wrong password, corrupt/truncated archive, invalid attachment, duplicate restore, cancellation, low storage, and Drive interruption.
Pass: authenticated restore only; failed restore does not partially replace live records or delete valid attachments.

## 7. Android 16 and 16 KB — P0
Run build, unit tests, lint, and API 36 instrumentation. Inspect every packaged native .so (OpenCV, SQLCipher, Tesseract included) with ELF program-header alignment checks; all loadable segments must meet 16 KB alignment. Check APK/AAB ZIP alignment too, noting ZIP alignment alone does not prove ELF compatibility. Run on a 16 KB emulator/device where available. Check edge-to-edge, system bars, scanner, keyboard, dialogs, and predictive Back.
Pass: no native linker/page-size errors; all native libraries pass ELF checks; workflows work on API 36 and 16 KB environment.

## 8. Candidate build
Only after checks pass: build debug APK and signed release candidate using the existing signing configuration; verify package ID, version, source commit, signatures, and SHA-256; install without removing the user's data-bearing app; execute device acceptance; deliver artifacts and a PASS/FAIL/NOT RUN report.

## Execution log
- 2026-11-28: reviewed measurements UI and found nested vertical-scroll layout around a lazy list; constrained the list below trend charts on API 36 branch.
- 2026-11-28: Android CI run #462 started; debug build in progress, unit tests/lint/instrumentation/package steps pending at time of writing.
- Not yet verified: real-device measurements, navigation/emergency flows, screenshot lifecycle, end-to-end OCR, on-device backup/restore, 16 KB ELF/device compatibility, and final candidate installation.