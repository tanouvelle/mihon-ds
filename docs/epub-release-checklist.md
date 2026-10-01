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
- Change font/size/spacing/margins, close the reader, reopen, and check that the
  previous text remains on the resumed page. Returning to the same exact screen
  position is not guaranteed when line wrapping changes.
- Check White, Sepia, Night and Black themes, including both physical displays.
- Try continuous text layout with Webtoon mode; then disable it, reopen and test
  single-page and dual-page controls, rotation, and the companion display.
- Test incognito mode and verify that it does not overwrite the saved EPUB position.
- Install the signed APK over the previous working build; verify existing manga,
  downloads, preferences, and backups still work.

## Current scope

This implementation renders text into the existing image-reader pipeline. It does
not provide text selection, dictionary lookup, highlights, search, clickable
footnotes, or full publisher CSS/fixed-layout reproduction. Settings previews update
immediately; book pages update when the book is reopened. Contents now live in the
EPUB settings tab instead of generated pages at the beginning of the book. This
changes page numbering compared with the experimental build. EPUB position markers
are local preferences; they are not a cross-device text-location sync protocol.

Do not merge/publish until CI and the relevant device checks pass. This patch does
not increment the app version or publish a release.
