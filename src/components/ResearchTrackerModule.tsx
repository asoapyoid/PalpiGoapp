import React, { useState } from 'react';
import {
  Star,
  RefreshCw,
  Plus,
  Bell,
  Search,
  Trash2,
  ExternalLink
} from 'lucide-react';
import { ResearchTaskRecord } from '../data/mockDatabase';

interface ResearchTrackerModuleProps {
  tasks: ResearchTaskRecord[];
  categories: string[];
  lastSyncedAt: string | null;
  sourceUrl: string;
  isLiveSyncing: boolean;
  onToggleStar: (id: string) => void;
  onAddTask: (task: Omit<ResearchTaskRecord, 'id' | 'dateAdded' | 'isActive'>) => void;
  onRefreshLeekDuck: () => void;
  amoledMode: boolean;
  onTriggerSnackbar: (message: string, type?: 'success' | 'warning' | 'info') => void;
}

export const ResearchTrackerModule: React.FC<ResearchTrackerModuleProps> = ({
  tasks,
  categories,
  lastSyncedAt,
  sourceUrl,
  isLiveSyncing,
  onToggleStar,
  onAddTask,
  onRefreshLeekDuck,
  amoledMode,
  onTriggerSnackbar,
}) => {
  const [filter, setFilter] = useState<'ALL' | 'SHINY' | 'HIGH_VALUE' | 'QUICK'>('ALL');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Manual Task Entry State
  const [newTaskName, setNewTaskName] = useState<string>('');
  const [newReward, setNewReward] = useState<string>('');
  const [newCpRange, setNewCpRange] = useState<string>('450 - 492 CP (100% IV)');
  const [newShiny, setNewShiny] = useState<boolean>(true);
  const [newHighValue, setNewHighValue] = useState<boolean>(true);
  const [newQuick, setNewQuick] = useState<boolean>(false);

  // Keyword Notification Rules (Bonus Feature)
  const [alertKeywords, setAlertKeywords] = useState<string[]>([
    'Rare Candy',
    'Silver Pinap',
    'Sweet Apple',
    'Applin',
    'Gible',
    'Beldum',
    'Snorlax',
  ]);
  const [keywordInput, setKeywordInput] = useState<string>('');

  const handleManualSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskName.trim() || !newReward.trim()) return;
    onAddTask({
      category: 'Manual Entry',
      taskName: newTaskName.trim(),
      reward: newReward.trim(),
      rewardType:
        newReward.toLowerCase().includes('candy') || newReward.toLowerCase().includes('pinap')
          ? 'ITEM'
          : 'ENCOUNTER',
      cpRange: newCpRange.trim() || 'Encounter / Item Reward',
      shinyPossible: newShiny,
      isHighValue: newHighValue,
      quickToComplete: newQuick,
      source: 'Manual Entry',
    });
    setNewTaskName('');
    setNewReward('');
    onTriggerSnackbar(`Added custom research task "${newTaskName.trim()}" to Room DB`, 'success');
  };

  const handleAddKeyword = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = keywordInput.trim();
    if (!trimmed || alertKeywords.includes(trimmed)) return;
    setAlertKeywords([...alertKeywords, trimmed]);
    setKeywordInput('');
    onTriggerSnackbar(`Keyword notification rule registered for "${trimmed}"`, 'info');
  };

  const filteredTasks = tasks.filter((t) => {
    if (filter === 'SHINY' && !t.shinyPossible) return false;
    if (filter === 'HIGH_VALUE' && !t.isHighValue) return false;
    if (filter === 'QUICK' && !t.quickToComplete) return false;
    if (selectedCategory !== 'ALL' && t.category !== selectedCategory) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const inTask = t.taskName.toLowerCase().includes(q);
      const inReward = t.reward.toLowerCase().includes(q);
      const inSubRewards = t.rewardsList?.some((r) => r.name.toLowerCase().includes(q)) ?? false;
      return inTask || inReward || inSubRewards;
    }
    return true;
  });

  const starredTasks = tasks.filter((t) => t.isHighValue);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
      {/* LEFT 8 COLS: Live LeekDuck Research Table, Category Filter & Scraper Controls */}
      <div className="lg:col-span-8 space-y-6">
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-800">
            <div>
              <h2 className="text-base font-semibold text-white">
                01. Live Season &amp; Event Field Research Pool
              </h2>
              <p className="text-xs text-slate-400 mt-0.5">
                <span>Aggregated from Official Season, Event &amp; Raid/PvP Meta data</span>
                <span className="mx-1.5">·</span>
                <span className="font-mono text-emerald-400 tabular-nums">
                  {tasks.length} Active Tasks across {categories.length || 1} Categories
                </span>
                {lastSyncedAt && (
                  <>
                    <span className="mx-1.5">·</span>
                    <span className="font-mono text-slate-500 tabular-nums">
                      Synced {new Date(lastSyncedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                    </span>
                  </>
                )}
              </p>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={onRefreshLeekDuck}
                disabled={isLiveSyncing}
                className="px-3.5 py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold text-xs flex items-center gap-1.5 transition-colors whitespace-nowrap"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isLiveSyncing ? 'animate-spin' : ''}`} />
                <span>{isLiveSyncing ? 'Syncing Live Pool...' : 'Sync Live Research Pool'}</span>
              </button>
            </div>
          </div>

          {/* Filter & Category Selector Bar */}
          <div className="py-4 flex flex-wrap items-center justify-between gap-3">
            {/* Interactive Segmented Control */}
            <div className="flex items-center gap-1 p-1 bg-slate-950 rounded-lg border border-slate-800">
              {([
                { id: 'ALL', label: `All (${tasks.length})` },
                { id: 'SHINY', label: 'Shiny Possible' },
                { id: 'HIGH_VALUE', label: 'High Value ★' },
                { id: 'QUICK', label: 'Quick Tasks' },
              ] as const).map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setFilter(tab.id)}
                  className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors whitespace-nowrap ${
                    filter === tab.id
                      ? 'bg-emerald-500 text-slate-950 font-semibold'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-2 flex-1 sm:flex-initial">
              <div className="relative flex-1 sm:w-52">
                <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search Pokémon or task..."
                  className="w-full pl-8 pr-3 py-1.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white placeholder:text-slate-500 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <select
                value={selectedCategory}
                onChange={(e) => setSelectedCategory(e.target.value)}
                className="px-2.5 py-1.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-200 max-w-[200px] truncate"
              >
                <option value="ALL">All Categories ({categories.length})</option>
                {[...categories]
                  .sort((a, b) => {
                    const aBonus = a.toLowerCase().includes('bonus');
                    const bBonus = b.toLowerCase().includes('bonus');
                    if (aBonus && !bBonus) return 1;
                    if (!aBonus && bBonus) return -1;
                    return 0;
                  })
                  .map((cat) => (
                    <option key={cat} value={cat}>
                      {cat}
                    </option>
                  ))}
              </select>
            </div>
          </div>

          {/* Research Tasks Data Grid */}
          <div className="overflow-x-auto max-h-[620px] overflow-y-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead className="sticky top-0 bg-slate-950 z-10">
                <tr className="border-b border-slate-800 text-slate-400 font-mono text-[11px]">
                  <th className="py-2.5 pr-3 w-8">Star</th>
                  <th className="py-2.5 pr-4">Field Research Task &amp; Category</th>
                  <th className="py-2.5 pr-4">Possible Rewards (Live Pool)</th>
                  <th className="py-2.5">CP / Attributes</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {filteredTasks.map((task) => {
                  const matchesAlert = alertKeywords.some(
                    (kw) =>
                      task.taskName.toLowerCase().includes(kw.toLowerCase()) ||
                      task.reward.toLowerCase().includes(kw.toLowerCase())
                  );
                  return (
                    <tr key={task.id} className="hover:bg-slate-800/30 transition-colors align-top">
                      <td className="py-3 pr-3">
                        <button
                          onClick={() => onToggleStar(task.id)}
                          className="p-1 rounded hover:bg-slate-800"
                          title="Star as High-Value Task"
                        >
                          <Star
                            className={`w-4 h-4 ${
                              task.isHighValue
                                ? 'text-amber-400 fill-amber-400'
                                : 'text-slate-600 hover:text-slate-400'
                            }`}
                          />
                        </button>
                      </td>
                      <td className="py-3 pr-4">
                        <div className="font-semibold text-slate-100">{task.taskName}</div>
                        <div className="text-[11px] text-slate-400 mt-0.5">
                          <span>{task.category || 'Field Research'}</span>
                          <span className="mx-1.5">·</span>
                          <span>{task.source}</span>
                        </div>
                      </td>
                      <td className="py-3 pr-4">
                        {task.rewardsList && task.rewardsList.length > 0 ? (
                          <div className="flex flex-wrap gap-2">
                            {task.rewardsList.map((r, idx) => (
                              <div
                                key={`${task.id}-r-${idx}`}
                                className="flex items-center gap-2 pr-2 py-0.5"
                              >
                                {r.imageUrl && (
                                  <div className="w-7 h-7 rounded-full bg-slate-800/80 flex items-center justify-center overflow-hidden shrink-0">
                                    <img
                                      src={r.imageUrl}
                                      alt={r.name}
                                      referrerPolicy="no-referrer"
                                      className="w-6 h-6 object-contain"
                                      onError={(e) => {
                                        (e.currentTarget as HTMLImageElement).style.display = 'none';
                                      }}
                                    />
                                  </div>
                                )}
                                <div>
                                  <div className="font-medium text-emerald-400 flex items-center gap-1">
                                    <span>{r.name}</span>
                                    {r.canBeShiny && (
                                      <span className="text-[10px] text-amber-300" title="Shiny Possible">
                                        ✨
                                      </span>
                                    )}
                                  </div>
                                  {r.minCp && r.maxCp && (
                                    <div className="text-[10px] font-mono text-slate-400 tabular-nums">
                                      CP {r.minCp}–{r.maxCp}
                                    </div>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <span className="font-semibold text-emerald-400">{task.reward}</span>
                        )}
                      </td>
                      <td className="py-3 text-slate-400 whitespace-nowrap">
                        <div className="font-mono text-slate-200 tabular-nums">{task.cpRange}</div>
                        <div className="text-[11px] mt-0.5">
                          <span>{task.shinyPossible ? '✨ Shiny Eligible' : 'Standard'}</span>
                          {matchesAlert && (
                            <>
                              <span className="mx-1.5">·</span>
                              <span className="text-sky-400 font-medium">Keyword Alert</span>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Manual Research Task Input Form */}
        <form
          onSubmit={handleManualSubmit}
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <h3 className="text-sm font-semibold text-white mb-3">
            02. Add Local PokéStop Field Research Observation
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <input
              type="text"
              value={newTaskName}
              onChange={(e) => setNewTaskName(e.target.value)}
              placeholder="Task (e.g. Catch 10 Dragon-type)"
              className="px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white placeholder:text-slate-500"
              required
            />
            <input
              type="text"
              value={newReward}
              onChange={(e) => setNewReward(e.target.value)}
              placeholder="Reward (e.g. Jangmo-o or 3x Rare Candy)"
              className="px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white placeholder:text-slate-500"
              required
            />
            <input
              type="text"
              value={newCpRange}
              onChange={(e) => setNewCpRange(e.target.value)}
              placeholder="100% IV CP (e.g. 402 - 438 CP)"
              className="px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white font-mono placeholder:text-slate-500"
            />
          </div>
          <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-4 text-xs text-slate-300">
              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={newShiny}
                  onChange={(e) => setNewShiny(e.target.checked)}
                  className="accent-emerald-500"
                />
                <span>Shiny Possible</span>
              </label>
              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={newHighValue}
                  onChange={(e) => setNewHighValue(e.target.checked)}
                  className="accent-emerald-500"
                />
                <span>Star as High-Value</span>
              </label>
              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={newQuick}
                  onChange={(e) => setNewQuick(e.target.checked)}
                  className="accent-emerald-500"
                />
                <span>Quick to Complete</span>
              </label>
            </div>
            <button
              type="submit"
              className="px-4 py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold text-xs flex items-center gap-1.5 whitespace-nowrap"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Insert into Room DB</span>
            </button>
          </div>
        </form>
      </div>

      {/* RIGHT 4 COLS: Home Screen AppWidget Preview & Keyword Notification Triggers */}
      <div className="lg:col-span-4 space-y-6">
        {/* Android Home Screen Widget Preview */}
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="flex items-center justify-between mb-3">
            <div>
              <h3 className="text-sm font-semibold text-white">
                Android Home Screen Widget
              </h3>
              <p className="text-xs text-slate-400">
                Live preview of <code className="text-emerald-300">ResearchWidgetProvider.kt</code>
              </p>
            </div>
            <span className="text-xs font-mono text-emerald-400">4×2 AppWidget</span>
          </div>

          <div className="p-4 rounded-2xl bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 border border-slate-700/80 shadow-xl space-y-2.5">
            <div className="flex items-center justify-between border-b border-slate-800 pb-2">
              <div className="text-xs font-bold text-white flex items-center gap-1.5">
                <Star className="w-3.5 h-3.5 text-amber-400 fill-amber-400" />
                <span>PokéMate Priority Research</span>
              </div>
              <span className="text-[10px] font-mono text-slate-400 tabular-nums">
                {starredTasks.length} Starred
              </span>
            </div>

            <div className="space-y-2">
              {starredTasks.slice(0, 5).map((t) => (
                <div
                  key={t.id}
                  className="flex items-center justify-between text-xs py-1 border-b border-slate-900 last:border-none"
                >
                  <div className="truncate pr-2">
                    <div className="text-slate-200 font-medium truncate">{t.taskName}</div>
                    <div className="text-[11px] text-emerald-400 truncate">→ {t.reward}</div>
                  </div>
                  <span className="text-[10px] font-mono text-slate-400 shrink-0 tabular-nums">
                    {t.cpRange.split(' ')[0]}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Keyword Notification Rules */}
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="flex items-center gap-2 mb-2">
            <Bell className="w-4 h-4 text-sky-400" />
            <h3 className="text-sm font-semibold text-white">
              Research Keyword Alert Rules
            </h3>
          </div>
          <p className="text-xs text-slate-400 mb-3">
            Fires a high-priority Android Notification via <code className="text-sky-300">NotificationCompat</code> when a Field Research task matches your keywords.
          </p>

          <form onSubmit={handleAddKeyword} className="flex gap-2 mb-3">
            <input
              type="text"
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              placeholder="Add keyword (e.g. Sweet Apple)"
              className="flex-1 px-3 py-1.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white placeholder:text-slate-500"
            />
            <button
              type="submit"
              className="px-3 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-slate-950 font-semibold text-xs whitespace-nowrap"
            >
              Add Alert
            </button>
          </form>

          <div className="divide-y divide-slate-800/70 text-xs">
            {alertKeywords.map((kw) => (
              <div key={kw} className="py-2 flex items-center justify-between">
                <span className="text-slate-200 font-medium">{kw}</span>
                <button
                  onClick={() =>
                    setAlertKeywords(alertKeywords.filter((item) => item !== kw))
                  }
                  className="text-slate-500 hover:text-rose-400 p-1"
                  title="Remove keyword"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
