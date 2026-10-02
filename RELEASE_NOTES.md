# EPUB reader polish — release candidate

This candidate adds a dedicated EPUB settings tab with a tappable table of contents,
font and spacing controls, margins, four page colours, an appearance preview and a
reset action. Text now preserves bold, italic, lists and line breaks, with improved
wrapping and punctuation spacing. Continuous text layout removes page-edge margins
for scrolling. Reading position follows the text when typography changes.

Use Apply to open book after changing layout settings; your text position is retained.
Paged layouts start spine sections and major headings on a fresh page. EPUB refresh
flashes are suppressed by default, and page-turn animation is available in EPUB settings. EPUB text still uses the existing
image reader; text selection, search, annotations and full publisher CSS are not
included. Contents are now in Settings > Contents, replacing the experimental contents
pages. See `docs/epub-release-checklist.md` for validation and upgrade notes.

Reading tools now show chapter/section progress and whole-book percentage, highlight
current contents entries, save bookmarks by text location, and offer a return action
after navigation jumps. Book appearance is independent, with reusable day/night
presets and an explicit default-for-new-books action. EPUB page-turn animation can
be toggled without changing manga transitions. Animation timing uses the existing
pager; this candidate does not add a speed slider or a page-curl effect.

Backups and restore now offer an independent EPUB data option for bookmarks,
reading positions, per-book appearance and presets. Existing backups remain readable.
Restoring merges bookmarks, while saved appearance and positions replace current
values. EPUB-only restores do not reschedule library updates or automatic backups.
Book files are not embedded in backups and must be copied separately.

Library filters now include All, Books (EPUB), and Manga. Local entries with both
formats appear in both views; entries without scanned chapter lists remain in All
and Manga until their chapter list is loaded. These are library filters, not a new
book importer or a separate database.

EPUB reading data now uses a fingerprint of package metadata and ordered spine
contents. An unchanged book keeps its reading data after an external rename or
move, including ZIP recompression. Open older books once at their old path to
migrate legacy bookmarks/settings before moving them. Altered package metadata or
text produces a new identity. This does not automatically remove duplicate library
entries or migrate data for a book already moved before its first upgrade open.

This candidate requires CI and device verification before a public release.

---

# Mihon DS 0.2.4

> **Credits.** Mihon DS is not my work. The dual-screen fork was created by
> [mis0suppe](https://github.com/mis0suppe/mihon-ds) and extended by
> [frazse](https://github.com/frazse/mihon-ds) — Layout Memory / manual panel training,
> instant SyncYomi, flicker-free reader transitions and stabilised dual-screen sync are all
> frazse's. Mihon itself is by the [Mihon team](https://github.com/mihonapp/mihon) and its
> contributors. This build only keeps their work current with upstream Mihon.

Mostly guided reading and the companion display, plus the first properly signed release.
Still based on Mihon v0.20.4.

## Read this before updating

This release is **signed with a real key** for the first time. Previous builds were signed
with a throwaway key generated during each CI run, so they could never update over one
another. That is fixed from here on — but this one cannot install over an existing build.
**Uninstall the old app first.** Your library lives in the app's own storage, so back it up
from Settings → Data and storage if you would rather not lose it. Every release after this
one will update in place.

## Changed

- **Dual-screen mode and "Close companion when leaving the app" now default to on.** This only
  affects fresh installs — if you have already set them, your choice stands. A single-screen
  device still turns dual-screen mode off automatically at startup.

## Fixed

- **"Close companion when leaving the app" now works from anywhere**, not just from the
  dashboard. Leaving with a manga open reset the companion to the dashboard instead of
  closing it, and leaving from the reader did nothing at all — two separate causes, because
  the reader puts its own window on the second screen rather than using the companion
  activity.
- **The companion no longer goes black mid-read.** Two different routes led here. One was the
  app deciding it had been backgrounded during a chapter transition; the other was a loop in
  which rebuilding the second-screen window briefly covered the reader, which caused it to be
  rebuilt again, about once a second.
- **Guided reading frames the right part of the page when "Crop borders" is on.** Panels were
  detected against the original image while the reader displayed a cropped one, so the
  focused panel sat well above where it belonged, drifting further off toward the bottom of
  the page. Cropping is now suppressed while guided reading is active, and the setting greys
  out to say so.
- **Advanced Recursive no longer reads a tall panel after the shorter ones beside it.** Where
  panels overlap too much to separate cleanly, the fallback ordering ranked them by their
  vertical centre — so a panel spanning two rows sorted between them instead of ahead.
- **The panel sorting setting no longer labels the wrong option as the default.**

## Other

- Releases are signed with a real keystore, so they update over each other from now on.
- The unit test suite compiles and runs again, and CI runs it before building. It had been
  broken since July without anyone noticing, because nothing ever ran it. 182 tests pass; two
  are deliberately disabled against real panel-detection bugs that are not fixed yet.

## Known issues

- Guided reading can discard a large panel that contains two smaller ones, treating it as a
  false detection, and then skip it. Unconfirmed on real pages; under investigation.
- Under Advanced Recursive, a panel nested inside another is read before its container.
- `AdaptiveSheet` has a long-standing bug where `context is Presentation` can never be true,
  so secure-flag handling for sheets on the secondary display has never engaged.
- The reader's companion-page ("book mode") toggle has no effect in webtoon mode — it applies
  to the paged viewer only. Same behaviour in mis0suppe's original.
- Closing the companion lags the main screen, because Android defers the signal until the
  leave animation finishes.

## Testing

Verified on an AYN Thor: leaving the app from the reader, a manga screen and the dashboard;
reading through chapter transitions; guided reading with "Crop borders" both on and off; and
the panel ordering fix against the layout that surfaced it. The signing path was verified
end to end with a disposable key, confirming the build signs with the supplied keystore
rather than falling back to a debug one.
