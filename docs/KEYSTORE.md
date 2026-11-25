# Signing Keys & First-Time Keystore Setup

Every Android app must be **signed** before a phone will install it. A signature is produced with a
private key that lives in a *keystore* file. Simple AI Browser (SAB) uses exactly **two** keys — one
for debug builds and one for release builds — and they must **never be regenerated or replaced**: an
Android phone only accepts an app update if it is signed with the *same key* as the installed app.

This page is written for the project owner (no GitHub or Android knowledge needed) and for
developers.

---

## 1. The two keys at a glance

| | Debug key | Release key |
|---|---|---|
| Keystore file | `debug.keystore` (repo root) | `sab-release.jks` (private) |
| Committed to Git? | **Yes, on purpose** | **No, never** |
| Key alias | `androiddebugkey` | `upload` |
| Passwords | `android` / `android` (the public Android standard) | random 48-character secret, stored only in the owner's credentials file |
| Signs | `sab-debug-*.apk` | `sab-release-*.apk` |
| Certificate | `CN=Android Debug, O=Android, C=US` | `CN=Simple AI Browser, OU=Release, O=Simple AI Browser` |
| Valid until | 17 Nov 2056 | 17 Nov 2056 |
| SHA-256 fingerprint | `59:24:C3:A9:17:0E:86:4F:00:E4:43:07:C7:F0:3E:DC:56:1D:F9:CF:29:AE:29:E4:C0:89:13:30:FD:E5:1D:C8` | `4F:8B:CD:E6:72:3E:21:AF:74:29:4D:65:0B:1A:FA:4D:D8:63:24:82:EE:6D:D6:95:DC:A4:2A:9D:A0:73:80:B9` |

**Why is the debug key committed?** Android Studio normally generates a *random* debug key on every
machine. If each PC and each CI run used a different key, every second debug build would fail to
install with *"App not installed… signatures do not match"*. SAB therefore ships **one fixed debug
key** in the repository. This is safe: the key only signs debug builds, and the password is the
public Android default.

**Why is the release key secret?** Anyone who holds it can build an APK that silently replaces the
official app on a user's phone. It exists in exactly three places: the owner's offline backup, the
GitHub Actions secrets, and (only while a release is being built) a temporary file on the CI runner.

The Gradle signing configuration in `app/build.gradle.kts` reads the release key from three
environment variables and must not be changed:

| Variable | Meaning |
|---|---|
| `KEYSTORE_PATH` | path to `sab-release.jks` on the build machine |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | private-key password (same value as the store password for this key) |

The alias is fixed to `upload` in `app/build.gradle.kts`.

---

## 2. One-time setup: add the release key to GitHub (about 3 minutes)

GitHub Actions (the automatic build robot) builds the release APK for you on every published version.
For that it needs the release key. You give it to GitHub **once**, as three encrypted *secrets*.

You need the owner folder **`keystore-backup`**, which contains three files:

| File | What it is |
|---|---|
| `sab-release.jks` | the release keystore itself |
| `sab-release.jks.base64.txt` | the same file converted to text (for GitHub) |
| `release-credentials.txt` | the passwords (`STORE_PASSWORD=…`, `KEY_PASSWORD=…`, `KEY_ALIAS=upload`) |

**Store this folder in two safe offline places now** (for example a USB stick and a password
manager). GitHub never shows a secret again after you save it, so GitHub is *not* your backup.

### Steps

1. Sign in to GitHub in your browser as the repository owner and open
   `https://github.com/Raj-Dev-Lab/Simple-Ai-Browser`.
2. Click the **Settings** tab of the repository.
3. In the left sidebar, under **Security**, click **Secrets and variables** → **Actions**.
4. Click the green button **New repository secret**.
5. Create these three secrets, one after another (the *Name* must match exactly, capital letters
   included):

   | Name | Secret (value) | Where to copy it from |
   |---|---|---|
   | `KEYSTORE_BASE64` | the complete content of `sab-release.jks.base64.txt` (one long line of text) | open the file in a text editor, select all, copy, paste |
   | `STORE_PASSWORD` | the text **after** `STORE_PASSWORD=` | `release-credentials.txt`, line 1 |
   | `KEY_PASSWORD` | the text **after** `KEY_PASSWORD=` | `release-credentials.txt`, line 2 |

6. After each one click **Add secret**. When finished, the page lists all three names with a green
   lock icon. You are done — nothing else needs to be configured.

Do **not** paste the passwords or the keystore into issues, pull requests, chats or e-mails, and do
not save them in any file inside the repository.

### How to check that it works

Push a version tag (see *Releasing a version* below) or open **Actions** → **Android CI** →
**Run workflow**. In the run summary the step **Build signed release APK** must have run (it is
skipped, without any error, while the secrets are missing) and the run must publish a
`sab-release-*.apk`. Anyone can verify that the APK really uses your key:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs sab-release-arm64-v8a.apk
```

The line `Signer #1 certificate SHA-256 digest` must show the release fingerprint from the table
above.

---

## 3. Building a release APK on your own computer

Export the three variables in the terminal, then build. Nothing is written into the repository.

```bash
export KEYSTORE_PATH="$HOME/keys/sab-release.jks"     # where you keep the keystore
read -rs STORE_PASSWORD && export STORE_PASSWORD       # typed hidden, not saved in shell history
read -rs KEY_PASSWORD    && export KEY_PASSWORD
./gradlew :app:assembleRelease
```

The signed APKs appear under `app/build/outputs/apk/release/`.

---

## 4. Restoring the keystore on a new computer

If only the text version of the key survived, rebuild the keystore from it:

- **Linux:** `base64 -d sab-release.jks.base64.txt > sab-release.jks`
- **macOS:** `base64 -D -i sab-release.jks.base64.txt -o sab-release.jks`
- **Windows PowerShell:**
  `[IO.File]::WriteAllBytes("sab-release.jks", [Convert]::FromBase64String((Get-Content .\sab-release.jks.base64.txt -Raw)))`

Check that the restored file is the right key (it must print the alias `upload` and the fingerprint
from the table above):

```bash
keytool -list -v -keystore sab-release.jks
```

---

## 5. Releasing a version

Releases are fully automatic. The CI workflow builds, verifies and publishes both APK types when a
tag starting with `v` is pushed:

```bash
git tag -a v1.3.0 -m "Release v1.3.0"
git push origin v1.3.0
```

| Tag | Result |
|---|---|
| `v*` on a commit | GitHub Release with `sab-debug-*.apk` and signed `sab-release-*.apk` attached |
| push to `main` | build + tests + unsigned release check, no Release |
| pull request | build + tests only |

---

## 6. Emergency notes

- **Debug key lost or replaced:** harmless for users. Debug builds simply need to be uninstalled and
  re-installed once. The committed key is the single source of truth — do not regenerate it.
- **Release key lost:** a phone can never accept an update signed with a different key. Google Play
  (with Play App Signing) can reset the *upload* key for the store listing; users who installed the
  APK directly would have to uninstall and install the new signing as a fresh app. This is why the
  backup rule above exists.
- **Release key leaked (published somewhere):** treat it as compromised. Create a new keystore with a
  new alias, replace the three GitHub secrets, rotate the passwords, and publish a new major version
  explaining that users must re-install once.
- **A token or password was pasted somewhere public:** revoke it immediately under
  **GitHub → Settings → Developer settings → Personal access tokens** and change the keystore
  passwords (see `keytool -storepasswd` and `-keypasswd`).
