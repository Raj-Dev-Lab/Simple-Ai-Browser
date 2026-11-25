# Security Policy

## Secret & API Key Handling

- Never commit `.env` files, `GEMINI_API_KEY` values, or GitHub Personal Access Tokens (`github_pat_...` / `ghp_...`) to this repository.
- Always keep third-party API keys on the server side (`process.env.*`), never exposed in client-side `VITE_*` variables.
- Use `.env.example` strictly for placeholder variable names.

## If a Secret Is Exposed

1. **Revoke / Rotate immediately:**
   - For GitHub tokens: **GitHub → Settings → Developer settings → Personal access tokens → Revoke/Delete**.
   - For Gemini API keys: **Google AI Studio / Google Cloud Console → Credentials → Delete or Regenerate key**.
2. Remove the secret from your local `.env` or git history before pushing further commits.

## Reporting a Vulnerability

Please open a private security advisory in the GitHub repository's **Security** tab rather than posting sensitive exploit details in a public issue.
