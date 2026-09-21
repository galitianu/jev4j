# Contributing to jev4j

Thanks for taking the time. Issues and pull requests are both welcome; if a change is
large or reshapes the public API, please open an issue first so we can agree on the shape
before you write it.

## Building

Java 21 or newer to build; the library itself targets Java 17.

```sh
./gradlew build            # compile, test, javadoc-lint
./gradlew test             # unit tests against an in-process fake API
./gradlew build -PjavaToolchain=17   # what CI runs on the oldest supported JDK
```

CI builds and tests on JDK 17, 21 and 25. `./gradlew build` locally is usually enough;
the `-PjavaToolchain=17` run is worth doing if you touched anything language-level.

`./gradlew demo` runs `src/demo/java/Demo.java` against the live API and needs a real
`TYPESAFE_API_KEY`. It is not part of CI, but it is compiled by `check`, so it cannot
silently stop building.

## What the build enforces

- **Java 17.** `options.release = 17`, so anything newer fails at compile time rather
  than at a user's runtime. Spring Boot 3.x's baseline is 17 and we are not going above
  it without a good reason.
- **Javadoc correctness.** `-Xdoclint:all,-missing -Xwerror`: a broken `{@link}`, a
  `@param` naming an argument that does not exist, or malformed HTML fails the build.
  Types may go undocumented, but a doc comment that is present must be right.
- **`-Xlint:all`** on compilation.
- **One runtime dependency.** `jackson-databind`, and we intend to keep it that way.
  Logging goes through `System.Logger`, not SLF4J. Please do not add dependencies without
  discussing it first.

## Style

Match the surrounding code rather than any external style guide. A few things that are
consistent across the codebase and worth keeping:

- 4-space indent, no tabs, no wildcard imports.
- Comments explain *why*, not *what*. If a line needs a comment to say what it does, the
  line is usually the problem.
- Public API is deliberately small, and the package layout says which is which:
  `com.galitianu.jev4j` holds the client and the question and answer types,
  `com.galitianu.jev4j.errors` the exception hierarchy, and `com.galitianu.jev4j.internal`
  everything else. Only the first two are exported by `module-info.java`, so the types
  under `internal` are `public` for the compiler's benefit and not API. New internals
  belong there, or stay package-private where they already are (`AnswerDecoder` needs
  package-private access to `SystemOneResponse`, so it stays put).
- Exception messages name the thing that went wrong and, where possible, what to do about
  it.

## Tests

Tests run against `FakeApi`, an in-process HTTP server, so they are fast and need no
network or API key. New behaviour should come with a test; bug fixes should come with a
test that fails before the fix.

## Pull requests

- One logical change per PR. If you find an unrelated bug along the way, that is a second
  PR.
- Write the commit message for someone reading it in two years: what changed and why, not
  a restatement of the diff.
- `./gradlew build` must pass before you push.
- Please do not bump the version or edit `CHANGELOG.md`'s released sections — releases are
  cut separately, see [RELEASING.md](RELEASING.md). Adding to `## [Unreleased]` is welcome.

## Reporting bugs

Include the jev4j version, the JDK, and the smallest snippet that reproduces the problem.
Redact your API key. For security problems, do not open an issue — see
[SECURITY.md](SECURITY.md).
