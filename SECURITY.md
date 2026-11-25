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
- Never commit `.env`, `debug.keystore`, release `.jks` upload keys, or GitHub Personal Access Tokens (`github_pat_...`) into the repository.
- If a token is ever exposed, revoke it immediately in **GitHub → Settings → Developer settings → Personal access tokens**.
