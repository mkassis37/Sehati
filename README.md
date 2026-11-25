# Sehati – صحتي

**Sehati** is an Android-first personal health record application designed to help users organize their medical information in one place.

## Product goals

- Personal and medical profile, including medical history and procedures.
- Flexible allergy records for medicines, foods, and environmental triggers.
- Lab results with units, reference ranges, laboratory details, dates, and attachments.
- Longitudinal result comparison and charts, with safeguards against comparing incompatible units or reference ranges.
- Current and historical medications and treatments.
- Medical visits, clinicians, diagnoses, and reports.
- Secure storage of documents and medical images.
- A chronological health timeline.
- Configurable sections, categories, and custom fields.

## Current implementation

The first Android foundation is being developed on `feature/android-foundation-plus`.

- Native Android app using Kotlin and Jetpack Compose.
- Arabic interface with right-to-left layout and a teal light/dark design system.
- Local Room database encrypted with SQLCipher.
- Random database passphrase wrapped by a non-exportable Android Keystore AES-GCM key.
- Medical profile and allergy records can be created, viewed, and updated/deleted locally.
- Android cloud/device backup is disabled for the app's private data.
- GitHub Actions workflow builds, tests, runs lint, and publishes a temporary debug APK artifact when checks pass.

This is an in-progress foundation, not a medically validated product or a finished security review. No production APK or release should be considered ready until CI and device testing pass.

## Technical direction

- Offline-first: the local database is the source of truth.
- Kotlin, Jetpack Compose, Room, and SQLCipher.
- Clear separation of presentation, data, and domain responsibilities.
- Cloud synchronization is not configured or enabled. It will require a separate provider, privacy, consent, access-control, and cost review.
- No real medical data, credentials, API keys, or signing keys belong in this repository.

## Privacy and security notes

Medical information is highly sensitive. Use synthetic test data during development. Do not commit real patient records, test results, scans, prescriptions, credentials, API keys, signing keys, or production configuration.

The initial encrypted database key is protected by Android Keystore. If the app is uninstalled, device security state is reset, or the Keystore key is invalidated, the local database may become unrecoverable. A separately designed encrypted export/restore flow is required before users rely on the app as their only medical record.

Before enabling cloud sync, define authentication, authorization, encryption, retention, deletion, backup, and account-recovery behavior. Any extracted data from PDFs or images must be reviewed by the user before it is saved as a medical record.

## Planned milestones

1. Android foundation, navigation, design system, and encrypted local database.
2. Medical profile and flexible allergy records.
3. Lab results, attachments, and safe comparison rules.
4. Medications, visits, reports, and timeline.
5. Encrypted export/restore, lock screen, and security hardening.
6. Automated tests, CI verification, device testing, and release.

## Language

The primary user interface is Arabic with right-to-left layout. English support is planned.
