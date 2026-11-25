# Sehati Development Roadmap

## Foundation branch: `feature/android-foundation-plus`

- [x] Create a native Kotlin/Jetpack Compose Android project.
- [x] Add Arabic RTL navigation and a shared light/dark theme with eight selectable palettes and three display modes (added 2026-11-25).
- [x] Persist appearance preferences locally and provide a reset option (Preferences DataStore; no medical records stored there).
- [x] Configure Room with SQLCipher database encryption.
- [x] Protect the random database passphrase using Android Keystore AES-GCM.
- [x] Disable Android cloud/device backup for private app data.
- [x] Implement local medical profile create/update.
- [x] Implement allergy record create/read/delete.
- [x] Add GitHub Actions build, unit-test, lint, and debug APK artifact workflow.
- [ ] Run CI and fix all build, test, and lint failures. Local build, unit tests, lint, and Android-test APK compilation passed 2026-11-25; device tests still need a connected device.
- [ ] Verify the encrypted database on a physical Android device.
- [ ] Review repository visibility and move sensitive project source to a private repository if approved.

## Next milestones

- [x] Add edit support for allergy records while enforcing member ownership in the database query; instrumented isolation test added (needs device execution).
- [ ] Support qualitative lab results such as Nil and Hazy alongside numeric values.
- [ ] Add medication dose logs, reminders, and treatment end notifications.
- [ ] Implement medical visits/appointments and PDF reports.
- [ ] Add secure daily backup status and evaluate Google Drive backup integration.

- [ ] Add structured medical history, procedures, and profile fields.
- [ ] Add lab result records, source documents, units, and reference intervals.
- [ ] Add compatibility rules before comparing results across dates/labs.
- [ ] Add medication and treatment history.
- [ ] Add medical visits, clinicians, reports, and timeline.
- [ ] Implement authenticated app lock and encrypted attachment storage.
- [ ] Design encrypted export/restore before relying on local records.
- [ ] Evaluate cloud sync only after explicit privacy, consent, access-control, and cost review.

Every milestone requires acceptance criteria, automated tests where practical, and a review before moving forward. Destructive database migrations are not permitted for medical records.
