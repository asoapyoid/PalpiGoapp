package com.pokemate.companion;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive Pokémon GO Meta, Base Stats, CP Math & Screen Appraisal Scanner:
 * - Evaluates any Pokémon on screen (via OCR + On-Device Appraisal Bar Pixel Vision) or via Search.
 * - Provides S+ to F Meta Tier Ranking, Raid/PvE vs PvP (GL/UL/ML) breakdown, Base Stats (ATK/DEF/STA),
 *   exact 100% IV Hundo CPs (Lv15 Research, Lv20 Raid/Egg, Lv25 Weather Raid, Lv40 Max, Lv50 Max XL),
 *   Optimal Moveset, and a clear Recommended Power-Up Level & Investment Verdict.
 */
public class PokemonMetaAnalyzer {

    // Exact Pokémon GO CP Multiplier (CPM) constants
    public static final double CPM_LV15 = 0.51739395;
    public static final double CPM_LV20 = 0.59740001;
    public static final double CPM_LV25 = 0.66793400;
    public static final double CPM_LV30 = 0.73170000;
    public static final double CPM_LV35 = 0.76156384;
    public static final double CPM_LV40 = 0.79030001;
    public static final double CPM_LV50 = 0.84029999;

    public static class PokemonMetaEntry {
        public final String name;
        public String types;
        public final int baseAtk;
        public final int baseDef;
        public final int baseSta;
        public final String tier; // "S+", "S", "A+", "A", "B+", "B", "C", "D", "F"
        public final boolean isMeta;
        public final String raidRole;
        public final String pvpRole;
        public final String bestMoveset; // Optimal Raid / Same-Type STAB Moveset
        public final String bestMoves;   // Alias for bestMoveset
        public String pvpMoveset;        // Optimal PvP Dual-Move Coverage Moveset
        public final String recommendedLevel;
        public final String evolutionNote;
        public final List<String> normalFastMoves = new ArrayList<String>();
        public final List<String> eliteFastMoves = new ArrayList<String>();
        public final List<String> normalChargedMoves = new ArrayList<String>();
        public final List<String> eliteChargedMoves = new ArrayList<String>();

        public PokemonMetaEntry(
                String name,
                String types,
                int baseAtk,
                int baseDef,
                int baseSta,
                String tier,
                boolean isMeta,
                String raidRole,
                String pvpRole,
                String bestMoveset,
                String recommendedLevel,
                String evolutionNote
        ) {
            this.name = name;
            this.types = types != null ? types : "Normal";
            this.baseAtk = baseAtk;
            this.baseDef = baseDef;
            this.baseSta = baseSta;
            this.tier = tier;
            this.isMeta = isMeta;
            this.raidRole = raidRole;
            this.pvpRole = pvpRole;
            this.bestMoveset = bestMoveset != null ? bestMoveset : "STAB Fast + Charged Move";
            this.bestMoves = this.bestMoveset;
            this.pvpMoveset = "";
            this.recommendedLevel = recommendedLevel;
            this.evolutionNote = evolutionNote != null ? evolutionNote : "";
        }

        public boolean isMoveElite(String moveName) {
            if (moveName == null) return false;
            String lower = moveName.toLowerCase(Locale.US);
            if (moveName.contains("*") || lower.contains("elite tm") || lower.contains("legacy")
                    || lower.contains("meteorite") || lower.contains("fusion")) {
                return true;
            }
            String clean = cleanMoveName(moveName);
            for (int i = 0; i < eliteFastMoves.size(); i++) {
                if (eliteFastMoves.get(i).equalsIgnoreCase(clean)) return true;
            }
            for (int i = 0; i < eliteChargedMoves.size(); i++) {
                if (eliteChargedMoves.get(i).equalsIgnoreCase(clean)) return true;
            }
            return false;
        }

        public String getRaidGradeBadge() {
            if (raidRole == null) return tier;
            Matcher m = Pattern.compile("^([SABCD][+\\-]?)\\b").matcher(raidRole.trim());
            if (m.find()) return m.group(1);
            Matcher m2 = Pattern.compile("Raid:\\s*([SABCD][+\\-]?)").matcher(raidRole);
            if (m2.find()) return m2.group(1);
            if (raidRole.startsWith("Evolves into")) return tier;
            return tier;
        }

        public String getPvpGradeBadge() {
            if (pvpRole == null) return "-";
            Matcher m = Pattern.compile(":\\s*([SABCD][+\\-]?)\\b").matcher(pvpRole);
            if (m.find()) return m.group(1);
            return "B";
        }

        public String getEffectivePvpMoveset() {
            return (pvpMoveset != null && !pvpMoveset.isEmpty()) ? pvpMoveset : bestMoveset;
        }

        public String getOptimalMovesTmBreakdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("⚔ RAID (Same-Element STAB): ").append(formatMovesetStringWithTypes(bestMoveset, this));
            String pvpRaw = getEffectivePvpMoveset();
            if (!pvpRaw.equalsIgnoreCase(bestMoveset)) {
                sb.append("\n🛡 PVP (Coverage): ").append(formatMovesetStringWithTypes(pvpRaw, this));
            }
            return sb.toString();
        }

        public String getFullMovePoolSummary() {
            StringBuilder sb = new StringBuilder();
            if (!normalFastMoves.isEmpty()) {
                sb.append("🟢 Fast TM: ").append(joinMovesWithTypes(normalFastMoves)).append("\n");
            }
            if (!eliteFastMoves.isEmpty()) {
                sb.append("🟣 Elite Fast: ").append(joinMovesWithTypes(eliteFastMoves)).append("\n");
            }
            if (!normalChargedMoves.isEmpty()) {
                sb.append("🟢 Charged TM: ").append(joinMovesWithTypes(normalChargedMoves)).append("\n");
            }
            if (!eliteChargedMoves.isEmpty()) {
                sb.append("🟣 Elite Charged: ").append(joinMovesWithTypes(eliteChargedMoves));
            }
            String res = sb.toString().trim();
            if (res.isEmpty()) {
                boolean anyEliteInBest = bestMoveset.contains("*") || bestMoveset.toLowerCase(Locale.US).contains("elite");
                return anyEliteInBest ? "🟣 Optimal move requires Elite TM" : "🟢 Standard Fast & Charged TMs";
            }
            return res;
        }

        public String getTypeWeaknessSummary() {
            return computeTypeEffectivenessText(this.types);
        }

        private static String joinList(List<String> items) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) s.append(", ");
                s.append(items.get(i));
            }
            return s.toString();
        }

        private static String joinMovesWithTypes(List<String> items) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) s.append("  •  ");
                String mv = items.get(i);
                String t = getMoveType(mv);
                s.append(getTypeEmoji(t)).append(" ").append(mv).append(" [").append(t).append("]");
            }
            return s.toString();
        }
    }

    public static class AppraisalBarVisionResult {
        public final boolean appraisalCardDetected;
        public final int ivAtk;
        public final int ivDef;
        public final int ivSta;
        public final int ivPercent;
        public final String starRating;
        public final boolean shadowAuraDetected;

        public AppraisalBarVisionResult(
                boolean appraisalCardDetected,
                int ivAtk,
                int ivDef,
                int ivSta,
                boolean shadowAuraDetected
        ) {
            this.appraisalCardDetected = appraisalCardDetected;
            this.ivAtk = Math.max(0, Math.min(15, ivAtk));
            this.ivDef = Math.max(0, Math.min(15, ivDef));
            this.ivSta = Math.max(0, Math.min(15, ivSta));
            int sum = this.ivAtk + this.ivDef + this.ivSta;
            this.ivPercent = Math.round((sum / 45.0f) * 100f);
            if (sum == 45) {
                this.starRating = "4★ HUNDO (100%)";
            } else if (sum >= 37) {
                this.starRating = "3★ (" + this.ivPercent + "%)";
            } else if (sum >= 30) {
                this.starRating = "2★ (" + this.ivPercent + "%)";
            } else if (sum >= 23) {
                this.starRating = "1★ (" + this.ivPercent + "%)";
            } else {
                this.starRating = "0★ (" + this.ivPercent + "%)";
            }
            this.shadowAuraDetected = shadowAuraDetected;
        }
    }

    public static class PokemonScanReport {
        public final PokemonMetaEntry entry;
        public final String displayTitle;
        public final String effectiveTier;
        public final boolean effectiveIsMeta;
        public final int scannedCp;
        public final int scannedHp;
        public final boolean isShadow;
        public final boolean isLucky;
        public final boolean isDynamax;
        public final AppraisalBarVisionResult appraisal;
        public final String scannedFastMove;
        public final String scannedChargedMove1;
        public final String scannedChargedMove2;
        public final boolean hasScannedMoves;
        public final boolean scannedMovesMatchRaid;
        public final boolean scannedMovesMatchPvp;
        public final String scannedMovesDisplay;
        public final String scannedMovesVerdict;
        public final int cpLv15Hundo;
        public final int cpLv20Hundo;
        public final int cpLv25Hundo;
        public final int cpLv40Hundo;
        public final int cpLv50Hundo;
        public final int yourCpLv40;
        public final int yourCpLv50;
        public final String verdictHeadline;
        public final String powerUpTargetText;
        public final String detailedAdvice;

        public PokemonScanReport(
                PokemonMetaEntry entry,
                int scannedCp,
                int scannedHp,
                boolean isShadow,
                boolean isLucky,
                boolean isDynamax,
                AppraisalBarVisionResult appraisal
        ) {
            this(entry, scannedCp, scannedHp, isShadow, isLucky, isDynamax, appraisal, "", "", "");
        }

        public PokemonScanReport(
                PokemonMetaEntry entry,
                int scannedCp,
                int scannedHp,
                boolean isShadow,
                boolean isLucky,
                boolean isDynamax,
                AppraisalBarVisionResult appraisal,
                String scannedFastMove,
                String scannedChargedMove1,
                String scannedChargedMove2
        ) {
            this.entry = entry;
            this.scannedCp = scannedCp;
            this.scannedHp = scannedHp;
            this.isShadow = isShadow;
            this.isLucky = isLucky;
            this.isDynamax = isDynamax;
            this.appraisal = appraisal;
            this.scannedFastMove = scannedFastMove != null ? scannedFastMove : "";
            this.scannedChargedMove1 = scannedChargedMove1 != null ? scannedChargedMove1 : "";
            this.scannedChargedMove2 = scannedChargedMove2 != null ? scannedChargedMove2 : "";
            this.hasScannedMoves = !this.scannedFastMove.isEmpty() || !this.scannedChargedMove1.isEmpty() || !this.scannedChargedMove2.isEmpty();

            if (this.hasScannedMoves) {
                StringBuilder disp = new StringBuilder();
                if (!this.scannedFastMove.isEmpty()) {
                    disp.append(formatSingleMoveBadge(this.scannedFastMove, entry));
                }
                if (!this.scannedChargedMove1.isEmpty()) {
                    if (disp.length() > 0) disp.append(" + ");
                    disp.append(formatSingleMoveBadge(this.scannedChargedMove1, entry));
                }
                if (!this.scannedChargedMove2.isEmpty()) {
                    disp.append(" & ").append(formatSingleMoveBadge(this.scannedChargedMove2, entry));
                }
                this.scannedMovesDisplay = disp.toString();

                boolean fastOkRaid = this.scannedFastMove.isEmpty() || isMoveOptimalForRaid(this.scannedFastMove);
                boolean chgOkRaid = (!this.scannedChargedMove1.isEmpty() && isMoveOptimalForRaid(this.scannedChargedMove1))
                        || (!this.scannedChargedMove2.isEmpty() && isMoveOptimalForRaid(this.scannedChargedMove2));
                this.scannedMovesMatchRaid = fastOkRaid && chgOkRaid;

                boolean fastOkPvp = this.scannedFastMove.isEmpty() || isMoveOptimalForPvp(this.scannedFastMove);
                boolean chgOkPvp = (!this.scannedChargedMove1.isEmpty() && isMoveOptimalForPvp(this.scannedChargedMove1))
                        || (!this.scannedChargedMove2.isEmpty() && isMoveOptimalForPvp(this.scannedChargedMove2));
                this.scannedMovesMatchPvp = fastOkPvp && chgOkPvp;

                if ("Frustration".equalsIgnoreCase(this.scannedChargedMove1)) {
                    this.scannedMovesVerdict = "⚠️ Has Frustration! TM away during Rocket Takeover event.";
                } else if (this.scannedMovesMatchRaid && this.scannedMovesMatchPvp) {
                    this.scannedMovesVerdict = "✅ OPTIMAL MOVESET for Both Raids & PvP!";
                } else if (this.scannedMovesMatchRaid) {
                    this.scannedMovesVerdict = "✅ OPTIMAL RAID MOVESET (Same-Element STAB)!";
                } else if (this.scannedMovesMatchPvp) {
                    this.scannedMovesVerdict = "✅ OPTIMAL PVP COVERAGE MOVESET!";
                } else {
                    this.scannedMovesVerdict = "🔧 SUBOPTIMAL MOVES — Use TM to match Optimal Raid or PvP set below";
                }
            } else {
                this.scannedMovesDisplay = "";
                this.scannedMovesVerdict = "";
                this.scannedMovesMatchRaid = false;
                this.scannedMovesMatchPvp = false;
            }

            String prefix = "";
            if (isShadow && !entry.name.toLowerCase(Locale.US).startsWith("shadow ")) {
                prefix += "Shadow ";
            }
            if (isLucky) {
                prefix += "Lucky ";
            }
            if (isDynamax) {
                prefix += "Dynamax ";
            }
            this.displayTitle = prefix + entry.name;

            // Calibrated Shadow tier promotion: only promotes offensive Raid attackers (baseAtk >= 215 or pre-evo) by half a tier
            boolean isRaidAttacker = (entry.baseAtk >= 210 || entry.raidRole.startsWith("Evolves into"))
                    && !entry.raidRole.startsWith("Raid: F") && !entry.raidRole.startsWith("Raid: D");
            if (isShadow && isRaidAttacker) {
                if (entry.tier.equals("S")) {
                    this.effectiveTier = "S+";
                } else if (entry.tier.equals("A+")) {
                    this.effectiveTier = "S";
                } else if (entry.tier.equals("A")) {
                    this.effectiveTier = "A+";
                } else if (entry.tier.equals("B+")) {
                    this.effectiveTier = "A";
                } else {
                    this.effectiveTier = entry.tier;
                }
                this.effectiveIsMeta = this.effectiveTier.startsWith("S") || this.effectiveTier.startsWith("A");
            } else {
                this.effectiveTier = entry.tier;
                this.effectiveIsMeta = entry.isMeta;
            }

            this.cpLv15Hundo = computeCp(entry.baseAtk, entry.baseDef, entry.baseSta, 15, 15, 15, CPM_LV15);
            this.cpLv20Hundo = computeCp(entry.baseAtk, entry.baseDef, entry.baseSta, 15, 15, 15, CPM_LV20);
            this.cpLv25Hundo = computeCp(entry.baseAtk, entry.baseDef, entry.baseSta, 15, 15, 15, CPM_LV25);
            this.cpLv40Hundo = computeCp(entry.baseAtk, entry.baseDef, entry.baseSta, 15, 15, 15, CPM_LV40);
            this.cpLv50Hundo = computeCp(entry.baseAtk, entry.baseDef, entry.baseSta, 15, 15, 15, CPM_LV50);

            if (appraisal != null && appraisal.appraisalCardDetected) {
                this.yourCpLv40 = computeCp(entry.baseAtk, entry.baseDef, entry.baseSta, appraisal.ivAtk, appraisal.ivDef, appraisal.ivSta, CPM_LV40);
                this.yourCpLv50 = computeCp(entry.baseAtk, entry.baseDef, entry.baseSta, appraisal.ivAtk, appraisal.ivDef, appraisal.ivSta, CPM_LV50);
            } else {
                this.yourCpLv40 = this.cpLv40Hundo;
                this.yourCpLv50 = this.cpLv50Hundo;
            }

            String[] rec = buildSmartRecommendation(
                    entry, effectiveTier, effectiveIsMeta, scannedCp, isShadow, isLucky, appraisal,
                    cpLv15Hundo, cpLv20Hundo, cpLv25Hundo, yourCpLv40, yourCpLv50
            );
            this.verdictHeadline = rec[0];
            this.powerUpTargetText = rec[1];
            this.detailedAdvice = rec[2];
        }

        public boolean hasScannedMoves() {
            return !scannedFastMove.isEmpty() || !scannedChargedMove1.isEmpty() || !scannedChargedMove2.isEmpty();
        }

        public boolean isMoveOptimalForRaid(String moveName) {
            if (moveName == null || moveName.isEmpty() || entry.bestMoveset == null) return false;
            return movesetContainsMove(entry.bestMoveset, moveName);
        }

        public boolean isMoveOptimalForPvp(String moveName) {
            if (moveName == null || moveName.isEmpty()) return false;
            String pvpRef = (entry.pvpMoveset != null && !entry.pvpMoveset.isEmpty()) ? entry.pvpMoveset : entry.bestMoveset;
            return movesetContainsMove(pvpRef, moveName) || movesetContainsMove(entry.bestMoveset, moveName);
        }
    }

    public static boolean movesetContainsMove(String movesetStr, String moveName) {
        if (movesetStr == null || movesetStr.isEmpty() || moveName == null || moveName.isEmpty()) return false;
        String cleanMove = normalizeMoveComparisonKey(cleanMoveName(moveName));
        if (cleanMove.isEmpty()) return false;
        String cleanSet = normalizeMoveComparisonKey(movesetStr);
        if (cleanSet.contains(cleanMove)) return true;
        // Also match base move name for typed moves like "Weather Ball Ice" -> "weather ball"
        String baseMove = stripMoveVariantSuffix(cleanMove);
        return !baseMove.isEmpty() && cleanSet.contains(baseMove);
    }

    private static String normalizeMoveComparisonKey(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.US)
                .replace("-", " ")
                .replace("futuresight", "future sight")
                .replace("super power", "superpower")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String stripMoveVariantSuffix(String normMove) {
        if (normMove == null) return "";
        if (normMove.startsWith("weather ball ")) return "weather ball";
        if (normMove.startsWith("techno blast ")) return "techno blast";
        if (normMove.startsWith("aura wheel ")) return "aura wheel";
        return normMove;
    }

    private static final Map<String, PokemonMetaEntry> META_MAP = new LinkedHashMap<String, PokemonMetaEntry>();
    private static final Map<String, String> MOVE_TYPE_MAP = new HashMap<String, String>();
    private static volatile boolean initialized = false;
    private static volatile boolean liveApiSynced = false;

    public static String cleanMoveName(String rawMove) {
        if (rawMove == null) return "";
        return rawMove
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\[[^\\]]*\\]", "")
                .replace("*", "")
                .replace("->", "")
                .replaceAll("(?i)evolve\\s+to\\s+[a-z0-9\\-]+", "")
                .trim();
    }

    public static String getMoveType(String moveName) {
        ensureInitialized();
        if (moveName == null) return "Normal";
        String clean = cleanMoveName(moveName).toLowerCase(Locale.US).replace("-", " ").trim();
        String t = MOVE_TYPE_MAP.get(clean);
        if (t != null) return t;
        // Substring fallback if move string has extra prefix/suffix
        for (Map.Entry<String, String> kv : MOVE_TYPE_MAP.entrySet()) {
            if (kv.getKey().length() >= 4 && clean.contains(kv.getKey())) {
                return kv.getValue();
            }
        }
        return "Normal";
    }

    public static String getTypeEmoji(String type) {
        if (type == null) return "⚪";
        String t = type.trim().toLowerCase(Locale.US);
        if (t.startsWith("fire")) return "🔥";
        if (t.startsWith("water")) return "💧";
        if (t.startsWith("grass")) return "🌿";
        if (t.startsWith("electric")) return "⚡";
        if (t.startsWith("ice")) return "🧊";
        if (t.startsWith("fighting")) return "🥊";
        if (t.startsWith("poison")) return "☠";
        if (t.startsWith("ground")) return "🌍";
        if (t.startsWith("flying")) return "🪽";
        if (t.startsWith("psychic")) return "🔮";
        if (t.startsWith("bug")) return "🐛";
        if (t.startsWith("rock")) return "🪨";
        if (t.startsWith("ghost")) return "👻";
        if (t.startsWith("dragon")) return "🐉";
        if (t.startsWith("dark")) return "🌑";
        if (t.startsWith("steel")) return "⚙";
        if (t.startsWith("fairy")) return "🧚";
        return "⚪";
    }

    public static String getTypeColorHex(String type) {
        if (type == null) return "#64748B";
        String t = type.trim().toLowerCase(Locale.US);
        if (t.startsWith("fire")) return "#EA580C";
        if (t.startsWith("water")) return "#0284C7";
        if (t.startsWith("grass")) return "#16A34A";
        if (t.startsWith("electric")) return "#CA8A04";
        if (t.startsWith("ice")) return "#0891B2";
        if (t.startsWith("fighting")) return "#DC2626";
        if (t.startsWith("poison")) return "#9333EA";
        if (t.startsWith("ground")) return "#B45309";
        if (t.startsWith("flying")) return "#4F46E5";
        if (t.startsWith("psychic")) return "#DB2777";
        if (t.startsWith("bug")) return "#65A30D";
        if (t.startsWith("rock")) return "#78716C";
        if (t.startsWith("ghost")) return "#6D28D9";
        if (t.startsWith("dragon")) return "#4338CA";
        if (t.startsWith("dark")) return "#334155";
        if (t.startsWith("steel")) return "#475569";
        if (t.startsWith("fairy")) return "#EC4899";
        return "#64748B";
    }

    public static String formatSingleMoveBadge(String rawMove, PokemonMetaEntry entry) {
        String clean = cleanMoveName(rawMove);
        if (clean.isEmpty()) return rawMove;
        String mType = getMoveType(clean);
        boolean elite = (rawMove != null && rawMove.contains("*")) || (entry != null && entry.isMoveElite(rawMove));
        return getTypeEmoji(mType) + " " + clean + " [" + mType + "]" + (elite ? " 🟣Elite" : "");
    }

    public static String decorateMovesetWithTypes(String rawMoveset) {
        return formatMovesetStringWithTypes(rawMoveset, null);
    }

    public static String formatMovesetStringWithTypes(String rawMoveset, PokemonMetaEntry entry) {
        if (rawMoveset == null || rawMoveset.isEmpty()) return "";
        String[] builds = rawMoveset.split("\\|");
        StringBuilder out = new StringBuilder();
        for (int b = 0; b < builds.length; b++) {
            String build = builds[b].trim();
            if (build.isEmpty()) continue;
            if (b > 0) out.append("   OR   ");
            if (build.contains("+")) {
                String[] fc = build.split("\\+", 2);
                String fastPart = fc[0].trim();
                String chargedPart = fc[1].trim();
                out.append(formatMoveAlternatives(fastPart, entry))
                        .append("  +  ")
                        .append(formatMoveAlternatives(chargedPart, entry));
            } else {
                out.append(build);
            }
        }
        return out.toString();
    }

    private static String formatMoveAlternatives(String part, PokemonMetaEntry entry) {
        String[] alts = part.split("(?<=[^\\s])\\s*(/|&)\\s*");
        if (part.contains("&")) {
            String[] amp = part.split("&");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < amp.length; i++) {
                if (i > 0) sb.append(" & ");
                sb.append(formatSlashMoves(amp[i].trim(), entry));
            }
            return sb.toString();
        }
        return formatSlashMoves(part, entry);
    }

    private static String formatSlashMoves(String part, PokemonMetaEntry entry) {
        String[] slash = part.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slash.length; i++) {
            if (i > 0) sb.append(" / ");
            sb.append(formatSingleMoveBadge(slash[i].trim(), entry));
        }
        return sb.toString();
    }

    public static int computeCp(int baseAtk, int baseDef, int baseSta, int ivAtk, int ivDef, int ivSta, double cpm) {
        double atk = baseAtk + ivAtk;
        double def = Math.sqrt(baseDef + ivDef);
        double sta = Math.sqrt(baseSta + ivSta);
        int cp = (int) Math.floor((atk * def * sta * cpm * cpm) / 10.0);
        return Math.max(10, cp);
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        // =========================================================================
        // 1. S+ & S TIER: GOD-TIER RAID ATTACKERS, MEGA EVOLUTIONS & MASTER LEAGUE
        // =========================================================================
        reg("Rayquaza", "Dragon / Flying", 284, 170, 213, "S+", true,
                "S+ (#1 Overall Mega Raid Attacker & Top Dragon/Flying DPS)",
                "Master League: A+",
                "Dragon Tail + Breaking Swipe* / Dragon Ascent* (Meteorite)",
                "Level 40 → Level 50 (Max XL Priority #1)", "");
        reg("Necrozma", "Psychic / Steel-Ghost", 251, 195, 219, "S+", true,
                "S+ (Dusk Mane #1 Steel Raid & Dawn Wings #1 Ghost Raid)",
                "Master League: S+ (#1 Overall ML Meta)",
                "Shadow Claw + Sunsteel Strike (Dusk Mane) / Moongeist Beam (Dawn Wings)",
                "Level 40 → Level 50 (Max XL Priority #1)", "Fuse into Dusk Mane (Solgaleo) or Dawn Wings (Lunala)!");
        reg("Dusk Mane Necrozma", "Psychic / Steel", 277, 220, 200, "S+", true,
                "S+ (#1 Steel Attacker in Pokémon GO — Beats Shadow Metagross)",
                "Master League: S+ (Rank #1 Master League)",
                "Shadow Claw / Metal Claw + Sunsteel Strike & Dark Pulse",
                "Level 40 → Level 50 (Max XL Priority #1)", "");
        reg("Dawn Wings Necrozma", "Psychic / Ghost", 277, 220, 200, "S+", true,
                "S+ (#1 Ghost & Dark Raid Attacker in Pokémon GO)",
                "Master League: S+ (Top 3 Master League)",
                "Shadow Claw + Moongeist Beam & Dark Pulse",
                "Level 40 → Level 50 (Max XL Priority #1)", "");
        reg("Palkia", "Water / Dragon", 280, 215, 189, "S+", true,
                "S+ (Origin Forme w/ Spacial Rend is #1 Non-Mega Dragon Raider)",
                "Master League: S+ (#1 Dragon in Master League)",
                "Dragon Breath + Spacial Rend* & Aqua Tail",
                "Level 40 → Level 50 (Max XL Priority)", "");
        reg("Origin Forme Palkia", "Water / Dragon", 286, 223, 189, "S+", true,
                "S+ (Top Dragon Raid Attacker w/ Spacial Rend)",
                "Master League: S+ (Rank #1–2 Master League)",
                "Dragon Breath + Spacial Rend* & Aqua Tail",
                "Level 40 → Level 50 (Max XL Priority)", "");
        reg("Dialga", "Steel / Dragon", 275, 211, 205, "S+", true,
                "S+ (Origin Forme w/ Roar of Time is Top Steel/Dragon Raider)",
                "Master League: S+ (Master League Staple)",
                "Dragon Breath + Roar of Time* / Iron Head",
                "Level 40 → Level 50 (Max XL Priority)", "");
        reg("Origin Forme Dialga", "Steel / Dragon", 270, 225, 205, "S+", true,
                "S+ (Top Dragon Raider w/ Roar of Time)",
                "Master League: S+ (Top 5 Master League)",
                "Dragon Breath + Roar of Time* & Iron Head",
                "Level 40 → Level 50 (Max XL Priority)", "");
        reg("Groudon", "Ground", 270, 228, 205, "S+", true,
                "S+ (Primal Groudon #1 Ground Raid Attacker in Game)",
                "Master League: S (Top Ground Closer)",
                "Mud Shot + Precipice Blades* & Fire Punch* (Elite TM)",
                "Level 40 → Level 50 (Max XL Priority)", "");
        reg("Kyogre", "Water", 270, 228, 205, "S+", true,
                "S+ (Primal Kyogre #1 Water Raid Attacker in Game)",
                "Master League: S (Top Water Bulk)",
                "Waterfall + Origin Pulse* & Surf",
                "Level 40 → Level 50 (Max XL Priority)", "");
        reg("Mewtwo", "Psychic", 300, 182, 214, "S", true,
                "S (#1 Psychic Raider; Shadow is S+)",
                "Master League: S",
                "Psycho Cut + Psystrike*",
                "Lv 40–50", "");
        reg("Metagross", "Steel / Psychic", 257, 228, 190, "S", true,
                "S (Top Steel Raider & Dynamax; Shadow is S+)",
                "Master Premier: S",
                "Bullet Punch + Meteor Mash*",
                "Lv 40–50", "");
        reg("Lucario", "Fighting / Steel", 236, 144, 172, "S", true,
                "S+ (Mega #1 Fighting Raider; Base A+)",
                "Ultra League: B",
                "Force Palm* + Aura Sphere",
                "Lv 40–50", "");
        reg("Terrakion", "Rock / Fighting", 260, 192, 209, "S+", true,
                "S+ (#1 Non-Mega Fighting Raider)",
                "Master League: B+",
                "Double Kick + Sacred Sword*",
                "Lv 40–50", "");
        reg("Kartana", "Grass / Steel", 323, 182, 139, "S+", true,
                "S+ (#1 Non-Mega Grass Raid DPS)",
                "PvP: C",
                "Razor Leaf + Leaf Blade",
                "Lv 40–50", "");
        reg("Rhyperior", "Ground / Rock", 241, 190, 251, "S", true,
                "S (Shadow Rhyperior is S+ #1 Rock Raider)",
                "Master League: S+",
                "Smack Down + Rock Wrecker* | Mud-Slap + Earthquake",
                "Lv 40–50", "");
        reg("Tyranitar", "Rock / Dark", 251, 207, 225, "A+", true,
                "A+ (Shadow S • Mega S+ #1 Dark & Rock Raider)",
                "Master Premier: A",
                "Bite + Brutal Swing | Smack Down* + Stone Edge",
                "Lv 40–50", "");
        reg("Garchomp", "Dragon / Ground", 261, 193, 239, "S", true,
                "S (Shadow & Mega S+ Ground/Dragon Raider)",
                "Master League: A+",
                "Mud Shot + Earth Power* | Dragon Tail + Outrage",
                "Lv 40–50", "");
        reg("Mamoswine", "Ice / Ground", 247, 146, 242, "A+", true,
                "A+ (Shadow S #1 Ice Raider)",
                "Master Premier: A+",
                "Powder Snow + Avalanche | Mud-Slap + High Horsepower",
                "Lv 40–50", "");
        reg("Reshiram", "Dragon / Fire", 275, 211, 205, "S", true,
                "S (#1 Non-Shadow Fire Raider)",
                "Master League: A+",
                "Fire Fang + Fusion Flare*",
                "Lv 40–50", "");
        reg("Zekrom", "Dragon / Electric", 275, 211, 205, "S", true,
                "S (Top Electric Raider)",
                "Master League: A+",
                "Charge Beam + Fusion Bolt*",
                "Lv 40–50", "");
        reg("Xurkitree", "Electric", 330, 144, 195, "S", true,
                "S (#1 Non-Shadow Electric Raid DPS)",
                "PvP: C",
                "Thunder Shock + Discharge",
                "Lv 40–50", "");
        reg("Landorus", "Ground / Flying", 289, 179, 205, "S", true,
                "S (Therian Top Ground Raider)",
                "Master League: S+",
                "Mud Shot + Sandsear Storm*",
                "Lv 40–50", "");
        reg("Zygarde", "Dragon / Ground", 184, 207, 389, "S", true,
                "Raid: C",
                "Master League: S+ (#1 Complete Tank)",
                "Dragon Tail + Outrage | Bulldoze + Earthquake",
                "Lv 50 (Master League)", "");
        reg("Ho-Oh", "Fire / Flying", 239, 244, 214, "S", true,
                "Raid: A (Apex Shadow S)",
                "Master League: S+ (Top ML Flyer)",
                "Incinerate + Sacred Fire*",
                "Lv 40–50", "");
        reg("Xerneas", "Fairy", 250, 185, 246, "S", true,
                "Raid: A+ (Top Fairy Raider)",
                "Master League: S",
                "Geomancy* + Moonblast & Close Combat",
                "Lv 40–50", "");
        reg("Zacian", "Fairy", 254, 236, 192, "S+", true,
                "Raid: A+ (Top Fairy Raider; Crowned Sword is S+ #1 Steel/Fairy)",
                "Master League: S+ (Rank #1–2 ML Meta Titan)",
                "Snarl / Quick Attack / Metal Claw + Play Rough & Close Combat / Wild Charge | Metal Claw + Behemoth Blade* / Iron Head",
                "Lv 40–50 (Max XL Priority)", "");
        reg("Crowned Sword Zacian", "Fairy / Steel", 332, 240, 192, "S+", true,
                "S+ (#1 Steel & Fairy Raid Attacker in Pokémon GO)",
                "Master League: S+ (#1 Master League Titan)",
                "Metal Claw + Behemoth Blade* / Iron Head | Snarl / Quick Attack + Play Rough & Close Combat / Wild Charge",
                "Lv 40–50 (Max XL Priority #1)", "");
        reg("Zamazenta", "Fighting", 254, 236, 192, "A+", true,
                "A+ (Strong Fighting Raider; Crowned Shield is S Tank)",
                "Master League: A+ (Anti-Steel/Dark Closer)",
                "Ice Fang / Snarl / Metal Claw + Close Combat & Crunch / Moonblast | Metal Claw + Behemoth Bash* / Iron Head",
                "Lv 40–50", "");
        reg("Crowned Shield Zamazenta", "Fighting / Steel", 250, 292, 192, "S", true,
                "S (Top Steel/Fighting Raid & Max Battle Tank)",
                "Master League: S (Elite Bulk Steel/Fighter)",
                "Metal Claw / Ice Fang + Behemoth Bash* & Close Combat / Crunch",
                "Lv 40–50", "");
        reg("Yveltal", "Dark / Flying", 250, 185, 246, "S", true,
                "Raid: A+ (Top Flying/Dark Raider)",
                "Master League: S+",
                "Gust* + Oblivion Wing* | Snarl + Dark Pulse",
                "Lv 40–50", "");
        reg("Giratina", "Ghost / Dragon", 225, 187, 284, "S", true,
                "Raid: A+ (Origin Ghost Raider)",
                "Ultra League: S • Master League: S",
                "Shadow Claw + Shadow Force*",
                "≤2,500 CP (UL) or Lv 50 (ML)", "");

        // =========================================================================
        // 2. S, A+ & A TIER: MEGA STARTERS, PSEUDO-LEGENDARIES & RAIDERS
        // =========================================================================
        reg("Gardevoir", "Psychic / Fairy", 237, 195, 169, "A+", true,
                "A+ (Shadow S • Mega S+ #1 Fairy Raider)",
                "PvP: B",
                "Charm + Dazzling Gleam | Confusion + Psychic",
                "Lv 40–50", "");
        reg("Salamence", "Dragon / Flying", 277, 168, 216, "A+", true,
                "A+ (Shadow & Mega S Dragon Raider)",
                "Master League: B",
                "Dragon Tail + Outrage* | Fly",
                "Lv 40–50", "");
        reg("Dragonite", "Dragon / Flying", 263, 198, 209, "A+", true,
                "A+ (Shadow S Dragon Raider)",
                "Master League: S",
                "Dragon Tail + Outrage",
                "Lv 40–50", "");
        reg("Hydreigon", "Dark / Dragon", 256, 188, 211, "A+", true,
                "A+ (Top Non-Shadow Dark Raider)",
                "Master Premier: A",
                "Bite + Brutal Swing*",
                "Lv 40–50", "");
        reg("Rampardos", "Rock", 295, 109, 219, "A+", true,
                "A+ (Shadow S Rock Glass Cannon)",
                "PvP: F",
                "Smack Down + Rock Slide",
                "Lv 35–40", "");
        reg("Chandelure", "Ghost / Fire", 271, 182, 155, "A+", true,
                "A+ (Shadow S Dual Fire & Ghost Raider)",
                "PvP: C",
                "Fire Spin + Overheat | Hex + Shadow Ball",
                "Lv 40", "");
        reg("Conkeldurr", "Fighting", 243, 158, 233, "A+", true,
                "A+ (Shadow S Fighting Raider)",
                "Master Premier: A",
                "Counter + Dynamic Punch",
                "Lv 40–50", "");
        reg("Machamp", "Fighting", 234, 159, 207, "A", true,
                "A (Shadow A+ Fighting Raider & Gigantamax)",
                "Great & Ultra League: A",
                "Counter + Dynamic Punch",
                "Lv 40 (or Lv 50 Shadow/G-Max)", "");
        reg("Excadrill", "Ground / Steel", 255, 129, 242, "A+", true,
                "A+ (Shadow S Ground Raider & #1 Dynamax)",
                "Master Premier: S",
                "Mud-Slap + Scorching Sands | Metal Claw + Iron Head",
                "Lv 40–50", "");
        reg("Swampert", "Water / Ground", 208, 175, 225, "A+", true,
                "A (Shadow A+ • Mega S+ Water Raider)",
                "Great & Ultra League: A+",
                "Water Gun + Hydro Cannon* | Mud Shot + Earthquake",
                "≤1500 GL / ≤2500 UL / Lv 40 Mega", "");
        reg("Sceptile", "Grass", 223, 169, 172, "A", true,
                "A (Mega S+ #1 Grass Raid DPS)",
                "Ultra League: B",
                "Bullet Seed + Frenzy Plant*",
                "Lv 40 (Mega)", "");
        reg("Blaziken", "Fire / Fighting", 240, 141, 190, "A", true,
                "A (Shadow A+ • Mega S+ Fire/Fighting Raider)",
                "Great & Ultra League: B+",
                "Fire Spin + Blast Burn* | Counter + Aura Sphere",
                "Lv 40 (Mega)", "");
        reg("Charizard", "Fire / Flying", 223, 173, 186, "A", true,
                "A (Shadow A+ • Mega Y & G-Max S+ Fire Raider)",
                "Ultra League: A • Great League: B+",
                "Fire Spin + Blast Burn* | Air Slash + Air Cutter",
                "Lv 40–50 (Mega/Max)", "");
        reg("Venusaur", "Grass / Poison", 198, 189, 190, "A", true,
                "A (Mega & G-Max A+ Grass Raider)",
                "Great & Ultra League: A",
                "Vine Whip + Frenzy Plant* | Poison Jab + Sludge Bomb",
                "≤1500 GL / ≤2500 UL / Lv 40 Mega", "");
        reg("Blastoise", "Water", 171, 207, 188, "B+", true,
                "B+ (Mega A & Dynamax Water Tank)",
                "Ultra League: A",
                "Water Gun + Hydro Cannon*",
                "≤2500 UL or Lv 40 (Mega/Max)", "");
        reg("Gengar", "Ghost / Poison", 261, 149, 155, "A+", true,
                "A+ (Shadow S • Mega & G-Max S+ Ghost DPS)",
                "Great & Ultra League: B",
                "Shadow Claw + Shadow Ball | Poison Jab + Sludge Bomb",
                "Lv 40–50 (Mega/Max)", "");
        reg("Gyarados", "Water / Flying", 237, 186, 216, "A", true,
                "A (Shadow A+ • Mega A+ Water/Dark Raider)",
                "Master Premier: S • Ultra League: A",
                "Waterfall + Hydro Pump | Bite + Crunch",
                "Lv 40–50", "");
        reg("Darmanitan", "Fire", 263, 114, 233, "A", true,
                "A (Shadow A+ Fire Raid DPS)",
                "PvP: D",
                "Fire Fang + Overheat",
                "Lv 35–40", "");
        reg("Weavile", "Dark / Ice", 243, 171, 172, "A", true,
                "A (Shadow A+ Ice & Dark Raider)",
                "PvP: C",
                "Ice Shard + Avalanche | Snarl + Foul Play",
                "Lv 35–40", "");
        reg("Baxcalibur", "Dragon / Ice", 254, 168, 251, "A+", true,
                "A+ (Top Non-Shadow Ice Raider)",
                "Master Premier: S",
                "Ice Fang + Avalanche | Dragon Breath + Outrage",
                "Lv 40–50", "");
        reg("Ursaluna", "Ground / Normal", 243, 181, 277, "A", true,
                "A (Shadow A+ Ground Raider)",
                "Master Premier: A+",
                "Tackle + High Horsepower*",
                "Lv 40–50", "");
        reg("Annihilape", "Fighting / Ghost", 220, 178, 242, "A+", true,
                "Raid: B+",
                "Great, Ultra & Master Premier: S",
                "Counter + Close Combat | Low Kick + Shadow Ball",
                "≤1500 GL / ≤2500 UL / Lv 50 ML", "");
        reg("Feraligatr", "Water", 205, 188, 198, "A+", true,
                "Raid: B+ (Shadow A+ Water Raider)",
                "Great & Ultra League: S+ (#1 Meta)",
                "Water Gun + Hydro Cannon*",
                "≤1500 GL or ≤2500 UL", "");

        // =========================================================================
        // 3. GREAT LEAGUE (1500 CP) & ULTRA LEAGUE (2500 CP) PVP META KINGS + GYM TANKS
        // =========================================================================
        reg("Clodsire", "Poison / Ground", 128, 151, 277, "A+", true,
                "Raid: F",
                "Great League: S+ (#1 GL Tank)",
                "Poison Jab + Sludge Bomb | Mud Shot + Earthquake",
                "≤1,500 CP (Great League)", "");
        reg("Azumarill", "Water / Fairy", 112, 152, 225, "A+", true,
                "Raid: F",
                "Great League: S+ (Top Bulk Staple)",
                "Bubble + Hydro Pump | Play Rough",
                "≤1,500 CP (Great League)", "");
        reg("Mandibuzz", "Dark / Flying", 129, 205, 242, "A+", true,
                "Raid: F",
                "Great League: S+ • Ultra League: S",
                "Snarl + Foul Play | Air Slash + Aerial Ace",
                "≤1,500 CP (GL) or Lv 50 (UL)", "");
        reg("Bastiodon", "Rock / Steel", 94, 286, 155, "A", true,
                "Raid: F",
                "Great League: S (#1 Defense Wall)",
                "Smack Down + Stone Edge",
                "≤1,500 CP (Great League)", "");
        reg("Carbink", "Rock / Fairy", 95, 285, 137, "A", true,
                "Raid: F",
                "Great League: S (Rock/Fairy Tank)",
                "Rock Throw + Rock Slide",
                "≤1,500 CP (Great League)", "");
        reg("Galarian Stunfisk", "Ground / Steel", 144, 171, 240, "A", true,
                "Raid: F",
                "Great & Ultra League: A+",
                "Mud Shot + Earthquake | Metal Claw + Flash Cannon",
                "≤1,500 CP (GL) or Lv 50 (UL)", "");
        reg("Stunfisk", "Ground / Electric", 144, 171, 240, "A", true,
                "Raid: F",
                "Great & Ultra League: A",
                "Thunder Shock + Discharge | Mud Shot + Mud Bomb",
                "≤1,500 CP (GL) or Lv 50 (UL)", "");
        reg("Sableye", "Dark / Ghost", 141, 136, 137, "A", true,
                "Raid: D",
                "Great League: S (Purified Return)",
                "Feint Attack + Foul Play | Shadow Claw + Shadow Sneak",
                "≤1,500 CP (Great League)", "");
        reg("Lickitung", "Normal", 108, 137, 207, "A", true,
                "Raid: F",
                "Great League: A+",
                "Lick + Body Slam*",
                "≤1,500 CP (Great League)", "");
        reg("Cresselia", "Psychic", 152, 258, 260, "A+", true,
                "Raid: D",
                "Great League: S • Ultra League: S+",
                "Confusion + Future Sight",
                "≤1,500 CP (GL) or ≤2,500 CP (UL)", "");
        reg("Talonflame", "Fire / Flying", 176, 155, 186, "A+", true,
                "Raid: C",
                "Great League: A+ • Ultra League: S",
                "Incinerate* + Flame Charge | Peck + Brave Bird",
                "≤1,500 CP (GL) or Lv 50 (UL)", "");
        reg("Umbreon", "Dark", 126, 240, 216, "A", true,
                "Raid: F",
                "Great League: A+ • Ultra League: S",
                "Snarl + Foul Play",
                "≤1,500 CP (GL) or Lv 50 (UL)", "");
        reg("Sylveon", "Fairy", 203, 205, 216, "A", true,
                "Raid: B+ (Budget Fairy)",
                "Master Premier: A • Ultra League: B+",
                "Charm + Dazzling Gleam",
                "Lv 35–40 or ≤2,500 CP (UL)", "");
        reg("Blissey", "Normal", 129, 169, 496, "S", true,
                "S (#1 Gym Defender & #1 Max Battle Healer)",
                "Gym & Max Healer: S+",
                "Pound + Hyper Beam",
                "Lv 40–50 (Gym & Max Battles)", "");
        reg("Chansey", "Normal", 60, 128, 487, "A", true,
                "Evolves into Blissey (#1 Gym & Max Tank)",
                "Gym Defense: A+",
                "Pound + Hyper Beam",
                "Evolve to Blissey → Lv 40", "Blissey");
        reg("Snorlax", "Normal", 190, 169, 330, "A", true,
                "Gym Defense: A+",
                "Master Premier: A+ • Ultra League: A",
                "Lick + Body Slam",
                "≤2,500 CP (UL) or Lv 40 (Gym)", "");
        reg("Lapras", "Water / Ice", 165, 174, 277, "A", true,
                "Raid: C (G-Max Ice Tank)",
                "Great & Ultra League: A",
                "Water Gun + Hydro Pump | Frost Breath + Ice Beam*",
                "≤1,500 CP (GL) or ≤2,500 CP (UL)", "");

        // =========================================================================
        // 4. PRE-EVOLUTIONS, EVENT SPAWNS & FIELD RESEARCH REWARD POKÉMON
        // =========================================================================
        reg("Beldum", "Steel / Psychic", 96, 132, 120, "S", true,
                "Evolves into Metagross (S Steel Raider; Shadow S+)",
                "Master Premier: S",
                "Evolve to Metagross -> Bullet Punch + Meteor Mash*",
                "Evolve to Metagross → Lv 40–50", "Metagross");
        reg("Metang", "Steel / Psychic", 138, 176, 155, "S", true,
                "Evolves into Metagross (S Steel Raider; Shadow S+)",
                "Master Premier: S",
                "Evolve to Metagross -> Bullet Punch + Meteor Mash*",
                "Evolve to Metagross → Lv 40–50", "Metagross");
        reg("Larvitar", "Rock / Ground", 115, 93, 137, "A+", true,
                "Evolves into Tyranitar (Shadow S / Mega S+ Raider)",
                "Master Premier: A",
                "Evolve to Tyranitar -> Bite + Brutal Swing | Smack Down* + Stone Edge",
                "Evolve to Tyranitar → Lv 40–50", "Tyranitar");
        reg("Pupitar", "Rock / Ground", 155, 133, 172, "A+", true,
                "Evolves into Tyranitar (Shadow S / Mega S+ Raider)",
                "Master Premier: A",
                "Evolve to Tyranitar -> Bite + Brutal Swing | Smack Down* + Stone Edge",
                "Evolve to Tyranitar → Lv 40–50", "Tyranitar");
        reg("Gible", "Dragon / Ground", 124, 84, 151, "S", true,
                "Evolves into Garchomp (Shadow/Mega S+ Raider)",
                "Master League: A+",
                "Evolve to Garchomp -> Mud Shot + Earth Power* | Dragon Tail + Outrage",
                "Evolve to Garchomp → Lv 40–50", "Garchomp");
        reg("Gabite", "Dragon / Ground", 172, 125, 169, "S", true,
                "Evolves into Garchomp (Shadow/Mega S+ Raider)",
                "Master League: A+",
                "Evolve to Garchomp -> Mud Shot + Earth Power* | Dragon Tail + Outrage",
                "Evolve to Garchomp → Lv 40–50", "Garchomp");
        reg("Dratini", "Dragon", 119, 91, 121, "A+", true,
                "Evolves into Dragonite (Shadow S Raider & ML S)",
                "Master League: S",
                "Evolve to Dragonite -> Dragon Tail + Outrage",
                "Evolve to Dragonite → Lv 40–50", "Dragonite");
        reg("Dragonair", "Dragon", 163, 135, 156, "A+", true,
                "Evolves into Dragonite (Shadow S Raider & ML S)",
                "Great League: A",
                "Dragon Breath + Aqua Tail",
                "≤1,500 CP (GL) or Evolve → Lv 40–50", "Dragonite");
        reg("Bagon", "Dragon", 134, 93, 128, "A+", true,
                "Evolves into Salamence (Shadow/Mega S Dragon Raider)",
                "Master League: B",
                "Evolve to Salamence -> Dragon Tail + Outrage*",
                "Evolve to Salamence → Lv 40–50", "Salamence");
        reg("Axew", "Dragon", 147, 101, 130, "A", true,
                "Evolves into Haxorus (A+ Dragon Raider)",
                "Master Premier: A+",
                "Evolve to Haxorus -> Dragon Tail + Breaking Swipe*",
                "Evolve to Haxorus → Lv 40", "Haxorus");
        reg("Haxorus", "Dragon", 284, 172, 183, "A", true,
                "A (High-ATK Dragon Raider)",
                "Master Premier: A+",
                "Dragon Tail + Breaking Swipe*",
                "Lv 40–50", "");
        reg("Ralts", "Psychic / Fairy", 79, 59, 99, "A+", true,
                "Evolves into Gardevoir (Shadow S / Mega S+ Fairy Raider)",
                "PvP: B+",
                "Evolve to Gardevoir -> Charm + Dazzling Gleam",
                "Evolve to Gardevoir → Lv 40–50", "Gardevoir");
        reg("Machop", "Fighting", 137, 82, 172, "A", true,
                "Evolves into Machamp (A Raider; Shadow is A+)",
                "Great & Ultra League: A",
                "Evolve to Machamp -> Counter + Dynamic Punch",
                "Evolve to Machamp → Lv 40–50", "Machamp");
        reg("Machoke", "Fighting", 177, 125, 190, "A", true,
                "Evolves into Machamp (A Raider; Shadow is A+)",
                "Great & Ultra League: A",
                "Evolve to Machamp -> Counter + Dynamic Punch",
                "Evolve to Machamp → Lv 40–50", "Machamp");
        reg("Gastly", "Ghost / Poison", 186, 67, 102, "A+", true,
                "Evolves into Gengar (Mega & G-Max S+ Ghost DPS)",
                "Great & Ultra League: B",
                "Evolve to Gengar -> Shadow Claw + Shadow Ball",
                "Evolve to Gengar → Lv 40–50", "Gengar");
        reg("Haunter", "Ghost / Poison", 223, 107, 128, "A+", true,
                "Evolves into Gengar (Mega & G-Max S+ Ghost DPS)",
                "Great League: B+",
                "Evolve to Gengar -> Shadow Claw + Shadow Ball",
                "Evolve to Gengar → Lv 40–50", "Gengar");
        reg("Swinub", "Ice / Ground", 90, 69, 137, "A+", true,
                "Evolves into Mamoswine (A+ Raider; Shadow is S #1 Ice)",
                "Master Premier: A+",
                "Evolve to Mamoswine -> Powder Snow + Avalanche",
                "Evolve to Mamoswine → Lv 40–50", "Mamoswine");
        reg("Rhyhorn", "Ground / Rock", 140, 127, 190, "S", true,
                "Evolves into Rhyperior (S Raider; Shadow is S+)",
                "Master League: S+",
                "Evolve to Rhyperior -> Smack Down + Rock Wrecker*",
                "Evolve to Rhyperior → Lv 40–50", "Rhyperior");
        reg("Cranidos", "Rock", 218, 71, 167, "A+", true,
                "Evolves into Rampardos (A+ Rock DPS; Shadow is S)",
                "PvP: F",
                "Evolve to Rampardos -> Smack Down + Rock Slide",
                "Evolve to Rampardos → Lv 35–40", "Rampardos");
        reg("Shieldon", "Rock / Steel", 76, 195, 102, "A", true,
                "Raid: F",
                "Evolves into Bastiodon (S Great League Wall)",
                "Evolve to Bastiodon -> Smack Down + Stone Edge",
                "Evolve to Bastiodon → ≤1,500 CP (GL)", "Bastiodon");
        reg("Applin", "Grass / Dragon", 85, 116, 120, "A", true,
                "Evolves into Hydrapple, Flapple, or Appletun",
                "Hydrapple & Appletun: A-Tier UL/ML",
                "Evolve to Hydrapple -> Dragon Breath + Outrage",
                "Evolve to Hydrapple (Lv 40) or Appletun (≤2,500 CP)", "Hydrapple");
        reg("Hydrapple", "Grass / Dragon", 216, 205, 235, "A", true,
                "A (Solid Grass/Dragon Attacker)",
                "Ultra & Master Premier: A+",
                "Bullet Seed + Seed Bomb | Dragon Breath + Outrage",
                "≤2,500 CP (UL) or Lv 40–50 (ML)", "");
        reg("Flapple", "Grass / Dragon", 214, 144, 172, "B+", false,
                "B+ (Budget Grass/Dragon)",
                "Great/Ultra Spice: B",
                "Bullet Seed + Seed Bomb | Dragon Breath + Outrage",
                "Lv 30–35 Only if Needed", "");
        reg("Appletun", "Grass / Dragon", 178, 146, 242, "A", true,
                "Raid: C",
                "Great & Ultra League: A",
                "Bullet Seed + Seed Bomb | Dragon Tail + Outrage",
                "≤1,500 CP (GL) or ≤2,500 CP (UL)", "");
        reg("Smoliv", "Grass / Normal", 92, 90, 121, "C", false,
                "Evolves into Arboliva (Budget Grass)",
                "Ultra League Spice: B-",
                "Evolve to Arboliva -> Magical Leaf + Seed Bomb",
                "Skip Power-Up (Dex / Shiny Only)", "Arboliva");
        reg("Arboliva", "Grass / Normal", 200, 189, 186, "B", false,
                "B (Budget Grass Filler)",
                "Ultra League: B",
                "Magical Leaf + Seed Bomb",
                "Skip Power-Up (Save Stardust)", "");
        reg("Foongus", "Grass / Poison", 97, 91, 170, "C", false,
                "Raid: F (+500 Catch Stardust)",
                "Little Cup: B",
                "Astonish + Grass Knot",
                "Skip Power-Up (+500 Stardust Catch)", "");
        reg("Skwovet", "Normal", 95, 86, 172, "B+", true,
                "Evolves into Greedent (Dynamax & UL Tank)",
                "Ultra League: A (Greedent)",
                "Evolve to Greedent -> Tackle + Body Slam",
                "Evolve to Greedent → ≤2,500 CP (UL)", "Greedent");
        reg("Greedent", "Normal", 160, 156, 260, "A", true,
                "Max Battle Tank: A",
                "Ultra League: A • Great League: A-",
                "Tackle + Body Slam",
                "≤1,500 CP (GL) or ≤2,500 CP (UL)", "");
        reg("Marill", "Water / Fairy", 37, 93, 172, "A+", true,
                "Raid: F",
                "Evolves into Azumarill (S+ Great League)",
                "Evolve to Azumarill -> Bubble + Play Rough",
                "Evolve to Azumarill → ≤1,500 CP (GL)", "Azumarill");
        reg("Darumaka", "Fire", 153, 86, 172, "A", true,
                "Evolves into Darmanitan (A Fire Raider; Shadow A+)",
                "PvP: D",
                "Evolve to Darmanitan -> Fire Fang + Overheat",
                "Evolve to Darmanitan → Lv 35–40", "Darmanitan");
        reg("Magikarp", "Water", 29, 85, 85, "A", true,
                "Evolves into Gyarados (Mega & ML Premier)",
                "Master Premier: S (Gyarados)",
                "Evolve to Gyarados -> Waterfall + Hydro Pump",
                "Evolve to Gyarados → Lv 40–50", "Gyarados");
        reg("Wimpod", "Bug / Water", 46, 80, 93, "A", true,
                "Evolves into Golisopod (UL & ML Premier)",
                "Ultra League: A+ (Golisopod)",
                "Evolve to Golisopod -> Waterfall + Liquidation",
                "Evolve to Golisopod → ≤2,500 CP (UL)", "Golisopod");
        reg("Golisopod", "Bug / Water", 218, 226, 181, "A", true,
                "Raid: B",
                "Ultra League: A+ • Master Premier: A",
                "Waterfall + Liquidation | Fury Cutter + X-Scissor",
                "≤2,500 CP (UL) or Lv 40–50 (ML)", "");
        reg("Stufful", "Normal / Fighting", 136, 95, 172, "B", false,
                "Evolves into Bewear (B Fighter)",
                "Ultra/Master Spice: B",
                "Low Kick + Superpower",
                "Skip Power-Up (Dex / Shiny Only)", "Bewear");
        reg("Vullaby", "Dark / Flying", 105, 139, 172, "A+", true,
                "Raid: F",
                "Evolves into Mandibuzz (S+ GL & UL Tank)",
                "Evolve to Mandibuzz -> Snarl + Foul Play",
                "Evolve to Mandibuzz → ≤1,500 CP (GL) or Lv 50 (UL)", "Mandibuzz");
        reg("Scraggy", "Dark / Fighting", 132, 132, 137, "B+", true,
                "Raid: D",
                "Evolves into Scrafty (A-Tier GL & UL)",
                "Evolve to Scrafty -> Counter + Dynamic Punch",
                "Evolve to Scrafty → ≤1,500 CP (GL) or ≤2,500 CP (UL)", "Scrafty");
        reg("Aerodactyl", "Rock / Flying", 221, 159, 190, "A", true,
                "A (Mega Aerodactyl Rock Raider)",
                "Flying Cup: S",
                "Rock Throw + Rock Slide",
                "Lv 35–40 (Mega)", "");
        reg("Alolan Marowak", "Fire / Ghost", 144, 186, 155, "A", true,
                "Raid: D",
                "Great League: A",
                "Fire Spin + Flame Wheel | Hex + Shadow Bone*",
                "≤1,500 CP (Great League)", "");
        reg("Sneasel", "Dark / Ice", 189, 146, 146, "A", true,
                "Evolves into Weavile (A Raider; Shadow A+)",
                "Sneasler: A in UL/ML",
                "Evolve to Weavile -> Ice Shard + Avalanche | Snarl + Foul Play",
                "Evolve to Weavile → Lv 35–40", "Weavile");
        reg("Mawile", "Steel / Fairy", 155, 141, 137, "B", false,
                "Mega Mawile (XL Booster)",
                "Great League Spice: B+",
                "Fairy Wind + Play Rough",
                "≤1,500 CP (GL) Only", "");
        reg("Feebas", "Water", 29, 85, 85, "B+", false,
                "Evolves into Milotic (Gym & ML Spice)",
                "Master Premier: B+",
                "Evolve to Milotic -> Waterfall + Surf",
                "Evolve to Milotic → Lv 35–40", "Milotic");
        reg("Eevee", "Normal", 104, 114, 146, "A", true,
                "Evolves into Sylveon, Glaceon, Espeon, or Umbreon",
                "Umbreon: S GL/UL • Sylveon: A ML",
                "Sylveon (Charm + Dazzling Gleam) | Glaceon (Ice Shard + Avalanche)",
                "Umbreon (≤1500 GL / Lv50 UL) or Sylveon/Glaceon (Lv 35–40)", "");
        reg("Spinda", "Normal", 116, 116, 155, "C", false,
                "Raid: F",
                "Great League Spice: C",
                "Tackle + Hyper Beam",
                "Skip Power-Up (Collector Trophy)", "");
        reg("Bidoof", "Normal", 80, 73, 153, "F", false,
                "Raid: F",
                "Little Cup: C",
                "Tackle + Hyper Fang",
                "Skip Power-Up (Transfer)", "");
        reg("Bunnelby", "Normal", 68, 72, 116, "A", true,
                "Raid: F",
                "Evolves into Diggersby (S Great League Tank)",
                "Evolve to Diggersby -> Mud Shot + Scorching Sands",
                "Evolve to Diggersby → ≤1,500 CP (GL)", "Diggersby");
        reg("Diggersby", "Normal / Ground", 112, 155, 198, "A+", true,
                "Raid: F",
                "Great League: S (Top GL Ground Tank)",
                "Mud Shot + Scorching Sands | Quick Attack + Hyper Beam",
                "≤1,500 CP (Great League)", "");
        reg("Bulbasaur", "Grass / Poison", 118, 111, 128, "A", true,
                "Evolves into Venusaur (Mega/G-Max Grass Raider)",
                "Great & Ultra League: A (Venusaur)",
                "Evolve to Venusaur -> Vine Whip + Frenzy Plant*",
                "Evolve to Venusaur → ≤1500 GL / ≤2500 UL / Lv 40", "Venusaur");
        reg("Charmander", "Fire", 116, 93, 118, "A", true,
                "Evolves into Charizard (Mega Y & G-Max Fire Raider)",
                "Ultra League: A (Charizard)",
                "Evolve to Charizard -> Fire Spin + Blast Burn*",
                "Evolve to Charizard → Lv 40–50 or ≤2500 UL", "Charizard");
        reg("Squirtle", "Water", 94, 121, 127, "B+", true,
                "Evolves into Blastoise (Mega & Dynamax Water Tank)",
                "Ultra League: A (Blastoise)",
                "Evolve to Blastoise -> Water Gun + Hydro Cannon*",
                "Evolve to Blastoise → ≤2500 UL or Lv 40", "Blastoise");
        reg("Mankey", "Fighting", 148, 82, 120, "A+", true,
                "Evolves into Annihilape (Raid B+ • PvP S)",
                "Great, Ultra & Master Premier: S (Annihilape)",
                "Evolve to Annihilape -> Counter + Rage Fist* & Close Combat",
                "Evolve to Annihilape → ≤1500 GL / ≤2500 UL / Lv 50 ML", "Annihilape");
        reg("Electabuzz", "Electric", 198, 158, 163, "A", true,
                "Evolves into Electivire (A Electric Raider; Shadow A+)",
                "Great/Ultra Spice: B",
                "Evolve to Electivire -> Thunder Shock + Wild Charge",
                "Evolve to Electivire → Lv 40", "Electivire");
        reg("Swablu", "Normal / Flying", 76, 132, 128, "A", true,
                "Evolves into Mega Altaria (Dragon/Fairy Mega)",
                "Great League: A (Altaria)",
                "Evolve to Altaria -> Dragon Breath + Sky Attack & Moonblast*",
                "Evolve to Altaria → ≤1,500 CP (GL)", "Altaria");
        reg("Teddiursa", "Normal", 142, 93, 155, "A", true,
                "Evolves into Ursaluna (A Ground Raider; Shadow A+)",
                "Master Premier: A+ (Ursaluna)",
                "Evolve to Ursaluna -> Tackle + High Horsepower*",
                "Evolve to Ursaluna → Lv 40–50", "Ursaluna");
        reg("Ursaring", "Normal", 236, 144, 207, "A", true,
                "Evolves into Ursaluna (A Ground Raider; Shadow A+)",
                "Master Premier: A+ (Ursaluna)",
                "Evolve to Ursaluna -> Tackle + High Horsepower*",
                "Evolve to Ursaluna → Lv 40–50", "Ursaluna");
        reg("Poliwag", "Water", 101, 82, 120, "A", true,
                "Evolves into Poliwrath (Counter + Scald PvP Meta)",
                "Great & Ultra League: A (Poliwrath)",
                "Evolve to Poliwrath -> Counter* + Icy Wind & Scald",
                "Evolve to Poliwrath → ≤1500 GL or ≤2500 UL", "Poliwrath");
        reg("Poliwhirl", "Water", 130, 123, 163, "A", true,
                "Evolves into Poliwrath (Counter + Scald PvP Meta)",
                "Great & Ultra League: A (Poliwrath)",
                "Evolve to Poliwrath -> Counter* + Icy Wind & Scald",
                "Evolve to Poliwrath → ≤1500 GL or ≤2500 UL", "Poliwrath");
        reg("Wingull", "Water / Flying", 106, 61, 120, "A", true,
                "Raid: F",
                "Great League: A (Pelipper Weather Ball Spam)",
                "Evolve to Pelipper -> Wing Attack + Weather Ball Water & Hurricane",
                "Evolve to Pelipper → ≤1,500 CP (GL)", "Pelipper");
        reg("Galarian Zigzagoon", "Dark / Normal", 58, 80, 116, "A", true,
                "Raid: F",
                "Great & Ultra League: A (Obstagoon)",
                "Evolve to Obstagoon -> Counter + Night Slash & Cross Chop",
                "Evolve to Obstagoon → ≤1500 GL or ≤2500 UL", "Obstagoon");
        reg("Alolan Sandshrew", "Ice / Steel", 125, 129, 137, "A+", true,
                "Raid: F",
                "Great & Ultra League: A+ (Alolan Sandslash)",
                "Evolve to Alolan Sandslash -> Shadow Claw* / Powder Snow + Ice Punch & Drill Run",
                "Evolve to Alolan Sandslash → ≤1500 GL or ≤2500 UL", "Alolan Sandslash");
        reg("Dunsparce", "Normal", 131, 128, 225, "A+", true,
                "Raid: F",
                "Great League: S (Top Rollout + Drill Run Safe Swap)",
                "Rollout + Drill Run & Rock Slide",
                "≤1,500 CP (Great League)", "");
        reg("Scyther", "Bug / Flying", 218, 170, 172, "A", true,
                "Evolves into Mega Scizor (A+ Bug/Steel Raider)",
                "Ultra League: B+ (Scizor)",
                "Evolve to Scizor -> Bullet Punch + Iron Head | Fury Cutter + X-Scissor",
                "Evolve to Scizor → Lv 40 (Mega)", "Scizor");
        reg("Croagunk", "Poison / Fighting", 116, 76, 134, "A", true,
                "Raid: C",
                "Great & Ultra League: A (Toxicroak)",
                "Evolve to Toxicroak -> Counter + Mud Bomb & Shadow Ball",
                "Evolve to Toxicroak → ≤1500 GL or ≤2500 UL", "Toxicroak");
        reg("Cottonee", "Grass / Fairy", 71, 111, 120, "A", true,
                "Raid: F",
                "Great League & Little Cup: A (Whimsicott)",
                "Evolve to Whimsicott -> Fairy Wind / Charm + Seed Bomb & Moonblast",
                "Evolve to Whimsicott → ≤1,500 CP (GL)", "Whimsicott");
        reg("Snivy", "Grass", 88, 107, 128, "A+", true,
                "Raid: C",
                "Great League: S (Serperior Frenzy Plant* Tank)",
                "Evolve to Serperior -> Vine Whip + Frenzy Plant* & Aerial Ace",
                "Evolve to Serperior → ≤1,500 CP (GL)", "Serperior");
        reg("Rowlet", "Grass / Flying", 102, 99, 169, "A", true,
                "Evolves into Decidueye (A Grass Raider w/ Frenzy Plant*)",
                "Great & Ultra League: A- (Decidueye)",
                "Evolve to Decidueye -> Magical Leaf + Frenzy Plant* & Spirit Shackle",
                "Evolve to Decidueye → ≤1500 GL or Lv 40", "Decidueye");
        reg("Litten", "Fire", 128, 79, 128, "A", true,
                "Evolves into Incineroar (A Fire/Dark w/ Blast Burn*)",
                "Ultra League: A (Incineroar)",
                "Evolve to Incineroar -> Snarl + Blast Burn* & Darkest Lariat*",
                "Evolve to Incineroar → ≤2500 UL or Lv 40", "Incineroar");
        reg("Popplio", "Water", 120, 103, 137, "A+", true,
                "Evolves into Primarina (A+ Fairy/Water Raider)",
                "Master Premier: S • Great/Ultra League: A+ (Primarina)",
                "Evolve to Primarina -> Charm / Waterfall + Hydro Cannon* & Moonblast",
                "Evolve to Primarina → Lv 40–50 or ≤2500 UL", "Primarina");
        reg("Clefairy", "Fairy", 107, 108, 172, "A", true,
                "Raid: F",
                "Great & Ultra League: A (Clefable Fairy Wind + Meteor Mash)",
                "Evolve to Clefable -> Fairy Wind + Meteor Mash & Moonblast",
                "Evolve to Clefable → ≤1500 GL or ≤2500 UL", "Clefable");
        reg("Jigglypuff", "Normal / Fairy", 80, 41, 251, "A", true,
                "Raid: F",
                "Great League: A (Wigglytuff Charm + Icy Wind)",
                "Evolve to Wigglytuff -> Charm + Icy Wind & Swift",
                "Evolve to Wigglytuff → ≤1,500 CP (GL)", "Wigglytuff");
        reg("Togetic", "Fairy / Flying", 139, 181, 146, "A+", true,
                "Evolves into Togekiss (A+ Fairy Raider)",
                "Master League: A+ (Togekiss) • Great League: A (Togetic)",
                "Evolve to Togekiss -> Charm + Dazzling Gleam & Aura Sphere*",
                "Evolve to Togekiss → Lv 40–50", "Togekiss");
        reg("Kadabra", "Psychic", 232, 117, 120, "A", true,
                "Evolves into Alakazam (A Psychic Raider; Mega S)",
                "Great League Spice: B",
                "Evolve to Alakazam -> Psycho Cut + Psychic* / Shadow Ball",
                "Evolve to Alakazam → Lv 40 (Mega)", "Alakazam");
        reg("Lileep", "Rock / Grass", 105, 150, 165, "A", true,
                "Raid: F",
                "Great & Ultra League: A (Cradily Rock Slide + Grass Knot)",
                "Evolve to Cradily -> Bullet Seed + Rock Slide & Grass Knot",
                "Evolve to Cradily → ≤1500 GL or Lv 50 UL", "Cradily");

        // Pokebattler Estimator 'Best of Raids (Tier 5 & Mega)' + Max Battle Usefulness + PvP Usefulness Additions
        reg("Black Kyurem", "Dragon / Ice", 310, 183, 245, "S+", true,
                "S+ (#1 Non-Mega Raid Attacker & #1 Ice/Dragon)",
                "Master League: S+ (#1 ML Titan)",
                "Dragon Tail + Outrage | Freeze Shock*",
                "Lv 50 (Max Priority #1)", "");
        reg("White Kyurem", "Dragon / Ice", 310, 183, 245, "S+", true,
                "S+ (#1 Ice Raid Attacker w/ Ice Burn*)",
                "Master League: S+ (Top 3 ML)",
                "Ice Fang + Ice Burn* | Dragon Breath + Dragon Pulse",
                "Lv 50 (Max Priority #1)", "");
        reg("Kyurem", "Dragon / Ice", 246, 170, 245, "A+", true,
                "A+ (Ice/Dragon Raider; Fuses into Black/White Kyurem)",
                "Master League: A+",
                "Dragon Breath + Glaciate*",
                "Lv 40–50 (Save for Fusion)", "");
        reg("Keldeo", "Water / Fighting", 260, 192, 209, "S", true,
                "S (#2 Non-Mega Fighting Raider)",
                "Master League: A+",
                "Low Kick + Sacred Sword* | Poison Jab + Hydro Pump",
                "Lv 40–50", "");
        reg("Enamorus", "Fairy / Flying", 281, 162, 179, "S", true,
                "S (#1 Non-Mega Fairy Raider)",
                "Master League: A",
                "Fairy Wind + Dazzling Gleam | Astonish + Fly",
                "Lv 40–50", "");
        reg("Volcarona", "Bug / Fire", 264, 189, 198, "S", true,
                "S (#1 Non-Mega Bug & Top Fire Raider)",
                "Master Premier: A",
                "Fire Spin + Overheat | Bug Bite + Bug Buzz",
                "Lv 40–50", "");
        reg("Electivire", "Electric", 249, 163, 181, "A", true,
                "A (Shadow A+ Electric Raid Attacker)",
                "Great/Ultra Spice: B",
                "Thunder Shock + Wild Charge",
                "Lv 40 (Lv 50 if Shadow)", "");
        reg("Magnezone", "Electric / Steel", 238, 205, 172, "A", true,
                "A (Shadow A+ Electric Raid Attacker)",
                "Master Premier & UL: A",
                "Spark + Wild Charge | Metal Sound + Mirror Shot",
                "Lv 40–50", "");
        reg("Roserade", "Grass / Poison", 243, 185, 155, "A", true,
                "A (Shadow A+ Poison & Grass Raider)",
                "Ultra Premier: B+",
                "Poison Jab + Sludge Bomb | Magical Leaf* + Grass Knot",
                "Lv 35–40", "");
        reg("Honchkrow", "Dark / Flying", 243, 103, 225, "B+", true,
                "B+ (Shadow A Flying & Dark Raider)",
                "Ultra Spice: B",
                "Peck + Sky Attack | Snarl + Dark Pulse",
                "Lv 35–40 (Shadow)", "");
        reg("Nihilego", "Rock / Poison", 249, 210, 240, "S", true,
                "S (#1 Non-Mega Poison Raider)",
                "Master League: A",
                "Poison Jab + Sludge Bomb | Pound + Rock Slide",
                "Lv 40–50", "");
        reg("Naganadel", "Poison / Dragon", 263, 159, 177, "S", true,
                "S (Top-2 Non-Mega Poison Raid DPS)",
                "Master Spice: B+",
                "Poison Jab + Sludge Bomb | Dragon Tail + Dragon Pulse",
                "Lv 40", "");
        reg("Overqwil", "Dark / Poison", 222, 171, 198, "B+", false,
                "B+ (Poison/Dark Raider)",
                "Great & Ultra League: A-",
                "Poison Jab + Sludge Bomb | Bite + Dark Pulse",
                "≤1,500 CP (GL) or ≤2,500 CP (UL)", "");
        reg("Tyrantrum", "Rock / Dragon", 227, 191, 193, "A", true,
                "A (Shadow A+ Rock Raider w/ Meteor Beam)",
                "Master Premier: B+",
                "Rock Throw + Meteor Beam | Dragon Tail + Outrage",
                "Lv 35–40", "");
        reg("Kingambit", "Dark / Steel", 238, 203, 225, "S", true,
                "A+ (Dark/Steel Raider)",
                "Master League & Premier: S",
                "Snarl + Dark Pulse | Metal Claw + Iron Head",
                "Lv 40–50", "");
        reg("Bisharp", "Dark / Steel", 232, 176, 163, "A", true,
                "Evolves into Kingambit (S Dark/Steel)",
                "Great League Cup: B+",
                "Snarl + Dark Pulse | Metal Claw + Iron Head",
                "Evolve to Kingambit → Lv 40–50", "Kingambit");
        reg("Cetitan", "Ice", 208, 123, 340, "A", true,
                "A (Bulky Ice Raider)",
                "Master Premier & UL: A",
                "Powder Snow + Avalanche",
                "Lv 35–40 or ≤2,500 CP (UL)", "");
        reg("Primarina", "Water / Fairy", 232, 195, 190, "A+", true,
                "A+ (Water/Fairy Raider)",
                "Master League & Premier: S",
                "Waterfall + Hydro Cannon* | Charm + Disarming Voice",
                "Lv 40–50", "");
        reg("Incineroar", "Fire / Dark", 214, 175, 216, "A", true,
                "A (Fire/Dark Raider)",
                "Ultra League: A",
                "Fire Fang + Blast Burn* | Snarl + Darkest Lariat*",
                "≤2,500 CP (UL) or Lv 35–40", "");
        reg("Decidueye", "Grass / Ghost", 210, 179, 186, "A", true,
                "A (Grass Raider w/ Frenzy Plant*)",
                "Great & Ultra League: A",
                "Magical Leaf + Frenzy Plant* | Astonish + Spirit Shackle*",
                "≤1,500 CP (GL) or Lv 35–40", "");
        reg("Skeledirge", "Fire / Ghost", 207, 178, 232, "A+", true,
                "B+ (Fire/Ghost Raider)",
                "Ultra & Great League: S",
                "Incinerate + Blast Burn* | Hex + Shadow Ball",
                "≤2,500 CP (UL) or ≤1,500 CP (GL)", "");
        reg("Dragapult", "Dragon / Ghost", 266, 170, 204, "A+", true,
                "A+ (High-DPS Ghost & Dragon Raider)",
                "Master Premier: A+",
                "Dragon Tail + Outrage | Astonish + Shadow Ball",
                "Lv 40–50", "");

        // Max Battle Usefulness (Dynamax & Gigantamax Meta)
        reg("Rillaboom", "Grass", 239, 168, 225, "A+", true,
                "S (#1 G-Max/D-Max Grass & A+ Grass Raider)",
                "Master Premier: B+",
                "Razor Leaf + Grass Knot",
                "Lv 40–50 (Max Battles & Raids)", "");
        reg("Cinderace", "Fire", 238, 163, 190, "A+", true,
                "S (#1 G-Max/D-Max Fire & A Fire Raider)",
                "PvP Spice: B",
                "Fire Spin + Flamethrower",
                "Lv 40 (Max Battles)", "");
        reg("Inteleon", "Water", 262, 142, 172, "A+", true,
                "S (#1 G-Max/D-Max Water & A+ Water Raider)",
                "PvP: C",
                "Water Gun + Surf",
                "Lv 40 (Max Battles)", "");
        reg("Toxtricity", "Electric / Poison", 224, 140, 181, "A+", true,
                "S (#1 G-Max Electric Attacker)",
                "Great & Ultra Cup: B+",
                "Spark + Wild Charge | Poison Jab + Acid Spray",
                "Lv 40 (Max Battles)", "");
        reg("Kingler", "Water", 240, 181, 146, "A", true,
                "A+ (Top G-Max Water & Crabhammer Raider)",
                "PvP: C",
                "Bubble + Crabhammer",
                "Lv 40 (Max Battles)", "");
        reg("Corviknight", "Flying / Steel", 163, 192, 221, "A+", true,
                "A+ (Top Max Battle Steel/Flying Tank)",
                "Great & Ultra League: S",
                "Air Slash + Sky Attack | Steel Wing + Iron Head*",
                "≤1,500 CP (GL), ≤2,500 CP (UL), or Lv 40", "");
        reg("Dubwool", "Normal", 159, 198, 176, "A", true,
                "B+ (Max Battle Normal Tank)",
                "Great & Ultra League: A",
                "Tackle + Body Slam",
                "≤1,500 CP (GL) or ≤2,500 CP (UL)", "");
        reg("Urshifu", "Fighting / Dark", 254, 177, 225, "S", true,
                "S (G-Max/D-Max & Raid Fighter)",
                "Master League: S",
                "Counter + Dynamic Punch | Sucker Punch + Payback",
                "Lv 40–50", "");
        reg("Kubfu", "Fighting", 172, 117, 155, "A+", true,
                "Evolves into Urshifu (S Max & ML Fighter)",
                "Little/Great Cup: B",
                "Evolve to Urshifu -> Counter + Dynamic Punch",
                "Evolve to Urshifu → Lv 40–50", "Urshifu");

        // Load all 319 Move Elemental Types + all 1,087 Pokémon GO species from compiled PokemonFullDexData
        loadCompiledMoveTypes(PokemonFullDexData.getMoveTypePairs());
        loadCompiledFullDexArray(PokemonFullDexData.getChunk1());
        loadCompiledFullDexArray(PokemonFullDexData.getChunk2());
        loadCompiledFullDexArray(PokemonFullDexData.getChunk3());
        populateFusedFormMovePools();
        propagateFinalEvolutionMetaToPreEvolutions();
    }

    private static void loadCompiledMoveTypes(String[] pairs) {
        if (pairs == null) return;
        for (int i = 0; i < pairs.length; i++) {
            String p = pairs[i];
            if (p == null) continue;
            int idx = p.indexOf(':');
            if (idx > 0 && idx < p.length() - 1) {
                String mName = p.substring(0, idx).trim().toLowerCase(Locale.US).replace("-", " ");
                String mType = p.substring(idx + 1).trim();
                MOVE_TYPE_MAP.put(mName, mType);
            }
        }
    }

    private static void loadCompiledFullDexArray(String[] rows) {
        if (rows == null) return;
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i];
            if (row == null || row.isEmpty()) continue;
            String[] parts = row.split("(?<!\\s)\\|(?!\\s)", -1);
            if (parts.length < 10) continue;
            String pName = parts[0];
            String types = parts[1];
            int atk;
            int def;
            int sta;
            try {
                atk = Integer.parseInt(parts[2]);
                def = Integer.parseInt(parts[3]);
                sta = Integer.parseInt(parts[4]);
            } catch (Exception e) {
                continue;
            }
            String raidMoves = parts[5];
            String nfCsv = parts[6];
            String efCsv = parts[7];
            String ncCsv = parts[8];
            String ecCsv = parts[9];
            String finalEvo = parts.length >= 11 ? parts[10].trim() : "";
            String pvpMoves = parts.length >= 12 ? parts[11].trim() : raidMoves;

            String key = normalizeKey(pName);
            PokemonMetaEntry existing = META_MAP.get(key);
            if (existing != null) {
                if ("Normal".equals(existing.types) && types != null && !types.isEmpty()) {
                    existing.types = types;
                }
                if (existing.pvpMoveset == null || existing.pvpMoveset.isEmpty()) {
                    existing.pvpMoveset = pvpMoves;
                }
                if (existing.normalFastMoves.isEmpty() && existing.normalChargedMoves.isEmpty()) {
                    addCsvMoves(existing.normalFastMoves, nfCsv);
                    addCsvMoves(existing.eliteFastMoves, efCsv);
                    addCsvMoves(existing.normalChargedMoves, ncCsv);
                    addCsvMoves(existing.eliteChargedMoves, ecCsv);
                }
            } else {
                int maxCp50 = computeCp(atk, def, sta, 15, 15, 15, CPM_LV50);
                boolean badFastMove = nfCsv.startsWith("Yawn") || nfCsv.equals("Splash") || pName.equalsIgnoreCase("Slaking")
                        || pName.equalsIgnoreCase("Regigigas") || pName.equalsIgnoreCase("Archeops");
                String autoTier;
                boolean autoMeta;
                String autoRaid;
                String autoPvp;
                String autoRec;
                if (badFastMove) {
                    autoTier = "C";
                    autoMeta = false;
                    autoRaid = "C (Hindered by Fast Move)";
                    autoPvp = "PvP: C";
                    autoRec = "Skip Power-Up (Save Stardust)";
                } else if (maxCp50 >= 4150 && atk >= 260 && def >= 175) {
                    autoTier = "A+";
                    autoMeta = true;
                    autoRaid = "A+ (Strong Raid & ML Attacker)";
                    autoPvp = "Master League: A";
                    autoRec = "Lv 40–50";
                } else if (maxCp50 >= 3350 && atk >= 235 && def >= 145) {
                    autoTier = "A";
                    autoMeta = true;
                    autoRaid = "A (Solid Raid Counter)";
                    autoPvp = "Master Premier / UL: B+";
                    autoRec = "Lv 35–40";
                } else if (def >= 195 && sta >= 190) {
                    autoTier = "A";
                    autoMeta = true;
                    autoRaid = "Raid: C (Defensive Tank)";
                    autoPvp = "Great / Ultra League: A";
                    autoRec = "≤1,500 CP (GL) or ≤2,500 CP (UL)";
                } else if (maxCp50 >= 2600 || atk >= 210) {
                    autoTier = "B";
                    autoMeta = false;
                    autoRaid = "B (Budget Raider)";
                    autoPvp = "Cup Spice: B";
                    autoRec = "Lv 30–35 Only if Needed";
                } else if (maxCp50 >= 1850) {
                    autoTier = "C";
                    autoMeta = false;
                    autoRaid = "C (Outclassed)";
                    autoPvp = "Limited Cup: C";
                    autoRec = "Skip Power-Up (Save Stardust)";
                } else {
                    autoTier = "D";
                    autoMeta = false;
                    autoRaid = "D/F (Base Form / Dex Filler)";
                    autoPvp = "Little Cup / Evolve First";
                    autoRec = "Evolve First or Skip Power-Up";
                }
                PokemonMetaEntry created = new PokemonMetaEntry(
                        pName,
                        types,
                        atk,
                        def,
                        sta,
                        autoTier,
                        autoMeta,
                        autoRaid,
                        autoPvp,
                        raidMoves,
                        autoRec,
                        finalEvo
                );
                created.pvpMoveset = pvpMoves;
                addCsvMoves(created.normalFastMoves, nfCsv);
                addCsvMoves(created.eliteFastMoves, efCsv);
                addCsvMoves(created.normalChargedMoves, ncCsv);
                addCsvMoves(created.eliteChargedMoves, ecCsv);
                META_MAP.put(key, created);
            }
        }
    }

    /**
     * Propagates Raid Estimator (Tier 5 & Mega), Max Battle, and PvP usefulness from every Final Evolution
     * down to its Base and Stage-1 pre-evolutions.
     */
    private static void propagateFinalEvolutionMetaToPreEvolutions() {
        List<String> keys = new ArrayList<String>(META_MAP.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            PokemonMetaEntry pre = META_MAP.get(k);
            if (pre == null || pre.evolutionNote == null || pre.evolutionNote.isEmpty()) continue;
            if (!pre.raidRole.startsWith("D/F") && !pre.raidRole.startsWith("C (") && !pre.raidRole.startsWith("B (")) {
                continue;
            }
            PokemonMetaEntry finalEvo = META_MAP.get(normalizeKey(pre.evolutionNote));
            if (finalEvo == null || finalEvo == pre) continue;

            if (finalEvo.isMeta || finalEvo.tier.startsWith("S") || finalEvo.tier.startsWith("A")) {
                PokemonMetaEntry upgraded = new PokemonMetaEntry(
                        pre.name,
                        pre.types,
                        pre.baseAtk,
                        pre.baseDef,
                        pre.baseSta,
                        finalEvo.tier,
                        true,
                        "Evolves into " + finalEvo.name + " → " + finalEvo.raidRole,
                        "Evolves into " + finalEvo.name + " → " + finalEvo.pvpRole,
                        finalEvo.bestMoveset,
                        "Evolve to " + finalEvo.name + " → " + finalEvo.recommendedLevel,
                        "Evolve to " + finalEvo.name
                );
                upgraded.pvpMoveset = finalEvo.pvpMoveset;
                upgraded.normalFastMoves.addAll(pre.normalFastMoves);
                upgraded.eliteFastMoves.addAll(pre.eliteFastMoves);
                upgraded.normalChargedMoves.addAll(pre.normalChargedMoves);
                upgraded.eliteChargedMoves.addAll(pre.eliteChargedMoves);
                for (String ef : finalEvo.eliteFastMoves) {
                    if (!upgraded.eliteFastMoves.contains(ef)) upgraded.eliteFastMoves.add(ef);
                }
                for (String ec : finalEvo.eliteChargedMoves) {
                    if (!upgraded.eliteChargedMoves.contains(ec)) upgraded.eliteChargedMoves.add(ec);
                }
                for (String nf : finalEvo.normalFastMoves) {
                    if (!upgraded.normalFastMoves.contains(nf)) upgraded.normalFastMoves.add(nf);
                }
                for (String nc : finalEvo.normalChargedMoves) {
                    if (!upgraded.normalChargedMoves.contains(nc)) upgraded.normalChargedMoves.add(nc);
                }
                META_MAP.put(k, upgraded);
            }
        }
    }

    private static void addCsvMoves(List<String> target, String csv) {
        if (csv == null || csv.isEmpty()) return;
        String[] items = csv.split(",");
        for (int i = 0; i < items.length; i++) {
            String m = items[i].trim();
            if (!m.isEmpty() && !target.contains(m)) {
                target.add(m);
            }
        }
    }

    private static void populateFusedFormMovePools() {
        PokemonMetaEntry necrozma = META_MAP.get("necrozma");
        PokemonMetaEntry duskMane = META_MAP.get("dusk mane necrozma");
        if (necrozma != null && duskMane != null && duskMane.normalFastMoves.isEmpty()) {
            duskMane.normalFastMoves.addAll(necrozma.normalFastMoves);
            duskMane.normalChargedMoves.addAll(necrozma.normalChargedMoves);
            if (!duskMane.normalChargedMoves.contains("Sunsteel Strike")) {
                duskMane.normalChargedMoves.add(0, "Sunsteel Strike");
            }
            if (!necrozma.normalChargedMoves.contains("Sunsteel Strike")) {
                necrozma.normalChargedMoves.add("Sunsteel Strike");
            }
        }
        PokemonMetaEntry dawnWings = META_MAP.get("dawn wings necrozma");
        if (necrozma != null && dawnWings != null && dawnWings.normalFastMoves.isEmpty()) {
            dawnWings.normalFastMoves.addAll(necrozma.normalFastMoves);
            dawnWings.normalChargedMoves.addAll(necrozma.normalChargedMoves);
            if (!dawnWings.normalChargedMoves.contains("Moongeist Beam")) {
                dawnWings.normalChargedMoves.add(0, "Moongeist Beam");
            }
            if (!necrozma.normalChargedMoves.contains("Moongeist Beam")) {
                necrozma.normalChargedMoves.add("Moongeist Beam");
            }
        }
        PokemonMetaEntry zacian = META_MAP.get("zacian");
        PokemonMetaEntry crownedZacian = META_MAP.get("crowned sword zacian");
        if (zacian != null && crownedZacian != null && crownedZacian.normalFastMoves.isEmpty()) {
            crownedZacian.normalFastMoves.addAll(zacian.normalFastMoves);
            crownedZacian.normalChargedMoves.addAll(zacian.normalChargedMoves);
            crownedZacian.eliteChargedMoves.addAll(zacian.eliteChargedMoves);
            crownedZacian.pvpMoveset = zacian.pvpMoveset;
        }
        PokemonMetaEntry zamazenta = META_MAP.get("zamazenta");
        PokemonMetaEntry crownedZamazenta = META_MAP.get("crowned shield zamazenta");
        if (zamazenta != null && crownedZamazenta != null && crownedZamazenta.normalFastMoves.isEmpty()) {
            crownedZamazenta.normalFastMoves.addAll(zamazenta.normalFastMoves);
            crownedZamazenta.normalChargedMoves.addAll(zamazenta.normalChargedMoves);
            crownedZamazenta.eliteChargedMoves.addAll(zamazenta.eliteChargedMoves);
            crownedZamazenta.pvpMoveset = zamazenta.pvpMoveset;
        }
        PokemonMetaEntry palkia = META_MAP.get("palkia");
        PokemonMetaEntry originPalkia = META_MAP.get("origin forme palkia");
        if (palkia != null && originPalkia != null) {
            if (!originPalkia.eliteChargedMoves.contains("Spacial Rend")) originPalkia.eliteChargedMoves.add("Spacial Rend");
            if (!palkia.eliteChargedMoves.contains("Spacial Rend")) palkia.eliteChargedMoves.add("Spacial Rend");
        }
        PokemonMetaEntry dialga = META_MAP.get("dialga");
        PokemonMetaEntry originDialga = META_MAP.get("origin forme dialga");
        if (dialga != null && originDialga != null) {
            if (!originDialga.eliteChargedMoves.contains("Roar of Time")) originDialga.eliteChargedMoves.add("Roar of Time");
            if (!dialga.eliteChargedMoves.contains("Roar of Time")) dialga.eliteChargedMoves.add("Roar of Time");
        }
        PokemonMetaEntry kyurem = META_MAP.get("kyurem");
        PokemonMetaEntry blackKyurem = META_MAP.get("black kyurem");
        PokemonMetaEntry whiteKyurem = META_MAP.get("white kyurem");
        if (kyurem != null && blackKyurem != null && blackKyurem.normalFastMoves.isEmpty()) {
            addCsvMoves(blackKyurem.normalFastMoves, "Dragon Tail,Shadow Claw");
            addCsvMoves(blackKyurem.normalChargedMoves, "Outrage,Blizzard,Iron Head");
            addCsvMoves(blackKyurem.eliteChargedMoves, "Freeze Shock,Fusion Bolt");
            addCsvMoves(kyurem.normalFastMoves, "Dragon Tail,Shadow Claw,Ice Fang");
            addCsvMoves(kyurem.eliteChargedMoves, "Freeze Shock,Ice Burn,Fusion Bolt,Fusion Flare");
        }
        if (kyurem != null && whiteKyurem != null && whiteKyurem.normalFastMoves.isEmpty()) {
            addCsvMoves(whiteKyurem.normalFastMoves, "Dragon Breath,Ice Fang,Steel Wing");
            addCsvMoves(whiteKyurem.normalChargedMoves, "Blizzard,Ancient Power,Dragon Pulse,Focus Blast");
            addCsvMoves(whiteKyurem.eliteChargedMoves, "Ice Burn,Fusion Flare");
        }
    }

    private static void reg(
            String name,
            String types,
            int atk,
            int def,
            int sta,
            String tier,
            boolean isMeta,
            String raidRole,
            String pvpRole,
            String bestMoveset,
            String recLevel,
            String evoNote
    ) {
        META_MAP.put(normalizeKey(name), new PokemonMetaEntry(
                name, types, atk, def, sta, tier, isMeta, raidRole, pvpRole, bestMoveset, recLevel, evoNote
        ));
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.US)
                .replace("é", "e")
                .replace("♀", "f")
                .replace("♂", "m")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Background pre-warm of all 1,000+ Pokémon GO species base stats, types, and Normal/Elite TM move pools.
     */
    public static void syncLivePoGoApiStatsIfNeeded() {
        ensureInitialized();
        if (liveApiSynced) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                syncLivePoGoApiStatsBlocking();
            }
        }).start();
    }

    /**
     * Synchronous loader (safe to call on background OCR threads) so that the very first Scan Pokémon
     * has all 1,000+ Pokémon species, types, Normal Fast/Charged TMs, and Elite Fast/Charged TMs ready!
     */
    public static synchronized void syncLivePoGoApiStatsBlocking() {
        ensureInitialized();
        if (liveApiSynced) return;
        liveApiSynced = true;
        try {
            // 1. Sync Base Stats (pokemon_stats.json)
            String statsJson = fetchUrlText("https://pogoapi.net/api/v1/pokemon_stats.json", 4500);
            if (statsJson != null && statsJson.length() > 100) {
                Pattern objPat = Pattern.compile(
                        "\\{[^\\}]*?\"base_attack\"\\s*:\\s*(\\d+)\\s*,\\s*\"base_defense\"\\s*:\\s*(\\d+)\\s*,\\s*\"base_stamina\"\\s*:\\s*(\\d+)\\s*,\\s*\"form\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"pokemon_id\"\\s*:\\s*\\d+\\s*,\\s*\"pokemon_name\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
                );
                Matcher m = objPat.matcher(statsJson);
                while (m.find()) {
                    int atk = Integer.parseInt(m.group(1));
                    int def = Integer.parseInt(m.group(2));
                    int sta = Integer.parseInt(m.group(3));
                    String form = m.group(4);
                    String pName = formatFormPokemonName(m.group(5), form);
                    if (pName == null) continue;

                    String fullKey = normalizeKey(pName);
                    if (!META_MAP.containsKey(fullKey)) {
                        int maxCp50 = computeCp(atk, def, sta, 15, 15, 15, CPM_LV50);
                        String autoTier;
                        boolean autoMeta;
                        String autoRaid;
                        String autoPvp;
                        String autoRec;
                        if (maxCp50 >= 4100 || atk >= 265) {
                            autoTier = "S";
                            autoMeta = true;
                            autoRaid = "S/A+ (High-Attack Raid & Master League Powerhouse)";
                            autoPvp = "Master League: A+";
                            autoRec = "Level 40 → Level 50 (High Stat Total)";
                        } else if (maxCp50 >= 3400 || atk >= 235) {
                            autoTier = "A";
                            autoMeta = true;
                            autoRaid = "A (Strong Raid Attacker / Counter)";
                            autoPvp = "Master Premier / Ultra League: B+";
                            autoRec = "Level 35 → Level 40 (Solid Raid Counter)";
                        } else if (def >= 195 && sta >= 190) {
                            autoTier = "A";
                            autoMeta = true;
                            autoRaid = "Raid: C (Defensive Bulk)";
                            autoPvp = "Great / Ultra League: A (High Bulk Tank)";
                            autoRec = "≤1,500 CP (Great League) or ≤2,500 CP (Ultra League)";
                        } else if (maxCp50 >= 2650 || atk >= 205) {
                            autoTier = "B";
                            autoMeta = false;
                            autoRaid = "B (Budget Raid / Situational)";
                            autoPvp = "Great / Ultra Cup Spice: B";
                            autoRec = "Level 30 Only if Needed (Save XL Candy & Stardust)";
                        } else if (maxCp50 >= 1850) {
                            autoTier = "C";
                            autoMeta = false;
                            autoRaid = "C (Outclassed in Raids)";
                            autoPvp = "Limited Cup Only: C";
                            autoRec = "Do NOT Power Up (Save Stardust)";
                        } else {
                            autoTier = "D";
                            autoMeta = false;
                            autoRaid = "D/F (Base Form / Dex Filler)";
                            autoPvp = "Little Cup or Evolve First";
                            autoRec = "Evolve First or Keep for Pokédex (Do NOT Power Up)";
                        }
                        META_MAP.put(fullKey, new PokemonMetaEntry(
                                pName,
                                "Normal",
                                atk,
                                def,
                                sta,
                                autoTier,
                                autoMeta,
                                autoRaid,
                                autoPvp,
                                "Use STAB Fast + Low-Energy Charged Move",
                                autoRec,
                                ""
                        ));
                    }
                }
            }

            // 2. Sync Official Pokémon Types (pokemon_types.json)
            String typesJson = fetchUrlText("https://pogoapi.net/api/v1/pokemon_types.json", 4000);
            if (typesJson != null && typesJson.length() > 100) {
                Pattern typePat = Pattern.compile(
                        "\\{[^\\}]*?\"form\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"pokemon_id\"\\s*:\\s*\\d+\\s*,\\s*\"pokemon_name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"type\"\\s*:\\s*\\[([^\\]]*)\\]\\s*\\}"
                );
                Matcher tm = typePat.matcher(typesJson);
                while (tm.find()) {
                    String form = tm.group(1);
                    String pName = formatFormPokemonName(tm.group(2), form);
                    if (pName == null) continue;
                    String key = normalizeKey(pName);
                    PokemonMetaEntry entry = META_MAP.get(key);
                    if (entry != null && ("Normal".equals(entry.types) || "Pokémon GO Stats".equals(entry.types))) {
                        List<String> tList = parseQuotedStrings(tm.group(3));
                        if (!tList.isEmpty()) {
                            StringBuilder tb = new StringBuilder();
                            for (int i = 0; i < tList.size(); i++) {
                                if (i > 0) tb.append(" / ");
                                tb.append(tList.get(i));
                            }
                            entry.types = tb.toString();
                        }
                    }
                }
            }

            // 3. Sync Normal TM vs Elite TM Move Pools (current_pokemon_moves.json)
            String movesJson = fetchUrlText("https://pogoapi.net/api/v1/current_pokemon_moves.json", 4500);
            if (movesJson != null && movesJson.length() > 100) {
                Pattern moveObjPat = Pattern.compile(
                        "\\{\\s*\"charged_moves\"\\s*:\\s*\\[([^\\]]*)\\]\\s*,\\s*\"elite_charged_moves\"\\s*:\\s*\\[([^\\]]*)\\]\\s*,\\s*\"elite_fast_moves\"\\s*:\\s*\\[([^\\]]*)\\]\\s*,\\s*\"fast_moves\"\\s*:\\s*\\[([^\\]]*)\\]\\s*,\\s*\"form\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"pokemon_id\"\\s*:\\s*\\d+\\s*,\\s*\"pokemon_name\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
                );
                Matcher mm = moveObjPat.matcher(movesJson);
                while (mm.find()) {
                    String form = mm.group(5);
                    String pName = formatFormPokemonName(mm.group(6), form);
                    if (pName == null) continue;
                    String key = normalizeKey(pName);
                    PokemonMetaEntry entry = META_MAP.get(key);
                    if (entry != null && entry.normalFastMoves.isEmpty() && entry.normalChargedMoves.isEmpty()) {
                        entry.normalChargedMoves.addAll(parseQuotedStrings(mm.group(1)));
                        entry.eliteChargedMoves.addAll(parseQuotedStrings(mm.group(2)));
                        entry.eliteFastMoves.addAll(parseQuotedStrings(mm.group(3)));
                        entry.normalFastMoves.addAll(parseQuotedStrings(mm.group(4)));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static String fetchUrlText(String urlStr, int timeoutMs) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> parseQuotedStrings(String rawArrayBody) {
        List<String> list = new ArrayList<String>();
        if (rawArrayBody == null) return list;
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(rawArrayBody);
        while (m.find()) {
            String val = m.group(1).trim();
            if (!val.isEmpty() && !list.contains(val)) {
                list.add(val);
            }
        }
        return list;
    }

    private static String formatFormPokemonName(String baseName, String form) {
        if (baseName == null) return null;
        if (form == null || form.isEmpty() || form.equalsIgnoreCase("Normal") || form.equalsIgnoreCase("Standard")) {
            return baseName;
        }
        if (form.equalsIgnoreCase("Alola") || form.equalsIgnoreCase("Alolan")) {
            return "Alolan " + baseName;
        } else if (form.equalsIgnoreCase("Galarian") || form.equalsIgnoreCase("Galar")) {
            return "Galarian " + baseName;
        } else if (form.equalsIgnoreCase("Hisuian") || form.equalsIgnoreCase("Hisui")) {
            return "Hisuian " + baseName;
        } else if (form.equalsIgnoreCase("Paldean") || form.equalsIgnoreCase("Paldea")) {
            return "Paldean " + baseName;
        } else if (form.equalsIgnoreCase("Origin")) {
            return "Origin Forme " + baseName;
        } else if (form.equalsIgnoreCase("Therian")) {
            return baseName + " (Therian)";
        }
        return null;
    }

    /**
     * Searches matching Pokémon Meta profiles for the HUD Search Bar, Full-Page Overlay, or MainActivity lookup.
     */
    public static List<PokemonScanReport> searchPokemonByName(String rawQuery) {
        ensureInitialized();
        syncLivePoGoApiStatsIfNeeded();
        List<PokemonScanReport> results = new ArrayList<PokemonScanReport>();
        if (rawQuery == null) return results;
        String q = normalizeKey(rawQuery);
        if (q.length() < 2) return results;

        boolean queryShadow = q.contains("shadow");
        boolean queryLucky = q.contains("lucky");
        String cleanQ = q.replace("shadow", "").replace("lucky", "").replace("dynamax", "").trim();
        if (cleanQ.length() < 2) cleanQ = q;

        synchronized (PokemonMetaAnalyzer.class) {
            // 1. Exact match first
            PokemonMetaEntry exact = META_MAP.get(cleanQ);
            if (exact != null) {
                results.add(new PokemonScanReport(exact, 0, 0, queryShadow, queryLucky, false, null));
            }
            // 2. Prefix / Substring matches (up to 8 results)
            for (Map.Entry<String, PokemonMetaEntry> kv : META_MAP.entrySet()) {
                if (results.size() >= 8) break;
                if (exact != null && kv.getValue() == exact) continue;
                if (kv.getKey().startsWith(cleanQ) || kv.getKey().contains(cleanQ)) {
                    results.add(new PokemonScanReport(kv.getValue(), 0, 0, queryShadow, queryLucky, false, null));
                }
            }
            // 3. Fuzzy match if user made a typo
            if (results.isEmpty() && cleanQ.length() >= 4) {
                PokemonMetaEntry fuzzy = findFuzzySpeciesToken(cleanQ);
                if (fuzzy != null) {
                    results.add(new PokemonScanReport(fuzzy, 0, 0, queryShadow, queryLucky, false, null));
                }
            }
        }
        return results;
    }

    /**
     * Analyzes a Pokémon GO screenshot (OCR text + Bitmap pixels) to identify:
     * 1. The Pokémon species (via Candy label, Name line, Appraisal speech bubble, Fuzzy OCR match, or Move Pool)
     * 2. Current CP & HP
     * 3. Shadow / Lucky / Dynamax status
     * 4. Team Leader Appraisal IV Bars (0–15 ATK / DEF / HP) directly from screen pixels!
     */
    public static PokemonScanReport analyzePokemonScreen(String rawOcrText, Bitmap scaledBmp) {
        ensureInitialized();
        syncLivePoGoApiStatsIfNeeded();

        AppraisalBarVisionResult appraisal = extractAppraisalBarsFromBitmap(scaledBmp);
        String text = rawOcrText != null ? rawOcrText : "";
        String upper = text.toUpperCase(Locale.US);

        boolean isShadow = upper.contains("SHADOW") || upper.contains("PURIFY") || (appraisal != null && appraisal.shadowAuraDetected);
        boolean isLucky = upper.contains("LUCKY POK") || upper.contains("LUCKY ");
        boolean isDynamax = upper.contains("DYNAMAX") || upper.contains("GIGANTAMAX") || upper.contains("MAX MOVE");

        // 1. Extract CP (handles "CP 3791", "CP3791", "cP 2410", or OCR misread "GP 2410")
        int scannedCp = 0;
        Matcher cpMatcher = Pattern.compile("(?i)\\b[CG]P\\s*([0-9]{2,4})\\b").matcher(text);
        if (cpMatcher.find()) {
            try {
                scannedCp = Integer.parseInt(cpMatcher.group(1));
            } catch (Exception ignored) {}
        }

        // 2. Extract HP (e.g. "184 / 184 HP" or "184/184 HP" or "HP 184/184")
        int scannedHp = 0;
        Matcher hpMatcher = Pattern.compile("(?i)\\b(\\d{2,3})\\s*/\\s*(\\d{2,3})\\s*HP\\b").matcher(text);
        if (hpMatcher.find()) {
            try {
                scannedHp = Integer.parseInt(hpMatcher.group(2));
            } catch (Exception ignored) {}
        }

        PokemonMetaEntry matchedEntry = null;
        String textWithoutCandy = text.replaceAll("(?i)[A-Za-z0-9\\-]+\\s+CANDY(\\s+XL)?", " ");

        // Priority A: Team Leader Appraisal dialogue ("your <Pokemon> is...", "Overall, your <Pokemon>...")
        Matcher leaderMatcher = Pattern.compile("(?i)\\byour\\s+([A-Za-z0-9\\-]{3,16})\\b").matcher(textWithoutCandy);
        while (leaderMatcher.find()) {
            String cand = normalizeKey(leaderMatcher.group(1));
            matchedEntry = findBestSpeciesMatch(cand, textWithoutCandy);
            if (matchedEntry != null) break;
        }

        // Priority B: Exact or Digit-Corrected Species Name on the Pokémon Name Header (excluding "<BASE> CANDY")
        if (matchedEntry == null) {
            matchedEntry = findBestSpeciesMatchInFullText(textWithoutCandy);
        }

        // Priority C: Fuzzy Levenshtein Edit-Distance Match on non-Candy lines (catches OCR 1-2 char misreads of the Pokémon Name!)
        if (matchedEntry == null) {
            matchedEntry = findFuzzySpeciesInOcrLines(textWithoutCandy);
        }

        // Priority D: "<SPECIES> CANDY" or "+3 <SPECIES> CANDY" on Summary / Catch screen (vital when Pokémon has a custom Nickname!)
        if (matchedEntry == null) {
            Matcher candyMatcher = Pattern.compile("(?i)([A-Z][A-Z0-9\\-\\s]{2,20})\\s+CANDY").matcher(text);
            while (candyMatcher.find()) {
                String rawCandy = candyMatcher.group(1)
                        .replaceAll("(?i)\\b(XL|MEGA|SHADOW|PURIFY|POWER|UP|EVOLVE)\\b", "")
                        .trim();
                String[] words = rawCandy.split("\\s+");
                if (words.length >= 1) {
                    String lastOne = normalizeKey(words[words.length - 1]);
                    String lastTwo = words.length >= 2
                            ? normalizeKey(words[words.length - 2] + " " + words[words.length - 1])
                            : lastOne;
                    matchedEntry = findBestSpeciesMatch(lastTwo, textWithoutCandy);
                    if (matchedEntry == null) {
                        matchedEntry = findBestSpeciesMatch(lastOne, textWithoutCandy);
                    }
                    if (matchedEntry != null) {
                        // If matched via "<BASE> CANDY" (nicknamed Pokémon), check if its CP exceeds the base form's max CP
                        // or if visible moves match its final evolution!
                        int maxBaseCp = computeCp(matchedEntry.baseAtk, matchedEntry.baseDef, matchedEntry.baseSta, 15, 15, 15, CPM_LV50);
                        if (scannedCp > maxBaseCp) {
                            PokemonMetaEntry moveMatched = findSpeciesByVisibleMoves(text);
                            if (moveMatched != null) {
                                matchedEntry = moveMatched;
                            } else {
                                PokemonMetaEntry evolved = resolveEvolvedEntryFromNote(matchedEntry);
                                if (evolved != null) matchedEntry = evolved;
                            }
                        }
                        break;
                    }
                }
            }
        }

        // Priority E: Move-Pool Reverse Match (if Pokémon has a custom nickname and Candy is covered by Appraisal, match by visible moves!)
        if (matchedEntry == null) {
            matchedEntry = findSpeciesByVisibleMoves(text);
        }

        // Priority F: Full-text / Fuzzy fallback including Candy tokens
        if (matchedEntry == null) {
            matchedEntry = findBestSpeciesMatchInFullText(text);
        }
        if (matchedEntry == null) {
            matchedEntry = findFuzzySpeciesInOcrLines(text);
        }

        if (matchedEntry == null) {
            return null;
        }

        matchedEntry = upgradeFormEntryByVisibleText(matchedEntry, text, scannedCp);

        // If Appraisal card was not detected by pixel vision alone, check if OCR text confirms 4★ Hundo appraisal dialogue
        if ((appraisal == null || !appraisal.appraisalCardDetected) && upper.contains("HIGHEST STATS")) {
            appraisal = new AppraisalBarVisionResult(true, 15, 15, 15, isShadow);
        }

        String[] scannedMoves = extractScannedMovesForSpecies(matchedEntry, text, isShadow);

        return new PokemonScanReport(
                matchedEntry,
                scannedCp,
                scannedHp,
                isShadow,
                isLucky,
                isDynamax,
                appraisal,
                scannedMoves[0],
                scannedMoves[1],
                scannedMoves[2]
        );
    }

    /**
     * Scans the OCR text from the Pokémon Summary screen to identify which Fast Move and Charged Move(s)
     * this Pokémon currently has equipped, matching against its species move pool (plus Frustration/Return).
     */
    public static String[] extractScannedMovesForSpecies(PokemonMetaEntry entry, String rawOcrText, boolean isShadow) {
        String fastFound = "";
        String charged1 = "";
        String charged2 = "";
        if (entry == null || rawOcrText == null || rawOcrText.isEmpty()) {
            return new String[]{fastFound, charged1, charged2};
        }

        String normOcr = " " + normalizeKey(rawOcrText) + " ";
        String[] ocrLines = rawOcrText.split("\\r?\\n");

        List<String> allFast = new ArrayList<String>();
        allFast.addAll(entry.eliteFastMoves);
        allFast.addAll(entry.normalFastMoves);

        List<String> allCharged = new ArrayList<String>();
        allCharged.addAll(entry.eliteChargedMoves);
        allCharged.addAll(entry.normalChargedMoves);
        if (!allCharged.contains("Frustration")) allCharged.add("Frustration");
        if (!allCharged.contains("Return")) allCharged.add("Return");

        // 1. Exact normalized phrase match for Fast Move
        for (int i = 0; i < allFast.size(); i++) {
            String mv = allFast.get(i);
            String nk = normalizeKey(mv);
            if (nk.length() >= 3 && (normOcr.contains(" " + nk + " ") || normOcr.contains(" " + normalizeMoveComparisonKey(mv) + " "))) {
                fastFound = mv;
                break;
            }
        }
        // Fuzzy line match for Fast Move if OCR had a 1-char typo
        if (fastFound.isEmpty()) {
            fastFound = findFuzzyMoveInLines(allFast, ocrLines, "");
        }

        // 2. Exact normalized phrase match for Charged Move 1 & Charged Move 2
        for (int i = 0; i < allCharged.size(); i++) {
            String mv = allCharged.get(i);
            if (mv.equalsIgnoreCase(fastFound)) continue;
            String nk = normalizeKey(mv);
            String nkComp = normalizeMoveComparisonKey(mv);
            String nkBase = stripMoveVariantSuffix(nkComp);
            boolean matched = (nk.length() >= 4 && normOcr.contains(" " + nk + " "))
                    || (nkComp.length() >= 4 && normOcr.contains(" " + nkComp + " "))
                    || (nkBase.length() >= 4 && normOcr.contains(" " + nkBase + " "));
            if (matched) {
                if (charged1.isEmpty()) {
                    charged1 = mv;
                } else if (charged2.isEmpty() && !mv.equalsIgnoreCase(charged1)) {
                    charged2 = mv;
                    break;
                }
            }
        }
        if (charged1.isEmpty()) {
            charged1 = findFuzzyMoveInLines(allCharged, ocrLines, fastFound);
        }
        if (charged2.isEmpty() && !charged1.isEmpty()) {
            charged2 = findFuzzyMoveInLines(allCharged, ocrLines, charged1);
        }

        return new String[]{fastFound, charged1, charged2};
    }

    private static String findFuzzyMoveInLines(List<String> movePool, String[] ocrLines, String excludeMove) {
        for (int l = 0; l < ocrLines.length; l++) {
            String line = normalizeKey(ocrLines[l]);
            if (line.length() < 4) continue;
            // Strip trailing numbers (move power like "100" or "50") from the OCR line
            String cleanLine = line.replaceAll("\\b\\d+\\b", " ").replaceAll("\\s+", " ").trim();
            if (cleanLine.length() < 4) continue;

            for (int i = 0; i < movePool.size(); i++) {
                String cand = movePool.get(i);
                if (excludeMove != null && !excludeMove.isEmpty() && cand.equalsIgnoreCase(excludeMove)) continue;
                String nk = normalizeKey(cand);
                if (nk.length() < 5) continue;
                if (cleanLine.contains(nk)) return cand;
                if (Math.abs(cleanLine.length() - nk.length()) <= 2 && levenshteinDistance(cleanLine, nk) <= 1) {
                    return cand;
                }
            }
        }
        return "";
    }

    private static PokemonMetaEntry resolveEvolvedEntryFromNote(PokemonMetaEntry baseEntry) {
        if (baseEntry == null) return null;
        String combined = (baseEntry.bestMoveset + " " + baseEntry.raidRole + " " + baseEntry.recommendedLevel + " " + baseEntry.evolutionNote);
        Matcher m = Pattern.compile("(?i)Evolve(?:s)?\\s+(?:to|into)\\s+(?:Mega\\s+|Shadow\\s+)?([A-Za-z0-9\\-]+)").matcher(combined);
        if (m.find()) {
            String evoKey = normalizeKey(m.group(1));
            if (META_MAP.containsKey(evoKey)) {
                return META_MAP.get(evoKey);
            }
        }
        return null;
    }

    private static PokemonMetaEntry upgradeFormEntryByVisibleText(PokemonMetaEntry base, String fullText, int scannedCp) {
        if (base == null || fullText == null) return base;
        String normFull = normalizeKey(fullText);
        String key = normalizeKey(base.name);
        synchronized (PokemonMetaAnalyzer.class) {
            if (key.contains("necrozma")) {
                if (normFull.contains("dusk mane") || normFull.contains("sunsteel")) {
                    PokemonMetaEntry dm = META_MAP.get("dusk mane necrozma");
                    if (dm != null) return dm;
                }
                if (normFull.contains("dawn wings") || normFull.contains("moongeist")) {
                    PokemonMetaEntry dw = META_MAP.get("dawn wings necrozma");
                    if (dw != null) return dw;
                }
            }
            if (key.contains("zacian") && (normFull.contains("crowned") || normFull.contains("behemoth blade") || scannedCp > 4350)) {
                PokemonMetaEntry cz = META_MAP.get("crowned sword zacian");
                if (cz != null) return cz;
            }
            if (key.contains("zamazenta") && (normFull.contains("crowned") || normFull.contains("behemoth bash") || scannedCp > 4350)) {
                PokemonMetaEntry cz = META_MAP.get("crowned shield zamazenta");
                if (cz != null) return cz;
            }
            if (key.contains("palkia") && (normFull.contains("origin") || normFull.contains("spacial rend"))) {
                PokemonMetaEntry op = META_MAP.get("origin forme palkia");
                if (op != null) return op;
            }
            if (key.contains("dialga") && (normFull.contains("origin") || normFull.contains("roar of time"))) {
                PokemonMetaEntry od = META_MAP.get("origin forme dialga");
                if (od != null) return od;
            }
            if (key.contains("kyurem")) {
                if (normFull.contains("black") || normFull.contains("freeze shock") || normFull.contains("fusion bolt")) {
                    PokemonMetaEntry bk = META_MAP.get("black kyurem");
                    if (bk != null) return bk;
                }
                if (normFull.contains("white") || normFull.contains("ice burn") || normFull.contains("fusion flare")) {
                    PokemonMetaEntry wk = META_MAP.get("white kyurem");
                    if (wk != null) return wk;
                }
            }
        }
        return base;
    }

    private static PokemonMetaEntry findBestSpeciesMatch(String candidateKey, String fullText) {
        if (candidateKey == null || candidateKey.length() < 3) return null;
        String normFull = normalizeKey(fullText);

        synchronized (PokemonMetaAnalyzer.class) {
            if (candidateKey.contains("necrozma")) {
                if (normFull.contains("dusk mane") || normFull.contains("sunsteel")) {
                    return META_MAP.get("dusk mane necrozma");
                }
                if (normFull.contains("dawn wings") || normFull.contains("moongeist")) {
                    return META_MAP.get("dawn wings necrozma");
                }
            }
            if (candidateKey.contains("palkia") && (normFull.contains("origin") || normFull.contains("spacial rend"))) {
                return META_MAP.get("origin forme palkia");
            }
            if (candidateKey.contains("dialga") && (normFull.contains("origin") || normFull.contains("roar of time"))) {
                return META_MAP.get("origin forme dialga");
            }

            PokemonMetaEntry bestInText = findBestSpeciesMatchInFullText(fullText);
            if (bestInText != null) {
                return bestInText;
            }

            if (META_MAP.containsKey(candidateKey)) {
                return META_MAP.get(candidateKey);
            }
            PokemonMetaEntry fuzzy = findFuzzySpeciesToken(candidateKey);
            if (fuzzy != null) return fuzzy;
        }
        return null;
    }

    private static PokemonMetaEntry findBestSpeciesMatchInFullText(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) return null;
        String normText = " " + normalizeKey(fullText) + " ";
        // Also prepare an OCR digit-corrected version (where 0->o, 1->i, 5->s) and strip trailing digits/gender f/m
        String digitFixedText = " " + fixOcrDigitsAndSuffixes(normText) + " ";

        PokemonMetaEntry bestMatch = null;
        int bestLen = 0;

        synchronized (PokemonMetaAnalyzer.class) {
            for (Map.Entry<String, PokemonMetaEntry> kv : META_MAP.entrySet()) {
                String k = kv.getKey();
                if (k.length() < 3) continue;
                if (normText.contains(" " + k + " ") || digitFixedText.contains(" " + k + " ")) {
                    if (k.length() > bestLen) {
                        bestLen = k.length();
                        bestMatch = kv.getValue();
                    }
                }
            }
        }
        return bestMatch;
    }

    private static String fixOcrDigitsAndSuffixes(String norm) {
        String[] tokens = norm.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            // Strip trailing numbers attached to a Pokémon name (e.g. "machamp98" -> "machamp", "kyogre100" -> "kyogre")
            t = t.replaceAll("([a-z]{4,})[0-9]{1,3}$", "$1");
            // Strip trailing 'f' or 'm' gender symbol if OCR merged it (e.g. "gyaradosm" -> "gyarados")
            if (t.length() >= 6 && (t.endsWith("f") || t.endsWith("m")) && META_MAP.containsKey(t.substring(0, t.length() - 1))) {
                t = t.substring(0, t.length() - 1);
            }
            // Fix common OCR digit substitutions inside words
            if (!META_MAP.containsKey(t) && t.length() >= 4) {
                String fixed = t.replace('0', 'o').replace('1', 'i').replace('5', 's');
                if (META_MAP.containsKey(fixed)) {
                    t = fixed;
                } else {
                    String fixedL = t.replace('0', 'o').replace('1', 'l').replace('5', 's');
                    if (META_MAP.containsKey(fixedL)) {
                        t = fixedL;
                    }
                }
            }
            if (i > 0) sb.append(" ");
            sb.append(t);
        }
        return sb.toString();
    }

    private static final String[] UI_STOP_WORDS = new String[]{
            "weight", "height", "stardust", "candy", "power", "evolve", "purify", "transfer",
            "appraise", "favorite", "pokedex", "attack", "defense", "stamina", "normal",
            "fighting", "flying", "poison", "ground", "rock", "steel", "ghost", "fire",
            "water", "grass", "electric", "psychic", "dragon", "fairy", "shadow", "lucky",
            "dynamax", "gigantamax", "caught", "hatched", "traded", "research", "field",
            "overall", "stats", "impress", "blown", "away", "trainer", "battle", "gyms", "raids"
    };

    private static boolean isUiStopWord(String word) {
        for (int i = 0; i < UI_STOP_WORDS.length; i++) {
            if (UI_STOP_WORDS[i].equals(word)) return true;
        }
        return false;
    }

    private static PokemonMetaEntry findFuzzySpeciesInOcrLines(String fullText) {
        if (fullText == null || fullText.isEmpty()) return null;
        String[] lines = fullText.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String normLine = fixOcrDigitsAndSuffixes(normalizeKey(lines[i]));
            if (normLine.length() < 4) continue;
            // Try whole line first if it's 1-2 words (like the Pokémon Name header on the summary card)
            String[] words = normLine.split("\\s+");
            if (words.length <= 3) {
                PokemonMetaEntry lineMatch = findFuzzySpeciesToken(normLine);
                if (lineMatch != null) return lineMatch;
            }
            for (int w = 0; w < words.length; w++) {
                String tok = words[w];
                if (tok.length() < 5 || isUiStopWord(tok) || tok.matches(".*\\d.*")) continue;
                PokemonMetaEntry m = findFuzzySpeciesToken(tok);
                if (m != null) return m;
            }
        }
        return null;
    }

    private static PokemonMetaEntry findFuzzySpeciesToken(String token) {
        if (token == null || token.length() < 4 || isUiStopWord(token)) return null;
        if (META_MAP.containsKey(token)) return META_MAP.get(token);

        PokemonMetaEntry best = null;
        int bestDist = 99;
        int maxAllowedDist = token.length() >= 8 ? 2 : 1;

        for (Map.Entry<String, PokemonMetaEntry> kv : META_MAP.entrySet()) {
            String k = kv.getKey();
            if (Math.abs(k.length() - token.length()) > maxAllowedDist) continue;
            // First letter or last letter should match to avoid false positives
            if (k.charAt(0) != token.charAt(0) && k.charAt(k.length() - 1) != token.charAt(token.length() - 1)) {
                continue;
            }
            int d = levenshteinDistance(token, k);
            if (d <= maxAllowedDist && d < bestDist) {
                bestDist = d;
                best = kv.getValue();
                if (d == 1) break;
            }
        }
        return best;
    }

    private static int levenshteinDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[n][m];
    }

    private static PokemonMetaEntry findSpeciesByVisibleMoves(String fullText) {
        if (fullText == null || fullText.length() < 8) return null;
        String lower = fullText.toLowerCase(Locale.US);
        PokemonMetaEntry bestEntry = null;
        int bestHits = 0;

        synchronized (PokemonMetaAnalyzer.class) {
            for (PokemonMetaEntry entry : META_MAP.values()) {
                int hits = 0;
                for (int i = 0; i < entry.normalFastMoves.size(); i++) {
                    String mv = entry.normalFastMoves.get(i).toLowerCase(Locale.US);
                    if (mv.length() >= 5 && lower.contains(mv)) hits++;
                }
                for (int i = 0; i < entry.eliteFastMoves.size(); i++) {
                    String mv = entry.eliteFastMoves.get(i).toLowerCase(Locale.US);
                    if (mv.length() >= 5 && lower.contains(mv)) hits += 2;
                }
                for (int i = 0; i < entry.normalChargedMoves.size(); i++) {
                    String mv = entry.normalChargedMoves.get(i).toLowerCase(Locale.US);
                    if (mv.length() >= 5 && lower.contains(mv)) hits++;
                }
                for (int i = 0; i < entry.eliteChargedMoves.size(); i++) {
                    String mv = entry.eliteChargedMoves.get(i).toLowerCase(Locale.US);
                    if (mv.length() >= 5 && lower.contains(mv)) hits += 2;
                }
                if (hits >= 2 && hits > bestHits) {
                    bestHits = hits;
                    bestEntry = entry;
                }
            }
        }
        return bestEntry;
    }

    public static String computeTypeEffectivenessText(String typesStr) {
        if (typesStr == null || typesStr.isEmpty()) return "Standard Type Chart";
        String[] allTypes = new String[]{
                "Normal", "Fire", "Water", "Grass", "Electric", "Ice",
                "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
                "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
        };
        String[] defTypes = typesStr.split("/");
        List<String> doubleWeak = new ArrayList<String>();
        List<String> singleWeak = new ArrayList<String>();
        List<String> resists = new ArrayList<String>();

        for (int a = 0; a < allTypes.length; a++) {
            String atkType = allTypes[a];
            double mult = 1.0;
            for (int d = 0; d < defTypes.length; d++) {
                String dt = defTypes[d].trim();
                mult *= getSingleTypeMultiplier(atkType, dt);
            }
            if (mult >= 2.4) {
                doubleWeak.add(atkType + " (2.56×)");
            } else if (mult >= 1.5) {
                singleWeak.add(atkType);
            } else if (mult <= 0.65) {
                resists.add(atkType);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!doubleWeak.isEmpty()) {
            sb.append("⚠ DOUBLE WEAK (2.56×): ").append(PokemonMetaEntry.joinList(doubleWeak)).append("\n");
        }
        if (!singleWeak.isEmpty()) {
            sb.append("🔻 Weak To (1.6×): ").append(PokemonMetaEntry.joinList(singleWeak)).append("\n");
        }
        if (!resists.isEmpty()) {
            sb.append("🛡 Resists: ").append(PokemonMetaEntry.joinList(resists));
        }
        return sb.toString().trim();
    }

    private static double getSingleTypeMultiplier(String atk, String def) {
        if (atk.equalsIgnoreCase("Normal")) {
            if (def.equalsIgnoreCase("Rock") || def.equalsIgnoreCase("Steel")) return 0.625;
            if (def.equalsIgnoreCase("Ghost")) return 0.390625;
        } else if (atk.equalsIgnoreCase("Fire")) {
            if (def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Ice") || def.equalsIgnoreCase("Bug") || def.equalsIgnoreCase("Steel")) return 1.6;
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Water") || def.equalsIgnoreCase("Rock") || def.equalsIgnoreCase("Dragon")) return 0.625;
        } else if (atk.equalsIgnoreCase("Water")) {
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Ground") || def.equalsIgnoreCase("Rock")) return 1.6;
            if (def.equalsIgnoreCase("Water") || def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Dragon")) return 0.625;
        } else if (atk.equalsIgnoreCase("Grass")) {
            if (def.equalsIgnoreCase("Water") || def.equalsIgnoreCase("Ground") || def.equalsIgnoreCase("Rock")) return 1.6;
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Poison") || def.equalsIgnoreCase("Flying") || def.equalsIgnoreCase("Bug") || def.equalsIgnoreCase("Dragon") || def.equalsIgnoreCase("Steel")) return 0.625;
        } else if (atk.equalsIgnoreCase("Electric")) {
            if (def.equalsIgnoreCase("Water") || def.equalsIgnoreCase("Flying")) return 1.6;
            if (def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Electric") || def.equalsIgnoreCase("Dragon")) return 0.625;
            if (def.equalsIgnoreCase("Ground")) return 0.390625;
        } else if (atk.equalsIgnoreCase("Ice")) {
            if (def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Ground") || def.equalsIgnoreCase("Flying") || def.equalsIgnoreCase("Dragon")) return 1.6;
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Water") || def.equalsIgnoreCase("Ice") || def.equalsIgnoreCase("Steel")) return 0.625;
        } else if (atk.equalsIgnoreCase("Fighting")) {
            if (def.equalsIgnoreCase("Normal") || def.equalsIgnoreCase("Ice") || def.equalsIgnoreCase("Rock") || def.equalsIgnoreCase("Dark") || def.equalsIgnoreCase("Steel")) return 1.6;
            if (def.equalsIgnoreCase("Poison") || def.equalsIgnoreCase("Flying") || def.equalsIgnoreCase("Psychic") || def.equalsIgnoreCase("Bug") || def.equalsIgnoreCase("Fairy")) return 0.625;
            if (def.equalsIgnoreCase("Ghost")) return 0.390625;
        } else if (atk.equalsIgnoreCase("Poison")) {
            if (def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Fairy")) return 1.6;
            if (def.equalsIgnoreCase("Poison") || def.equalsIgnoreCase("Ground") || def.equalsIgnoreCase("Rock") || def.equalsIgnoreCase("Ghost")) return 0.625;
            if (def.equalsIgnoreCase("Steel")) return 0.390625;
        } else if (atk.equalsIgnoreCase("Ground")) {
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Electric") || def.equalsIgnoreCase("Poison") || def.equalsIgnoreCase("Rock") || def.equalsIgnoreCase("Steel")) return 1.6;
            if (def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Bug")) return 0.625;
            if (def.equalsIgnoreCase("Flying")) return 0.390625;
        } else if (atk.equalsIgnoreCase("Flying")) {
            if (def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Fighting") || def.equalsIgnoreCase("Bug")) return 1.6;
            if (def.equalsIgnoreCase("Electric") || def.equalsIgnoreCase("Rock") || def.equalsIgnoreCase("Steel")) return 0.625;
        } else if (atk.equalsIgnoreCase("Psychic")) {
            if (def.equalsIgnoreCase("Fighting") || def.equalsIgnoreCase("Poison")) return 1.6;
            if (def.equalsIgnoreCase("Psychic") || def.equalsIgnoreCase("Steel")) return 0.625;
            if (def.equalsIgnoreCase("Dark")) return 0.390625;
        } else if (atk.equalsIgnoreCase("Bug")) {
            if (def.equalsIgnoreCase("Grass") || def.equalsIgnoreCase("Psychic") || def.equalsIgnoreCase("Dark")) return 1.6;
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Fighting") || def.equalsIgnoreCase("Poison") || def.equalsIgnoreCase("Flying") || def.equalsIgnoreCase("Ghost") || def.equalsIgnoreCase("Steel") || def.equalsIgnoreCase("Fairy")) return 0.625;
        } else if (atk.equalsIgnoreCase("Rock")) {
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Ice") || def.equalsIgnoreCase("Flying") || def.equalsIgnoreCase("Bug")) return 1.6;
            if (def.equalsIgnoreCase("Fighting") || def.equalsIgnoreCase("Ground") || def.equalsIgnoreCase("Steel")) return 0.625;
        } else if (atk.equalsIgnoreCase("Ghost")) {
            if (def.equalsIgnoreCase("Psychic") || def.equalsIgnoreCase("Ghost")) return 1.6;
            if (def.equalsIgnoreCase("Dark")) return 0.625;
            if (def.equalsIgnoreCase("Normal")) return 0.390625;
        } else if (atk.equalsIgnoreCase("Dragon")) {
            if (def.equalsIgnoreCase("Dragon")) return 1.6;
            if (def.equalsIgnoreCase("Steel")) return 0.625;
            if (def.equalsIgnoreCase("Fairy")) return 0.390625;
        } else if (atk.equalsIgnoreCase("Dark")) {
            if (def.equalsIgnoreCase("Psychic") || def.equalsIgnoreCase("Ghost")) return 1.6;
            if (def.equalsIgnoreCase("Fighting") || def.equalsIgnoreCase("Dark") || def.equalsIgnoreCase("Fairy")) return 0.625;
        } else if (atk.equalsIgnoreCase("Steel")) {
            if (def.equalsIgnoreCase("Ice") || def.equalsIgnoreCase("Rock") || def.equalsIgnoreCase("Fairy")) return 1.6;
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Water") || def.equalsIgnoreCase("Electric") || def.equalsIgnoreCase("Steel")) return 0.625;
        } else if (atk.equalsIgnoreCase("Fairy")) {
            if (def.equalsIgnoreCase("Fighting") || def.equalsIgnoreCase("Dragon") || def.equalsIgnoreCase("Dark")) return 1.6;
            if (def.equalsIgnoreCase("Fire") || def.equalsIgnoreCase("Poison") || def.equalsIgnoreCase("Steel")) return 0.625;
        }
        return 1.0;
    }

    /**
     * ON-DEVICE COMPUTER VISION APPRAISAL BAR SCANNER:
     * Measures the 3 horizontal Attack / Defense / HP IV bars on Pokémon GO's Appraisal Card
     * directly from the screenshot Bitmap across all Android screen aspect ratios.
     */
    public static AppraisalBarVisionResult extractAppraisalBarsFromBitmap(Bitmap bmp) {
        if (bmp == null || bmp.getWidth() < 160 || bmp.getHeight() < 300) {
            return new AppraisalBarVisionResult(false, 0, 0, 0, false);
        }
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        // 1. Check for purple Shadow flames/aura around the Pokémon (y = 0.25h..0.46h, x = 0.30w..0.70w)
        int purpleShadowPixels = 0;
        for (int y = (int) (h * 0.26f); y <= (int) (h * 0.45f); y += 10) {
            for (int x = (int) (w * 0.30f); x <= (int) (w * 0.70f); x += 10) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                if (r >= 110 && b >= 150 && g <= 85 && (b - g) >= 70 && (r - g) >= 35) {
                    purpleShadowPixels++;
                }
            }
        }
        boolean shadowAura = purpleShadowPixels >= 10;

        // 2. Scan the Appraisal Card region (y = 0.40h .. 0.88h) for the 3 horizontal IV stat bars.
        // Do NOT require x = 0.62w to be white because the circular Star Stamp & Team Leader sit on the right side!
        int xScanLeft = (int) (w * 0.10f);
        int xScanRight = (int) (w * 0.60f);
        int minBarWidth = Math.max(28, (int) (w * 0.18f));
        int maxBarWidth = (int) (w * 0.48f);

        int yScanStart = (int) (h * 0.40f);
        int yScanEnd = (int) (h * 0.88f);

        // Each matched scanline stores: [y, barStartX, fillEndX, barEndX, isRedHundoFlag, filledCount]
        List<int[]> candidateRows = new ArrayList<int[]>();

        for (int y = yScanStart; y <= yScanEnd; y++) {
            int bestTrackSpan = 0;
            int bestStartX = -1;
            int bestFillEndX = -1;
            int bestEndX = -1;
            int bestFilledCount = 0;
            int bestRedCount = 0;

            int curStartX = -1;
            int curFillEndX = -1;
            int curEndX = -1;
            int curFilledCount = 0;
            int curRedCount = 0;
            int curTrackPixels = 0;
            int gapTolerance = 0;

            for (int x = xScanLeft; x <= xScanRight; x++) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));

                // Filled Orange/Amber IV bar (1..14 IV): R >= 200, G = 105..198, B <= 105, R - B >= 95
                boolean isOrange = (r >= 200 && g >= 105 && g <= 198 && b <= 105 && (r - b) >= 95 && r > g + 12);
                // Filled Red/Coral/Pink IV bar (15/15 Max IV): R >= 195, G = 55..155, B = 65..165, R - G >= 58
                boolean isRedHundo = (r >= 195 && g >= 55 && g <= 155 && b >= 65 && b <= 165 && (r - g) >= 58 && (r - b) >= 45);
                // Unfilled Gray IV bar track inside white card: R,G,B in 175..236 with low saturation
                boolean isGrayTrack = !isOrange && !isRedHundo && (r >= 175 && r <= 236 && g >= 175 && g <= 236 && b >= 175 && b <= 236 && sat <= 18);

                if (isOrange || isRedHundo || isGrayTrack) {
                    if (curStartX < 0) {
                        curStartX = x;
                        curFillEndX = x;
                    }
                    curEndX = x;
                    curTrackPixels++;
                    gapTolerance = 0;

                    if (isOrange || isRedHundo) {
                        // Filled pixels must be contiguous from the left side of the bar (allow small divider ticks)
                        if ((x - curStartX) - curFilledCount <= Math.max(14, minBarWidth / 5)) {
                            curFilledCount++;
                            curFillEndX = x;
                            if (isRedHundo) curRedCount++;
                        }
                    }
                } else {
                    // Allow up to 5px gap for the two white vertical segment dividers (at 5/15 and 10/15 IVs)
                    gapTolerance++;
                    if (gapTolerance > 5 && curStartX >= 0) {
                        int span = curEndX - curStartX;
                        if (span >= minBarWidth && span <= maxBarWidth && curTrackPixels >= minBarWidth * 0.70f && span > bestTrackSpan) {
                            bestTrackSpan = span;
                            bestStartX = curStartX;
                            bestFillEndX = curFillEndX;
                            bestEndX = curEndX;
                            bestFilledCount = curFilledCount;
                            bestRedCount = curRedCount;
                        }
                        curStartX = -1;
                        curFillEndX = -1;
                        curEndX = -1;
                        curFilledCount = 0;
                        curRedCount = 0;
                        curTrackPixels = 0;
                        gapTolerance = 0;
                    }
                }
            }
            if (curStartX >= 0) {
                int span = curEndX - curStartX;
                if (span >= minBarWidth && span <= maxBarWidth && curTrackPixels >= minBarWidth * 0.70f && span > bestTrackSpan) {
                    bestTrackSpan = span;
                    bestStartX = curStartX;
                    bestFillEndX = curFillEndX;
                    bestEndX = curEndX;
                    bestFilledCount = curFilledCount;
                    bestRedCount = curRedCount;
                }
            }

            if (bestTrackSpan >= minBarWidth && bestStartX >= 0) {
                // Verify the pixel slightly above or to the left of the bar is on the white Appraisal card background
                int leftCardX = Math.max(4, bestStartX - Math.max(8, w / 45));
                int aboveCardY = Math.max(4, y - Math.max(8, h / 95));
                int cLeft = bmp.getPixel(leftCardX, y);
                int cAbove = bmp.getPixel((bestStartX + bestEndX) / 2, aboveCardY);
                boolean whiteLeft = (Color.red(cLeft) >= 220 && Color.green(cLeft) >= 220 && Color.blue(cLeft) >= 215);
                boolean whiteAbove = (Color.red(cAbove) >= 220 && Color.green(cAbove) >= 220 && Color.blue(cAbove) >= 215);

                if (whiteLeft || whiteAbove) {
                    int isRedFlag = (bestRedCount > bestFilledCount / 2 && bestFilledCount >= bestTrackSpan * 0.78f) ? 1 : 0;
                    candidateRows.add(new int[]{y, bestStartX, bestFillEndX, bestEndX, isRedFlag, bestFilledCount});
                }
            }
        }

        if (candidateRows.size() < 3) {
            return new AppraisalBarVisionResult(false, 0, 0, 0, shadowAura);
        }

        // Cluster consecutive Y scanlines into distinct horizontal bars
        List<int[]> bars = new ArrayList<int[]>(); // [centerY, startX, fillEndX, endX, isRedFlag, filledCount]
        int[] curCluster = candidateRows.get(0);
        int count = 1;
        int mergeGapY = Math.max(7, (int) (h * 0.012f));

        for (int idx = 1; idx < candidateRows.size(); idx++) {
            int[] row = candidateRows.get(idx);
            if (row[0] - curCluster[0] <= mergeGapY) {
                curCluster[0] = (curCluster[0] * count + row[0]) / (count + 1);
                curCluster[1] = Math.min(curCluster[1], row[1]);
                if (row[5] >= curCluster[5]) {
                    curCluster[2] = row[2];
                    curCluster[5] = row[5];
                }
                curCluster[3] = Math.max(curCluster[3], row[3]);
                curCluster[4] = Math.max(curCluster[4], row[4]);
                count++;
            } else {
                bars.add(curCluster);
                curCluster = row;
                count = 1;
            }
        }
        bars.add(curCluster);

        if (bars.size() >= 3) {
            // Search from bottom to top for 3 evenly spaced horizontal bars (Attack, Defense, HP) with aligned left edges
            for (int i = bars.size() - 3; i >= 0; i--) {
                for (int j = i + 1; j <= bars.size() - 2; j++) {
                    for (int k = j + 1; k <= bars.size() - 1; k++) {
                        int[] b1 = bars.get(i);
                        int[] b2 = bars.get(j);
                        int[] b3 = bars.get(k);

                        int d1 = b2[0] - b1[0];
                        int d2 = b3[0] - b2[0];
                        int maxLeftDiff = Math.max(Math.abs(b1[1] - b2[1]), Math.abs(b2[1] - b3[1]));

                        if (maxLeftDiff <= w * 0.06f
                                && d1 >= h * 0.018f && d1 <= h * 0.095f
                                && Math.abs(d1 - d2) <= Math.max(14, d1 * 0.45f)) {
                            // Ensure at least one bar has filled pixels or all 3 have consistent track widths
                            int commonLeft = Math.min(b1[1], Math.min(b2[1], b3[1]));
                            int commonRight = Math.max(b1[3], Math.max(b2[3], b3[3]));
                            if (b1[4] == 1) commonRight = Math.max(commonRight, b1[2]);
                            if (b2[4] == 1) commonRight = Math.max(commonRight, b2[2]);
                            if (b3[4] == 1) commonRight = Math.max(commonRight, b3[2]);

                            int fullTrackWidth = Math.max(minBarWidth, commonRight - commonLeft);
                            int atk = computeBarIv15(b1, commonLeft, fullTrackWidth);
                            int def = computeBarIv15(b2, commonLeft, fullTrackWidth);
                            int sta = computeBarIv15(b3, commonLeft, fullTrackWidth);

                            return new AppraisalBarVisionResult(true, atk, def, sta, shadowAura);
                        }
                    }
                }
            }
        }

        return new AppraisalBarVisionResult(false, 0, 0, 0, shadowAura);
    }

    private static int computeBarIv15(int[] bar, int commonLeft, int fullTrackWidth) {
        if (bar[4] == 1) return 15; // Red 15/15 bar
        if (bar[5] <= 3) return 0;  // Empty gray bar (0/15)
        int fillSpan = Math.max(0, bar[2] - commonLeft);
        float ratio = Math.max(0f, Math.min(1f, (float) fillSpan / (float) Math.max(1, fullTrackWidth)));
        int iv = Math.round(ratio * 15f);
        if (iv == 15 && bar[4] == 0 && ratio < 0.96f) {
            iv = 14;
        }
        return Math.max(0, Math.min(15, iv));
    }

    private static String[] buildSmartRecommendation(
            PokemonMetaEntry entry,
            String effectiveTier,
            boolean effectiveIsMeta,
            int scannedCp,
            boolean isShadow,
            boolean isLucky,
            AppraisalBarVisionResult appraisal,
            int cpLv15,
            int cpLv20,
            int cpLv25,
            int yourCpLv40,
            int yourCpLv50
    ) {
        boolean hasIvs = appraisal != null && appraisal.appraisalCardDetected;
        int ivPct = hasIvs ? appraisal.ivPercent : -1;
        boolean isHighIv = hasIvs && (ivPct >= 91 || (appraisal.ivAtk == 15 && ivPct >= 87));
        boolean isGoodShadowIv = isShadow && hasIvs && (ivPct >= 78 || appraisal.ivAtk >= 13);
        boolean isPvpSpread = hasIvs && (appraisal.ivAtk <= 5 && appraisal.ivDef >= 12 && appraisal.ivSta >= 12);

        boolean cpMatchesHundoBenchmark = (scannedCp > 0)
                && (scannedCp == cpLv15 || scannedCp == cpLv20 || scannedCp == cpLv25 || scannedCp == yourCpLv40 || scannedCp == yourCpLv50);

        String luckyTag = isLucky ? " • ✨ Lucky (-50% Dust)" : "";
        String shadowTag = isShadow ? " • 😈 Do Not Purify" : "";

        // CASE 1: Pre-Evolution of a Meta Species
        if (entry.evolutionNote != null && entry.evolutionNote.length() > 0) {
            if (isHighIv || isGoodShadowIv || cpMatchesHundoBenchmark || !hasIvs) {
                return new String[]{
                        "✅ Evolve First → " + entry.recommendedLevel,
                        "Target: " + entry.recommendedLevel + luckyTag + shadowTag,
                        entry.evolutionNote + (hasIvs ? "" : " • Tip: Open Appraisal & scan to read IV bars.")
                };
            } else {
                return new String[]{
                        "⚠ Hold Candy (" + ivPct + "% IV is low for " + effectiveTier + "-Tier)",
                        "Target: Wait for 3★ / High-ATK before evolving",
                        entry.evolutionNote
                };
            }
        }

        // CASE 2: Great League (1500 CP) / Ultra League (2500 CP) PvP Specialist
        boolean isGlUlSpecialist = entry.recommendedLevel.contains("1,500") || entry.recommendedLevel.contains("1500")
                || entry.recommendedLevel.contains("2,500") || entry.recommendedLevel.contains("2500");
        if (isGlUlSpecialist && !entry.recommendedLevel.contains("Level 40")) {
            return new String[]{
                    "✅ PvP Meta (" + effectiveTier + ") — Cap at League Limit",
                    "Target: " + entry.recommendedLevel + luckyTag,
                    isPvpSpread ? "★ Ideal Low-ATK / High-Bulk PvP IVs!" : "Keep ≤1,500 CP (GL) or ≤2,500 CP (UL)."
            };
        }

        // CASE 3: S+ / S / A+ / A Tier Raid & Master League Meta
        if (effectiveIsMeta && (effectiveTier.startsWith("S") || effectiveTier.startsWith("A"))) {
            if (hasIvs) {
                if (isHighIv || isGoodShadowIv) {
                    return new String[]{
                            "🔥 Power Up (" + effectiveTier + " Meta • " + ivPct + "% IV)",
                            "Target: " + entry.recommendedLevel + luckyTag + shadowTag,
                            "Hits " + yourCpLv40 + " CP (Lv 40) / " + yourCpLv50 + " CP (Lv 50)."
                    };
                } else if (ivPct >= 76 || isShadow) {
                    return new String[]{
                            "👍 Budget Build (Lv 30–35 Only)",
                            "Target: Lv 30–35 • Save XL Candy for 96%+" + shadowTag,
                            "Solid for Raids now; hold Lv 50 XL dust for a higher IV."
                    };
                } else {
                    return new String[]{
                            "⚠ Hold Stardust (Low IVs: " + ivPct + "%)",
                            "Target: Mirror Lucky Trade or wait for 3★",
                            effectiveTier + "-Tier species, but IVs are low for max investment."
                    };
                }
            }

            return new String[]{
                    "✅ Worth Building (" + effectiveTier + " Meta)",
                    "Target: " + entry.recommendedLevel + luckyTag + shadowTag,
                    cpMatchesHundoBenchmark
                            ? "🎯 Scanned CP (" + scannedCp + ") matches a 100% IV benchmark!"
                            : "Open Appraisal in-game & tap Scan to check exact ATK/DEF/HP bars."
            };
        }

        // CASE 4: Non-Meta (B / C / D / F Tier)
        return new String[]{
                "❌ Save Stardust (" + effectiveTier + " Tier)",
                "Target: Dex / Collection Only",
                "Outclassed in Raids & Open PvP."
        };
    }
}
