# Changelog

All notable changes to jev4j are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and jev4j follows
[semantic versioning](https://semver.org/spec/v2.0.0.html).

While the version is below 1.0.0 a minor bump may contain breaking changes.

## [Unreleased]

## [0.1.0] - 2026-09-20

First public release.

### Added

- `TypeSafeClient` for `POST /v1/systemone`, synchronous and `CompletableFuture`-based,
  with retries, timeouts and typed exceptions per HTTP status.
- Typed question handles: `Noul` (yes/no probability), `Choice` (a label from an enum or a
  fixed set) and `Score` (a rating on an ordered rubric), read back through
  `SystemOneResponse.get(question)`.
- `Models` resource for listing available models.
- Configuration from the builder, then `TYPESAFE_API_KEY`, `TYPESAFE_BASE_URL` and
  `TYPESAFE_DEFAULT_MODEL`, then defaults.

[Unreleased]: https://github.com/galitianu/jev4j/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/galitianu/jev4j/releases/tag/v0.1.0
