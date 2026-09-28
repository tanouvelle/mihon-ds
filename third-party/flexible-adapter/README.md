# FlexibleAdapter source dependency

This module contains the `flexible-adapter/src/main` sources from
https://github.com/arkon/FlexibleAdapter at commit
`c80135339bcff5f7f8c2c2380329dfc155b26232` (`c8013533`).

It replaces the unavailable JitPack dependency
`com.github.arkon.FlexibleAdapter:flexible-adapter:c8013533` with a local build
of the same revision. Java sources and Android resources are unchanged.

Build integration changes:
- Use Mihon DS's Android library convention and SDK configuration.
- Move the manifest package to the Gradle namespace (required by current AGP).
- Generate the original `BuildConfig.VERSION_NAME` value of `5.1.0`.
- Retain the original RecyclerView 1.1.0 compile dependency; the application
  still resolves its newer RecyclerView dependency normally.

The upstream Apache 2.0 license is included in `LICENSE`. Copyright headers
remain in the source files. No upstream publishing plugins or sample apps are included.
