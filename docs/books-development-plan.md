# Books and manga development plan

## Product direction

Keep one Android app with separate Books and Manga library views. Retain the
existing manga readers and AYN Thor dual-screen controls. Book sources will have
an independent provider interface and eventually a separate extension repository;
they must not pretend that ebook downloads are manga image pages.

## Implemented starting point

- Local EPUB import through the local source and bitmap-backed text pagination.
- Contents, in-book layout changes, section breaks and reduced refresh flashing.
- Source-offset bookmarks, section progress, per-book appearance and day/night presets.
- Explicit EPUB backup/restore selection, legacy backup compatibility, bookmark
  merge on restore, and separate EPUB selection in existing sync configuration.

The backup implementation is awaiting Android CI and device validation. It backs
up reader data, not ebook files. Restored books currently need matching local
library paths. It does not establish portable cross-device book identity.

## Implementation order and acceptance criteria

1. **Backup safety** — implemented, pending validation. Check EPUB-only and mixed
   backups, legacy option arrays, older backups, merge behaviour, incognito's
   explicit bookmark actions, and clean-profile restoration.
2. **Book identity and library** — planned. Introduce stable book and edition IDs,
   author/series/language/format metadata, import history and duplicate detection.
   Migrate path-keyed bookmarks without deleting legacy records. Provide Books and
   Manga filters before changing the application's navigation hierarchy. Different
   editions must not silently share text positions.
3. **Text reader** — planned. Introduce a reader interface alongside the existing
   image viewer, initially for reflowable EPUB. Preserve chapter/fragment locations,
   reading direction and dual-screen page order. Add selection, copy, in-book
   search, inline footnotes and dictionary intents. Restrict active book content
   and external navigation. Keep the existing renderer as a fallback until parity
   is demonstrated on single-screen, paired-page and companion-screen layouts.
4. **Book catalogues** — planned. Implement OPDS navigation, search, pagination,
   book details and acquisition links. Start with a user-entered Calibre server.
   Handle authentication separately from catalogue data and exclude credentials
   from ordinary backups. Download into staging, validate EPUB structure, support
   retry/cancellation, then import atomically. Do not index incomplete downloads.
5. **Source repository** — planned, not created. Proposed name:
   `mihon-ds-book-sources`. Define a versioned provider API for search, metadata and
   acquisition. Extensions return ebook resources and format information, not
   manga chapters. Include build checks, compatibility metadata and an example
   OPDS provider. The application repository must not depend on every provider.
   Anna's Archive and Z-Library remain investigation items: their usable APIs,
   authentication and supported download workflows have not been verified.
6. **Reading tools** — planned. Persist highlights and notes against stable text
   locations; add export and backup tests. Add font import, alignment controls,
   optional device text-to-speech with sleep timer, brightness gestures and reader
   presets. Handle accessibility/reduced-motion settings and language direction.
7. **Dual-screen and manga polish** — planned. Companion contents/notes, remembered
   screen layouts and independent brightness; reliable spread handling, per-series
   crop preferences and accessible reading-direction controls. Test EPUB and manga
   independently so novel-specific changes do not alter manga transitions.
8. **App and release polish** — planned. Cross-library search, resumable downloads,
   storage cleanup with previews, actionable errors, opt-in crash diagnostics,
   signed upgrade testing, accessibility checks and clear release notes. Test
   installation over the previous working APK before increasing release status.

## Validation gate for each stage

Compile and run focused unit tests in GitHub Actions, install the resulting APK,
and exercise the affected paths on the AYN Thor's two physical displays. For
changes to storage or identity, restore a real backup in a separate test profile.
Do not label unbuilt or untested changes as public-release ready.
