import React from 'react';
import { WifiOff } from 'lucide-react';
import { useOnlineStatus } from '../hooks/usePWAInstall';

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();

  if (isOnline) return null;

  return (
    <div className="fixed bottom-4 left-4 z-50 flex items-center gap-2 rounded-lg bg-amber-500/90 text-slate-950 font-medium px-3 py-1.5 text-xs shadow-lg backdrop-blur-xs animate-in slide-in-from-bottom-2 duration-200">
      <WifiOff className="w-3.5 h-3.5" />
      <span>Offline Mode — Service Worker caching active</span>
    </div>
  );
};
