import {useState} from 'react';
import {AnimatePresence, motion} from 'motion/react';
import {ExternalLink, Loader2, Send, Sparkles, Trash2, X} from 'lucide-react';
import type {AiChatMessage, BrowserTheme, ParsedPageData} from '../types/browser';

interface SabAiSheetProps {
  open: boolean;
  onClose: () => void;
  theme: BrowserTheme;
  activePage: ParsedPageData | null;
  messages: AiChatMessage[];
  isThinking: boolean;
  onSendPrompt: (prompt: string) => void;
  onClearChat: () => void;
  onOpenSourceUrl: (url: string) => void;
}

const PAGE_QUICK_PROMPTS = [
  'Summarize this page in 4 bullet points',
  'What are the key takeaways?',
  'Explain this page in simple terms',
];

const GENERAL_QUICK_PROMPTS = [
  'What are the top AI & browser tech updates today?',
  'Explain how Mozilla GeckoView works on Android 10+',
  'Compare bottom-bar mobile browsers for one-handed use',
];

export function SabAiSheet({
  open,
  onClose,
  theme,
  activePage,
  messages,
  isThinking,
  onSendPrompt,
  onClearChat,
  onOpenSourceUrl,
}: SabAiSheetProps) {
  const [input, setInput] = useState('');
  const isLight = theme === 'daylight';

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = input.trim();
    if (!trimmed || isThinking) return;
    onSendPrompt(trimmed);
    setInput('');
  };

  const quickPrompts = activePage ? PAGE_QUICK_PROMPTS : GENERAL_QUICK_PROMPTS;

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{opacity: 0}}
          animate={{opacity: 1}}
          exit={{opacity: 0}}
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center sm:p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{y: 40, opacity: 0}}
            animate={{y: 0, opacity: 1}}
            exit={{y: 40, opacity: 0}}
            transition={{duration: 0.2, ease: [0.16, 1, 0.3, 1]}}
            onClick={(e) => e.stopPropagation()}
            className={`w-full sm:max-w-2xl h-[82vh] sm:h-[78vh] rounded-t-3xl sm:rounded-3xl border flex flex-col overflow-hidden shadow-2xl ${
              isLight
                ? 'bg-white border-slate-200 text-slate-900'
                : 'bg-[#141821] border-white/10 text-slate-100'
            }`}
          >
            {/* Header */}
            <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between gap-3 shrink-0">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-full bg-[#FF2E4C] text-white flex items-center justify-center shrink-0">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-semibold">SAB AI</div>
                  <div className="text-xs text-slate-400 truncate">
                    {activePage
                      ? `Reading ${activePage.domain}`
                      : 'Web-grounded Gemini Assistant'}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-1.5">
                {messages.length > 0 && (
                  <button
                    type="button"
                    onClick={onClearChat}
                    aria-label="Clear AI chat"
                    className="p-2 rounded-xl hover:bg-white/10 text-slate-400 hover:text-white transition-colors"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                )}
                <button
                  type="button"
                  onClick={onClose}
                  aria-label="Close SAB AI"
                  className="p-2 rounded-xl hover:bg-white/10 text-slate-400 hover:text-white transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Messages Area */}
            <div className="flex-1 overflow-y-auto p-5 space-y-4">
              {messages.length === 0 ? (
                <div className="space-y-5 py-4">
                  <div className="space-y-1.5">
                    <h3 className="text-base font-semibold">
                      {activePage
                        ? `Ask about "${activePage.title}"`
                        : 'Ask SAB AI anything'}
                    </h3>
                    <p className="text-xs text-slate-400 leading-relaxed">
                      {activePage
                        ? 'SAB extracts the active webpage text and answers directly from page context.'
                        : 'Search the web with live grounding or ask technical questions.'}
                    </p>
                  </div>

                  <div className="space-y-2">
                    {quickPrompts.map((prompt) => (
                      <button
                        key={prompt}
                        type="button"
                        onClick={() => onSendPrompt(prompt)}
                        className={`w-full text-left px-4 py-3 rounded-2xl border text-xs font-medium transition-colors hover:border-[#FF2E4C]/50 ${
                          isLight
                            ? 'bg-slate-50 border-slate-200 text-slate-800'
                            : 'bg-[#0B0D12] border-white/10 text-slate-200'
                        }`}
                      >
                        {prompt}
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex flex-col ${
                      msg.role === 'user' ? 'items-end' : 'items-start'
                    }`}
                  >
                    <div
                      className={`max-w-[88%] rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap ${
                        msg.role === 'user'
                          ? 'bg-[#FF2E4C] text-white'
                          : msg.role === 'error'
                            ? 'bg-red-500/15 border border-red-500/30 text-red-300'
                            : isLight
                              ? 'bg-slate-100 text-slate-900'
                              : 'bg-[#0B0D12] border border-white/10 text-slate-200'
                      }`}
                    >
                      {msg.text}

                      {msg.sources && msg.sources.length > 0 && (
                        <div className="mt-3 pt-3 border-t border-white/10 space-y-1.5">
                          <div className="text-[11px] font-semibold text-slate-400">
                            Web Sources
                          </div>
                          {msg.sources.map((src) => (
                            <button
                              key={src.uri}
                              type="button"
                              onClick={() => {
                                onOpenSourceUrl(src.uri);
                                onClose();
                              }}
                              className="flex items-center gap-1.5 text-xs text-sky-400 hover:underline text-left"
                            >
                              <ExternalLink className="w-3 h-3 shrink-0" />
                              <span className="truncate">{src.title}</span>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                ))
              )}

              {isThinking && (
                <div className="flex items-center gap-2 text-xs text-slate-400">
                  <Loader2 className="w-4 h-4 animate-spin text-[#FF2E4C]" />
                  <span>SAB AI is thinking...</span>
                </div>
              )}
            </div>

            {/* Prompt Input */}
            <form
              onSubmit={handleSubmit}
              className="p-4 border-t border-white/10 shrink-0"
            >
              <div
                className={`flex items-center gap-2 p-2 rounded-2xl border ${
                  isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                }`}
              >
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder={
                    activePage ? 'Ask anything about this page...' : 'Ask SAB AI or search web...'
                  }
                  className="flex-1 bg-transparent px-2 text-sm focus:outline-none"
                />
                <button
                  type="submit"
                  disabled={isThinking || !input.trim()}
                  className="px-4 py-2 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold disabled:opacity-40 inline-flex items-center gap-1.5"
                >
                  <Send className="w-3.5 h-3.5" />
                  Send
                </button>
              </div>
            </form>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
