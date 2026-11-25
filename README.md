# Simple AI Browser

A clean, fast AI-powered web browsing and research workspace built with React, TypeScript, Vite, Tailwind CSS, and the Gemini SDK.

---

## Workspace Status

This repository includes:

- **Architecture & Research Blueprint:** Deep teardown of Opera Mobile UI (`docs/OPERA_UI_ANALYSIS.md`), GeckoView 159+ non-omni `arm64-v8a` Android 10+ setup (`docs/GECKOVIEW_ENGINE.md`), and the decoupled engine-swap spec (`docs/ARCHITECTURE.md`)
- **Android Engine-Swap Starter (`android/`):** Gradle catalog (`geckoview-arm64-v8a:159+`), `BrowserEngine` contracts, `GeckoEngineAdapter`, and `sab-bridge` WebExtension
- **Interactive Web Showcase & UI Prototype (`src/`):** Live SAB logo, splash animation, Opera-style mobile UI prototype, and interactive architecture explorer
- **Documentation & Governance:** `README.md`, `docs/SETUP.md`, `CONTRIBUTING.md`, `SECURITY.md`, `LICENSE` (Apache-2.0)

---

## Required Keys & Environment Setup

You do **not** put secrets directly in source code. All sensitive keys live in environment variables (see [`.env.example`](./.env.example)):

| Key | Purpose | Where to Get It |
| --- | --- | --- |
| `GEMINI_API_KEY` | Authenticates server-side Gemini API requests | [Google AI Studio API Keys](https://aistudio.google.com/apikey) |
| `APP_URL` | Public URL of your deployed or local app | Auto-set in AI Studio, or `http://localhost:3000` locally |

For full step-by-step instructions (including screenshots-free walkthroughs for beginners), read **[docs/SETUP.md](./docs/SETUP.md)**.

---

## Quick Start (Local Development)

### Prerequisites

- **Node.js** `18+` (recommended `20+`)
- **npm** (comes with Node.js) or **bun**

### 1. Clone the repository

```bash
git clone https://github.com/Raj-Dev-Lab/Simple-Ai-Browser.git
cd Simple-Ai-Browser
```

### 2. Install dependencies

```bash
npm install
```

### 3. Configure environment variables

```bash
cp .env.example .env
```

Open `.env` and replace `"MY_GEMINI_API_KEY"` with your real key from [Google AI Studio](https://aistudio.google.com/apikey), and set `APP_URL="http://localhost:3000"`.

> **Never commit `.env` to GitHub.** It is already listed in `.gitignore`.

### 4. Start the development server

```bash
npm run dev
```

Open `http://localhost:3000` in your browser.

---

## Available Scripts

| Command | Description |
| --- | --- |
| `npm run dev` | Start the Vite development server on port `3000` |
| `npm run build` | Build the production bundle into `dist/` |
| `npm run preview` | Preview the production build locally |
| `npm run lint` | Run TypeScript type-checking (`tsc --noEmit`) |
| `npm run clean` | Remove build output (`dist/`) |

---

## Project Structure

```text
Simple-Ai-Browser/
├── android/
│   ├── app/build.gradle.kts              # Android 10+ (minSdk 29) & arm64-v8a config
│   ├── core-browser/                     # Engine-agnostic BrowserEngine & TabSession contracts
│   ├── engine-gecko/                     # GeckoView 159+ non-omni adapter & sab-bridge
│   ├── gradle/libs.versions.toml         # Pinned GeckoView 159+ Maven coordinates
│   └── settings.gradle.kts               # Mozilla Maven repository setup
├── docs/
│   ├── ARCHITECTURE.md                   # 5-layer Opera -> SAB engine-swap blueprint
│   ├── GECKOVIEW_ENGINE.md               # GeckoView 159+ non-omni arm64-v8a guide
│   ├── OPERA_UI_ANALYSIS.md              # Opera Mobile UI & thumb-zone teardown
│   └── SETUP.md                          # Beginner-friendly API key & setup guide
├── src/
│   ├── assets/images/                    # Generated SAB app logo asset
│   ├── components/                       # SplashOverlay, SabLogo, Prototype, Explorer
│   ├── data/                             # Architecture & Speed Dial blueprint data
│   ├── App.tsx                           # Root application component
│   ├── index.css                         # Tailwind CSS entry point
│   └── main.tsx                          # React DOM mount
├── .env.example                          # Environment variable template (safe to commit)
├── .gitignore                            # Excludes .env, node_modules, dist, logs
├── CONTRIBUTING.md       # Contribution guidelines
├── index.html            # HTML entry & OpenGraph metadata
├── LICENSE               # Apache License 2.0
├── metadata.json         # App manifest metadata
├── package.json          # Dependencies and npm scripts
├── README.md             # Project overview
├── SECURITY.md           # Security & key management policy
├── tsconfig.json         # TypeScript configuration
└── vite.config.ts        # Vite bundler configuration
```

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](./CONTRIBUTING.md) before opening a pull request.

## Security

If you accidentally paste or commit an API key or GitHub token, revoke it immediately and see [SECURITY.md](./SECURITY.md).

## License

Licensed under the **Apache License 2.0**. See [LICENSE](./LICENSE) for details.
