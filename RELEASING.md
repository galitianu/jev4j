# Releasing jev4j

jev4j is published to Maven Central as `com.galitianu:jev4j`. Releases are cut by pushing
a tag; nothing is published from a laptop.

A version released to Maven Central can never be replaced or withdrawn. The release
workflow checks the tag, the project version, the README and the changelog before it
uploads anything, but the checks only catch what they know about — read the diff first.

## One-time setup

### 1. Verify the `com.galitianu` namespace

In the [Central Portal](https://central.sonatype.com/), add the namespace `com.galitianu`
and complete the DNS challenge: a TXT record on `galitianu.com` containing the
verification code the Portal shows. Verification is permanent once it succeeds.

### 2. Generate a Central Portal token

Portal → *View Account* → *Generate User Token*. It gives a username and a password;
these are the `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` secrets below. They are
not your Portal login.

### 3. Create and publish a signing key

Central requires a detached GPG signature for every artifact, and the public key must be
resolvable from a keyserver. You need `gpg` locally for this step only (`brew install
gnupg`); the release itself signs in CI from the exported key.

```sh
gpg --quick-generate-key "Andrei Galitianu <you@galitianu.com>" rsa4096 sign 2y
gpg --list-secret-keys --keyid-format=long          # note the key id
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --armor --export-secret-keys <KEY_ID>           # the value for SIGNING_KEY
```

Export the *secret* key in ASCII-armored form, newlines and all, including the
`-----BEGIN/END PGP PRIVATE KEY BLOCK-----` lines. Keep a backup somewhere offline: losing
it means future releases are signed by a different key than past ones.

### 4. Add the repository secrets

Settings → Secrets and variables → Actions → *Secrets*:

| Secret                    | Value                                          |
| ------------------------- | ---------------------------------------------- |
| `MAVEN_CENTRAL_USERNAME`  | Portal user token username                     |
| `MAVEN_CENTRAL_PASSWORD`  | Portal user token password                     |
| `SIGNING_KEY`             | ASCII-armored GPG secret key                   |
| `SIGNING_KEY_PASSWORD`    | passphrase for that key (empty if none)        |

Under *Variables*, set `PUBLISH_SNAPSHOTS` to `true` to publish a snapshot from every push
to `main`. Leave it unset until the secrets are in place; the snapshot job is skipped
without it.

## Cutting a release

Say the new version is `0.2.0`.

1. Move the `## [Unreleased]` entries in `CHANGELOG.md` into a `## [0.2.0] - <date>`
   section and update the link definitions at the bottom.
2. Set `version = "0.2.0"` in `build.gradle.kts`. This is the only place the version
   lives; `Version.VERSION` is generated from it at build time.
3. Update both install snippets in `README.md` to `0.2.0`.
4. `./gradlew build javadoc` locally, then commit as `Release 0.2.0`.
5. Tag and push:

   ```sh
   git tag v0.2.0
   git push origin main v0.2.0
   ```

The `Release` workflow then verifies the tag against the project version, the README and
the changelog, builds and tests, signs, publishes to Maven Central, and creates the
GitHub release from the changelog section. The `Javadoc` workflow publishes the API
reference to `/0.2.0/` and moves `/latest/`.

Artifacts usually appear on `central.sonatype.com` within minutes and reach
`repo1.maven.org` within a few hours.

6. Afterwards, bump `build.gradle.kts` to the next snapshot (`0.3.0-SNAPSHOT`) and commit,
   so `main` publishes snapshots again. Leave the README install snippets on `0.2.0` —
   they should always name the newest release.

## Local checks

```sh
./gradlew publishToMavenLocal   # full artifact set into ~/.m2; unsigned without a key
./gradlew -q printVersion
./gradlew build -PjavaToolchain=17   # what CI runs on the oldest supported JDK
```

`./gradlew publishToMavenCentral` uploads a deployment to the Portal *without* releasing
it, which is the safe way to inspect a candidate by hand. Only
`publishAndReleaseToMavenCentral`, which the workflow runs, makes a release permanent.

## Compatibility

Below 1.0.0, a minor bump may break source or binary compatibility; say so in the
changelog when it does. At 1.0.0, add a binary compatibility check (japicmp or revapi) to
CI and treat breakage as a major bump.

The library compiles with `--release 17` so it works on Spring Boot 3.x's baseline JDK.
CI builds and tests on 17, 21 and 25. Raising the floor is a breaking change.
