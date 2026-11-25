# Beginner Setup Guide — Simple AI Browser

This guide walks you through setting up your API keys, running the project in Google AI Studio or on your own computer, and keeping your GitHub account safe.

---

## 1. Which Key Do You Need?

To power AI features in **Simple AI Browser**, you need one main key:

- **`GEMINI_API_KEY`** — your personal key from Google AI Studio that lets the backend talk to Gemini models.

*(You do not need to create an `APP_URL` manually inside Google AI Studio—it is injected automatically. Locally, set `APP_URL="http://localhost:3000"`.)*

---

## 2. How to Get Your `GEMINI_API_KEY`

1. Go to [Google AI Studio — API Keys](https://aistudio.google.com/apikey) and sign in with your Google account.
2. Click **Create API key**.
3. Choose a Google Cloud project (or let AI Studio create a default one) and confirm.
4. Copy the generated key (it starts with `AIza...`).
5. Keep it private—never share it in chat, screenshots, or GitHub commits.

---

## 3. Adding the Key

### Option A: Inside Google AI Studio

1. Open the **Settings** (or **Secrets**) panel in your AI Studio project.
2. Add a secret named:
   - **Name:** `GEMINI_API_KEY`
   - **Value:** *(paste your `AIza...` key)*
3. Save and restart the preview server if prompted.

### Option B: On Your Own Computer (Local `.env`)

1. Copy `.env.example` to a new file named `.env`:
   ```bash
   cp .env.example .env
   ```
2. Open `.env` in any text editor and update it:
   ```env
   GEMINI_API_KEY="paste-your-real-key-here"
   APP_URL="http://localhost:3000"
   ```
3. Run the app:
   ```bash
   npm install
   npm run dev
   ```
4. Visit `http://localhost:3000`.

---

## 4. Keeping Your GitHub Token (PAT) Safe

If you ever share a GitHub Personal Access Token (`github_pat_...`) in a chat, issue, or public file:

1. Go to **GitHub → Settings → Developer settings → Personal access tokens**.
2. Find the exposed token and click **Delete** / **Revoke**.
3. Generate a fresh token with only the permissions you need (for example, `Contents: Read and write` scoped only to `Simple-Ai-Browser`).
4. Never commit tokens into `.git/config`, `.env.example`, or source files. Use Git Credential Manager or SSH keys on your computer instead.
