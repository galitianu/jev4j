# jev4j

[![Maven Central](https://img.shields.io/maven-central/v/com.galitianu/jev4j?label=maven%20central)](https://central.sonatype.com/artifact/com.galitianu/jev4j)
[![CI](https://github.com/galitianu/jev4j/actions/workflows/ci.yml/badge.svg)](https://github.com/galitianu/jev4j/actions/workflows/ci.yml)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue)](https://galitianu.github.io/jev4j/latest/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

Java SDK for the [TypeSafe AI](https://typesafe.ai) API. Java 17+.

TypeSafe answers typed questions about a piece of text or JSON (the *state*) in one call:
a yes/no probability (**noul**), a pick from a fixed set (**choice**), or a rating on an
ordered rubric (**score**), each with probabilities and confidence.

API reference: **[latest](https://galitianu.github.io/jev4j/latest/)** &middot; [all versions](https://galitianu.github.io/jev4j/)

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.galitianu:jev4j:0.1.0")
}
```

```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.galitianu</groupId>
  <artifactId>jev4j</artifactId>
  <version>0.1.0</version>
</dependency>
```

Jackson (`jackson-databind`) is the only dependency.

Every commit on `main` is also published as a snapshot of the next version (for example
`0.2.0-SNAPSHOT`), from:

```kotlin
repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}
```

## Quickstart

Set `TYPESAFE_API_KEY` in your environment, then:

```java
import com.galitianu.jev4j.*;

enum Team {
    @Describe("charges, invoices, refunds") BILLING,
    @Describe("bugs and outages") TECHNICAL,
    OTHER
}

try (TypeSafeClient client = TypeSafeClient.create()) {
    var team    = Choice.of("Which team should handle this?", Team.class);
    var urgent  = Noul.of("Does the message express urgency?");
    var anger   = Score.of("How upset is the writer?", "neutral", "annoyed", "furious");

    SystemOneResponse res = client.systemOne(
        "I was charged twice. Please fix this ASAP.", team, urgent, anger);

    Team   t    = res.get(team).choice();          // Team.BILLING
    double conf = res.get(team).confidence();      // 0..1
    double p    = res.get(urgent).noul();          // 0..1
    double s    = res.get(anger).score();          // 0..2, may be fractional
}
```

Each question is a typed handle. Passing the handle back to `res.get(...)` returns the
matching answer record: `NoulAnswer`, `ChoiceAnswer<L>`, or `ScoreAnswer`. All answers
are also available as a sealed `Answer` you can `switch` over.

### State, instructions, and criteria

Strings are sent as-is. Anything else (records, POJOs, `Map`, `List`) is serialized to
JSON with Jackson, so structured state works directly:

```java
record Ticket(String subject, String body, List<String> tags) {}
client.systemOne(new Ticket(...), team);
```

Instructions and criteria descriptions may also be objects. The API lets instructions
reference sibling fields in backticks:

```java
Noul.of(Map.of(
    "question", "Does the state match `reference_record`?",
    "reference_record", Map.of("id", 42, "status", "open")));
```

### Choice labels

- **Enum**: label is the constant name in lower case, or `@Label("...")`. Description comes
  from `@Describe`. The answer's `choice()` is the enum constant.
- **Strings**: `Choice.of("Team?", "billing", "technical")` or a `Map<String, ?>` of
  label to description (`null` for none). The answer's `choice()` is the `String`.

### Score

`Score.of(instructions, "level 0", "level 1", ...)` with 2 to 10 levels. The answer has
`score()`, `confidence()`, `legend()` (index to description), `probabilities()`, plus
`nearestLevel()` and `topLevel()` helpers.

### Explicit keys, model override, extra fields

```java
SystemOneRequest req = SystemOneRequest.builder()
    .state(ticket)
    .model("jev-1.13.0")
    .question("team", team)                  // explicit wire key
    .question(urgent.named("urgent"))        // or name the handle
    .question(anger)                         // positional key: q0, q1, ...
    .extraBody("metadata", Map.of("tenant", "acme"))
    .options(RequestOptions.none().withTimeout(Duration.ofSeconds(30)))
    .build();

SystemOneResponse res = client.systemOne(req);
res.choice("team");     // by key
res.get(team);          // by handle, typed
```

String-keyed maps also work: `client.systemOne(state, Map.of("team", team))`.

### Async

Every call has an `...Async` twin returning `CompletableFuture`:

```java
client.systemOneAsync(ticket, team).thenAccept(res -> ...);
```

The sync methods block on the same futures, so they are safe on virtual threads.

### Models

```java
List<ModelCard> models = client.models().list();
```

## Configuration

Explicit builder values win over environment variables, which win over defaults.

| Builder            | Env var                  | Default                    |
|--------------------|--------------------------|----------------------------|
| `apiKey`           | `TYPESAFE_API_KEY`       | required                   |
| `baseUrl`          | `TYPESAFE_BASE_URL`      | `https://api.typesafe.ai`  |
| `defaultModel`     | `TYPESAFE_DEFAULT_MODEL` | `jev-latest`               |
| `timeout`          |                          | 10s per attempt            |
| `retry`            |                          | see below                  |
| `defaultHeader`    |                          | none                       |
| `httpClient`       |                          | a new `java.net.http` client |
| `objectMapper`     |                          | a new Jackson mapper       |

### Retries

Defaults match the JS and Python SDKs: 2 retries, exponential backoff 500ms to 5s with
25% jitter, retrying HTTP 408, 429, 5xx, connection errors and timeouts, honoring
`Retry-After` / `retry-after-ms` up to 60s.

```java
TypeSafeClient.builder()
    .retry(RetryPolicy.defaults().withMaxRetries(5).withBackoff(Duration.ofSeconds(1), Duration.ofSeconds(20)))
    .build();

RetryPolicy.none();   // disable
```

Per call: `RequestOptions.none().withRetry(...)`, `.withTimeout(...)`, `.withHeader(...)`.

### Errors

All exceptions extend `TypeSafeException` (unchecked).

| Exception                       | When                                   |
|---------------------------------|----------------------------------------|
| `BadRequestException`           | 400                                    |
| `AuthenticationException`       | 401                                    |
| `PermissionDeniedException`     | 403                                    |
| `NotFoundException`             | 404                                    |
| `UnprocessableEntityException`  | 422, message lists the offending fields |
| `RateLimitException`            | 429, exposes `retryAfter()`            |
| `InternalServerException`       | 5xx                                    |
| `ApiException`                  | any other non-2xx; `status()`, `body()`, `headers()`, `requestId()` |
| `ApiTimeoutException`           | an attempt exceeded the timeout        |
| `ApiConnectionException`        | DNS, TLS, connection failures          |
| `TypeSafeException`             | client-side validation or decoding     |

### Logging

The SDK logs through `System.Logger` under the name `com.galitianu.jev4j`: request
summaries at `INFO`, request bodies at `DEBUG`. Route it through SLF4J or JUL as usual.

## Development

```
./gradlew test          # unit tests against an in-process fake API
./gradlew demo          # live demo, needs TYPESAFE_API_KEY
./gradlew javadoc       # API reference in build/docs/javadoc
```

The build provisions JDK 21 via the Foojay toolchain resolver if none is installed.

Javadoc runs with `-Xdoclint:all,-missing -Xwerror`, so a broken `{@link}`, a stale
`@param` name or malformed HTML fails the build rather than reaching the docs site.
Undocumented elements are allowed; documentation that contradicts the code is not.

`.github/workflows/javadoc.yml` publishes to GitHub Pages: pushes to `main` land in
`/snapshot/`, and tagging `vX.Y.Z` publishes `/X.Y.Z/` and moves `/latest/` forward.
Released versions stay online, so users on an old release read docs for that release.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security reports go through
[SECURITY.md](SECURITY.md), not the issue tracker.
