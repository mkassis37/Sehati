# Sehati Development Roadmap

## Foundation branch: `feature/android-foundation-plus`

- [x] Create a native Kotlin/Jetpack Compose Android project.
- [x] Add Arabic RTL navigation and a shared light/dark teal theme.
- [x] Configure Room with SQLCipher database encryption.
- [x] Protect the random database passphrase using Android Keystore AES-GCM.
- [x] Disable Android cloud/device backup for private app data.
- [x] Implement local medical profile create/update.
- [x] Implement allergy record create/read/delete.
- [x] Add GitHub Actions build, unit-test, lint, and debug APK artifact workflow.
- [ ] Run CI and fix all build, test, and lint failures.
- [ ] Verify the encrypted database on a physical Android device.
- [ ] Review repository visibility and move sensitive project source to a private repository if approved.

## Next milestones

- [ ] Add structured medical history, procedures, and profile fields.
- [ ] Add lab result records, source documents, units, and reference intervals.
- [ ] Add compatibility rules before comparing results across dates/labs.
- [ ] Add medication and treatment history.
- [ ] Add medical visits, clinicians, reports, and timeline.
- [ ] Implement authenticated app lock and encrypted attachment storage.
- [ ] Design encrypted export/restore before relying on local records.
- [ ] Evaluate cloud sync only after explicit privacy, consent, access-control, and cost review.

Every milestone requires acceptance criteria, automated tests where practical, and a review before moving forward. Destructive database migrations are not permitted for medical records.
