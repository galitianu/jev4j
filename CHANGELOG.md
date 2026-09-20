# Changelog

All notable changes to jev4j are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and jev4j follows
[semantic versioning](https://semver.org/spec/v2.0.0.html).

While the version is below 1.0.0 a minor bump may contain breaking changes.

## [Unreleased]

### Changed

- Logging is no longer on by default. Request and response summaries moved from `INFO` to
  `DEBUG`, so adding jev4j to an application no longer writes two lines per API call to
  stderr on a stock JVM, or to Logback in Spring Boot.
- The single `com.galitianu.jev4j` logger split into `com.galitianu.jev4j.http`
  (method, path, status, timing, retries; `DEBUG`) and `com.galitianu.jev4j.wire`
  (request and response bodies; `TRACE`). Debugging latency no longer emits the caller's
  state, which is usually their own users' content, into the log.
- Failures are no longer logged before being thrown; they were reported twice.

### Added

- A `WARNING` when a caller-supplied header is ignored because the SDK sets that header
  itself. It was previously dropped silently with no way to find out why.

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
