# Mihon DS book-source definitions

These are independently installable declarative OPDS 1 book-source extensions.
They are not manga APK extensions and execute no downloaded code. Install a JSON
file from Browse > Sources > Book sources > Install book-source definition.

- Project Gutenberg: configured public OPDS catalogue, browsing and EPUB acquisition.
- Calibre: user-configured catalogue; enter your server's OPDS address. No credentials
  are stored in definitions. This version supports servers without authentication.

Copy this directory into a separate GitHub repository if desired. No remote
repository has been created by this patch. The app provides the OPDS engine;
source definitions can be distributed and updated independently by importing JSON.

Schema: `schemaVersion: 1`, `type: "opds1"`, lowercase `id`, display `name`, and
HTTP(S) `catalogUrl`. An empty URL means user configuration is required. Source
URLs containing passwords/usernames are rejected. Definitions are capped at 64 KiB.
Feed responses are capped at 4 MiB; EPUB acquisitions at 256 MiB. Acquisitions are
validated and imported through the same staging/duplicate-checking path as local files.

Supported: Atom navigation/acquisition feeds, relative links, next-page links,
EPUB open-access acquisition, Atom search templates and OpenSearch descriptions.
Not supported: OPDS 2 JSON, authentication, borrowing/DRM, paid acquisition,
background download resumption, source APKs or provider-specific scraping.

Project Gutenberg documents its OPDS feed at
https://www.gutenberg.org/ebooks/offline_catalogs.html and expects XML OPDS retirement
in 2027. This definition will need migration when its OPDS 2 service is available.
Calibre documentation: https://manual.calibre-ebook.com/server.html
OPDS reference: https://specs.opds.io/opds-1.2

Anna's Archive and Z-Library are not implemented: no verified provider interface
has been established in this project. Do not advertise these definitions as working
connectors for those services.

Live catalogue access and Android download/import still require device verification.
