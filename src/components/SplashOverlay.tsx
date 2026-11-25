import {motion} from 'motion/react';
import {SabLogo} from './SabLogo';

interface SplashOverlayProps {
  onDismiss: () => void;
  compact?: boolean;
}

export function SplashOverlay({onDismiss, compact = false}: SplashOverlayProps) {
  return (
    <motion.div
      initial={{opacity: 1}}
      animate={{opacity: 1}}
      exit={{opacity: 0}}
      transition={{duration: 0.25, ease: [0.16, 1, 0.3, 1]}}
      className={`${
        compact ? 'absolute inset-0 z-40 rounded-[34px]' : 'fixed inset-0 z-50'
      } bg-[#0B0D12] flex flex-col items-center justify-center px-6 select-none overflow-hidden`}
    >
      {/* Concentric geometric orbital rings */}
      <div className="relative flex items-center justify-center">
        <motion.div
          initial={{scale: 0.72, opacity: 0, rotate: -24}}
          animate={{scale: 1.28, opacity: 0.28, rotate: 0}}
          transition={{duration: 0.75, ease: [0.16, 1, 0.3, 1]}}
          className="absolute w-36 h-36 rounded-full border border-[#FF2E4C]/50"
        />
        <motion.div
          initial={{scale: 0.85, opacity: 0, rotate: 18}}
          animate={{scale: 1.52, opacity: 0.14, rotate: 0}}
          transition={{duration: 0.9, delay: 0.08, ease: [0.16, 1, 0.3, 1]}}
          className="absolute w-40 h-40 rounded-full border border-slate-300/30"
        />

        {/* SAB Emblem Reveal */}
        <motion.div
          initial={{scale: 0.78, opacity: 0, rotate: -10}}
          animate={{scale: 1, opacity: 1, rotate: 0}}
          transition={{duration: 0.55, ease: [0.16, 1, 0.3, 1]}}
          className="relative z-10 shadow-2xl shadow-[#FF2E4C]/20 rounded-3xl"
        >
          <SabLogo size={compact ? 88 : 108} className="rounded-3xl" />
        </motion.div>
      </div>

      {/* Wordmark Reveal */}
      <motion.div
        initial={{opacity: 0, y: 12}}
        animate={{opacity: 1, y: 0}}
        transition={{duration: 0.45, delay: 0.22, ease: [0.16, 1, 0.3, 1]}}
        className="mt-7 text-center space-y-1.5"
      >
        <div className="text-2xl font-bold tracking-tight text-white">SAB</div>
        <div className="text-xs tracking-wide text-slate-400">
          Simple AI Browser · GeckoView 159+
        </div>
      </motion.div>

      {/* Engine Boot Progress Line */}
      <div className="mt-7 w-40 h-1 rounded-full bg-slate-800 overflow-hidden">
        <motion.div
          initial={{scaleX: 0}}
          animate={{scaleX: 1}}
          transition={{duration: 1.25, ease: [0.16, 1, 0.3, 1]}}
          style={{transformOrigin: 'left'}}
          className="h-full bg-[#FF2E4C] rounded-full"
        />
      </div>

      <button
        type="button"
        onClick={onDismiss}
        className="mt-8 text-xs font-medium text-slate-400 hover:text-white transition-colors px-3 py-1.5"
      >
        Skip intro
      </button>
    </motion.div>
  );
}
