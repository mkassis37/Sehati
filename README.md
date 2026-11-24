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

## Initial technical direction

- Android native application.
- Kotlin and Jetpack Compose.
- Offline-first data access.
- Local persistence with Room.
- Modular architecture and clear separation of UI, domain, and data layers.
- Cloud synchronization only after the provider, privacy model, consent flow, and security rules are explicitly defined.

These are architectural intentions, not claims that the features have already been implemented.

## Privacy and security

Medical information is highly sensitive. Never commit real patient records, test results, scans, prescriptions, credentials, API keys, signing keys, or production configuration to this repository.

Before enabling cloud sync, define authentication, authorization, encryption, retention, deletion, backup, and account-recovery behavior. Any extracted data from PDFs or images must be reviewed by the user before it is saved as a medical record.

## Project status

The repository is at its initial documentation and architecture-planning stage. No working Android APK is available yet.

## Planned milestones

1. Project foundation and architecture.
2. Design system and navigation.
3. Medical profile and history.
4. Flexible allergy records.
5. Lab results and attachments.
6. Document import and reviewed data extraction.
7. Safe longitudinal comparisons and charts.
8. Medications, visits, and reports.
9. Backup, synchronization, and security hardening.
10. Automated testing and release.

## Language

The primary user interface is planned in Arabic, with support for clear right-to-left layouts.
