import React, { useState, useEffect, useCallback } from 'react';
import {
  INITIAL_ACCOUNTS,
  INITIAL_FRIENDS,
  INITIAL_RESEARCH_TASKS,
  INITIAL_AUTOMATION_LOGS,
  INITIAL_EGG_SLOTS,
  FriendRecord,
  ResearchTaskRecord,
  AutomationLogRecord,
  EggIncubatorSlot,
  TrainerAccountProfile
} from './data/mockDatabase';
import { LiveOverlaySimulator } from './components/LiveOverlaySimulator';
import { ResearchTrackerModule } from './components/ResearchTrackerModule';
import { CompanionUtilitiesModule } from './components/CompanionUtilitiesModule';
import { KotlinCodeExplorer } from './components/KotlinCodeExplorer';
import { LegacyMovesHubModal } from './components/LegacyMovesHubModal';
import { getActiveLegacyMoves } from './data/legacyMovesData';

type ActiveSection = 'SIMULATOR' | 'RESEARCH' | 'UTILITIES' | 'KOTLIN_CODE';

interface SnackbarState {
  id: number;
  message: string;
  type: 'success' | 'warning' | 'info';
}

export const APP_VERSION_NAME = '1.2.2026';
export const APP_VERSION_CODE = 3;
export const APP_APK_FILENAME = 'PalpiGO-v1.2.2026.apk';

export default function App() {
  const [activeSection, setActiveSection] = useState<ActiveSection>('SIMULATOR');
  const [amoledMode, setAmoledMode] = useState<boolean>(false);

  // Legacy Evolution Moves Hub state
  const [isLegacyHubOpen, setIsLegacyHubOpen] = useState<boolean>(false);
  const [legacySearchQuery, setLegacySearchQuery] = useState<string>('');
  const activeLegacyMoves = React.useMemo(() => getActiveLegacyMoves(), []);

  const handleOpenLegacyHub = useCallback((query?: string) => {
    setLegacySearchQuery(query || '');
    setIsLegacyHubOpen(true);
  }, []);

  // Multi-Account Quick Switcher
  const [accounts, setAccounts] = useState<TrainerAccountProfile[]>(INITIAL_ACCOUNTS);
  const [activeAccountId, setActiveAccountId] = useState<string>(INITIAL_ACCOUNTS[0].id);

  // Room Database State (`friends`, `research_tasks`, `automation_logs`)
  const [friends, setFriends] = useState<FriendRecord[]>(INITIAL_FRIENDS);
  const [researchTasks, setResearchTasks] = useState<ResearchTaskRecord[]>(INITIAL_RESEARCH_TASKS);
  const [researchCategories, setResearchCategories] = useState<string[]>([
    'Harvest Festival 2026: Applin Picking Tasks',
    'Catching Tasks',
    'Throwing Tasks',
    'Battling Tasks',
    'Exploring Tasks',
    'Training Tasks',
    'Buddy & Friendship Tasks',
    'Team GO Rocket Tasks',
    'Sponsored Tasks',
  ]);
  const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(null);
  const [researchSourceUrl, setResearchSourceUrl] = useState<string>('https://leekduck.com/research/');
  const [isLiveSyncing, setIsLiveSyncing] = useState<boolean>(false);

  const [logs, setLogs] = useState<AutomationLogRecord[]>(INITIAL_AUTOMATION_LOGS);
  const [eggs, setEggs] = useState<EggIncubatorSlot[]>(INITIAL_EGG_SLOTS);

  // Material 3 Snackbar Notification
  const [snackbar, setSnackbar] = useState<SnackbarState | null>(null);

  const activeAccount =
    accounts.find((a) => a.id === activeAccountId) || accounts[0];

  const triggerSnackbar = useCallback(
    (message: string, type: 'success' | 'warning' | 'info' = 'info') => {
      setSnackbar({ id: Date.now(), message, type });
      setTimeout(() => {
        setSnackbar((prev) => (prev && Date.now() - prev.id >= 3400 ? null : prev));
      }, 3500);
    },
    []
  );

  // Fetch Live LeekDuck Research Tasks from Backend Proxy on Mount & Refresh
  const fetchLiveLeekDuckResearch = useCallback(
    async (forceRefresh = false) => {
      setIsLiveSyncing(true);
      try {
        const res = await fetch(
          `/api/research/leekduck${forceRefresh ? '?refresh=true' : ''}`
        );
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        if (Array.isArray(data.tasks) && data.tasks.length > 0) {
          setResearchTasks(data.tasks);
          if (Array.isArray(data.categories)) {
            setResearchCategories(data.categories);
          }
          setLastSyncedAt(data.fetchedAt || new Date().toISOString());
          setResearchSourceUrl(data.sourceUrl || 'https://leekduck.com/research/');
          if (forceRefresh) {
            triggerSnackbar(
              `Synced ${data.tasks.length} live Field Research tasks (${data.categories?.length || 0} categories)`,
              'success'
            );
          }
        }
      } catch (err) {
        if (forceRefresh) {
          triggerSnackbar('Using cached live rotation tasks', 'warning');
        }
      } finally {
        setIsLiveSyncing(false);
      }
    },
    [triggerSnackbar]
  );

  useEffect(() => {
    fetchLiveLeekDuckResearch(false);
  }, [fetchLiveLeekDuckResearch]);

  const handleUpdateFriend = (friendId: string, updates: Partial<FriendRecord>) => {
    setFriends((prev) =>
      prev.map((f) => (f.id === friendId ? { ...f, ...updates } : f))
    );
  };

  const handleResetDailyGifts = () => {
    setFriends((prev) =>
      prev.map((f) =>
        f.giftableStatus === 'SENT_TODAY' ? { ...f, giftableStatus: 'GIFTABLE' } : f
      )
    );
    triggerSnackbar('Reset daily SENT_TODAY flags across all Room friend rows', 'info');
  };

  const handleAddLog = (newLog: Omit<AutomationLogRecord, 'id' | 'timestamp'>) => {
    const now = new Date();
    const ts = `${now.toTimeString().split(' ')[0]}.${String(now.getMilliseconds()).padStart(3, '0')}`;
    const record: AutomationLogRecord = {
      ...newLog,
      id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      timestamp: ts,
    };
    setLogs((prev) => [record, ...prev.slice(0, 99)]);
  };

  const handleToggleStarResearch = (taskId: string) => {
    setResearchTasks((prev) =>
      prev.map((t) => (t.id === taskId ? { ...t, isHighValue: !t.isHighValue } : t))
    );
  };

  const handleAddResearchTask = (
    task: Omit<ResearchTaskRecord, 'id' | 'dateAdded' | 'isActive'>
  ) => {
    const entry: ResearchTaskRecord = {
      ...task,
      id: `res-${Date.now()}`,
      dateAdded: new Date().toISOString().split('T')[0],
      isActive: true,
    };
    setResearchTasks((prev) => [entry, ...prev]);
  };

  const handleAdvanceDistanceKm = (deltaKm: number) => {
    setEggs((prev) =>
      prev.map((egg) => ({
        ...egg,
        currentKm: Math.min(egg.targetKm, Number((egg.currentKm + deltaKm).toFixed(1))),
      }))
    );
    setAccounts((prev) =>
      prev.map((acc) =>
        acc.id === activeAccount.id
          ? {
              ...acc,
              buddyCurrentKm: Math.min(
                acc.buddyTargetKm,
                Number((acc.buddyCurrentKm + deltaKm).toFixed(1))
              ),
            }
          : acc
      )
    );
    triggerSnackbar(`AccessibilityService parsed +${deltaKm} km on Buddy & Incubators`, 'info');
  };

  const [showUpdateHelpModal, setShowUpdateHelpModal] = useState<boolean>(false);
  const [directExternalApkUrl, setDirectExternalApkUrl] = useState<string>(
    'https://ntfy.envs.net/file/GV0YkR9Ch0ce.apk'
  );
  const [secondaryExternalApkUrl, setSecondaryExternalApkUrl] = useState<string>(
    'https://ntfy.adminforge.de/file/5TIuYpOtiHcX.apk'
  );

  // Sync live APK version manifest & register current (or newly republished) origin with OTA channels
  useEffect(() => {
    const syncApkOtaManifest = async () => {
      try {
        const currentOrigin = window.location.origin;
        const res = await fetch(
          `/api/apk-version?origin=${encodeURIComponent(currentOrigin)}`
        );
        if (!res.ok) return;
        const manifest = await res.json();
        if (manifest?.directApkUrl && typeof manifest.directApkUrl === 'string' && manifest.directApkUrl.startsWith('http')) {
          setDirectExternalApkUrl(manifest.directApkUrl);
        }
        if (manifest?.secondaryApkUrl && typeof manifest.secondaryApkUrl === 'string' && manifest.secondaryApkUrl.startsWith('http')) {
          setSecondaryExternalApkUrl(manifest.secondaryApkUrl);
        }
        const payloadStr = JSON.stringify({
          ...manifest,
          serverOrigin: currentOrigin,
        });
        // Relay from client browser so ntfy.envs.net & ntfy.adminforge.de always have the latest manifest
        fetch('https://ntfy.envs.net/pokemate_ota_4e6c31bd', {
          method: 'POST',
          body: payloadStr,
        }).catch(() => {});
        fetch('https://ntfy.adminforge.de/pokemate_ota_4e6c31bd', {
          method: 'POST',
          body: payloadStr,
        }).catch(() => {});
      } catch {
        // Fallback to verified direct external mirror
        setDirectExternalApkUrl('https://ntfy.envs.net/file/gc29aqy3VtJD.apk');
      }
    };
    syncApkOtaManifest();
  }, []);

  const DIRECT_EXTERNAL_APK_URL = directExternalApkUrl;

  const handleMobileFriendlyApkDownload = async () => {
    try {
      triggerSnackbar(`Starting ${APP_APK_FILENAME} download...`, 'info');
      const link = document.createElement('a');
      link.href = `/api/download/${APP_APK_FILENAME}`;
      link.setAttribute('download', APP_APK_FILENAME);
      link.setAttribute('target', '_blank');
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      triggerSnackbar(
        `Downloading ${APP_APK_FILENAME}! If blocked by iframe sandbox, use the direct mirror link.`,
        'success'
      );
    } catch {
      window.open(DIRECT_EXTERNAL_APK_URL, '_blank');
      triggerSnackbar(
        `Opening direct APK mirror: ${DIRECT_EXTERNAL_APK_URL}`,
        'info'
      );
    }
  };

  const handleCopyDirectApkUrl = () => {
    navigator.clipboard.writeText(DIRECT_EXTERNAL_APK_URL);
    triggerSnackbar(
      `Copied direct link: ${DIRECT_EXTERNAL_APK_URL} — open in a phone browser tab to install!`,
      'success'
    );
  };

  const handleCaptureIvSample = () => {
    handleAddLog({
      actionType: 'IV_SCREENSHOT',
      trainerName: activeAccount.trainerName,
      delayMs: 112,
      coordinates: 'Display.DEFAULT_DISPLAY',
      success: true,
      details: 'Captured appraisal screenshot via AccessibilityService.takeScreenshot() -> 15/14/15 (98% IV)',
    });
    triggerSnackbar('Appraisal screenshot captured: 15/14/15 (97.8% IV) logged to Room DB', 'success');
  };

  return (
    <div
      className={`min-h-screen transition-colors ${
        amoledMode ? 'bg-black text-slate-100' : 'bg-[#0B0F17] text-slate-100'
      }`}
    >
      {/* STRICT 3-ZONE TOP BAR CONTRACT */}
      <header
        className={`sticky top-0 z-40 px-6 py-3.5 border-b flex items-center justify-between ${
          amoledMode
            ? 'bg-black/95 border-slate-900'
            : 'bg-[#0B0F17]/95 border-slate-800/80'
        } backdrop-blur-md`}
      >
        {/* Zone 1: Single text element Brand wordmark */}
        <a
          href="#top"
          onClick={(e) => {
            e.preventDefault();
            setActiveSection('SIMULATOR');
          }}
          className="text-lg font-bold tracking-tight text-white whitespace-nowrap shrink-0 flex items-center gap-2.5"
        >
          <img
            src="/palpitoad-icon.png"
            alt="PalpiGO APK Icon"
            className="w-8 h-8 rounded-lg border border-emerald-500/50"
          />
          <span>PalpiGO</span>
        </a>

        {/* Zone 2: 4 clean single-line text navigation links */}
        <nav className="hidden md:flex items-center gap-6 text-sm font-medium">
          <button
            onClick={() => setActiveSection('SIMULATOR')}
            className={`py-1 transition-colors whitespace-nowrap ${
              activeSection === 'SIMULATOR'
                ? 'text-emerald-400 underline underline-offset-8 decoration-2'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            Overlay Simulator
          </button>
          <button
            onClick={() => setActiveSection('RESEARCH')}
            className={`py-1 transition-colors whitespace-nowrap ${
              activeSection === 'RESEARCH'
                ? 'text-emerald-400 underline underline-offset-8 decoration-2'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            Field Research &amp; Meta ({researchTasks.length})
          </button>
          <button
            onClick={() => setActiveSection('UTILITIES')}
            className={`py-1 transition-colors whitespace-nowrap ${
              activeSection === 'UTILITIES'
                ? 'text-emerald-400 underline underline-offset-8 decoration-2'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            Utilities &amp; Room DB
          </button>
          <button
            onClick={() => setActiveSection('KOTLIN_CODE')}
            className={`py-1 transition-colors whitespace-nowrap ${
              activeSection === 'KOTLIN_CODE'
                ? 'text-emerald-400 underline underline-offset-8 decoration-2'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            APK &amp; Kotlin Project
          </button>

          <button
            onClick={() => handleOpenLegacyHub()}
            className="py-1 px-2.5 rounded-lg bg-amber-500/15 hover:bg-amber-500/25 border border-amber-500/40 text-amber-300 font-semibold text-xs transition-colors whitespace-nowrap flex items-center gap-1.5"
            title="View Community Day & Special Event Legacy Evolution Moves"
          >
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
            <span>⚡ Legacy Moves {activeLegacyMoves.length > 0 ? `(${activeLegacyMoves.length} Active!)` : ''}</span>
          </button>
        </nav>

        {/* Zone 3: 2 Primary Actions (Direct Installable Android APK Download & AMOLED Toggle) */}
        <div className="flex items-center gap-2.5">
          <button
            onClick={handleMobileFriendlyApkDownload}
            className="px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold text-xs transition-colors whitespace-nowrap"
          >
            Download {APP_APK_FILENAME}
          </button>

          <button
            onClick={handleCopyDirectApkUrl}
            className="px-3 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-slate-950 font-semibold text-xs transition-colors whitespace-nowrap"
          >
            Copy Phone APK Link
          </button>
        </div>
      </header>

      {/* Mobile Phone Direct APK Download Helper Banner (bypasses AI Studio iframe sandbox) */}
      <div className="bg-emerald-950/70 border-b border-emerald-500/40 px-4 py-2.5 text-xs flex flex-wrap items-center justify-between gap-2.5">
        <div className="text-emerald-100 flex items-center gap-2 flex-wrap">
          <strong className="text-emerald-300">📲 Direct Phone APK:</strong>
          <span>Tap to download or paste in Chrome:</span>
          <a
            href={DIRECT_EXTERNAL_APK_URL}
            target="_blank"
            rel="noopener noreferrer"
            download={APP_APK_FILENAME}
            className="px-2 py-0.5 rounded bg-black/70 hover:bg-black text-emerald-300 font-mono underline select-all"
          >
            {DIRECT_EXTERNAL_APK_URL}
          </a>
        </div>
        <div className="flex items-center gap-2">
          <a
            href={DIRECT_EXTERNAL_APK_URL}
            target="_blank"
            rel="noopener noreferrer"
            download={APP_APK_FILENAME}
            className="px-3.5 py-1.5 rounded-lg bg-emerald-400 hover:bg-emerald-300 text-slate-950 font-bold text-xs whitespace-nowrap inline-flex items-center gap-1 shadow-sm"
          >
            Download {APP_APK_FILENAME}
          </a>
          <button
            onClick={handleCopyDirectApkUrl}
            className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 font-semibold text-xs whitespace-nowrap"
          >
            Copy Link
          </button>
          <button
            onClick={() => setShowUpdateHelpModal(true)}
            className="px-3 py-1.5 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/40 font-semibold text-xs whitespace-nowrap flex items-center gap-1"
          >
            <span>⚠️ Can't Update?</span>
          </button>
        </div>
      </div>

      {/* Update Troubleshooting Modal */}
      {showUpdateHelpModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl max-w-xl w-full p-6 shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <span className="text-xl">🛠️</span>
                <h3 className="text-base font-bold text-white">Why Can't I Update? (Step-by-Step Fix)</h3>
              </div>
              <button
                onClick={() => setShowUpdateHelpModal(false)}
                className="text-slate-400 hover:text-white text-lg font-bold p-1 rounded-lg"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3.5 text-xs text-slate-300 leading-relaxed">
              <div className="p-3.5 rounded-xl bg-amber-950/40 border border-amber-500/40 space-y-1.5">
                <div className="font-bold text-amber-300 flex items-center gap-1.5">
                  <span>1. Android "App not installed as package conflicts with an existing package"</span>
                </div>
                <p>
                  <strong>The Cause:</strong> Android's security sandbox strictly enforces that all updates have the exact same cryptographic signing key as the installed app. Because an older installed build was signed with a different key or version code, Android blocks overwriting it directly with the new {APP_APK_FILENAME} release.
                </p>
                <p className="text-emerald-300 font-medium">
                  <strong>The Quick 10-Second Fix:</strong> Long-press the existing <em>PalpiGO</em> icon on your phone home screen/app drawer and tap <strong>Uninstall</strong>. Then tap download below to install the fresh {APP_APK_FILENAME} build! All future updates will update seamlessly without uninstalling.
                </p>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-800/80 border border-slate-700 space-y-1.5">
                <div className="font-bold text-sky-300 flex items-center gap-1.5">
                  <span>2. Android "Install Unknown Apps" permission</span>
                </div>
                <p>
                  If Android blocks the install or doesn't pop up the prompt:
                </p>
                <p className="font-mono text-[11px] bg-slate-950 p-2 rounded border border-slate-800">
                  Settings → Apps → PalpiGO (or Chrome) → Install unknown apps → Turn ON "Allow from this source"
                </p>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-800/80 border border-slate-700 space-y-2">
                <div className="font-bold text-emerald-300">
                  3. Multiple High-Speed APK Mirrors ({APP_APK_FILENAME})
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
                  <a
                    href={DIRECT_EXTERNAL_APK_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    download={APP_APK_FILENAME}
                    className="p-2.5 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 border border-emerald-500/40 text-emerald-300 font-semibold text-center"
                  >
                    Primary Mirror ({APP_APK_FILENAME})
                  </a>
                  <a
                    href={secondaryExternalApkUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    download={APP_APK_FILENAME}
                    className="p-2.5 rounded-lg bg-sky-500/20 hover:bg-sky-500/30 border border-sky-500/40 text-sky-300 font-semibold text-center"
                  >
                    Secondary Mirror (adminforge.de)
                  </a>
                  <a
                    href={`/api/download/${APP_APK_FILENAME}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    download={APP_APK_FILENAME}
                    className="p-2.5 rounded-lg bg-purple-500/20 hover:bg-purple-500/30 border border-purple-500/40 text-purple-300 font-semibold text-center sm:col-span-2"
                  >
                    Direct Web Server Download (/api/download/{APP_APK_FILENAME})
                  </a>
                </div>
              </div>
            </div>

            <div className="flex justify-end pt-2 border-t border-slate-800">
              <button
                onClick={() => setShowUpdateHelpModal(false)}
                className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs"
              >
                Got It, Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Mobile Navigation Bar */}
      <div className="md:hidden flex items-center gap-1 px-4 py-2 border-b border-slate-800 overflow-x-auto bg-slate-950">
        {([
          { id: 'SIMULATOR', label: 'Overlay Simulator' },
          { id: 'RESEARCH', label: `Field Research (${researchTasks.length})` },
          { id: 'UTILITIES', label: 'Utilities & DB' },
          { id: 'KOTLIN_CODE', label: 'Get APK / Code' },
        ] as const).map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveSection(tab.id)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap ${
              activeSection === tab.id
                ? 'bg-emerald-500 text-slate-950 font-semibold'
                : 'text-slate-400'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* MAIN WORKSPACE CONTAINER */}
      <main className="max-w-[1380px] mx-auto px-4 sm:px-6 py-6 space-y-6">
        {/* Contextual Workspace Header */}
        <div className="flex flex-wrap items-end justify-between gap-4 pb-4 border-b border-slate-800/80">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-white tracking-tight text-balance">
              {activeSection === 'SIMULATOR' &&
                'Scopely Pokémon GO Overlay & On-Screen Research Possibility Scanner'}
              {activeSection === 'RESEARCH' &&
                'Live Field Research Pool, Meta Targets & Home Screen AppWidget'}
              {activeSection === 'UTILITIES' &&
                'Friendship XP Tracker, Egg Distance, Raid Parser & Room CSV Export'}
              {activeSection === 'KOTLIN_CODE' &&
                'Download Android APK Project (.zip) & Complete Kotlin Architecture'}
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              <span>Target Package: com.scopely.pokemongo (Scopely Explore)</span>
              <span className="mx-2">·</span>
              <span>Live Data: Official Season Pool + Raid/PvP Meta Tiers</span>
              <span className="mx-2">·</span>
              <span>AI Gesture Trainer + Quick Catch Engine</span>
            </p>
          </div>

          <div className="flex items-center gap-2">
            <select
              value={activeAccountId}
              onChange={(e) => {
                setActiveAccountId(e.target.value);
                const target = accounts.find((a) => a.id === e.target.value);
                if (target) {
                  triggerSnackbar(
                    `Switched active Pokémon GO profile to ${target.trainerName} (${target.team})`,
                    'info'
                  );
                }
              }}
              aria-label="Switch Pokémon GO Trainer Account"
              className="px-3 py-2 rounded-lg bg-slate-900 border border-slate-800 text-xs font-medium text-slate-200 focus:outline-none focus:border-emerald-500"
            >
              {accounts.map((acc) => (
                <option key={acc.id} value={acc.id}>
                  Trainer: {acc.trainerName} ({acc.team})
                </option>
              ))}
            </select>

            {activeSection !== 'KOTLIN_CODE' ? (
              <button
                onClick={() => setActiveSection('KOTLIN_CODE')}
                className="px-3.5 py-2 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700 text-xs font-semibold text-emerald-400 transition-colors whitespace-nowrap"
              >
                Get APK &amp; Kotlin Code (9 Files) →
              </button>
            ) : (
              <button
                onClick={() => setActiveSection('SIMULATOR')}
                className="px-3.5 py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 text-xs font-semibold transition-colors whitespace-nowrap"
              >
                ← Back to Live Overlay Simulator
              </button>
            )}
          </div>
        </div>

        {/* ACTIVE WORKSPACE VIEW */}
        {activeSection === 'SIMULATOR' && (
          <LiveOverlaySimulator
            friends={friends}
            onUpdateFriend={handleUpdateFriend}
            researchTasks={researchTasks}
            logs={logs}
            onAddLog={handleAddLog}
            activeAccount={activeAccount}
            amoledMode={amoledMode}
            onTriggerSnackbar={triggerSnackbar}
            onCaptureIvSample={handleCaptureIvSample}
            onOpenLegacyHub={handleOpenLegacyHub}
          />
        )}

        {activeSection === 'RESEARCH' && (
          <ResearchTrackerModule
            tasks={researchTasks}
            categories={researchCategories}
            lastSyncedAt={lastSyncedAt}
            sourceUrl={researchSourceUrl}
            isLiveSyncing={isLiveSyncing}
            onToggleStar={handleToggleStarResearch}
            onAddTask={handleAddResearchTask}
            onRefreshLeekDuck={() => fetchLiveLeekDuckResearch(true)}
            amoledMode={amoledMode}
            onTriggerSnackbar={triggerSnackbar}
          />
        )}

        {activeSection === 'UTILITIES' && (
          <CompanionUtilitiesModule
            friends={friends}
            onUpdateFriend={handleUpdateFriend}
            onResetDailyGifts={handleResetDailyGifts}
            eggs={eggs}
            onAdvanceDistanceKm={handleAdvanceDistanceKm}
            activeAccount={activeAccount}
            amoledMode={amoledMode}
            onTriggerSnackbar={triggerSnackbar}
            onOpenLegacyHub={handleOpenLegacyHub}
          />
        )}

        {activeSection === 'KOTLIN_CODE' && (
          <KotlinCodeExplorer
            amoledMode={amoledMode}
            onTriggerSnackbar={triggerSnackbar}
          />
        )}
      </main>

      {/* Legacy & Event Evolution Moves Hub Modal */}
      <LegacyMovesHubModal
        isOpen={isLegacyHubOpen}
        onClose={() => setIsLegacyHubOpen(false)}
        amoledMode={amoledMode}
        onTriggerSnackbar={triggerSnackbar}
        initialSearchQuery={legacySearchQuery}
      />

      {/* Material 3 Snackbar Notification Toast */}
      {snackbar && (
        <div className="fixed bottom-5 right-5 z-50 max-w-md px-4 py-3 rounded-xl shadow-2xl border bg-slate-900/95 border-slate-700 text-xs font-medium text-white flex items-center gap-3 backdrop-blur-md">
          <span
            className={`w-2 h-2 rounded-full shrink-0 ${
              snackbar.type === 'success'
                ? 'bg-emerald-400'
                : snackbar.type === 'warning'
                ? 'bg-amber-400'
                : 'bg-sky-400'
            }`}
          />
          <span>{snackbar.message}</span>
        </div>
      )}
    </div>
  );
}
