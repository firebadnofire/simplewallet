# Release APK Workflow

This document explains `.forgejo/workflows/release-apk.yml`. The workflow builds
a signed Android release APK when a version tag is pushed, then publishes that
APK to Forgejo releases and, when configured, to a GitHub mirror.

## Trigger

The workflow runs on pushed tags matching:

```yaml
on:
  push:
    tags:
      - "v*"
      - "V*"
```

## Runner Environment

The job runs on:

```yaml
runs-on: ubuntu-22.04
```

and uses:

```yaml
container:
  image: ubuntu:22.04
  options: --network ci-network
```

## Required Secrets

Forgejo release builds require:

| Secret | Purpose |
| --- | --- |
| `KEY_ALIAS` | Android signing key alias |
| `KEY_PASSWORD` | Android signing key password |
| `KEYSTORE_BASE64` | Base64-encoded Android keystore |
| `KEYSTORE_PASSWORD` | Android keystore password |

Optional GitHub mirroring requires:

| Setting | Purpose |
| --- | --- |
| `GH_KEY` | GitHub token for release publishing |
| `GITHUB_RELEASE_OWNER` | GitHub owner or organization |
| `GITHUB_RELEASE_REPO` | GitHub repository name |

If `GH_KEY` is set, both GitHub repository variables must also be set.

## Android Signing

The workflow decodes the keystore to:

```text
${RUNNER_TEMP}/simplewallet-release.keystore
```

It exports these variables before Gradle runs:

```text
RELEASE_KEYSTORE_PATH
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

The Gradle build should use them only when all values are present.

## Build Output

The build command is:

```bash
./gradlew --no-daemon assembleRelease
```

The workflow expects exactly one APK under:

```text
app/build/outputs/apk/release/*.apk
```

It copies that APK to:

```text
dist/simplewallet-${TAG}.apk
```

## Publishing

Forgejo publishing is always active for matching tags.

GitHub publishing is optional. If `GH_KEY` is missing, the workflow prints a
skip message and does not publish to GitHub.

Both publish steps are idempotent for the same tag:

1. Look up the release by tag.
2. Create it if missing.
3. Patch it if present.
4. Replace any existing APK asset with the same name.
5. Upload `simplewallet-${tag}.apk`.
