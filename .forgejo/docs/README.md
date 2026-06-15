# Simple Wallet Release Workflow Notes

Use this checklist when maintaining or adapting Simple Wallet's Forgejo release
automation. The detailed workflow behavior is documented in
[`release-apk-workflow.md`](release-apk-workflow.md).

## Plan

1. Keep the APK artifact and release text aligned with Simple Wallet naming.
2. Keep Forgejo publishing required and GitHub publishing optional.
3. Read release signing values from environment variables in Gradle.
4. Configure Forgejo secrets, then configure optional GitHub variables only if a mirror is needed.
5. Validate the workflow syntax and a local release build before pushing a tag.

## Implementation

### Keep Project Naming Consistent

Edit `.forgejo/workflows/release-apk.yml`:

```bash
keystore_path="${temp_dir}/simplewallet-release.keystore"
export RELEASE_KEYSTORE_PATH="${temp_dir}/simplewallet-release.keystore"
cp "${apk_files[0]}" "dist/simplewallet-${tag}.apk"
asset_path="dist/simplewallet-${tag}.apk"
release_name="Simple Wallet ${tag}"
```

### Remove Source-Project Leftovers

Search for assumptions from the source project:

```bash
rg -n "fuelmath|trapmaster|upstream|PWA|PROJECT_SLUG|APP_NAME|GITHUB_REPO_NAME|milestones" .forgejo
```

There should be no app-specific setup left over from the source repository.

### Wire Release Signing in Gradle

The workflow exports these values before `assembleRelease`:

```text
RELEASE_KEYSTORE_PATH
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

The Android app should only attach release signing when all four values exist.

### Configure Secrets

Required Forgejo secrets:

```text
KEY_ALIAS
KEY_PASSWORD
KEYSTORE_BASE64
KEYSTORE_PASSWORD
```

Optional GitHub mirroring needs:

```text
GH_KEY
GITHUB_RELEASE_OWNER
GITHUB_RELEASE_REPO
```

The default GitHub mirror target is:

```text
firebadnofire/simplewallet
```

If `GH_KEY` exists as a global upstream secret, the workflow publishes there by
default. Set `GITHUB_RELEASE_OWNER` and `GITHUB_RELEASE_REPO` only to override
that target.

Generate `KEYSTORE_BASE64` from the keystore file with:

```bash
base64 -i release.keystore
```

Do not commit the keystore.

## Validation

Run these checks before pushing a release tag:

```bash
ruby -e 'require "yaml"; YAML.load_file(".forgejo/workflows/release-apk.yml"); puts "yaml ok"'
rg -n "fuelmath|trapmaster|upstream|PWA|PROJECT_SLUG|APP_NAME|GITHUB_REPO_NAME" .forgejo
GRADLE_USER_HOME="$PWD/.gradle" sh ./gradlew --no-daemon tasks --all
GRADLE_USER_HOME="$PWD/.gradle" sh ./gradlew --no-daemon assembleRelease
```

If signing secrets are absent locally, `assembleRelease` may produce an unsigned
APK. That is acceptable for local validation.
