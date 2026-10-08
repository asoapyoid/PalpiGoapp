import React, { useState, useMemo } from 'react';
import {
  Download,
  Copy,
  Footprints,
  Users,
  Compass,
  Camera,
  RotateCcw,
  Check,
  Zap,
  Sparkles,
  ExternalLink
} from 'lucide-react';
import {
  FriendRecord,
  EggIncubatorSlot,
  TrainerAccountProfile
} from '../data/mockDatabase';
import {
  LEGACY_MOVES_DATA,
  getActiveLegacyMoves,
  getLegacyEvolutionInfo,
  LegacyEvolutionMove
} from '../data/legacyMovesData';

interface CompanionUtilitiesModuleProps {
  friends: FriendRecord[];
  onUpdateFriend: (friendId: string, updates: Partial<FriendRecord>) => void;
  onResetDailyGifts: () => void;
  eggs: EggIncubatorSlot[];
  onAdvanceDistanceKm: (deltaKm: number) => void;
  activeAccount: TrainerAccountProfile;
  amoledMode: boolean;
  onTriggerSnackbar: (message: string, type?: 'success' | 'warning' | 'info') => void;
  onOpenLegacyHub?: (query?: string) => void;
}

export interface LoggedIvEntry {
  id: string;
  pokemonName: string;
  cp: number;
  atk: number;
  def: number;
  sta: number;
  ivPercent: number;
  timestamp: string;
}

export const CompanionUtilitiesModule: React.FC<CompanionUtilitiesModuleProps> = ({
  friends,
  onUpdateFriend,
  onResetDailyGifts,
  eggs,
  onAdvanceDistanceKm,
  activeAccount,
  amoledMode,
  onTriggerSnackbar,
  onOpenLegacyHub,
}) => {
  // Legacy Moves
  const activeLegacyMoves = useMemo(() => getActiveLegacyMoves(), []);
  const [quickCopiedId, setQuickCopiedId] = useState<string | null>(null);

  // Friendship Filter
  const [onlyMilestoneClose, setOnlyMilestoneClose] = useState<boolean>(false);

  // Live Raid Bosses & Exclusives from pokemon-go-api & pogoapi.net
  const [liveRaidBosses, setLiveRaidBosses] = useState<any[]>([]);
  const [researchExclusives, setResearchExclusives] = useState<any[]>([]);

  React.useEffect(() => {
    fetch('/api/live-gamedata')
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (data?.raidBosses) setLiveRaidBosses(data.raidBosses);
        if (data?.researchExclusives) setResearchExclusives(data.researchExclusives);
      })
      .catch(() => {});
  }, []);

  // Raid Coordination Parser State
  const [rawRaidText, setRawRaidText] = useState<string>(
    '🐉 Weather Boosted Mega Rayquaza on me! Coords: 35.6595, 139.7004 (Shibuya Gym) | Add Trainer Code: 4829 1049 8821 | Starting in 3 mins!'
  );
  const [copiedField, setCopiedField] = useState<string | null>(null);

  // IV Appraisal Logger State
  const [ivPokemon, setIvPokemon] = useState<string>('Shadow Metagross');
  const [ivCp, setIvCp] = useState<number>(3791);
  const [ivAtk, setIvAtk] = useState<number>(15);
  const [ivDef, setIvDef] = useState<number>(14);
  const [ivSta, setIvSta] = useState<number>(15);
  const [ivLogs, setIvLogs] = useState<LoggedIvEntry[]>([
    {
      id: 'iv-1',
      pokemonName: 'Origin Forme Palkia',
      cp: 4180,
      atk: 15,
      def: 15,
      sta: 15,
      ivPercent: 100.0,
      timestamp: '15:18',
    },
    {
      id: 'iv-2',
      pokemonName: 'Garchomp',
      cp: 3922,
      atk: 15,
      def: 14,
      sta: 15,
      ivPercent: 97.8,
      timestamp: '14:52',
    },
  ]);

  // Export Friends Table to CSV (Bonus Feature)
  const handleExportCsv = () => {
    const header =
      'trainer_name,trainer_code,friendship_level,days_to_next_tier,giftable_status,friend_group,last_gift_sent\n';
    const rows = friends
      .map(
        (f) =>
          `"${f.name}","${f.trainerCode}",${f.friendshipLevel},${f.daysToNextTier},${f.giftableStatus},${f.friendGroup},"${f.lastGiftSent || 'NEVER'}"`
      )
      .join('\n');
    const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'pokemate_friends_gift_export.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    onTriggerSnackbar('Exported Room friends table to pokemate_friends_gift_export.csv', 'success');
  };

  // Parse GPS Coordinates & Trainer Code from Discord/Telegram Raid String
  const coordMatch = rawRaidText.match(/(-?\d{1,2}\.\d{3,7})\s*,\s*(-?\d{1,3}\.\d{3,7})/);
  const parsedCoords = coordMatch ? `${coordMatch[1]}, ${coordMatch[2]}` : 'No GPS coordinates detected';

  const codeMatch = rawRaidText.match(/(\d{4}[\s-]?\d{4}[\s-]?\d{4})/);
  const parsedTrainerCode = codeMatch ? codeMatch[1].replace(/\D/g, '') : 'No 12-digit code detected';

  const handleCopy = (value: string, label: string) => {
    navigator.clipboard.writeText(value);
    setCopiedField(label);
    onTriggerSnackbar(`Copied ${label} (${value}) to clipboard`, 'success');
    setTimeout(() => setCopiedField(null), 1800);
  };

  const handleLogIvAppraisal = (e: React.FormEvent) => {
    e.preventDefault();
    const pct = Math.round(((ivAtk + ivDef + ivSta) / 45) * 1000) / 10;
    const entry: LoggedIvEntry = {
      id: `iv-${Date.now()}`,
      pokemonName: ivPokemon.trim() || 'Unknown Pokémon',
      cp: ivCp,
      atk: ivAtk,
      def: ivDef,
      sta: ivSta,
      ivPercent: pct,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };
    setIvLogs([entry, ...ivLogs]);
    onTriggerSnackbar(`Logged Appraisal: ${entry.pokemonName} (${ivAtk}/${ivDef}/${ivSta} • ${pct}%)`, 'success');
  };

  const visibleFriends = onlyMilestoneClose
    ? friends.filter((f) => f.daysToNextTier > 0 && f.daysToNextTier <= 3)
    : friends;

  const tierNames: Record<number, string> = {
    1: 'Good (1♥)',
    2: 'Great (2♥)',
    3: 'Ultra (3♥)',
    4: 'Best (4♥)',
  };

  return (
    <div className="space-y-6">
      {/* SECTION 1: FRIENDSHIP LEVEL TRACKER & CSV EXPORT (`friends` Room Table) */}
      <div
        className={`p-5 rounded-xl border ${
          amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
        }`}
      >
        <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-800">
          <div>
            <h2 className="text-base font-semibold text-white">
              01. Friendship Level Tracker &amp; Room `friends` Table
            </h2>
            <p className="text-xs text-slate-400">
              Identifies trainers within 1–3 days of Ultra (50k/100k XP) or Best Friends (100k/200k XP) for Lucky Egg coordination
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={() => setOnlyMilestoneClose(!onlyMilestoneClose)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors whitespace-nowrap ${
                onlyMilestoneClose
                  ? 'bg-amber-500/20 border-amber-400 text-amber-300'
                  : 'bg-slate-900 border-slate-800 text-slate-300 hover:text-white'
              }`}
            >
              {onlyMilestoneClose ? 'Showing ≤3 Days to Tier Up ★' : 'Filter: Close to Ultra/Best (≤3d)'}
            </button>

            <button
              onClick={onResetDailyGifts}
              className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 flex items-center gap-1.5 whitespace-nowrap"
            >
              <RotateCcw className="w-3.5 h-3.5 text-sky-400" />
              <span>Reset Daily Gift Flags</span>
            </button>

            <button
              onClick={handleExportCsv}
              className="px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold text-xs flex items-center gap-1.5 whitespace-nowrap"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Export CSV</span>
            </button>
          </div>
        </div>

        <div className="overflow-x-auto mt-3">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 font-mono text-[11px]">
                <th className="py-2.5 pr-4">Trainer Name</th>
                <th className="py-2.5 pr-4">Friendship Level</th>
                <th className="py-2.5 pr-4 text-right">Days to Next Tier</th>
                <th className="py-2.5 pr-4">XP Milestone</th>
                <th className="py-2.5 pr-4">Last Gift Sent</th>
                <th className="py-2.5">Giftable Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {visibleFriends.map((f) => {
                const isLuckyEggAlert = f.daysToNextTier > 0 && f.daysToNextTier <= 2;
                return (
                  <tr key={f.id} className="hover:bg-slate-800/30">
                    <td className="py-2.5 pr-4 font-medium text-white">
                      {f.name}
                      <span className="text-slate-500 font-mono text-[11px] ml-2">
                        {f.friendGroup}
                      </span>
                    </td>
                    <td className="py-2.5 pr-4 text-slate-300">
                      {tierNames[f.friendshipLevel]}
                    </td>
                    <td className="py-2.5 pr-4 text-right font-mono tabular-nums">
                      {f.daysToNextTier === 0 ? (
                        <span className="text-slate-500">MAX (Best)</span>
                      ) : (
                        <span className={isLuckyEggAlert ? 'text-amber-400 font-bold' : 'text-slate-200'}>
                          {f.daysToNextTier}d remaining
                        </span>
                      )}
                    </td>
                    <td className="py-2.5 pr-4 text-slate-400 font-mono tabular-nums">
                      {f.friendshipLevel === 2 && '50,000 XP (Ultra)'}
                      {f.friendshipLevel === 3 && '100,000 XP (Best)'}
                      {f.friendshipLevel === 4 && 'Lucky Trade Eligible'}
                    </td>
                    <td className="py-2.5 pr-4 font-mono text-slate-400 tabular-nums">
                      {f.lastGiftSent || 'Never'}
                    </td>
                    <td className="py-2.5">
                      <select
                        value={f.giftableStatus}
                        onChange={(e) =>
                          onUpdateFriend(f.id, {
                            giftableStatus: e.target.value as FriendRecord['giftableStatus'],
                          })
                        }
                        className="bg-slate-950 border border-slate-800 rounded px-2 py-1 text-xs font-mono text-slate-200"
                      >
                        <option value="GIFTABLE">GIFTABLE</option>
                        <option value="SENT_TODAY">SENT_TODAY</option>
                        <option value="UNOPENED_GIFT">UNOPENED_GIFT</option>
                      </select>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* SECTION 2: LEGACY & SPECIAL EVENT EVOLUTION MOVES HUB */}
      <div
        className={`p-5 rounded-xl border ${
          amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
        }`}
      >
        <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-800">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-semibold text-white flex items-center gap-2">
                <Zap className="w-4 h-4 text-amber-400 fill-current" />
                <span>02. Legacy &amp; Event Evolution Moves Hub</span>
              </h2>
              {activeLegacyMoves.length > 0 && (
                <span className="px-2 py-0.5 rounded-full bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-[11px] font-mono font-bold flex items-center gap-1 animate-pulse">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                  {activeLegacyMoves.length} Live Event Moves Active!
                </span>
              )}
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Verify exclusive Community Day &amp; event moves granted upon evolving without needing Elite TMs.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={() => onOpenLegacyHub?.()}
              className="px-3.5 py-1.5 rounded-lg bg-amber-400 hover:bg-amber-300 text-slate-950 font-bold text-xs flex items-center gap-1.5 transition-colors shadow-sm"
            >
              <Zap className="w-3.5 h-3.5 fill-current" />
              <span>Browse All Legacy Moves ({LEGACY_MOVES_DATA.length}) →</span>
            </button>
          </div>
        </div>

        {/* Active Event Highlights */}
        {activeLegacyMoves.length > 0 ? (
          <div className="mt-4 space-y-3">
            <div className="text-xs font-semibold text-amber-300 flex items-center gap-1.5">
              <Sparkles className="w-4 h-4 text-amber-400" />
              <span>Currently Active Event Evolution Moves (Evolve Now to Receive Without Elite TM):</span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {activeLegacyMoves.map((m) => {
                const isCopied = quickCopiedId === m.id;
                return (
                  <div
                    key={m.id}
                    className="p-3.5 rounded-xl bg-amber-950/20 border border-amber-500/50 shadow-md flex flex-col justify-between"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-bold text-white">{m.speciesName}</span>
                            <span className="px-1.5 py-0.5 rounded bg-slate-800 text-[10px] font-mono text-slate-300">
                              {m.elementalType}
                            </span>
                          </div>
                          <div className="text-[11px] text-slate-400">
                            Evolves from: <strong className="text-slate-200">{m.preEvolution}</strong> ({m.candyCost} Candy)
                          </div>
                        </div>

                        <span className="px-2 py-0.5 rounded bg-amber-400 text-slate-950 text-[10px] font-mono font-bold flex items-center gap-1 shrink-0">
                          <Zap className="w-3 h-3 fill-current" />
                          <span>ACTIVE NOW</span>
                        </span>
                      </div>

                      <div className="my-2 p-2 rounded-lg bg-amber-900/30 border border-amber-500/30 text-xs">
                        <div className="font-bold text-amber-300">
                          {m.exclusiveMove} ({m.moveType === 'CHARGED' ? 'Charged Move' : 'Fast Move'})
                        </div>
                        <div className="text-[11px] text-slate-300 mt-0.5">{m.pveRole}</div>
                        {m.activeUntil && (
                          <div className="text-[10px] text-emerald-300 font-mono mt-1">
                            ⏰ {m.activeEventName}: {m.activeUntil}
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center justify-between pt-2 border-t border-slate-800/80 gap-2">
                      <div className="text-[10px] font-mono text-slate-400 truncate">
                        Search: <code className="text-emerald-400 bg-slate-900 px-1 py-0.5 rounded">{m.searchString}</code>
                      </div>
                      <button
                        onClick={() => {
                          navigator.clipboard.writeText(m.searchString);
                          setQuickCopiedId(m.id);
                          onTriggerSnackbar(`Copied filter "${m.searchString}" for Pokémon GO!`, 'success');
                          setTimeout(() => setQuickCopiedId(null), 2500);
                        }}
                        className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-[10px] font-semibold flex items-center gap-1 shrink-0"
                      >
                        {isCopied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                        <span>{isCopied ? 'Copied' : 'Copy'}</span>
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ) : (
          <div className="mt-4 p-4 rounded-xl bg-slate-950/60 border border-slate-800/80 text-xs text-slate-400 flex items-center justify-between">
            <span>No event evolution moves are currently active in this hour. All 42 legacy moves require an Elite TM or future Community Day event.</span>
            <button
              onClick={() => onOpenLegacyHub?.()}
              className="px-3 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium shrink-0 ml-3"
            >
              View Full List
            </button>
          </div>
        )}
      </div>

      {/* SECTION 3: THREE-COLUMN UTILITY GRID (Egg/Buddy Tracker, Raid Parser, IV Logger) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 2A. Egg Distance & Buddy Candy Tracker */}
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="flex items-center justify-between mb-3">
            <div>
              <h3 className="text-sm font-semibold text-white">
                02. Egg &amp; Buddy Distance Tracker
              </h3>
              <p className="text-xs text-slate-400">
                Parsed via <code className="text-emerald-300">regexKm</code> in AccessibilityService
              </p>
            </div>
            <button
              onClick={() => onAdvanceDistanceKm(0.4)}
              className="px-2.5 py-1 rounded-lg bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-xs font-mono hover:bg-emerald-500/30 whitespace-nowrap"
            >
              +0.4 km Walk
            </button>
          </div>

          {/* Buddy Progress */}
          <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 mb-3">
            <div className="flex items-center justify-between text-xs mb-1.5">
              <span className="font-semibold text-sky-300">
                Buddy: {activeAccount.buddyName}
              </span>
              <span className="font-mono text-white tabular-nums">
                {activeAccount.buddyCurrentKm.toFixed(1)} / {activeAccount.buddyTargetKm.toFixed(1)} km
              </span>
            </div>
            <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
              <div
                className="h-full bg-sky-400 transition-all"
                style={{
                  width: `${Math.min(
                    100,
                    (activeAccount.buddyCurrentKm / activeAccount.buddyTargetKm) * 100
                  )}%`,
                }}
              />
            </div>
          </div>

          {/* Incubator Slots */}
          <div className="space-y-2.5">
            {eggs.map((egg) => {
              const pct = Math.min(100, Math.round((egg.currentKm / egg.targetKm) * 100));
              return (
                <div
                  key={egg.id}
                  className="p-2.5 rounded-lg bg-slate-950/70 border border-slate-800/80 text-xs"
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-medium text-slate-200">
                      {egg.eggTierKm}km Egg · {egg.incubatorType}
                    </span>
                    <span className="font-mono text-emerald-400 tabular-nums">
                      {egg.currentKm.toFixed(1)} / {egg.targetKm.toFixed(1)} km ({pct}%)
                    </span>
                  </div>
                  <div className="w-full h-1.5 rounded-full bg-slate-800 overflow-hidden mb-1.5">
                    <div
                      className="h-full bg-emerald-400 transition-all"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <div className="text-[11px] text-slate-400 truncate">
                    Pool: {egg.possibleHatchPool.join(', ')}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* 2B. Raid Coordination Parser (Discord / Telegram Format) */}
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="mb-3">
            <h3 className="text-sm font-semibold text-white">
              03. Raid Coordination Clipboard Parser
            </h3>
            <p className="text-xs text-slate-400">
              Extracts GPS gym coordinates &amp; 12-digit host friend codes from Discord/Telegram messages
            </p>
          </div>

          <textarea
            value={rawRaidText}
            onChange={(e) => setRawRaidText(e.target.value)}
            rows={4}
            className="w-full p-3 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-200 font-mono focus:outline-none focus:border-emerald-500 mb-3"
            placeholder="Paste Discord or Telegram raid callout here..."
          />

          <div className="space-y-2.5 text-xs">
            <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between gap-2">
              <div>
                <div className="text-[11px] text-slate-400">Extracted Gym Coordinates</div>
                <div className="font-mono font-semibold text-emerald-400 tabular-nums">
                  {parsedCoords}
                </div>
              </div>
              <button
                onClick={() => handleCopy(parsedCoords, 'Gym Coordinates')}
                className="px-2.5 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 flex items-center gap-1 whitespace-nowrap"
              >
                {copiedField === 'Gym Coordinates' ? (
                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                ) : (
                  <Copy className="w-3.5 h-3.5" />
                )}
                <span>Copy GPS</span>
              </button>
            </div>

            <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between gap-2">
              <div>
                <div className="text-[11px] text-slate-400">Host Trainer Code (Stripped for Paste)</div>
                <div className="font-mono font-semibold text-sky-400 tabular-nums">
                  {parsedTrainerCode}
                </div>
              </div>
              <button
                onClick={() => handleCopy(parsedTrainerCode, 'Host Trainer Code')}
                className="px-2.5 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 flex items-center gap-1 whitespace-nowrap"
              >
                {copiedField === 'Host Trainer Code' ? (
                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                ) : (
                  <Copy className="w-3.5 h-3.5" />
                )}
                <span>Copy Code</span>
              </button>
            </div>

            {liveRaidBosses.length > 0 && (
              <div className="pt-2 border-t border-slate-800">
                <div className="text-[11px] text-slate-400 mb-1.5">
                  Live Raid Bosses (`pokemon-go-api` • {liveRaidBosses.length} active):
                </div>
                <div className="max-h-[115px] overflow-y-auto space-y-1 pr-1">
                  {liveRaidBosses.slice(0, 12).map((boss) => (
                    <div
                      key={boss.id}
                      className="px-2 py-1 rounded bg-slate-950/90 border border-slate-800/80 flex items-center justify-between text-[11px]"
                    >
                      <div className="flex items-center gap-1.5 truncate">
                        {boss.imageUrl && (
                          <img
                            src={boss.imageUrl}
                            alt={boss.name}
                            referrerPolicy="no-referrer"
                            className="w-4 h-4 object-contain shrink-0"
                            onError={(e) => {
                              (e.currentTarget as HTMLImageElement).style.display = 'none';
                            }}
                          />
                        )}
                        <span className="font-medium text-white truncate">{boss.name}</span>
                        <span className="text-[10px] text-slate-500">({boss.tier})</span>
                      </div>
                      <span className="font-mono text-[10px] text-emerald-400 shrink-0 tabular-nums">
                        {boss.cpRangeNormal}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {researchExclusives.length > 0 && (
              <div className="pt-1 text-[11px] text-slate-400">
                <span>PoGoAPI.net Research Exclusives: </span>
                <span className="text-slate-200">
                  {researchExclusives.map((e: any) => e.name).join(', ')}
                </span>
              </div>
            )}
          </div>
        </div>

        {/* 2C. IV Check Helper (Appraisal Bar Screenshot Logger) */}
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="mb-3">
            <h3 className="text-sm font-semibold text-white">
              04. IV Check Appraisal Logger
            </h3>
            <p className="text-xs text-slate-400">
              Parses Attack / Defense / HP bars from <code className="text-emerald-300">takeScreenshot()</code>
            </p>
          </div>

          <form onSubmit={handleLogIvAppraisal} className="space-y-2.5 text-xs mb-4">
            <div className="grid grid-cols-2 gap-2">
              <input
                type="text"
                value={ivPokemon}
                onChange={(e) => setIvPokemon(e.target.value)}
                className="px-2.5 py-1.5 rounded bg-slate-950 border border-slate-800 text-white"
                placeholder="Pokémon Name"
              />
              <input
                type="number"
                value={ivCp}
                onChange={(e) => setIvCp(Number(e.target.value))}
                className="px-2.5 py-1.5 rounded bg-slate-950 border border-slate-800 text-white font-mono tabular-nums"
                placeholder="CP"
              />
            </div>

            {([
              { label: 'Attack', val: ivAtk, set: setIvAtk },
              { label: 'Defense', val: ivDef, set: setIvDef },
              { label: 'HP / Stamina', val: ivSta, set: setIvSta },
            ] as const).map((stat) => (
              <div key={stat.label} className="space-y-1">
                <div className="flex justify-between text-[11px]">
                  <span className="text-slate-400">{stat.label}</span>
                  <span className="font-mono text-amber-400 tabular-nums">{stat.val} / 15</span>
                </div>
                <input
                  type="range"
                  min={0}
                  max={15}
                  value={stat.val}
                  onChange={(e) => stat.set(Number(e.target.value))}
                  className="w-full accent-amber-400 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
                />
              </div>
            ))}

            {(() => {
              const legacy = getLegacyEvolutionInfo(ivPokemon);
              if (!legacy) return null;
              if (legacy.isCurrentlyActive) {
                return (
                  <div className="p-2 rounded-lg bg-amber-500/20 border border-amber-400 text-[11px] text-amber-200 flex items-center justify-between gap-1.5 animate-pulse">
                    <div className="flex items-center gap-1.5 truncate">
                      <Zap className="w-3.5 h-3.5 text-amber-400 fill-amber-400 shrink-0" />
                      <span className="truncate">
                        ⚡ <strong>LIVE EVENT:</strong> Evolve for <strong>{legacy.exclusiveMove}</strong>!
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => onOpenLegacyHub?.(legacy.speciesName)}
                      className="px-2 py-0.5 rounded bg-amber-400 text-slate-950 font-bold text-[9px] shrink-0 hover:bg-amber-300"
                    >
                      View Move
                    </button>
                  </div>
                );
              }
              return (
                <div className="p-1.5 rounded-lg bg-slate-900 border border-slate-800 text-[10px] text-slate-400 flex items-center justify-between">
                  <span className="truncate">Legacy Move: {legacy.exclusiveMove} (Elite TM)</span>
                  <button
                    type="button"
                    onClick={() => onOpenLegacyHub?.(legacy.speciesName)}
                    className="text-amber-400 hover:underline font-mono shrink-0 ml-1"
                  >
                    View
                  </button>
                </div>
              );
            })()}

            <button
              type="submit"
              className="w-full py-2 rounded-lg bg-amber-400 hover:bg-amber-300 text-slate-950 font-semibold text-xs flex items-center justify-center gap-1.5"
            >
              <Camera className="w-3.5 h-3.5" />
              <span>
                Log Appraisal ({Math.round(((ivAtk + ivDef + ivSta) / 45) * 1000) / 10}% IV)
              </span>
            </button>
          </form>

          <div className="space-y-1.5 max-h-[120px] overflow-y-auto">
            {ivLogs.map((item) => (
              <div
                key={item.id}
                className="px-2.5 py-1.5 rounded bg-slate-950 border border-slate-800/80 flex items-center justify-between text-xs"
              >
                <div className="truncate">
                  <span className="font-medium text-white">{item.pokemonName}</span>
                  <span className="text-slate-400 font-mono ml-1.5 tabular-nums">
                    {item.cp} CP
                  </span>
                </div>
                <div className="font-mono text-emerald-400 shrink-0 tabular-nums">
                  {item.atk}/{item.def}/{item.sta} ({item.ivPercent}%)
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
