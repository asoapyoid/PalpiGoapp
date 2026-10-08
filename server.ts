import express from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createServer as createViteServer } from 'vite';
import { KOTLIN_SOURCE_FILES } from './src/data/kotlinFiles.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export interface LeekDuckRewardItem {
  name: string;
  rewardType: 'ENCOUNTER' | 'ITEM' | 'MEGA_ENERGY';
  imageUrl: string;
  canBeShiny: boolean;
  minCp?: number;
  maxCp?: number;
  quantity?: string;
}

export interface LeekDuckTaskItem {
  id: string;
  category: string;
  taskName: string;
  reward: string;
  rewardType: 'ENCOUNTER' | 'ITEM' | 'MEGA_ENERGY';
  cpRange: string;
  shinyPossible: boolean;
  isHighValue: boolean;
  quickToComplete: boolean;
  source: 'Leek Duck' | 'PoGoAPI.net' | 'pokemon-go-api' | 'Manual Entry';
  dateAdded: string;
  isActive: boolean;
  rewardsList: LeekDuckRewardItem[];
}

export interface LiveRaidBoss {
  id: string;
  name: string;
  tier: string;
  imageUrl: string;
  shinyImageUrl?: string;
  canBeShiny: boolean;
  cpRangeNormal: string;
  cpRangeBoosted: string;
  types: string[];
  weather: string[];
}

interface CachedLeekDuckData {
  fetchedAt: string;
  sourceUrl: string;
  categories: string[];
  tasks: LeekDuckTaskItem[];
}

let cachedResearch: CachedLeekDuckData | null = null;
let lastFetchTimestamp = 0;
const CACHE_TTL_MS = 5 * 60 * 1000;

function decodeHtmlEntities(str: string): string {
  return str
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&times;/g, '×')
    .replace(/&nbsp;/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function classifyHighValue(taskName: string, rewards: LeekDuckRewardItem[], category: string): boolean {
  const combinedRewards = rewards.map((r) => r.name.toLowerCase()).join(' ');
  const highValueKeywords = [
    'rare candy',
    'silver pinap',
    'golden razz',
    'sweet apple',
    'syrupy apple',
    'tart apple',
    'applin',
    'mega',
    'gible',
    'gabite',
    'beldum',
    'metang',
    'larvitar',
    'dratini',
    'bagon',
    'axew',
    'snorlax',
    'lapras',
    'chansey',
    'aerodactyl',
    'mawile',
    'hisuian',
    'galarian',
    'spinda',
    'audino',
    'sableye',
    'feebas',
    'machop',
    'machoke',
    'gastly',
    'haunter',
    'mankey',
    'drilbur',
    'litwick',
    'timburr',
    'cranidos',
    'swinub',
    'rhyhorn',
    'ralts',
    'sneasel',
    'darumaka',
    'rowlet',
    'litten',
    'popplio',
    'grookey',
    'scorbunny',
    'sobble',
    'rookidee',
    'cetoddle',
    'frigibax',
    'togetic',
    'electabuzz',
    'magmar',
    'roselia',
    'bunnelby',
    'marill',
    'vullaby',
    'scraggy',
  ];
  if (category.toLowerCase().includes('event') || category.toLowerCase().includes('festival')) {
    return true;
  }
  return highValueKeywords.some((kw) => combinedRewards.includes(kw) || taskName.toLowerCase().includes(kw));
}

function classifyQuickTask(taskName: string): boolean {
  const lower = taskName.toLowerCase();
  return (
    lower.includes('catch 3') ||
    lower.includes('catch 5') ||
    lower.includes('spin 3') ||
    lower.includes('spin 5') ||
    lower.includes('send 3') ||
    lower.includes('use 3') ||
    lower.includes('use 5') ||
    lower.includes('power up pokémon 3') ||
    lower.includes('power up pokémon 5') ||
    lower.includes('make 3 great') ||
    lower.includes('make 5 nice') ||
    lower.includes('take a snapshot') ||
    lower.includes('battle in a raid') ||
    lower.includes('evolve a pokémon')
  );
}

/**
 * Parses the live HTML from https://leekduck.com/research/
 */
function parseLeekDuckResearchHtml(html: string): CachedLeekDuckData {
  const tasks: LeekDuckTaskItem[] = [];
  const categories: string[] = [];
  const todayStr = new Date().toISOString().split('T')[0];

  const categoryBlocks = html.split(/<div class="task-category[^>]*>/);

  for (let cIdx = 1; cIdx < categoryBlocks.length; cIdx++) {
    const block = categoryBlocks[cIdx];
    const h2Match = block.match(/<h2[^>]*>([\s\S]*?)<\/h2>/);
    const categoryName = h2Match ? decodeHtmlEntities(h2Match[1]) : `Research Group ${cIdx}`;
    if (!categories.includes(categoryName)) {
      categories.push(categoryName);
    }

    const taskChunks = block.split(/<li class="task-item">/);
    for (let tIdx = 1; tIdx < taskChunks.length; tIdx++) {
      const taskHtml = taskChunks[tIdx];
      const taskTextMatch = taskHtml.match(/<span class="task-text">([\s\S]*?)<\/span>\s*<ul class="reward-list">/);
      if (!taskTextMatch) continue;

      const taskName = decodeHtmlEntities(taskTextMatch[1]);
      const rewardChunks = taskHtml.split(/<li class="reward"/);
      const rewardsList: LeekDuckRewardItem[] = [];

      for (let rIdx = 1; rIdx < rewardChunks.length; rIdx++) {
        const rHtml = rewardChunks[rIdx];
        const typeMatch = rHtml.match(/data-reward-type="([^"]+)"/);
        const rawType = typeMatch ? typeMatch[1].toLowerCase() : 'encounter';

        const rewardType: 'ENCOUNTER' | 'ITEM' | 'MEGA_ENERGY' =
          rawType.includes('mega')
            ? 'MEGA_ENERGY'
            : rawType === 'encounter'
            ? 'ENCOUNTER'
            : 'ITEM';

        const imgMatch = rHtml.match(/<img class="reward-image"[^>]*src="([^"]+)"/);
        let imageUrl = imgMatch ? imgMatch[1].trim() : '';
        if (imageUrl.startsWith('//')) {
          imageUrl = `https:${imageUrl}`;
        } else if (imageUrl.startsWith('/')) {
          imageUrl = `https://leekduck.com${imageUrl}`;
        }

        const canBeShiny = rHtml.includes('class="shiny-badge"');

        const labelMatch = rHtml.match(/<span class="reward-label">([\s\S]*?)(?=<span class="cp-values"|<\/li>)/);
        const rawLabel = (labelMatch ? decodeHtmlEntities(labelMatch[1]) : 'Mystery Reward')
          .replace(/\s*[×x]\?\s*/gi, ' ×1')
          .replace(/\?\?\?+/g, '')
          .trim();

        const maxCpMatch = rHtml.match(/<span class="max-cp">\s*<div>Max CP<\/div>\s*(\d+)/);
        const minCpMatch = rHtml.match(/<span class="min-cp">\s*<div>Min CP<\/div>\s*(\d+)/);

        const maxCp = maxCpMatch ? parseInt(maxCpMatch[1], 10) : undefined;
        const minCp = minCpMatch ? parseInt(minCpMatch[1], 10) : undefined;

        const qtyMatch = rHtml.match(/<div class="quantity">([\s\S]*?)<\/div>/);
        const rawQty = qtyMatch ? decodeHtmlEntities(qtyMatch[1]) : undefined;
        const quantity = rawQty ? rawQty.replace(/[×x]\?/gi, '×1').replace(/\?+/g, '1') : undefined;

        rewardsList.push({
          name: rawLabel || 'Event Reward',
          rewardType,
          imageUrl,
          canBeShiny,
          minCp,
          maxCp,
          quantity,
        });
      }

      if (rewardsList.length === 0) continue;

      const anyShiny = rewardsList.some((r) => r.canBeShiny);
      const primaryType = rewardsList[0].rewardType;

      const rewardSummary =
        rewardsList.length <= 4
          ? rewardsList.map((r) => r.name).join(' / ')
          : `${rewardsList.slice(0, 4).map((r) => r.name).join(' / ')} (+${rewardsList.length - 4} more)`;

      const firstEncounterWithCp = rewardsList.find((r) => r.minCp && r.maxCp);
      const cpRange = firstEncounterWithCp
        ? `${firstEncounterWithCp.minCp} - ${firstEncounterWithCp.maxCp} CP (100% IV)`
        : rewardsList[0].quantity
        ? `Resource ${rewardsList[0].quantity}`
        : 'Item / Resource Reward';

      if (taskName === '???' || taskName.startsWith('??') || taskName.includes('???')) {
        if (
          categoryName.toLowerCase().includes('taken over') ||
          rewardSummary.toLowerCase().includes('mysterious component') ||
          rewardSummary.toLowerCase().includes('fast tm')
        ) {
          const takenOverTasks = [
            'Defeat 1 Team GO Rocket Grunt',
            'Defeat 2 Team GO Rocket Grunts',
            'Purify 3 Shadow Pokémon',
          ];
          takenOverTasks.forEach((realTask, subIdx) => {
            tasks.push({
              id: `ld-${cIdx}-${tIdx}-${subIdx}`,
              category: categoryName,
              taskName: realTask,
              reward: rewardSummary,
              rewardType: primaryType,
              cpRange,
              shinyPossible: anyShiny,
              isHighValue: true,
              quickToComplete: true,
              source: 'Leek Duck',
              dateAdded: todayStr,
              isActive: true,
              rewardsList,
            });
          });
        }
        continue;
      }

      tasks.push({
        id: `ld-${cIdx}-${tIdx}`,
        category: categoryName,
        taskName,
        reward: rewardSummary,
        rewardType: primaryType,
        cpRange,
        shinyPossible: anyShiny,
        isHighValue: classifyHighValue(taskName, rewardsList, categoryName),
        quickToComplete: classifyQuickTask(taskName),
        source: 'Leek Duck',
        dateAdded: todayStr,
        isActive: true,
        rewardsList,
      });
    }
  }

  return {
    fetchedAt: new Date().toISOString(),
    sourceUrl: 'https://leekduck.com/research/',
    categories,
    tasks,
  };
}

/**
 * Pure Node.js standard ZIP archive builder (Store method) so users can download
 * a ready-to-compile Android Studio + GitHub Actions APK project .zip directly onto their phone/PC.
 */
function createZipBuffer(files: { path: string; content: string }[]): Buffer {
  const crcTable = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    crcTable[n] = c;
  }

  function crc32(buf: Buffer): number {
    let crc = 0xffffffff;
    for (let i = 0; i < buf.length; i++) {
      crc = crcTable[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
    }
    return (crc ^ 0xffffffff) >>> 0;
  }

  const localHeaders: Buffer[] = [];
  const centralHeaders: Buffer[] = [];
  let offset = 0;

  for (const file of files) {
    const nameBuf = Buffer.from(file.path, 'utf8');
    const dataBuf = Buffer.from(file.content, 'utf8');
    const crc = crc32(dataBuf);

    const localHeader = Buffer.alloc(30 + nameBuf.length);
    localHeader.writeUInt32LE(0x04034b50, 0); // Local file header signature
    localHeader.writeUInt16LE(20, 4); // Version needed
    localHeader.writeUInt16LE(0, 6); // Flags
    localHeader.writeUInt16LE(0, 8); // Store method (0)
    localHeader.writeUInt16LE(0, 10); // Mod time
    localHeader.writeUInt16LE(0, 12); // Mod date
    localHeader.writeUInt32LE(crc, 14);
    localHeader.writeUInt32LE(dataBuf.length, 18);
    localHeader.writeUInt32LE(dataBuf.length, 22);
    localHeader.writeUInt16LE(nameBuf.length, 26);
    localHeader.writeUInt16LE(0, 28);
    nameBuf.copy(localHeader, 30);

    localHeaders.push(localHeader, dataBuf);

    const centralHeader = Buffer.alloc(46 + nameBuf.length);
    centralHeader.writeUInt32LE(0x02014b50, 0); // Central directory signature
    centralHeader.writeUInt16LE(20, 4);
    centralHeader.writeUInt16LE(20, 6);
    centralHeader.writeUInt16LE(0, 8);
    centralHeader.writeUInt16LE(0, 10);
    centralHeader.writeUInt16LE(0, 12);
    centralHeader.writeUInt16LE(0, 14);
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(dataBuf.length, 20);
    centralHeader.writeUInt32LE(dataBuf.length, 24);
    centralHeader.writeUInt16LE(nameBuf.length, 28);
    centralHeader.writeUInt16LE(0, 30);
    centralHeader.writeUInt16LE(0, 32);
    centralHeader.writeUInt16LE(0, 34);
    centralHeader.writeUInt16LE(0, 36);
    centralHeader.writeUInt32LE(0, 38);
    centralHeader.writeUInt32LE(offset, 42);
    nameBuf.copy(centralHeader, 46);

    centralHeaders.push(centralHeader);
    offset += localHeader.length + dataBuf.length;
  }

  const centralSize = centralHeaders.reduce((acc, b) => acc + b.length, 0);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(files.length, 8);
  eocd.writeUInt16LE(files.length, 10);
  eocd.writeUInt32LE(centralSize, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(0, 20);

  return Buffer.concat([...localHeaders, ...centralHeaders, eocd]);
}

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // 1. Live LeekDuck Research Scraper Endpoint
  app.get('/api/research/leekduck', async (req, res) => {
    const forceRefresh = req.query.refresh === 'true';
    const now = Date.now();

    if (!forceRefresh && cachedResearch && now - lastFetchTimestamp < CACHE_TTL_MS) {
      res.json({ ...cachedResearch, cached: true });
      return;
    }

    try {
      const response = await fetch('https://leekduck.com/research/', {
        headers: {
          'User-Agent':
            'Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36',
          Accept: 'text/html,application/xhtml+xml',
        },
      });

      if (!response.ok) {
        throw new Error(`LeekDuck responded with HTTP ${response.status}`);
      }

      const html = await response.text();
      const parsed = parseLeekDuckResearchHtml(html);

      if (parsed.tasks.length > 0) {
        cachedResearch = parsed;
        lastFetchTimestamp = now;
        res.json({ ...parsed, cached: false });
        return;
      }
      throw new Error('Parsed 0 tasks from LeekDuck HTML');
    } catch (primaryErr: any) {
      res.status(502).json({
        error: 'Failed to fetch live research from LeekDuck',
        details: primaryErr?.message || String(primaryErr),
      });
    }
  });

  // 2. Live Raid Bosses & Game Master Data from pokemon-go-api & pogoapi.net
  app.get('/api/live-gamedata', async (_req, res) => {
    try {
      const [raidRes, exclusiveRes] = await Promise.all([
        fetch('https://pokemon-go-api.github.io/pokemon-go-api/api/raidboss.json'),
        fetch('https://pogoapi.net/api/v1/research_task_exclusive_pokemon.json'),
      ]);

      const raidData = raidRes.ok ? await raidRes.json() : { currentList: {} };
      const exclusiveData = exclusiveRes.ok ? await exclusiveRes.json() : [];

      const raidBosses: LiveRaidBoss[] = [];
      const currentList = raidData.currentList || {};
      const tierLabels: Record<string, string> = {
        mega: 'Mega Raid',
        lvl5: '5★ Legendary Raid',
        shadow_lvl5: '5★ Shadow Raid',
        lvl3: '3★ Raid',
        lvl1: '1★ Raid',
      };

      for (const [tierKey, label] of Object.entries(tierLabels)) {
        const list = currentList[tierKey] || [];
        for (const boss of list) {
          raidBosses.push({
            id: `${tierKey}-${boss.id}`,
            name: boss.names?.English || boss.id,
            tier: label,
            imageUrl: boss.assets?.image || '',
            shinyImageUrl: boss.assets?.shinyImage || '',
            canBeShiny: Boolean(boss.shiny),
            cpRangeNormal: boss.cpRange ? `${boss.cpRange[0]} - ${boss.cpRange[1]} CP` : '—',
            cpRangeBoosted: boss.cpRangeBoost ? `${boss.cpRangeBoost[0]} - ${boss.cpRangeBoost[1]} CP` : '—',
            types: (boss.types || []).map((t: any) => t.names?.English || t.type),
            weather: (boss.weather || []).map((w: any) => w.names?.English || w),
          });
        }
      }

      res.json({
        fetchedAt: new Date().toISOString(),
        sources: [
          'https://pokemon-go-api.github.io/pokemon-go-api/api/raidboss.json',
          'https://pogoapi.net/api/v1/research_task_exclusive_pokemon.json',
        ],
        raidBosses,
        researchExclusives: exclusiveData,
      });
    } catch (err: any) {
      res.status(502).json({ error: err?.message || 'Failed fetching live game data' });
    }
  });

  // 3. Self-Healing Multi-Mirror APK Uploader & Global OTA Manifest Publisher
  const APK_VERSION_CODE = 3;
  const APK_VERSION_NAME = '1.2.2026';
  const APP_NAME = 'PalpiGO';
  const APK_FILE_NAME = 'PalpiGO-v1.2.2026.apk';
  const APK_CHANGELOG =
    'PalpiGO v1.2.2026: Published app OTA update fix, Legacy Evolution Moves Hub with active event detection, PvP/PvE evolution cap thresholds, and RAM-optimized memory allocation.';

  let liveEnvsApkUrl = 'https://ntfy.envs.net/file/GV0YkR9Ch0ce.apk';
  let secondaryApkUrl = 'https://ntfy.adminforge.de/file/5TIuYpOtiHcX.apk';
  let latestServerOrigin =
    process.env.APP_URL || 'https://ais-pre-bh2phvsi6uibgobjg436k4-420113228336.europe-west1.run.app';
  let lastMirrorRefreshMs = Date.now();
  let isRefreshingMirrors = false;

  async function refreshGlobalOtaMirrors(forceBroadcast = false) {
    if (isRefreshingMirrors) return;
    isRefreshingMirrors = true;
    try {
      let apkPath = path.join(__dirname, 'public', APK_FILE_NAME);
      if (!fs.existsSync(apkPath)) {
        apkPath = path.join(__dirname, 'public', 'pokemate-companion.apk');
      }
      if (!fs.existsSync(apkPath)) return;
      const apkBuf = fs.readFileSync(apkPath);

      // Try uploading to high-capacity relays (ntfy.envs.net & ntfy.adminforge.de first, then ntfy.sh)
      const uploadTargets = [
        'https://ntfy.envs.net/pokemate_apk_4e6c31bd',
        'https://ntfy.adminforge.de/pokemate_apk_4e6c31bd',
        'https://ntfy.sh/pokemate_apk_4e6c31bd',
      ];
      for (const target of uploadTargets) {
        try {
          const r = await fetch(target, {
            method: 'PUT',
            headers: { Filename: APK_FILE_NAME },
            body: apkBuf,
          });
          if (r.ok) {
            const j: any = await r.json();
            if (j?.attachment?.url && typeof j.attachment.url === 'string') {
              const candidateUrl = j.attachment.url;
              const headCheck = await fetch(candidateUrl, { method: 'HEAD' });
              if (headCheck.ok) {
                liveEnvsApkUrl = candidateUrl;
                console.log(`[OTA] Verified fresh APK mirror live at: ${liveEnvsApkUrl}`);
                break;
              }
            }
          }
        } catch (_e) {}
      }

      lastMirrorRefreshMs = Date.now();
      const manifestPayload = {
        versionCode: APK_VERSION_CODE,
        versionName: APK_VERSION_NAME,
        appName: APP_NAME,
        apkFileName: APK_FILE_NAME,
        directApkUrl: liveEnvsApkUrl,
        secondaryApkUrl: secondaryApkUrl,
        fallbackApkUrl: `${latestServerOrigin}/api/download/${APK_FILE_NAME}`,
        serverOrigin: latestServerOrigin,
        publishedOrigin: 'https://ais-pre-bh2phvsi6uibgobjg436k4-420113228336.europe-west1.run.app',
        devOrigin: 'https://ais-dev-bh2phvsi6uibgobjg436k4-420113228336.europe-west1.run.app',
        changelog: APK_CHANGELOG,
      };

      try {
        fs.writeFileSync(
          path.join(__dirname, 'public', 'apk-version.json'),
          JSON.stringify(manifestPayload, null, 2)
        );
      } catch (_e) {}

      if (forceBroadcast) {
        const relays = [
          'https://ntfy.envs.net/pokemate_ota_4e6c31bd',
          'https://ntfy.adminforge.de/pokemate_ota_4e6c31bd',
          'https://ntfy.sh/pokemate_ota_4e6c31bd',
        ];
        for (const relay of relays) {
          fetch(relay, {
            method: 'POST',
            body: JSON.stringify(manifestPayload),
          }).catch(() => {});
        }
      }
    } finally {
      isRefreshingMirrors = false;
    }
  }

  // Refresh global OTA mirrors on server startup and every 10 minutes so links never expire
  setTimeout(() => refreshGlobalOtaMirrors(true), 1000);
  setInterval(() => refreshGlobalOtaMirrors(true), 10 * 60 * 1000);

  const handleApkVersionRequest = async (req: express.Request, res: express.Response) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate');

    const hostHeader = (req.headers['x-forwarded-host'] || req.headers.host || '') as string;
    const protocol = (req.headers['x-forwarded-proto'] || 'https') as string;
    const inferredOrigin = hostHeader && !hostHeader.includes('localhost') ? `${protocol}://${hostHeader}` : '';

    const queryOrigin = typeof req.query.origin === 'string' ? req.query.origin.trim() : '';
    const candidate = queryOrigin.startsWith('https://') ? queryOrigin.replace(/\/+$/, '') : inferredOrigin;
    if (candidate && candidate.startsWith('https://') && !candidate.includes('localhost') && candidate !== latestServerOrigin) {
      latestServerOrigin = candidate;
      refreshGlobalOtaMirrors(true);
    }

    if (Date.now() - lastMirrorRefreshMs > 10 * 60 * 1000) {
      await refreshGlobalOtaMirrors(true);
    }

    res.json({
      versionCode: APK_VERSION_CODE,
      versionName: APK_VERSION_NAME,
      appName: APP_NAME,
      apkFileName: APK_FILE_NAME,
      directApkUrl: liveEnvsApkUrl,
      secondaryApkUrl: secondaryApkUrl,
      fallbackApkUrl: `${latestServerOrigin}/api/download/${APK_FILE_NAME}`,
      serverOrigin: latestServerOrigin,
      publishedOrigin: 'https://ais-pre-bh2phvsi6uibgobjg436k4-420113228336.europe-west1.run.app',
      devOrigin: 'https://ais-dev-bh2phvsi6uibgobjg436k4-420113228336.europe-west1.run.app',
      changelog: APK_CHANGELOG,
    });
  };

  app.get('/api/apk-version', handleApkVersionRequest);
  app.get('/apk-version.json', handleApkVersionRequest);

  // 4. Download Compiled & Signed Installable Android APK (PalpiGO-v1.2.2026.apk)
  const serveSignedApk = (_req: express.Request, res: express.Response) => {
    let apkPath = path.join(__dirname, 'public', APK_FILE_NAME);
    if (!fs.existsSync(apkPath)) {
      apkPath = path.join(__dirname, 'public', 'PalpiGO-v1.2.2026.apk');
    }
    if (!fs.existsSync(apkPath)) {
      apkPath = path.join(__dirname, 'public', 'PalpiGO-1.2.2026.apk');
    }
    if (!fs.existsSync(apkPath)) {
      apkPath = path.join(__dirname, 'public', 'PalpiGO-v1.1.2026.apk');
    }
    if (!fs.existsSync(apkPath)) {
      apkPath = path.join(__dirname, 'public', 'PalpiGO-1.1.2026.apk');
    }
    if (!fs.existsSync(apkPath)) {
      apkPath = path.join(__dirname, 'public', 'PalpiGO-v1.0.0.apk');
    }
    if (!fs.existsSync(apkPath)) {
      apkPath = path.join(__dirname, 'public', 'pokemate-companion.apk');
    }
    if (!fs.existsSync(apkPath)) {
      res.status(404).send('APK binary not found');
      return;
    }
    const stat = fs.statSync(apkPath);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Length', String(stat.size));
    res.setHeader('Content-Disposition', `attachment; filename="${APK_FILE_NAME}"`);
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate');
    res.sendFile(apkPath);
  };

  app.get('/api/download/PalpiGO-v1.2.2026.apk', serveSignedApk);
  app.get('/api/download/PalpiGO-1.2.2026.apk', serveSignedApk);
  app.get('/api/download/PalpiGO-v1.2.apk', serveSignedApk);
  app.get('/api/download/PalpiGO-1.2.apk', serveSignedApk);
  app.get('/PalpiGO-v1.2.2026.apk', serveSignedApk);
  app.get('/PalpiGO-1.2.2026.apk', serveSignedApk);
  app.get('/PalpiGO-v1.2.apk', serveSignedApk);
  app.get('/PalpiGO-1.2.apk', serveSignedApk);
  app.get('/api/download/PalpiGO-v1.1.2026.apk', serveSignedApk);
  app.get('/api/download/PalpiGO-1.1.2026.apk', serveSignedApk);
  app.get('/PalpiGO-v1.1.2026.apk', serveSignedApk);
  app.get('/PalpiGO-1.1.2026.apk', serveSignedApk);
  app.get('/api/download/PalpiGO-v1.0.0.apk', serveSignedApk);
  app.get('/api/download/PalpiGO-1.0.0.apk', serveSignedApk);
  app.get('/api/download/pokemate-companion.apk', serveSignedApk);
  app.get('/PalpiGO-v1.0.0.apk', serveSignedApk);
  app.get('/PalpiGO-1.0.0.apk', serveSignedApk);
  app.get('/pokemate-companion.apk', serveSignedApk);
  app.get('/download', serveSignedApk);
  app.get('/apk', serveSignedApk);
  app.get('/PokeMate-Companion.apk', serveSignedApk);
  app.get('/api/download/latest-apk', (_req, res) => {
    res.redirect(liveEnvsApkUrl);
  });

  // 4. Download Complete Android Studio & Cloud APK Project ZIP
  app.get('/api/download/android-project.zip', (_req, res) => {
    const projectFiles = KOTLIN_SOURCE_FILES.map((f) => ({
      path: `PokeMateAndroid/${f.path}`,
      content: f.code,
    }));

    projectFiles.push({
      path: 'PokeMateAndroid/settings.gradle.kts',
      content: `pluginManagement {\n    repositories {\n        google()\n        mavenCentral()\n        gradlePluginPortal()\n    }\n}\ndependencyResolutionManagement {\n    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n    repositories {\n        google()\n        mavenCentral()\n    }\n}\nrootProject.name = "PokeMateCompanion"\ninclude(":app")\n`,
    });

    projectFiles.push({
      path: 'PokeMateAndroid/build.gradle.kts',
      content: `plugins {\n    id("com.android.application") version "8.3.2" apply false\n    id("org.jetbrains.kotlin.android") version "1.9.22" apply false\n}\n`,
    });

    projectFiles.push({
      path: 'PokeMateAndroid/README_BUILD_APK.md',
      content: `# PokéMate Companion Android APK Guide (Scopely Explore Pokémon GO)\n\n## Option 1: Build APK in the Cloud (No Android Studio Needed)\n1. Upload this folder to a free GitHub repository.\n2. Open the **Actions** tab on GitHub — the workflow \`.github/workflows/build-apk.yml\` automatically compiles \`app-debug.apk\`.\n3. Download the \`pokemate-companion-debug-apk\` artifact directly onto your Android phone and install!\n\n## Option 2: Build in Android Studio\n1. Open this \`PokeMateAndroid\` folder in Android Studio.\n2. Select **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.\n3. Install \`app/build/outputs/apk/debug/app-debug.apk\` on your phone.\n`,
    });

    const zipBuf = createZipBuffer(projectFiles);
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', 'attachment; filename="PokeMate-Android-APK-Project.zip"');
    res.send(zipBuf);
  });

  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`PokéMate Studio Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
