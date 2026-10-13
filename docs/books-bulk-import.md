# Bulk book import

Requires the EPUB book importer and PDF support update. The existing single-book flow becomes a multiple-file picker and folder scanner.

## Use

Open Import book and choose books, or Scan a folder. EPUB and PDF files in subfolders are included. Review and edit the title and author, then import each book. Skip advances past an unsupported or unwanted file. Import remaining books uses the current edited metadata and then embedded metadata or filenames for subsequent files; an invalid file stops the batch for review.

Optional Open Library lookup sends the entered title and author only when requested. Selecting a match fetches its description and cover when available; the displayed description can be reviewed before import. The downloaded cover is written as cover.jpg for Local source. Lookup failures do not prevent local import. Online details are never written into the original EPUB or PDF.

Each imported copy receives an Author - Title filename and its own corresponding folder. A short identity suffix on the folder distinguishes editions and prevents overwriting. Existing content identity checks skip duplicates, including manually organised books. Existing library folders are not renamed. PDFs without extracted metadata use the source filename until corrected or matched online.

## Limits and lifecycle

Scans are bounded to 250 books, 2,000 visited folders and 16 folder levels. Hidden entries are skipped. Import files are limited to 256 MiB each. Only one book is staged at a time. Stop keeps published books and clears the active review. Leaving the screen cancels active work and cleans its temporary file. An unfinished review does not resume after process death; imported books remain in Local source and can be scanned again without copying duplicates.

## Validation

Patch whitespace and resource XML/reference checks passed. Filename and Open Library response parser tests are included. Android compilation and tests could not run in this environment because the Gradle distribution download was blocked. Live lookup, SAF folder providers, device rotation and batch cancellation still require CI/device testing.

Device checks: import a mixed EPUB/PDF folder with nested folders; edit a title before import; skip a corrupt file; re-import the same selection; select a matching online record; try lookup offline; stop a batch; verify existing books and originals remain intact.

API references: https://openlibrary.org/dev/docs/api/search and https://openlibrary.org/dev/docs/api/covers.
