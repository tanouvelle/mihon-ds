# OpenlibExtended feature integration

Reference reviewed: https://github.com/warreth/OpenlibExtended at
`3359940a443c0226d6cae63c661481db4ce572f5` (2026-10-02).

This is a staged native Kotlin implementation plan, not a claim of feature parity.
OpenlibExtended is Flutter/Dart. Its source declares AGPL-3.0; no implementation
code or assets from that repository are included in this update. Any future reuse
needs a separate license and attribution review before merging.

## Feature mapping

| Area | Reference | Mihon DS status and next work |
| --- | --- | --- |
| Search and catalogues | README advanced search; services/search_manager.dart | Initial OPDS navigation/search and individual source definitions implemented. Next: provider capabilities, language/format/year filters only when supported, book detail view. |
| Source downloads | services/download_manager.dart, instance_manager.dart | Direct EPUB download, validation and duplicate-safe import implemented. Next: persistent queue, progress, cancellation, retry, notifications, restart recovery. Mirror fallback needs provider-specific verification. |
| EPUB reading | ui/epub_viewer.dart | Existing layout preferences and contents plus new selectable text/copy/search. Native reflow, linked footnotes and dual-screen selectable text remain separate work. |
| Read aloud | services/tts_service.dart; EPUB viewer integration | Not implemented. Add Android TTS with language selection, stop/pause semantics, speech rate, audio focus, lifecycle cleanup and optional timer. Test engine absence and interruption. |
| PDF reader | ui/pdf_viewer.dart | Not part of this EPUB update. Add a separate PDF pipeline with remembered position and dual-screen page pairing; do not route PDF through EPUB parsing. |
| Library/export | services/library_organization.dart, share_book.dart | Books/Manga filters and metadata-aware EPUB import already exist. Next: book detail editing, safe file export/share and reading-status filters. |
| Backup | services/backup_service.dart | EPUB reader-data backup/merge already implemented in Mihon DS. Validate restoration with installed source definitions and unavailable book files. |
| Anna's Archive/LibGen | services/annas_archieve.dart, libgen_service.dart | Reference has provider-specific HTML/network handling. Not provided by OPDS definitions. Requires native adapters, fixture tests and real endpoint validation. Do not label Z-Library supported merely because it appears in repository tags. |

OpenlibExtended's README lists Booklore cloud sync, expanded formats and metadata
editing as roadmap items. They are not verified implemented capabilities of the
reference app.

## Delivery order

1. Validate the current selectable-text/OPDS patch in CI and on the AYN Thor.
2. Build source capability metadata and a durable download queue before adding
   more provider adapters. Preserve files already downloaded when a retry fails;
   verify range responses before supporting resume.
3. Add rich book details and supported search filters, keeping provider results
   separate from the user's local library until import succeeds.
4. Add native read-aloud and file sharing, then PDF support as an independent reader.
5. Implement and test each additional provider independently. Authentication and
   browser verification must be explicit user flows, not silently bypassed.

## Current patch verification scope

Static resource/JSON checks and clean patch application are local checks only.
Gradle distribution retrieval is blocked in this environment, so compilation,
unit tests, live catalogue access and device testing remain required. Do not
publish this as a tested public release until those checks pass.
