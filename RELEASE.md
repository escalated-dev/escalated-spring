# Releasing `dev.escalated:escalated-spring` to Maven Central

Publishing a GitHub release runs `.github/workflows/publish.yml`, which
publishes `dev.escalated:escalated-spring` to Maven Central through the
[Central Portal](https://central.sonatype.com).

## What the workflow does

1. Checks out the release tag (`vX.Y.Z`).
2. Fails if `version` in `build.gradle.kts` is not `X.Y.Z`.
3. `./gradlew build` (compile, Checkstyle, the H2 test suite, jar, sources jar,
   javadoc jar).
4. Skips the rest, still green, unless all four secrets below are set; each
   missing one is logged as `skipping: <SECRET> not configured`.
5. Skips if `X.Y.Z` is already on `repo1.maven.org` (safe re-runs).
6. `./gradlew centralBundle` signs the publication with the in-memory GPG key,
   writes a Maven-layout staging repo (jar, sources, javadoc, POM, Gradle
   module, `.asc` signatures, md5/sha1/sha256/sha512) and zips it to
   `build/central-bundle.zip`, which is also uploaded as a workflow artifact.
7. Uploads the bundle to the Central Portal Publisher API with
   `publishingType=AUTOMATIC` and waits for `PUBLISHED` (fails on `FAILED`).

## Required secrets

Add in **Settings -> Secrets and variables -> Actions** (repo or org level).
These are the same names `escalated-dev/escalated-locale` uses.

| Secret | Value |
|---|---|
| `MAVEN_USERNAME` | Central Portal **user token** username (<https://central.sonatype.com/account> -> Generate User Token), not the login name. |
| `MAVEN_PASSWORD` | The user token password. |
| `MAVEN_GPG_PRIVATE_KEY` | ASCII-armored private key: `gpg --armor --export-secret-keys <KEY_ID>`. |
| `MAVEN_GPG_PASSPHRASE` | That key's passphrase. |

One-time setup:

1. Verify the `dev.escalated` namespace on the Central Portal (DNS TXT record on
   `escalated.dev`).
2. Generate a signing key (`gpg --full-generate-key`, RSA 4096) and publish the
   public half: `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`.
   Central rejects signatures it cannot verify against a public keyserver.

## Cutting a release

1. Bump `version` in `build.gradle.kts` (and the README install snippet, which
   `scripts/verify_package.py` checks) and update `CHANGELOG.md`; merge to `main`.
2. `gh release create vX.Y.Z --generate-notes` (or the GitHub UI). Publishing the
   release triggers the workflow.

## Re-running for an existing tag

```bash
gh workflow run publish.yml -f tag=v0.2.1
gh run watch "$(gh run list --workflow publish.yml --limit 1 --json databaseId -q '.[0].databaseId')"
```

Or **Actions -> Publish -> Run workflow**, tag `v0.2.1`.

`v0.2.1` was tagged before this publishing config existed. For such a tag the
workflow uses `build.gradle.kts` from the commit it runs on (`main`), but only
if no other source file differs from the tag, so the published code is exactly
the tagged code; it prints the build-script diff. Once `main` moves on, an old
tag fails with a clear error and needs a new release instead.

## Local checks

```bash
./gradlew publishToMavenLocal        # unsigned; ~/.m2/repository/dev/escalated/escalated-spring/
ORG_GRADLE_PROJECT_signingKey="$(cat key.asc)" ORG_GRADLE_PROJECT_signingPassword=... \
  ./gradlew centralBundle            # signed build/central-bundle.zip
```

## Dependencies

Every runtime dependency in the generated POM is versioned and on Maven
Central. Translations from `escalated-dev/escalated-locale` are vendored under
`META-INF/escalated/locale/`, so the artifact does not need
`dev.escalated:escalated-locale` (not yet on Maven Central).
