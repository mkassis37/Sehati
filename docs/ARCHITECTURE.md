# Architecture Principles

## Application shape

Sehati is planned as a native Android application using Kotlin and Jetpack Compose.

The codebase should separate responsibilities into:

- **Presentation:** Compose screens, navigation, UI state, and design system.
- **Domain:** use cases, business rules, and domain models.
- **Data:** repositories, local database, file storage, and remote synchronization adapters.

## Offline-first behavior

- The local database is the primary source for the user interface.
- User actions should remain usable without a network connection.
- Synchronization must be explicit, observable, retryable, and conflict-aware.
- Never silently overwrite a newer medical record during conflict resolution.

## Flexible medical data

The model must support many allergy entries, test records, attachments, and user-defined fields. Avoid fixed limits such as a small predefined number of entries or fields.

Every lab result should preserve its original value, unit, reference interval, laboratory, date, and source document when available. Comparisons must be blocked or clearly qualified when units or reference intervals are not compatible.

## Security requirements

- Do not store secrets in source control.
- Use platform-backed secure key management for encryption keys.
- Protect local records and attachments at rest.
- Use authenticated, encrypted network communication.
- Enforce per-user authorization on every cloud resource.
- Obtain informed consent before any medical data is uploaded.
- Provide export and deletion paths.
- Do not treat OCR or PDF extraction as medically verified; require user confirmation.

## Cloud decision

A cloud provider has not yet been selected or configured. Firebase may be evaluated, but no provider should receive medical data until the threat model, costs, privacy terms, access rules, and consent experience have been reviewed.
