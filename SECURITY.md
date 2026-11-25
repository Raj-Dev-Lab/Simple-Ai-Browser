# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 1.1.x   | Yes       |
| < 1.1.0 | No        |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities (such as WebExtension privilege escalation, intent redirection, URL spoofing, or private browsing data leaks).

Instead, report vulnerabilities privately via GitHub Security Advisories on `Raj-Dev-Lab/Simple-Ai-Browser` with:
- Affected SAB version and Android API level
- Steps to reproduce or proof-of-concept
- Expected vs. actual security behavior

We aim to acknowledge reports within 48 hours and publish coordinated fixes with credit.

## Credential & Token Hygiene
- Never commit `.env`, release upload keys (`*.jks`, `my-upload-key.jks`), or GitHub Personal Access Tokens (`github_pat_...`) into the repository.
- The one exception is `debug.keystore` in the repository root: it is committed **on purpose**. It only signs debug builds, uses the public Android debug password, and exists so that debug builds from every machine and from CI update each other in place. Only release keys are secret. See [docs/KEYSTORE.md](docs/KEYSTORE.md).
- Release signing credentials belong only in GitHub Actions secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) or in local environment variables — never in files inside the repository.
- If a token is ever exposed, revoke it immediately in **GitHub → Settings → Developer settings → Personal access tokens**.
