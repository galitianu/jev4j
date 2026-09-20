# Security policy

## Supported versions

jev4j is pre-1.0. Fixes go into the next release from `main`; older minors are not
patched. If you are on an older version, upgrading is the fix.

| Version | Supported |
| ------- | --------- |
| 0.1.x   | yes       |

## Reporting a vulnerability

Please do not open a public issue for a security problem.

Use GitHub's [private vulnerability
reporting](https://github.com/galitianu/jev4j/security/advisories/new), which is enabled
on this repository. If that does not work for you, email <andrei@galitianu.com>.

Include what you need to make the problem reproducible: the jev4j version, the JDK, and
the smallest snippet that shows the behaviour. Please redact your API key.

Expect an acknowledgement within a few days. Since this is a small project maintained by
one person, I would rather set that expectation honestly than promise a same-day SLA.

## Scope

In scope: anything in the published `com.galitianu:jev4j` artifact — credential handling,
what the client puts on the wire, what it writes to logs or exception messages,
deserialization of API responses, and TLS or redirect behaviour.

Out of scope: the TypeSafe AI service itself (report that to the service operator), and
findings that require an attacker to already control the JVM running your code.

## Release integrity

Releases are built and signed only by the tag-triggered
[`Release` workflow](.github/workflows/release.yml); nothing is published from a
developer machine.

Every artifact on Maven Central is signed with:

```
Andrei Galitianu <andrei@galitianu.com>
7B9C 0BE8 A57F C168 3936  272F 31F0 7899 40A0 31BB
```

To check a download:

```sh
gpg --keyserver keyserver.ubuntu.com --recv-keys 7B9C0BE8A57FC1683936272F31F0789940A031BB
gpg --verify jev4j-0.1.0.jar.asc jev4j-0.1.0.jar
```

A good result names the key above. gpg will also warn that the key "is not certified
with a trusted signature" — that is expected and only means you have not personally
signed it; it does not indicate a problem with the artifact.

The key is also on `keys.openpgp.org`, but that server strips the user ID from a key
until the address is confirmed, and gpg discards a key with no user ID. Use
`keyserver.ubuntu.com`.

If a jar claiming to be jev4j does not verify against that fingerprint, please report it.
