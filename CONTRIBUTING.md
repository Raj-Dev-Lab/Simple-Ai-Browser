# Contributing to Simple AI Browser

Thank you for contributing to **Simple AI Browser**!

## Getting Started

1. Fork the repository or create a feature branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Copy `.env.example` to `.env` and add your `GEMINI_API_KEY`.
4. Run the dev server:
   ```bash
   npm run dev
   ```

## Before Opening a Pull Request

- Run type checks and verify the production build:
  ```bash
  npm run lint
  npm run build
  ```
- Never commit `.env`, API keys, or personal access tokens.
- Keep commits focused and write clear commit messages (for example: `feat: add tab bar`, `docs: update setup guide`).

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](./LICENSE).
