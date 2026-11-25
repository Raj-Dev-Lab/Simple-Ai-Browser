import {useState} from 'react';

interface SabLogoProps {
  size?: number;
  className?: string;
}

const GENERATED_LOGO_PATH = '/src/assets/images/sab_app_logo_1795596083056.jpg';

export function SabLogo({size = 48, className = ''}: SabLogoProps) {
  const [failed, setFailed] = useState(false);

  if (failed) {
    return (
      <div
        style={{width: size, height: size}}
        className={`rounded-2xl bg-[#0B0D12] border border-white/10 flex items-center justify-center shrink-0 ${className}`}
        aria-label="Simple AI Browser logo"
      >
        <svg viewBox="0 0 64 64" className="w-3/4 h-3/4" fill="none">
          <circle cx="32" cy="32" r="24" stroke="#FF2E4C" strokeWidth="4" />
          <path
            d="M42 22C38 17 26 17 23 24C20 31 44 33 41 41C38 48 25 48 21 42"
            stroke="#F8FAFC"
            strokeWidth="4"
            strokeLinecap="round"
          />
          <circle cx="32" cy="32" r="4" fill="#FF2E4C" />
        </svg>
      </div>
    );
  }

  return (
    <img
      src={GENERATED_LOGO_PATH}
      alt="Simple AI Browser (SAB) emblem"
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
      style={{width: size, height: size}}
      className={`rounded-2xl object-cover shrink-0 border border-white/10 ${className}`}
    />
  );
}
