# EPUB reader release candidate

## What changed

- An EPUB tab appears in reader settings only while an EPUB is open.
- Contents entries are tappable and include page numbers; nested entries are indented.
  EPUB 3 navigation and EPUB 2 NCX are supported, with headings as a fallback.
- Android text layout preserves inline bold/italic, explicit line breaks, lists,
  punctuation spacing, Unicode shaping and automatic line wrapping.
- Font family, size, line spacing, paragraph spacing, margins, and four page colours
  have a live appearance preview and a reset action.
- Continuous text layout removes the top and bottom margins between text pages.
  Select Webtoon mode for vertical scrolling, or use the existing paged/dual-screen
  controls for page turns.
- Reading position is stored as a source-text offset so reopening after changing
  typography returns to the page containing the previously saved text. Position
  writes respect incognito mode. Existing books acquire the new position marker
  after a page is selected in this version.
- URL-encoded local resources resolve correctly; missing chapters produce an error
  rather than silently disappearing. External resources and book scripts are not loaded.
- The pinned FlexibleAdapter source module remains in use.

## Validation before publishing

The feature branch builds an APK automatically on push. CI runs the archive reader
regression tests and app tests before building. A green build is required, followed
by the device checks below. Local Gradle execution was blocked by the build
workspace's network restriction, so this change must not be described as already
build-verified or device-tested.

Use the original test books in `test-fixtures/epub/`, in your usual local source
folder, to check EPUB 2 and EPUB 3 alongside a real novel and an image-only EPUB.

- Open Settings > EPUB. Check that manga readers do not show the EPUB tab.
- Jump to each contents entry, including nested fragments; verify its heading is visible.
- Check the inline punctuation sample: `Hello world! Books, not book s.`
- Check bold, italic, bullet/numbered lists, line breaks, Arabic, CJK and emoji.
- Test first/last page and a long chapter at the smallest and largest text sizes.
- Change font/size/spacing/margins, tap Apply to open book, and check that the
  previous text remains on the resumed page. Returning to the same exact screen
  position is not guaranteed when line wrapping changes.
- Check White, Sepia, Night and Black themes, including both physical displays.
- Check that chapter one starts on a fresh page after front matter in paged layout.
- Open Settings > Contents and jump to chapters after applying a larger font.
- Enable global refresh flashes; verify EPUB suppression prevents them, then check
  animation on/off in paged mode. Check for contrasting flashes while images load.
- Apply layout with companion and side-by-side modes active; verify the current text
  remains in the visible spread. Leave the reader during pagination and reopen.
- Verify that cancelling/leaving a failed reflow keeps the old pages usable.
- Try continuous text layout with Webtoon mode; then disable it, apply and test
  single-page and dual-page controls, rotation, and the companion display.
- Test incognito mode and verify that it does not overwrite the saved EPUB position.
- Bookmark a page, change typography, apply and jump back. Restart the app and
  verify bookmarks persist. Remove one and verify other bookmarks remain.
- Jump through Contents, then use Back to where I was; repeat after a reflow.
- Check front matter, nested headings, first/last chapter and books without a TOC.
- Configure two books differently, reopen both and verify their settings stay separate.
- Save/load day and night presets. Set a new-book default; check a previously opened
  book retains its appearance and a new book inherits the new default.
- Toggle EPUB animation and verify manga transition settings remain unchanged.
- Install the signed APK over the previous working build; verify existing manga,
  downloads, preferences, and backups still work.

## Current scope

This implementation renders text into the existing image-reader pipeline. It does
not provide text selection, dictionary lookup, highlights, search, clickable
footnotes, or full publisher CSS/fixed-layout reproduction. Settings previews update
immediately; book pages update with Apply to open book. Contents now live in a dedicated
Contents tab instead of generated pages at the beginning of the book. This
changes page numbering compared with the experimental build. EPUB position markers
and bookmarks are local preferences; they are not a cross-device text-location sync
protocol. Return history is session-only and keeps the most recent jump origin.
Bookmarks record the start of a rendered page, not an individually selected word.
Progress counts the visible TOC section; nested entries form their own sections.
Moving or replacing a book file can change its key or invalidate stored text offsets.
Bookmarks are saved only on explicit request, including in incognito mode.

Do not merge/publish until CI and the relevant device checks pass. This patch does
not increment the app version or publish a release.

## EPUB backup and restore

- Create a backup with only “EPUB bookmarks, reading positions and appearance” selected.
  Restore into a clean profile with the same local books and verify bookmarks, resume
  locations, per-book font/colour settings, presets and EPUB animation preferences.
- Restore over a book with a newer bookmark; both locations must remain available.
  Appearance and resume position deliberately use the values in the backup.
- Restore an older backup created before the separate EPUB option existed.
- Deselect EPUB data while keeping App settings selected, on both creation and
  restoration. Existing EPUB data must remain untouched.
- Verify scheduled backups still include EPUB data by default and the sync selector
  saves the new option. Legacy, unmigrated data still needs matching paths; this is not book-file sync.
- Book files and ephemeral “Back to where I was” history are not included.

## Book identity and library filters

- Open a book with old bookmarks/settings before renaming its file or directory.
  Refresh its local chapter list, reopen it and verify the same data is restored.
- Open another copy of the same EPUB; verify it shares reading data. Change its
  text or package metadata and verify it gets a distinct identity.
- Delete a migrated bookmark, reopen twice and verify it does not reappear.
- Restore a backup on another device and open the same EPUB under another name.
- Check All / Books / Manga against EPUB-only, image-only, mixed and unscanned
  local folders. Mixed folders must be visible in both filtered views.
- Classification currently relies on downloaded local chapter metadata; load or
  refresh chapter lists first. Online novel extensions are not yet supported.
