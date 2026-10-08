import React, { useState, useMemo } from 'react';
import {
  Sparkles,
  Zap,
  Search,
  X,
  ExternalLink,
  Copy,
  Check,
  Shield,
  Swords,
  Clock,
  AlertCircle,
  Filter
} from 'lucide-react';
import {
  LEGACY_MOVES_DATA,
  LegacyEvolutionMove,
  getActiveLegacyMoves
} from '../data/legacyMovesData';

interface LegacyMovesHubModalProps {
  isOpen: boolean;
  onClose: () => void;
  amoledMode: boolean;
  onTriggerSnackbar: (message: string, type?: 'success' | 'warning' | 'info') => void;
  initialSearchQuery?: string;
}

export const LegacyMovesHubModal: React.FC<LegacyMovesHubModalProps> = ({
  isOpen,
  onClose,
  amoledMode,
  onTriggerSnackbar,
  initialSearchQuery = '',
}) => {
  const [searchQuery, setSearchQuery] = useState<string>(initialSearchQuery);
  const [filterMode, setFilterMode] = useState<'ALL' | 'ACTIVE_NOW' | 'PVP_S_TIER' | 'RAID_S_TIER'>('ALL');
  const [selectedType, setSelectedType] = useState<string>('ALL');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Active moves count
  const activeMoves = useMemo(() => getActiveLegacyMoves(), []);

  // Filtered list with useMemo for zero-lag and minimal memory churn
  const filteredList = useMemo(() => {
    let result = LEGACY_MOVES_DATA;

    if (filterMode === 'ACTIVE_NOW') {
      result = result.filter((m) => m.isCurrentlyActive);
    } else if (filterMode === 'PVP_S_TIER') {
      result = result.filter((m) => m.pvpViability === 'S+' || m.pvpViability === 'S');
    } else if (filterMode === 'RAID_S_TIER') {
      result = result.filter((m) => m.pveViability === 'S+' || m.pveViability === 'S');
    }

    if (selectedType !== 'ALL') {
      result = result.filter((m) => m.elementalType.toLowerCase() === selectedType.toLowerCase());
    }

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter(
        (m) =>
          m.speciesName.toLowerCase().includes(q) ||
          m.preEvolution.toLowerCase().includes(q) ||
          m.exclusiveMove.toLowerCase().includes(q) ||
          m.elementalType.toLowerCase().includes(q)
      );
    }

    return result;
  }, [filterMode, selectedType, searchQuery]);

  const handleCopySearchString = (move: LegacyEvolutionMove) => {
    navigator.clipboard.writeText(move.searchString);
    setCopiedId(move.id);
    onTriggerSnackbar(
      `Copied Pokémon GO storage search filter: "${move.searchString}"`,
      'success'
    );
    setTimeout(() => setCopiedId(null), 2500);
  };

  const handleCopyAllActiveString = () => {
    const allSearch = activeMoves.map((m) => m.searchString).join(',');
    navigator.clipboard.writeText(allSearch);
    onTriggerSnackbar(
      `Copied multi-species search filter for all ${activeMoves.length} active event Pokémon!`,
      'success'
    );
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
      <div
        className={`w-full max-w-4xl max-h-[92vh] flex flex-col rounded-2xl border shadow-2xl overflow-hidden transition-colors ${
          amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900 border-slate-700/80 text-slate-100'
        }`}
      >
        {/* Modal Header */}
        <div className="px-5 py-4 border-b border-slate-800/80 flex items-center justify-between bg-slate-950/60 shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-amber-500/20 border border-amber-500/40 flex items-center justify-center text-amber-400">
              <Zap className="w-4 h-4 fill-current" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base font-bold text-white tracking-tight">
                  Legacy & Event Evolution Moves Hub
                </h2>
                {activeMoves.length > 0 && (
                  <span className="px-2 py-0.5 rounded-full bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-[11px] font-mono font-semibold flex items-center gap-1 animate-pulse">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                    {activeMoves.length} Available NOW
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-400">
                Exclusive Community Day & Special Event moves granted upon evolution without Elite TMs.
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            title="Close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Live Active Event Highlight Banner (Only shows when moves are active!) */}
        {activeMoves.length > 0 && (
          <div className="px-5 py-2.5 bg-gradient-to-r from-amber-950/70 via-emerald-950/50 to-slate-900 border-b border-amber-500/30 flex flex-wrap items-center justify-between gap-2 shrink-0">
            <div className="flex items-center gap-2 text-xs text-amber-200">
              <Sparkles className="w-4 h-4 text-amber-400 shrink-0 animate-spin" style={{ animationDuration: '6s' }} />
              <div>
                <strong className="text-amber-300 font-bold">⚡ Active Evolution Window: </strong>
                <span>
                  {activeMoves.map((m) => `${m.speciesName} (${m.exclusiveMove})`).join(' • ')}
                </span>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={handleCopyAllActiveString}
                className="px-2.5 py-1 rounded-lg bg-amber-400 hover:bg-amber-300 text-slate-950 font-bold text-xs flex items-center gap-1.5 shadow-sm"
              >
                <Copy className="w-3.5 h-3.5" />
                <span>Copy Active Search Filter</span>
              </button>
            </div>
          </div>
        )}

        {/* Controls & Search Toolbar */}
        <div className="p-4 border-b border-slate-800/80 bg-slate-950/30 flex flex-wrap items-center justify-between gap-3 shrink-0">
          {/* Search Bar */}
          <div className="relative flex-1 min-w-[240px]">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search Pokémon (e.g. Beldum, Swampert, Charizard) or move..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-xs text-white placeholder-slate-400 focus:outline-none focus:border-amber-400"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white text-xs"
              >
                ✕
              </button>
            )}
          </div>

          {/* Quick Filter Buttons */}
          <div className="flex items-center gap-1.5 overflow-x-auto text-xs">
            <button
              onClick={() => setFilterMode('ALL')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all ${
                filterMode === 'ALL'
                  ? 'bg-white/20 text-white border border-white/30'
                  : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800 border border-slate-700/60'
              }`}
            >
              All ({LEGACY_MOVES_DATA.length})
            </button>

            <button
              onClick={() => setFilterMode('ACTIVE_NOW')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all flex items-center gap-1.5 ${
                filterMode === 'ACTIVE_NOW'
                  ? 'bg-amber-500 text-slate-950 font-bold border border-amber-400'
                  : 'bg-amber-500/10 text-amber-300 hover:bg-amber-500/20 border border-amber-500/30'
              }`}
            >
              <Zap className="w-3.5 h-3.5 fill-current" />
              <span>Active Now ({activeMoves.length})</span>
            </button>

            <button
              onClick={() => setFilterMode('PVP_S_TIER')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all flex items-center gap-1.5 ${
                filterMode === 'PVP_S_TIER'
                  ? 'bg-sky-500 text-slate-950 font-bold border border-sky-400'
                  : 'bg-sky-500/10 text-sky-300 hover:bg-sky-500/20 border border-sky-500/30'
              }`}
            >
              <Shield className="w-3.5 h-3.5" />
              <span>PvP S-Tier</span>
            </button>

            <button
              onClick={() => setFilterMode('RAID_S_TIER')}
              className={`px-3 py-1.5 rounded-lg font-medium transition-all flex items-center gap-1.5 ${
                filterMode === 'RAID_S_TIER'
                  ? 'bg-purple-500 text-slate-950 font-bold border border-purple-400'
                  : 'bg-purple-500/10 text-purple-300 hover:bg-purple-500/20 border border-purple-500/30'
              }`}
            >
              <Swords className="w-3.5 h-3.5" />
              <span>Raid Attackers</span>
            </button>
          </div>
        </div>

        {/* Scrollable Cards Grid (Low memory footprint, plain HTML DOM) */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {filteredList.length === 0 ? (
            <div className="py-12 text-center text-slate-400 space-y-2">
              <AlertCircle className="w-8 h-8 mx-auto text-slate-500" />
              <p className="text-sm font-medium">No legacy evolution moves match your filter.</p>
              <button
                onClick={() => {
                  setSearchQuery('');
                  setFilterMode('ALL');
                  setSelectedType('ALL');
                }}
                className="text-xs text-amber-400 hover:underline"
              >
                Clear all search filters
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {filteredList.map((item) => {
                const isCopied = copiedId === item.id;
                return (
                  <div
                    key={item.id}
                    className={`p-3.5 rounded-xl border transition-all relative flex flex-col justify-between ${
                      item.isCurrentlyActive
                        ? 'bg-amber-950/20 border-amber-500/60 shadow-lg shadow-amber-950/30 ring-1 ring-amber-500/40'
                        : amoledMode
                        ? 'bg-slate-950 border-slate-800 hover:border-slate-700'
                        : 'bg-slate-800/40 border-slate-700/80 hover:border-slate-600'
                    }`}
                  >
                    <div>
                      {/* Top Row: Pokémon & Active Badge */}
                      <div className="flex items-start justify-between gap-2 mb-2">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-bold text-white tracking-tight">
                              {item.speciesName}
                            </span>
                            <span className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-[10px] font-mono text-slate-300">
                              {item.elementalType}
                            </span>
                          </div>
                          <div className="text-[11px] text-slate-400 mt-0.5">
                            Evolves from: <strong className="text-slate-200">{item.preEvolution}</strong> ({item.candyCost} Candy)
                          </div>
                        </div>

                        {/* Status Badge */}
                        {item.isCurrentlyActive ? (
                          <span className="px-2 py-0.5 rounded-full bg-amber-400 text-slate-950 text-[10px] font-mono font-bold flex items-center gap-1 shadow-sm shrink-0">
                            <Zap className="w-3 h-3 fill-current" />
                            <span>EVOLVE NOW!</span>
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-400 text-[10px] font-mono shrink-0">
                            Elite TM / Event
                          </span>
                        )}
                      </div>

                      {/* Exclusive Move Card */}
                      <div className={`p-2.5 rounded-lg border my-2 ${
                        item.isCurrentlyActive
                          ? 'bg-amber-900/30 border-amber-500/40'
                          : 'bg-slate-900/80 border-slate-800'
                      }`}>
                        <div className="flex items-center justify-between text-xs">
                          <div className="font-bold text-amber-300 flex items-center gap-1.5">
                            <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                            <span>{item.exclusiveMove}</span>
                          </div>
                          <span className="text-[10px] font-mono text-slate-400">
                            {item.moveType === 'CHARGED' ? 'Charged Attack' : 'Fast Attack'}
                          </span>
                        </div>
                        <div className="text-[11px] text-slate-300 mt-1 leading-relaxed">
                          {item.pveRole}
                        </div>
                      </div>

                      {/* Active Event Notice or Normal Acquisition */}
                      {item.isCurrentlyActive && item.activeEventName && (
                        <div className="text-[11px] text-emerald-300 font-medium flex items-center gap-1.5 mb-2">
                          <Clock className="w-3.5 h-3.5 shrink-0" />
                          <span>
                            {item.activeEventName} ({item.activeUntil || 'Active now'})
                          </span>
                        </div>
                      )}

                      {/* PvP & PvE Viability Badges & Evolution CP Caps */}
                      <div className="grid grid-cols-2 gap-2 text-[11px] my-2 bg-slate-950/60 p-2 rounded-lg border border-slate-800">
                        <div>
                          <span className="text-slate-400">PvP Viability: </span>
                          <strong className="text-sky-300 font-mono">{item.pvpViability}</strong>
                          <div className="text-[10px] text-slate-400 truncate">{item.pvpRole}</div>
                        </div>
                        <div>
                          <span className="text-slate-400">Raid DPS: </span>
                          <strong className="text-purple-300 font-mono">{item.pveViability}</strong>
                          <div className="text-[10px] text-slate-400">
                            GL Cap: {item.greatLeagueCapCp ? `≤${item.greatLeagueCapCp} CP` : '—'}
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Bottom Row: Quick Copy Storage Search Filter */}
                    <div className="pt-2 border-t border-slate-800 flex items-center justify-between gap-2 mt-1">
                      <div className="text-[10px] font-mono text-slate-400 truncate">
                        Filter: <code className="text-emerald-400 bg-slate-900 px-1 py-0.5 rounded">{item.searchString}</code>
                      </div>
                      <button
                        onClick={() => handleCopySearchString(item)}
                        className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-[10px] font-semibold flex items-center gap-1 shrink-0 transition-colors"
                        title="Copy filter to search your Pokémon storage"
                      >
                        {isCopied ? (
                          <>
                            <Check className="w-3 h-3 text-emerald-400" />
                            <span className="text-emerald-400">Copied!</span>
                          </>
                        ) : (
                          <>
                            <Copy className="w-3 h-3" />
                            <span>Copy Filter</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="px-5 py-3 border-t border-slate-800 bg-slate-950/80 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-400 shrink-0">
          <div>
            Showing <strong className="text-white">{filteredList.length}</strong> of {LEGACY_MOVES_DATA.length} legacy evolution moves.
          </div>
          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-semibold transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
