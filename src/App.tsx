/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import {BookOpen, CheckCircle2, FileCode2, FolderGit2, KeyRound, ShieldCheck} from 'lucide-react';

const WORKSPACE_ITEMS = [
  {
    icon: FolderGit2,
    title: 'Repository Connected',
    detail: 'Initialized with README, Apache-2.0 License, Contributing guide, and Git ignore rules.',
  },
  {
    icon: FileCode2,
    title: 'Core Stack Ready',
    detail: 'React 19, TypeScript, Vite 8, Tailwind CSS v4, Express, and Gemini SDK installed.',
  },
  {
    icon: KeyRound,
    title: 'Environment Template',
    detail: 'GEMINI_API_KEY and APP_URL defined cleanly in .env.example without hardcoded secrets.',
  },
  {
    icon: ShieldCheck,
    title: 'Security Hygiene',
    detail: 'Secret files excluded from Git and server-side key boundaries documented in SECURITY.md.',
  },
];

export default function App() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <header className="border-b border-slate-800/80 bg-slate-950/90">
        <div className="mx-auto max-w-4xl px-6 py-5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-sky-500/15 border border-sky-400/30 flex items-center justify-center text-sky-400">
              <BookOpen className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-lg font-semibold tracking-tight text-white">
                Simple AI Browser
              </h1>
              <p className="text-xs text-slate-400">
                Workspace Initialized &amp; Documented
              </p>
            </div>
          </div>
          <span className="text-xs font-medium text-emerald-400 flex items-center gap-1.5">
            <CheckCircle2 className="h-4 w-4" />
            Ready to Build
          </span>
        </div>
      </header>

      <main className="flex-1 mx-auto w-full max-w-4xl px-6 py-12 space-y-10">
        <section className="space-y-3">
          <h2 className="text-3xl font-semibold tracking-tight text-white">
            Your workspace is ready.
          </h2>
          <p className="text-slate-300 max-w-2xl leading-relaxed">
            Documentation, licensing, environment templates, and TypeScript tooling are all set up.
            Tell us what features you want in your Simple AI Browser next (tabs, address bar,
            page summaries, bookmarks, or search) and we will build them right here.
          </p>
        </section>

        <section className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {WORKSPACE_ITEMS.map(({icon: Icon, title, detail}) => (
            <div
              key={title}
              className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-2"
            >
              <div className="flex items-center gap-2.5 text-sky-400">
                <Icon className="h-4 w-4 shrink-0" />
                <h3 className="text-sm font-medium text-white">{title}</h3>
              </div>
              <p className="text-sm text-slate-400 leading-relaxed">{detail}</p>
            </div>
          ))}
        </section>

        <section className="rounded-xl border border-slate-800 bg-slate-900/40 p-6 space-y-3">
          <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
            Quick Setup Reminder
          </h3>
          <ol className="list-decimal list-inside space-y-1.5 text-sm text-slate-300">
            <li>
              Get your API key from Google AI Studio and add it as{' '}
              <code className="px-1.5 py-0.5 rounded bg-slate-800 text-sky-300 font-mono text-xs">
                GEMINI_API_KEY
              </code>{' '}
              in Secrets (or your local <code className="px-1.5 py-0.5 rounded bg-slate-800 text-sky-300 font-mono text-xs">.env</code> file).
            </li>
            <li>
              Read <code className="px-1.5 py-0.5 rounded bg-slate-800 text-sky-300 font-mono text-xs">docs/SETUP.md</code> for step-by-step instructions.
            </li>
            <li>
              Revoke and regenerate any GitHub token shared in chat once your initial push is complete.
            </li>
          </ol>
        </section>
      </main>
    </div>
  );
}
