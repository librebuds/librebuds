# LibreBuds 6i fork signing — v1.0.0

This fork has its **own signing identity**, separate from upstream LibreBuds and the APK attached to the older `Stable` release.

## Protect the keystore

Keep the private `.jks` file, passwords and GitHub Actions secret values backed up **offline** or in an encrypted password manager. Do not commit them to Git, upload them to GitHub releases, paste them into issues or share them in chats.

The public release certificate fingerprint (SHA-256) is:

```text
D7:1B:E0:50:B3:C9:6D:E6:63:4A:1A:13:61:26:AE:78:8E:F8:AD:35:EF:EA:8F:B9:88:58:C3:5F:C8:8D:85:D6
```

This public fingerprint is pinned in the release workflow. It is safe to publish; the keystore and passwords are not.

## Configure GitHub Actions

In **Repository → Settings → Secrets and variables → Actions → New repository secret**, create all four secrets (values are supplied privately to the repository owner in the signing kit):

| Secret name | Value |
| --- | --- |
| `LIBREBUDS_KEYSTORE_BASE64` | The single-line Base64 encoded contents of the newly generated `.jks` |
| `LIBREBUDS_STORE_PASSWORD` | Password for the keystore |
| `LIBREBUDS_KEY_ALIAS` | Key alias |
| `LIBREBUDS_KEY_PASSWORD` | Password for the release-signing private key |

The release workflow creates temporary local signing files on the GitHub runner, builds the release APK and deletes them afterwards.

## Publish v1.0.0

1. Merge the release-preparation pull request so the workflow file exists on the default branch.
2. Navigate to **Actions → Publish signed stable APK → Run workflow**.
3. Choose branch `release/stable-v1.0.0` and start the workflow.
4. The workflow checks the signing certificate fingerprint and package metadata before creating GitHub Release `v1.0.0`, with `librebuds.apk` and `librebuds.apk.sha256` attached.
5. Download the published APK and perform a fresh-install test on FreeBuds 6i.

> **Fresh install is required for users of the previous signing key.** Android rejects direct updates if APK signatures differ. Back up local app settings before uninstalling the old APK. Future releases should reuse the new fork keystore.

If any signing secret is missing or the uploaded keystore does not match the pinned fingerprint, publication fails without creating a release.
