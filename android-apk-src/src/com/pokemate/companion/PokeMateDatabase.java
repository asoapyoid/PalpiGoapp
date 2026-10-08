package com.pokemate.companion;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PokeMateDatabase extends SQLiteOpenHelper {

    public static class RewardPokemonEntry {
        public final String displayName;
        public final String shinySymbol; // "" (not shiny eligible), "✨" (standard shiny), "✨✨" (boosted shiny rate)
        public final String tier;
        public final boolean isMeta;
        public final PokemonMetaAnalyzer.PokemonScanReport report;

        public RewardPokemonEntry(
                String displayName,
                String shinySymbol,
                String tier,
                boolean isMeta,
                PokemonMetaAnalyzer.PokemonScanReport report
        ) {
            this.displayName = displayName;
            this.shinySymbol = shinySymbol;
            this.tier = tier;
            this.isMeta = isMeta;
            this.report = report;
        }

        public String getChipLabel() {
            String sh = (shinySymbol != null && shinySymbol.length() > 0) ? (" " + shinySymbol) : "";
            return displayName + sh + " [" + tier + "]";
        }
    }

    public static class ResearchItem {
        public final String category;
        public final String outlineType; // "EVENT", "BONUS", or "NORMAL"
        public final String taskName;
        public final String reward;
        public final String cpRange;
        public final boolean shinyPossible;
        public final boolean isMeta;
        public final String metaHighlight;
        public final int oddsPercentEach;
        public final int poolCount;
        public final List<RewardPokemonEntry> pokemonEntries;
        public final String extraPoolSuffix;

        public ResearchItem(String category, String taskName, String reward, String cpRange, boolean shinyPossible) {
            this.category = category;
            this.taskName = taskName;
            this.reward = reward;
            this.outlineType = inferOutlineType(category, reward, cpRange);
            this.cpRange = cleanCpRange(cpRange);
            this.shinyPossible = shinyPossible;
            this.metaHighlight = buildMetaHighlight(taskName, reward);
            this.isMeta = this.metaHighlight.length() > 0;

            String[] parts = reward.split("/");
            int extra = 0;
            String foundExtraSuffix = "";
            Matcher m = Pattern.compile("\\(\\+(\\d+)\\s+more\\)").matcher(reward);
            if (m.find()) {
                foundExtraSuffix = "(+" + m.group(1) + " more)";
                try {
                    extra = Integer.parseInt(m.group(1));
                } catch (Exception ignored) {}
            }
            this.extraPoolSuffix = foundExtraSuffix;
            this.poolCount = Math.max(1, parts.length + extra);
            this.oddsPercentEach = Math.max(1, Math.round(100f / this.poolCount));

            this.pokemonEntries = new ArrayList<RewardPokemonEntry>();
            if (!isItemRewardPool(reward)) {
                for (int i = 0; i < parts.length; i++) {
                    String rawTok = parts[i].replaceAll("\\(\\+\\d+\\s+more\\)", "").trim();
                    String lookupName = rawTok.replaceAll("\\([^\\)]*\\)", "").replaceAll("×\\d+", "").trim();
                    if (lookupName.length() < 3) continue;
                    List<PokemonMetaAnalyzer.PokemonScanReport> rMatches = PokemonMetaAnalyzer.searchPokemonByName(lookupName);
                    if (!rMatches.isEmpty()) {
                        PokemonMetaAnalyzer.PokemonScanReport matchedRep = rMatches.get(0);
                        String shSym = getPokemonShinySymbol(matchedRep.entry.name, shinyPossible);
                        this.pokemonEntries.add(new RewardPokemonEntry(
                                matchedRep.entry.name,
                                shSym,
                                matchedRep.effectiveTier,
                                matchedRep.effectiveIsMeta,
                                matchedRep
                        ));
                    } else {
                        String shSym = getPokemonShinySymbol(lookupName, shinyPossible);
                        this.pokemonEntries.add(new RewardPokemonEntry(
                                lookupName,
                                shSym,
                                "B",
                                false,
                                null
                        ));
                    }
                }
            }
        }

        private static boolean isItemRewardPool(String reward) {
            if (reward == null) return true;
            String rl = reward.toLowerCase();
            return rl.contains("ball") || rl.contains("stardust") || rl.contains("berry")
                    || rl.contains("rare candy") || rl.contains("poffin") || rl.contains("tm ×")
                    || rl.contains("apple ×") || rl.contains("energy") || rl.contains("component");
        }

        private static String cleanCpRange(String cp) {
            if (cp == null) return "";
            return cp.replaceAll("(?i)\\s*•?\\s*\\(?(?:gold|green|no)\\s+outline\\)?", "").trim();
        }

        public static String getPokemonShinySymbol(String speciesName, boolean taskShinyPossible) {
            if (speciesName == null || speciesName.isEmpty()) return "";
            String lower = speciesName.toLowerCase().replaceAll("\\s+\\d+$", "").trim();
            if (!taskShinyPossible && !LIVE_SHINY_ELIGIBLE_SPECIES.contains(lower)) {
                return "";
            }
            // 1. Check if species is currently non-shiny in Pokémon GO (verified from live Oct 2, 2026 LeekDuck)
            if (LIVE_NON_SHINY_SPECIES.contains(lower) && !LIVE_SHINY_ELIGIBLE_SPECIES.contains(lower)) {
                return "";
            }
            // 2. Active Event-Boosted Shinies (Oct 2, 2026: Smoliv & Slugma) + Permaboosted (1/64–1/128) -> Two Stars (✨✨)
            if (isBoostedShinySpecies(lower)) {
                return "✨✨";
            }
            // 3. Standard Shiny Eligible (including Oct 2026 Shiny Applin debut & research encounters) -> Single Star (✨)
            return "✨";
        }

        private static boolean isBoostedShinySpecies(String lower) {
            if (LIVE_EVENT_BOOSTED_SHINIES.contains(lower)) {
                return true;
            }
            if (lower.startsWith("alolan ") && !lower.equals("alolan diglett") && !lower.equals("alolan geodude") && !lower.equals("alolan rattata")) {
                return true;
            }
            if (lower.startsWith("galarian ") || lower.startsWith("hisuian ")) {
                return true;
            }
            String[] boosted = new String[]{
                    "smoliv", "slugma",
                    "spinda", "lapras", "aerodactyl", "sneasel", "scyther", "feebas", "clamperl",
                    "mawile", "sableye", "vullaby", "gible", "pineco", "onix", "lickitung",
                    "chansey", "gligar", "skarmory", "absol", "bronzor", "shinx", "riolu",
                    "timburr", "klink", "espurr", "druddigon", "pawniard", "pancham", "furfrou",
                    "rockruff", "ditto", "smeargle", "meltan", "cryogonal", "audino", "chimecho",
                    "spiritomb"
            };
            for (int i = 0; i < boosted.length; i++) {
                if (lower.equals(boosted[i])) return true;
            }
            return false;
        }

        private static String inferOutlineType(String cat, String reward, String cp) {
            String c = (cat != null ? cat : "").toLowerCase();
            String r = ((reward != null ? reward : "") + " " + (cp != null ? cp : "")).toLowerCase();
            if (c.contains("event") || r.contains("gold outline")) return "EVENT";
            if (c.contains("bonus") || r.contains("daily bonus") || r.contains("green outline")) return "BONUS";
            return "NORMAL";
        }

        public String getOutlineBadgeLabel() {
            if ("EVENT".equals(outlineType)) return "🟨 EVENT";
            if ("BONUS".equals(outlineType)) return "🟩 BONUS";
            return "⬜ NORMAL";
        }

        private static String buildMetaHighlight(String task, String reward) {
            String r = (task + " " + reward).toLowerCase();
            if (r.contains("beldum") || r.contains("larvitar") || r.contains("gible")) {
                return "★ META TARGETS: Beldum (Metagross #1 Steel Raid/ML) • Larvitar (Tyranitar Dark/Rock Raid) • Gible (Garchomp Ground/Dragon Raid & ML)";
            }
            if (r.contains("dratini") && r.contains("bagon") && r.contains("axew")) {
                return "★ META TARGETS: Bagon (Salamence Top Dragon Raid) • Dratini (Dragonite #2 Master League) • Axew (Haxorus Dragon DPS)";
            }
            if (r.contains("dratini")) {
                return "★ META TARGET: Dratini → Dragonite (Top Master League & Dragon Raid Attacker)";
            }
            if (r.contains("cranidos") || r.contains("shieldon")) {
                return "★ META TARGETS: Cranidos (Rampardos #1 Rock Raid DPS) • Shieldon (Bastiodon #1 Great League Tank)";
            }
            if (r.contains("sableye") || r.contains("vullaby") || r.contains("scraggy")) {
                return "★ META TARGETS: Vullaby (Mandibuzz #1 GL/UL Tank) • Sableye (Top GL Safe Swap) • Scraggy (Scrafty UL)";
            }
            if (r.contains("marill") || r.contains("darumaka")) {
                return "★ META TARGETS: Marill (Azumarill S-Tier Great League Bulk) • Darumaka (Darmanitan Top Fire Raid DPS)";
            }
            if (r.contains("stunfisk")) {
                return "★ META TARGET: Galarian Stunfisk (Top Great League & Ultra League XL Steel/Ground Tank)";
            }
            if (r.contains("treecko") || r.contains("torchic") || r.contains("mudkip")) {
                return "★ META TARGETS: Mudkip (Swampert #1 Water Raid/PvP) • Treecko (Mega Sceptile #1 Grass Raid) • Torchic (Mega Blaziken Fire/Fighting)";
            }
            if (r.contains("bulbasaur") || r.contains("charmander") || r.contains("squirtle")) {
                return "★ META TARGETS: Charmander (Mega Charizard Y #1 Fire Raid) • Bulbasaur (Mega Venusaur Grass) • Squirtle (Blastoise UL)";
            }
            if (r.contains("ralts")) {
                return "★ META TARGET: Ralts (Mega Gardevoir #1 Fairy Raid Attacker • Skip Doduo/Remoraid/Aron)";
            }
            if (r.contains("lapras") && r.contains("snorlax")) {
                return "★ META TARGETS: Metang (Metagross) • Gabite (Garchomp) • Snorlax (ML/Gym) • Lapras (PvP Bulk)";
            }
            if (r.contains("alolan marowak") && r.contains("aerodactyl")) {
                return "★ META TARGETS: Aerodactyl (Mega Rock Raid Attacker) • Alolan Marowak (Great League Core)";
            }
            if (r.contains("alolan marowak") || r.contains("wimpod") || r.contains("magikarp")) {
                return "★ META TARGETS: Magikarp (Mega Gyarados ML/Raid) • Wimpod (Golisopod UL) • Alolan Marowak (GL)";
            }
            if (r.contains("sneasel") || r.contains("mawile")) {
                return "★ META TARGETS: Sneasel (Weavile Top Ice/Dark Raid DPS) • Mawile (Mega Mawile & Great League)";
            }
            if (r.contains("applin") || r.contains("sweet apple") || r.contains("syrupy apple") || r.contains("tart apple")) {
                return "★ META TARGET: Applin & Evolution Apples → Hydrapple / Flapple / Appletun (Event Dragon/Grass Meta)";
            }
            if (r.contains("mega energy")) {
                return "★ META RESOURCE: Starter Mega Energy (Powers up #1 Fire/Water/Grass Mega Raid Attackers)";
            }
            if (r.contains("rare candy") || r.contains("poffin") || r.contains("silver pinap") || r.contains("fast tm") || r.contains("charged tm")) {
                return "★ META RESOURCE: Rare Candy / TM / Poffin / Silver Pinap (Best for Top Raid & PvP Meta)";
            }

            // Dynamic Pokebattler Raid Estimator + Max Battle + PvP Evaluation for any Pokémon in the reward pool!
            if (reward != null && !reward.toLowerCase().contains("ball") && !reward.toLowerCase().contains("stardust") && !reward.toLowerCase().contains("berry")) {
                String[] rawParts = reward.split("/");
                StringBuilder dynamicHits = new StringBuilder();
                int hitCount = 0;
                for (int i = 0; i < rawParts.length; i++) {
                    String cand = rawParts[i].replaceAll("\\([^\\)]*\\)", "").trim();
                    if (cand.length() < 3) continue;
                    List<PokemonMetaAnalyzer.PokemonScanReport> matches = PokemonMetaAnalyzer.searchPokemonByName(cand);
                    if (!matches.isEmpty()) {
                        PokemonMetaAnalyzer.PokemonScanReport rep = matches.get(0);
                        PokemonMetaAnalyzer.PokemonMetaEntry e = rep.entry;
                        if (e != null && e.name.equalsIgnoreCase(cand) && (rep.effectiveIsMeta || rep.effectiveTier.startsWith("S") || rep.effectiveTier.startsWith("A"))) {
                            if (hitCount > 0) dynamicHits.append(" • ");
                            dynamicHits.append(e.name).append(" [").append(rep.effectiveTier).append(" Tier: ").append(e.raidRole).append("]");
                            hitCount++;
                        }
                    }
                }
                if (hitCount > 0) {
                    return "★ RAID / MAX / PVP META: " + dynamicHits.toString();
                }
            }
            return "";
        }
    }

    private static final String DB_NAME = "pokemate_companion_v21.db";
    private static final int DB_VERSION = 21;
    private static PokeMateDatabase instance;

    // Live Event-Boosted & Shiny-Eligibility Sets synced with October 2, 2026 LeekDuck (Harvest Festival 2026 & Taken Over)
    private static final Set<String> LIVE_EVENT_BOOSTED_SHINIES = new HashSet<String>();
    private static final Set<String> LIVE_SHINY_ELIGIBLE_SPECIES = new HashSet<String>();
    private static final Set<String> LIVE_NON_SHINY_SPECIES = new HashSet<String>();

    static {
        // Active October 2, 2026 Event-Boosted Shinies:
        // - Harvest Festival 2026: Applin Picking (Sept 29 – Oct 5, 2026): Increased chance to encounter Shiny Smoliv
        // - Harvest Festival: Taken Over (Oct 2 – Oct 5, 2026): Slugma encounters have an increased chance of being Shiny
        LIVE_EVENT_BOOSTED_SHINIES.add("smoliv");
        LIVE_EVENT_BOOSTED_SHINIES.add("slugma");

        // Confirmed Shiny Debut in Harvest Festival 2026: Applin Picking (Sept 29 – Oct 5, 2026) & Taken Over (Oct 2 – Oct 5, 2026)
        LIVE_SHINY_ELIGIBLE_SPECIES.add("applin");
        LIVE_SHINY_ELIGIBLE_SPECIES.add("smoliv");
        LIVE_SHINY_ELIGIBLE_SPECIES.add("foongus");
        LIVE_SHINY_ELIGIBLE_SPECIES.add("skwovet");
        LIVE_SHINY_ELIGIBLE_SPECIES.add("shroodle");

        // Non-shiny encounters in the current October 2, 2026 Field Research pool
        LIVE_NON_SHINY_SPECIES.add("blipbug");
        LIVE_NON_SHINY_SPECIES.add("arrokuda");
        LIVE_NON_SHINY_SPECIES.add("tarountula");
        LIVE_NON_SHINY_SPECIES.add("nacli");
    }

    public static synchronized PokeMateDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new PokeMateDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private PokeMateDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS research_tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "category TEXT, " +
                "task_name TEXT, " +
                "reward TEXT, " +
                "cp_range TEXT, " +
                "shiny_possible INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS telemetry_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "action_type TEXT, " +
                "details TEXT)");

        seedCurrentActiveTasks(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS research_tasks");
        onCreate(db);
    }

    /**
     * Seeds strictly 1 single authoritative current pool per task (verified from live LeekDuck)
     * so there are NEVER duplicate tasks or conflicting pools for the same task!
     */
    private void seedCurrentActiveTasks(SQLiteDatabase db) {
        // 1. CURRENT EVENT TASKS (🟨 Oct 2, 2026 — Harvest Festival 2026: Applin Picking & Taken Over)
        ins(db, "Event", "Catch 5 Grass-type Pokémon", "Foongus / Skwovet / Smoliv", "CP 335–419 (100% IV)", 1);
        ins(db, "Event", "Catch 15 Grass-type Pokémon", "Applin", "CP 277–306 (100% IV)", 1);
        ins(db, "Event", "Use 10 Berries to help catch Pokémon", "Smoliv", "CP 335–366 (100% IV)", 1);
        ins(db, "Event", "Use 20 Berries to help catch Pokémon", "Sweet Apple ×1 / Syrupy Apple ×1 / Tart Apple ×1", "Event Evolution Item", 0);
        ins(db, "Event", "Defeat 2 Team GO Rocket Grunts", "Mysterious Component ×1", "Taken Over Event", 0);
        ins(db, "Event", "Purify 3 Shadow Pokémon", "Fast TM ×1 / Charged TM ×1", "Taken Over Event", 0);

        // 2. CURRENT NORMAL SEASONAL TASKS (⬜ Seasonal Field Research — Strictly 1 Pool per Task!)
        // Throwing Tasks
        ins(db, "Throw", "Make 3 Excellent Throws in a row", "Larvitar / Beldum / Gible", "CP 418–477 (100% IV)", 1);
        ins(db, "Throw", "Make 2 Excellent Throws", "Dratini", "CP 397–430 (100% IV)", 1);
        ins(db, "Throw", "Make an Excellent Throw", "Great Ball ×5 / Pinap Berry ×2 / Ultra Ball ×2 / Stardust ×500", "Item / Resource Pool", 0);
        ins(db, "Throw", "Make 5 Great Curveball Throws in a row", "Spinda", "CP 486–523 (100% IV • Pattern #4/#5)", 1);
        ins(db, "Throw", "Make 3 Great Curveball Throws in a row", "Rare Candy ×3 / Golden Razz Berry ×2 / Ultra Ball ×10 / Stardust ×1500", "High-Value Resource Pool", 0);
        ins(db, "Throw", "Make 3 Great Curveball Throws", "Rare Candy ×1 / Pinap Berry ×3 / Ultra Ball ×5 / Stardust ×1000", "Resource Pool", 0);
        ins(db, "Throw", "Make 3 Great Throws in a row", "Lileep / Anorith", "CP 517–654 (100% IV)", 1);
        ins(db, "Throw", "Make 3 Great Throws", "Omanyte / Kabuto / Clamperl / Elgyem (+1 more)", "CP 405–662 (100% IV)", 1);
        ins(db, "Throw", "Make 5 Great Throws", "Machop / Gastly / Mankey (+2 more)", "CP 498–548 (100% IV)", 1);
        ins(db, "Throw", "Make 10 Curveball Throws", "Alolan Geodude / Electabuzz / Makuhita / Swablu (+6 more)", "CP 258–1000 (100% IV)", 1);
        ins(db, "Throw", "Make 5 Curveball Throws in a row", "Great Ball ×5 / Razz Berry ×6 / Pinap Berry ×2 / Stardust ×500", "Item / Resource Pool", 0);
        ins(db, "Throw", "Make 3 Curveball Throws", "Psyduck / Nosepass", "CP 425–474 (100% IV)", 1);
        ins(db, "Throw", "Make 2 Nice Curveball Throws in a row", "Poké Ball ×5 / Razz Berry ×3 / Pinap Berry ×1 / Stardust ×200", "Item / Resource Pool", 0);
        ins(db, "Throw", "Make 10 Nice Throws", "Galarian Stunfisk / Stunfisk / Magikarp / Magmar (+6 more)", "CP 117–1026 (100% IV)", 1);
        ins(db, "Throw", "Make 5 Nice Throws", "Diglett / Alolan Diglett / Sudowoodo", "CP 289–920 (100% IV)", 1);
        ins(db, "Throw", "Make 3 Nice Throws in a row", "Great Ball ×5 / Pinap Berry ×2 / Ultra Ball ×2 / Stardust ×500", "Item / Resource Pool", 0);

        // Catching Tasks
        ins(db, "Catch", "Catch a Dragon-type Pokémon", "Dratini / Bagon / Axew", "CP 397–586 (or Rare Candy ×3)", 1);
        ins(db, "Catch", "Catch a Ditto", "Rare Candy ×3 / Golden Razz Berry ×2 / Ultra Ball ×10 / Stardust ×1500", "High-Value Resource Pool", 0);
        ins(db, "Catch", "Catch 7 different species of Pokémon", "Marill / Darumaka / Psyduck / Teddiursa (+8 more)", "CP 197–618 (100% IV)", 1);
        ins(db, "Catch", "Catch 7 Pokémon", "Magikarp / Stufful / Wimpod", "CP 117–589 (100% IV)", 1);
        ins(db, "Catch", "Catch 5 Pokémon with Weather Boost", "Vulpix / Poliwag / Wingull / Hippopotas (+3 more)", "CP 328–583 (100% IV)", 1);
        ins(db, "Catch", "Catch 5 Pokémon", "Bidoof / Bunnelby / Pidove (+2 more)", "CP 237–363 (100% IV)", 1);
        ins(db, "Catch", "Catch 8 Pokémon", "Rare Candy ×1 / Stardust ×1000", "Sponsored Research Pool", 0);
        ins(db, "Catch", "Catch 10 Pokémon", "Poké Ball ×5 / Razz Berry ×3 / Pinap Berry ×1 / Stardust ×200", "Item / Resource Pool", 0);
        ins(db, "Catch", "Catch 10 Pokémon with Weather Boost", "Great Ball ×5 / Razz Berry ×6 / Pinap Berry ×2 / Stardust ×500", "Item / Resource Pool", 0);
        ins(db, "Catch", "Catch 25 Pokémon", "Rare Candy ×1", "100% Guaranteed Resource", 0);
        ins(db, "Catch", "Catch 50 Pokémon", "Poffin ×1", "100% Guaranteed Resource", 0);
        ins(db, "Catch", "Catch 10 Grass-type Pokémon", "Sunkern / Seedot / Shroomish / Petilil (+2 more)", "CP 169–588 (or Venusaur/Sceptile Mega Energy)", 1);
        ins(db, "Catch", "Catch 10 Fire-type Pokémon", "Charizard Mega Energy ×10 / Blaziken Mega Energy ×10", "Mega Energy / Item Pool", 0);
        ins(db, "Catch", "Catch 10 Water-type Pokémon", "Blastoise Mega Energy ×10 / Swampert Mega Energy ×10", "Mega Energy / Item Pool", 0);
        ins(db, "Catch", "Catch 10 Normal-type Pokémon", "Pidgeot Mega Energy ×10 / Great Ball ×5 / Stardust ×500", "Mega Energy / Item Pool", 0);
        ins(db, "Catch", "Catch 10 Ghost-type Pokémon", "Gastly / Shuppet / Duskull / Drifloon (+2 more)", "CP 302–527 (100% IV)", 1);
        ins(db, "Catch", "Catch 10 Dark-type Pokémon", "Sneasel / Murkrow / Poochyena / Purrloin (+2 more)", "CP 290–879 (100% IV)", 1);
        ins(db, "Catch", "Use 5 Berries to help catch Pokémon", "Great Ball ×5 / Razz Berry ×6 / Pinap Berry ×2 / Stardust ×500", "Item / Resource Pool", 0);

        // Battling, Raid & Rocket Tasks
        ins(db, "Raid", "Defeat a Team GO Rocket Grunt", "Arbok / Meowth / Weezing", "CP 320–983 (100% IV)", 1);
        ins(db, "Raid", "Defeat 3 Team GO Rocket Grunts", "Sableye / Scraggy / Vullaby", "CP 568–632 (100% IV)", 1);
        ins(db, "Raid", "Win a raid", "Lapras / Snorlax / Metang / Gabite (+8 more)", "CP 738–1382 (100% IV)", 1);
        ins(db, "Raid", "Win a three-star raid or higher", "Tirtouga / Archen", "CP 639–789 (100% IV)", 1);
        ins(db, "Raid", "Win 5 raids", "Aerodactyl / Alolan Exeggutor / Alolan Marowak", "CP 788–1292 (100% IV)", 1);
        ins(db, "Raid", "Battle in a raid", "Rare Candy ×1", "100% Guaranteed Resource", 0);
        ins(db, "Raid", "Battle in 3 raids", "Poffin ×1", "100% Guaranteed Resource", 0);
        ins(db, "Raid", "Win a Max Battle", "Stardust ×1000", "100% Guaranteed Resource", 0);

        // Exploring, Hatching & PokéStop Tasks
        ins(db, "Explore", "Spin 3 PokéStops or Gyms", "Ralts / Aron / Doduo / Remoraid", "CP 231–562 (100% IV)", 1);
        ins(db, "Explore", "Spin 5 PokéStops or Gyms", "Growlithe / Hisuian Growlithe / Slowpoke / Galarian Slowpoke", "CP 526–575 (100% IV)", 1);
        ins(db, "Explore", "Spin 5 PokéStops", "Sneasel / Galarian Zigzagoon / Alolan Sandshrew / Dunsparce (+13 more)", "CP 218–879 (100% IV)", 1);
        ins(db, "Explore", "Hatch an Egg", "Alolan Marowak / Scyther / Stufful / Wimpod (+10 more)", "CP 215–1160 (100% IV)", 1);
        ins(db, "Explore", "Hatch 2 Eggs", "Sneasel / Mawile / Feebas", "CP 117–879 (or Rare Candy ×1)", 1);
        ins(db, "Explore", "Hatch 5 Eggs", "Poffin ×1", "100% Guaranteed Resource", 0);
        ins(db, "Explore", "Explore 2 km", "Lapras / Growlithe / Galarian Ponyta / Ponyta (+6 more)", "CP 526–1131 (100% IV)", 1);
        ins(db, "Explore", "Take a snapshot of a wild Pokémon", "Trapinch / Croagunk / Cottonee", "CP 300–546 (100% IV)", 1);
        ins(db, "Explore", "Earn 10000 Stardust", "Silver Pinap Berry ×1", "100% Guaranteed Resource", 0);
        ins(db, "Explore", "Earn 10000 XP", "Golden Razz Berry ×1", "100% Guaranteed Resource", 0);

        // Power Up, Training & Buddy Tasks
        ins(db, "Power/Buddy", "Power up Pokémon 3 times", "Bulbasaur / Charmander / Squirtle", "CP 405–477 (100% IV)", 1);
        ins(db, "Power/Buddy", "Power up Pokémon 5 times", "Snivy / Tepig / Oshawott", "CP 364–461 (or Mega Energy ×10)", 1);
        ins(db, "Power/Buddy", "Power up Pokémon 7 times", "Rowlet / Litten / Popplio", "CP 444–483 (100% IV)", 1);
        ins(db, "Power/Buddy", "Power up Pokémon 15 times", "Rare Candy ×3", "100% Guaranteed Resource", 0);
        ins(db, "Power/Buddy", "Evolve a Pokémon", "Eevee / Seadra / Ursaring / Magcargo (+4 more)", "CP 459–1262 (100% IV)", 1);
        ins(db, "Power/Buddy", "Earn 2 Candies walking with your buddy", "Jigglypuff / Buneary / Glameow / Bunnelby (+2 more)", "CP 237–789 (100% IV)", 1);
        ins(db, "Power/Buddy", "Earn 3 Candies walking with your buddy", "Galarian Stunfisk / Stunfisk", "CP 887–926 (100% IV)", 1);
        ins(db, "Power/Buddy", "Send 3 Gifts and add a sticker to each", "Clefairy / Jigglypuff / Eevee / Togetic", "CP 310–733 (100% IV)", 1);
        ins(db, "Power/Buddy", "Send 3 Gifts to friends", "Rare Candy ×1 / Stardust ×1000", "Sponsored Research Pool", 0);
        ins(db, "Power/Buddy", "Trade a Pokémon", "Machoke / Kadabra / Haunter / Poliwhirl", "CP 608–879 (100% IV)", 1);

        // 3. DAILY BONUS TASKS (🟩 Daily Bonus Field Research)
        ins(db, "Bonus", "Catch 3 Pokémon", "Poké Ball ×5 / Razz Berry ×3 / Stardust ×500", "Daily Bonus", 0);
        ins(db, "Bonus", "Use 3 Berries to help catch Pokémon", "Stardust ×500 / Poké Ball ×5", "Daily Bonus", 0);
        ins(db, "Bonus", "Transfer 10 Pokémon", "Poké Ball ×10 / Stardust ×500", "Daily Bonus", 0);
        ins(db, "Bonus", "Send a Gift to a friend", "Stardust ×500 / Pinap Berry ×2", "Daily Bonus", 0);
        ins(db, "Bonus", "Earn a Heart with your buddy", "Stardust ×500 / Razz Berry ×3", "Daily Bonus", 0);
        ins(db, "Bonus", "Take a snapshot of your buddy", "Stardust ×500 / Great Ball ×3", "Daily Bonus", 0);
    }

    private void ins(SQLiteDatabase db, String cat, String task, String reward, String cp, int shiny) {
        ContentValues cv = new ContentValues();
        cv.put("category", cat);
        cv.put("task_name", task);
        cv.put("reward", reward);
        cv.put("cp_range", cp);
        cv.put("shiny_possible", shiny);
        db.insert("research_tasks", null, cv);
    }

    /**
     * Queries ResearchItems with:
     * 1. Strict deduplication (never returns the same task twice!)
     * 2. Outline-aware exact matching when scanning (`EVENT::Catch 15 Grass-type Pokémon` vs `NORMAL::Make 3 Great Throws` vs `BONUS::...`)
     *    so scanned tasks never mix Event pools with Normal pools or pull in partial substring tasks!
     */
    public List<ResearchItem> queryResearchItems(String searchQuery, String categoryFilter) {
        List<ResearchItem> list = new ArrayList<ResearchItem>();
        Set<String> seenUniqueTasks = new HashSet<String>();
        SQLiteDatabase db = getReadableDatabase();
        String q = searchQuery != null ? searchQuery.trim() : "";
        String cat = categoryFilter != null ? categoryFilter.trim() : "ALL";

        Cursor c = db.rawQuery("SELECT category, task_name, reward, cp_range, shiny_possible FROM research_tasks ORDER BY id ASC", null);
        while (c.moveToNext()) {
            String cCat = c.getString(0);
            String cTask = c.getString(1);
            String cReward = c.getString(2);
            String cCp = c.getString(3);
            boolean cShiny = c.getInt(4) == 1;

            if (cTask == null || cReward == null) continue;
            cTask = cTask.trim();
            cReward = cReward.replace("×?", "×1").replace("x?", "×1").trim();

            // Never allow a placeholder "???" task from LeekDuck to appear in the UI!
            if (cTask.isEmpty() || cTask.contains("??") || cTask.equals("?") || cReward.contains("???")) {
                continue;
            }

            ResearchItem candidateItem = new ResearchItem(cCat, cTask, cReward, cCp, cShiny);

            if (!"ALL".equalsIgnoreCase(cat)) {
                if ("SHINY".equalsIgnoreCase(cat)) {
                    if (!cShiny) continue;
                } else if ("META".equalsIgnoreCase(cat)) {
                    if (!candidateItem.isMeta) continue;
                } else if ("Event".equalsIgnoreCase(cat)) {
                    if (!"EVENT".equals(candidateItem.outlineType)) continue;
                } else if ("Bonus".equalsIgnoreCase(cat)) {
                    if (!"BONUS".equals(candidateItem.outlineType)) continue;
                } else if (!cCat.toLowerCase().contains(cat.toLowerCase())) {
                    continue;
                }
            }

            String normCandidateTask = normalizeForMatch(cTask);

            if (q.length() > 0) {
                if (q.contains("|") || q.contains("::")) {
                    String[] parts = q.split("\\|");
                    boolean matchedExact = false;
                    for (int p = 0; p < parts.length; p++) {
                        String rawPart = parts[p].trim();
                        if (rawPart.length() == 0) continue;

                        String requiredOutline = null;
                        String taskPart = rawPart;
                        if (rawPart.contains("::")) {
                            String[] splitType = rawPart.split("::", 2);
                            requiredOutline = splitType[0].trim().toUpperCase();
                            taskPart = splitType[1].trim();
                        }

                        String normSub = normalizeForMatch(taskPart);
                        if (normSub.length() == 0) continue;

                        // Exact normalized task name match so "Make 3 Great Throws" never matches "Make 3 Great Throws in a row"!
                        if (normCandidateTask.equals(normSub)) {
                            if (requiredOutline == null || requiredOutline.equals(candidateItem.outlineType)) {
                                matchedExact = true;
                                break;
                            }
                        }
                    }
                    if (!matchedExact) continue;
                } else {
                    String normQ = normalizeForMatch(q);
                    String combined = normalizeForMatch(cTask + " " + cReward + " " + cCat + " " + candidateItem.outlineType + " " + candidateItem.metaHighlight);
                    if (!combined.contains(normQ)) continue;
                }
            }

            // Strict Deduplication by normalized task name + outline so the same research NEVER appears twice!
            String dedupKey = candidateItem.outlineType + "::" + normCandidateTask;
            if (seenUniqueTasks.contains(dedupKey) || seenUniqueTasks.contains(normCandidateTask)) {
                continue;
            }
            seenUniqueTasks.add(dedupKey);
            seenUniqueTasks.add(normCandidateTask);
            list.add(candidateItem);
        }
        c.close();

        // Always keep Daily Bonus tasks at the back of the list (All -> Event -> Meta/Normal -> Bonus)
        List<ResearchItem> nonBonus = new ArrayList<ResearchItem>();
        List<ResearchItem> bonusOnly = new ArrayList<ResearchItem>();
        for (int i = 0; i < list.size(); i++) {
            ResearchItem ri = list.get(i);
            if ("BONUS".equals(ri.outlineType)) {
                bonusOnly.add(ri);
            } else {
                nonBonus.add(ri);
            }
        }
        nonBonus.addAll(bonusOnly);
        return nonBonus;
    }

    private String normalizeForMatch(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replace("é", "e")
                .replace("pokémon", "pokemon")
                .replace("pokestop", "pokestop")
                .replace("pokéstops", "pokestops")
                .replace("three star", "3 star")
                .replace("three-star", "3 star")
                .replace("-", " ")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Outline-Aware Field Research Screen Scanner:
     * Inspects BOTH the OCR text lines (with their Y-coordinates) AND the actual screenshot Bitmap
     * to detect whether each card on your Pokémon GO screen has:
     * - A Gold/Orange Border or "EVENT" tag -> matches the EVENT pool (`EVENT::TaskName`)
     * - A Green Border or "BONUS" tag -> matches the BONUS pool (`BONUS::TaskName`)
     * - No Colored Outline (Plain White Card) -> matches the NORMAL Seasonal pool (`NORMAL::TaskName`)
     * And strictly deduplicates so even if you have 2 or 3 identical tasks on screen, each task appears only ONCE!
     */
    public String matchTasksFromScreenOcrWithVision(String rawOcrJson, String rawParsedText, Bitmap scaledScreenBmp) {
        if (rawParsedText == null || rawParsedText.trim().length() == 0) return "";
        List<ResearchItem> allTasks = queryResearchItems("", "ALL");

        List<OcrLineEntry> lineEntries = parseOcrLinesWithY(rawOcrJson, rawParsedText);
        List<String> uniqueMatchedQueries = new ArrayList<String>();
        Set<String> seenTaskNames = new HashSet<String>();

        for (int i = 0; i < lineEntries.size(); i++) {
            OcrLineEntry entry = lineEntries.get(i);
            String normLine = normalizeForMatch(entry.text);
            if (normLine.length() < 6) continue;

            if (normLine.equals("field research") || normLine.equals("today") || normLine.equals("special")
                    || normLine.equals("event") || normLine.equals("bonus")
                    || normLine.startsWith("complete ") || normLine.contains("claim reward")) {
                continue;
            }

            // Only check the OCR line IMMEDIATELY ABOVE this task line for an "EVENT" or "BONUS" pill badge
            // (since in Pokémon GO the EVENT/BONUS tag is always at the top-left of the card above the task text)
            String aboveBadgeText = "";
            if (i > 0) {
                OcrLineEntry prev = lineEntries.get(i - 1);
                int maxBadgeDist = scaledScreenBmp != null ? Math.round(scaledScreenBmp.getHeight() * 0.085f) : 140;
                if (entry.topY <= 0 || prev.topY <= 0 || Math.abs(entry.topY - prev.topY) <= maxBadgeDist) {
                    aboveBadgeText = prev.text;
                }
            }

            String detectedCardOutline = detectCardOutlineTypeOnScreen(scaledScreenBmp, entry.topY, aboveBadgeText);

            // Check if the task text wraps onto the immediately following OCR line on the same card
            ResearchItem bestMatch = null;
            if (i + 1 < lineEntries.size()) {
                OcrLineEntry next = lineEntries.get(i + 1);
                String normNext = normalizeForMatch(next.text);
                int maxWrapDist = scaledScreenBmp != null ? Math.round(scaledScreenBmp.getHeight() * 0.065f) : 110;
                boolean closeVertically = (entry.topY <= 0 || next.topY <= 0 || Math.abs(next.topY - entry.topY) <= maxWrapDist);
                boolean isNextContinuation = normNext.length() >= 2
                        && !normNext.startsWith("catch ")
                        && !normNext.startsWith("make ")
                        && !normNext.startsWith("spin ")
                        && !normNext.startsWith("hatch ")
                        && !normNext.startsWith("win ")
                        && !normNext.startsWith("battle ")
                        && !normNext.startsWith("defeat ")
                        && !normNext.startsWith("power ")
                        && !normNext.startsWith("evolve ")
                        && !normNext.startsWith("earn ")
                        && !normNext.startsWith("send ")
                        && !normNext.startsWith("trade ")
                        && !normNext.startsWith("take ")
                        && !normNext.startsWith("use ")
                        && !normNext.startsWith("explore ")
                        && !normNext.startsWith("purify ")
                        && !normNext.startsWith("transfer ")
                        && !normNext.equals("event")
                        && !normNext.equals("bonus")
                        && !normNext.contains("claim reward");
                if (closeVertically && isNextContinuation) {
                    String combinedTwoLine = (normLine + " " + normNext).trim();
                    bestMatch = findBestMatchingTask(combinedTwoLine, detectedCardOutline, allTasks);
                }
            }

            if (bestMatch == null) {
                bestMatch = findBestMatchingTask(normLine, detectedCardOutline, allTasks);
            }
            if (bestMatch != null) {
                String normBestName = normalizeForMatch(bestMatch.taskName);
                // Strict deduplication by task name: even if you have 2 or 3 of the exact same task on screen, show it ONCE!
                if (!seenTaskNames.contains(normBestName)) {
                    seenTaskNames.add(normBestName);
                    uniqueMatchedQueries.add(bestMatch.outlineType + "::" + bestMatch.taskName);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < uniqueMatchedQueries.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(uniqueMatchedQueries.get(i));
        }
        return sb.toString();
    }

    public String matchTasksFromScreenOcr(String rawOcrText) {
        return matchTasksFromScreenOcrWithVision("", rawOcrText, null);
    }

    private static class OcrLineEntry {
        final String text;
        final int topY;

        OcrLineEntry(String text, int topY) {
            this.text = text;
            this.topY = topY;
        }
    }

    private List<OcrLineEntry> parseOcrLinesWithY(String rawJson, String fallbackText) {
        List<OcrLineEntry> list = new ArrayList<OcrLineEntry>();
        if (rawJson != null && rawJson.contains("\"LineText\"")) {
            Pattern linePat = Pattern.compile("\"LineText\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"[\\s\\S]*?\"MinTop\"\\s*:\\s*([0-9.]+)");
            Matcher m = linePat.matcher(rawJson);
            while (m.find()) {
                String txt = m.group(1).replace("\\\"", "\"").trim();
                int y = Math.round(Float.parseFloat(m.group(2)));
                list.add(new OcrLineEntry(txt, y));
            }
        }
        if (list.isEmpty() && fallbackText != null) {
            String[] lines = fallbackText.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                list.add(new OcrLineEntry(lines[i], -1));
            }
        }
        return list;
    }

    /**
     * Inspects the Field Research card on screen at `lineY` (and the OCR badge line above it) to determine
     * whether the card has:
     * - Gold/Yellow-Orange Outline or "EVENT" badge -> "EVENT"
     * - Green Outline or "BONUS" badge -> "BONUS"
     * - No Outline (Plain White/Cream Card) -> "NORMAL"
     */
    private String detectCardOutlineTypeOnScreen(Bitmap bmp, int lineY, String aboveBadgeText) {
        String badgeUpper = (aboveBadgeText != null ? aboveBadgeText : "").trim().toUpperCase();
        if (badgeUpper.equals("EVENT") || badgeUpper.contains("EVENT")) return "EVENT";
        if (badgeUpper.equals("BONUS") || badgeUpper.contains("BONUS")) return "BONUS";

        if (bmp == null || lineY <= 0 || lineY >= bmp.getHeight()) {
            return "NORMAL";
        }

        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int yStart = Math.max(4, lineY - (int) (h * 0.052f));
        int yEnd = Math.min(h - 4, lineY + (int) (h * 0.042f));

        int goldBorderPixels = 0;
        int greenBorderPixels = 0;
        int totalBorderSamples = 0;

        // Sample left card border + top-left badge region (x = 2%..15% of width) and right card border (x = 91%..97%)
        int xLeftStart = Math.max(2, (int) (w * 0.025f));
        int xLeftEnd = Math.min(w - 2, (int) (w * 0.145f));
        int xRightStart = Math.max(2, (int) (w * 0.91f));
        int xRightEnd = Math.min(w - 2, (int) (w * 0.975f));

        for (int y = yStart; y <= yEnd; y += 3) {
            for (int x = xLeftStart; x <= xLeftEnd; x += 3) {
                int px = bmp.getPixel(x, y);
                int r = Color.red(px);
                int g = Color.green(px);
                int b = Color.blue(px);
                if (r > 212 && g >= 130 && g <= 215 && b < 92 && (r - b) > 125) {
                    goldBorderPixels++;
                } else if (g > 170 && r < 115 && b < 138 && (g - r) > 65) {
                    greenBorderPixels++;
                }
                totalBorderSamples++;
            }
            for (int x = xRightStart; x <= xRightEnd; x += 3) {
                int px = bmp.getPixel(x, y);
                int r = Color.red(px);
                int g = Color.green(px);
                int b = Color.blue(px);
                if (r > 212 && g >= 130 && g <= 215 && b < 92 && (r - b) > 125) {
                    goldBorderPixels++;
                } else if (g > 170 && r < 115 && b < 138 && (g - r) > 65) {
                    greenBorderPixels++;
                }
                totalBorderSamples++;
            }
        }

        if (totalBorderSamples > 0) {
            if ((float) goldBorderPixels / totalBorderSamples >= 0.065f) {
                return "EVENT";
            }
            if ((float) greenBorderPixels / totalBorderSamples >= 0.065f) {
                return "BONUS";
            }
        }

        return "NORMAL";
    }

    private ResearchItem findBestMatchingTask(String normLine, String preferredOutlineType, List<ResearchItem> allTasks) {
        ResearchItem bestMatch = null;
        int bestScore = 0;

        for (ResearchItem item : allTasks) {
            String normTask = normalizeForMatch(item.taskName);
            int outlineBonus = item.outlineType.equals(preferredOutlineType) ? 45 : 0;

            if (normLine.equals(normTask)) {
                int score = 1000 + outlineBonus;
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = item;
                }
            } else if (normLine.contains(normTask) || (normLine.length() >= 12 && normTask.contains(normLine))) {
                String lineNum = extractFirstNumber(normLine);
                String taskNum = extractFirstNumber(normTask);
                if (!lineNum.equals(taskNum)) continue;

                if (normTask.contains("in a row") != normLine.contains("in a row")) continue;
                if (normTask.contains("weather") != normLine.contains("weather")) continue;
                if (normTask.contains("curveball") != normLine.contains("curveball")) continue;
                if (normTask.contains("species") != normLine.contains("species")) continue;
                if (normTask.contains("gyms") != normLine.contains("gyms")) continue;

                int score = normTask.length() + 60 + outlineBonus;
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = item;
                }
            } else {
                String lineNum = extractFirstNumber(normLine);
                String taskNum = extractFirstNumber(normTask);
                if (!lineNum.equals(taskNum)) continue;
                if (normTask.contains("in a row") != normLine.contains("in a row")) continue;
                if (normTask.contains("weather") != normLine.contains("weather")) continue;
                if (normTask.contains("curveball") != normLine.contains("curveball")) continue;
                if (normTask.contains("species") != normLine.contains("species")) continue;
                if (normTask.contains("gyms") != normLine.contains("gyms")) continue;

                String[] taskTokens = normTask.split(" ");
                int hitTokens = 0;
                for (int t = 0; t < taskTokens.length; t++) {
                    String tok = taskTokens[t];
                    if (tok.matches("\\d+")) {
                        if (normLine.contains(tok)) hitTokens += 3;
                    } else if (tok.length() >= 4 && normLine.contains(tok)) {
                        hitTokens += 2;
                    }
                }
                if (hitTokens >= 6) {
                    int score = hitTokens + outlineBonus;
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = item;
                    }
                }
            }
        }
        return bestMatch;
    }

    private String extractFirstNumber(String s) {
        Matcher m = Pattern.compile("\\b(\\d+)\\b").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Live Sync from LeekDuck:
     * - Parses <div class="task-category"> blocks accurately
     * - Distinguishes Event category (Gold Outline) vs Seasonal categories (No Outline)
     * - Prioritizes Pokémon encounter pools over generic berry/ball fillers when a task has encounter rewards
     * - Guarantees strictly 1 single merged pool per unique task name!
     */
    public int syncLiveFromLeekDuck() {
        try {
            URL url = new URL("https://leekduck.com/research/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 16; Mobile)");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder htmlBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                htmlBuilder.append(line).append("\n");
            }
            reader.close();

            String html = htmlBuilder.toString();
            String[] categoryBlocks = html.split("<div class=\"task-category[^>]*>");
            if (categoryBlocks.length <= 2) return 0;

            Map<String, ScrapedTaskGroup> groupedTasks = new LinkedHashMap<String, ScrapedTaskGroup>();

            Pattern h2Pat = Pattern.compile("<h2[^>]*>([\\s\\S]*?)</h2>");
            Pattern taskPat = Pattern.compile("<span class=\"task-text\"[^>]*>([\\s\\S]*?)</span>");
            Pattern rewardLabelPat = Pattern.compile("<span class=\"reward-label\"[^>]*>([\\s\\S]*?)(?=<span class=\"cp-values\"|</li>)");
            Pattern maxCpPat = Pattern.compile("<span class=\"max-cp\">\\s*<div>Max CP</div>\\s*([0-9]+)");
            Pattern minCpPat = Pattern.compile("<span class=\"min-cp\">\\s*<div>Min CP</div>\\s*([0-9]+)");

            for (int cIdx = 1; cIdx < categoryBlocks.length; cIdx++) {
                String catBlock = categoryBlocks[cIdx];
                Matcher h2m = h2Pat.matcher(catBlock);
                String rawCatHeader = h2m.find() ? cleanHtml(h2m.group(1)) : "";
                boolean isEventCategory = isEventCategoryHeader(rawCatHeader);

                String[] taskItems = catBlock.split("<li class=\"task-item\">");
                for (int tIdx = 1; tIdx < taskItems.length; tIdx++) {
                    String taskHtml = taskItems[tIdx];
                    Matcher tm = taskPat.matcher(taskHtml);
                    if (!tm.find()) continue;

                    String taskName = cleanHtml(tm.group(1)).replaceAll("(?i)^event:\\s*", "").trim();
                    if (taskName.isEmpty()) continue;

                    String[] rewardChunks = taskHtml.split("<li class=\"reward\"");
                    List<String> encounterRewards = new ArrayList<String>();
                    List<String> nonEncounterRewards = new ArrayList<String>();
                    int minCp = -1;
                    int maxCp = -1;
                    boolean shiny = false;

                    for (int rIdx = 1; rIdx < rewardChunks.length; rIdx++) {
                        String rHtml = rewardChunks[rIdx];
                        boolean isEncounter = rHtml.contains("data-reward-type=\"encounter\"");
                        Matcher rm = rewardLabelPat.matcher(rHtml);
                        if (!rm.find()) continue;
                        String rName = cleanHtml(rm.group(1))
                                .replace("×?", "×1")
                                .replace("x?", "×1")
                                .trim();
                        if (rName.isEmpty() || rName.contains("???")) continue;

                        if (isEncounter) {
                            String cleanEncName = rName.replaceAll("(?i)^spinda\\s+\\d+$", "Spinda").trim();
                            if (!encounterRewards.contains(cleanEncName)) encounterRewards.add(cleanEncName);
                            boolean hasShinyBadge = rHtml.contains("class=\"shiny-badge\"");
                            if (hasShinyBadge) {
                                shiny = true;
                                LIVE_SHINY_ELIGIBLE_SPECIES.add(cleanEncName.toLowerCase());
                                LIVE_NON_SHINY_SPECIES.remove(cleanEncName.toLowerCase());
                            } else {
                                LIVE_NON_SHINY_SPECIES.add(cleanEncName.toLowerCase());
                            }
                            Matcher minM = minCpPat.matcher(rHtml);
                            Matcher maxM = maxCpPat.matcher(rHtml);
                            int rMin = minM.find() ? Integer.parseInt(minM.group(1)) : -1;
                            int rMax = maxM.find() ? Integer.parseInt(maxM.group(1)) : -1;
                            if (rMin > 0 && (minCp < 0 || rMin < minCp)) minCp = rMin;
                            if (rMax > 0 && (maxCp < 0 || rMax > maxCp)) maxCp = rMax;
                        } else {
                            if (!nonEncounterRewards.contains(rName)) nonEncounterRewards.add(rName);
                        }
                    }

                    // If LeekDuck has an unconfirmed "???" placeholder task (e.g., Taken Over Rocket tasks),
                    // expand Known Taken Over rewards into their real tasks and NEVER insert "???" into the database!
                    if (taskName.contains("??") || taskName.equals("?")) {
                        String joinedNonEnc = nonEncounterRewards.toString().toLowerCase();
                        if (rawCatHeader.toLowerCase().contains("taken over")
                                || joinedNonEnc.contains("mysterious component")
                                || joinedNonEnc.contains("fast tm")) {
                            ScrapedTaskGroup g1 = new ScrapedTaskGroup("Event", "Defeat 2 Team GO Rocket Grunts", "EVENT");
                            g1.rewards.add("Mysterious Component ×1");
                            groupedTasks.put(normalizeForMatch(g1.taskName), g1);

                            ScrapedTaskGroup g2 = new ScrapedTaskGroup("Event", "Purify 3 Shadow Pokémon", "EVENT");
                            g2.rewards.add("Fast TM ×1");
                            g2.rewards.add("Charged TM ×1");
                            groupedTasks.put(normalizeForMatch(g2.taskName), g2);
                        }
                        continue;
                    }

                    // If a task has Pokémon encounters, show the Pokémon encounter pool cleanly!
                    List<String> chosenRewards = !encounterRewards.isEmpty() ? encounterRewards : nonEncounterRewards;
                    if (chosenRewards.isEmpty()) continue;

                    String cat = isEventCategory ? "Event" : inferCategory(taskName);
                    String outlineType = isEventCategory ? "EVENT" : "NORMAL";
                    String groupKey = normalizeForMatch(taskName);

                    ScrapedTaskGroup grp = groupedTasks.get(groupKey);
                    if (grp == null) {
                        grp = new ScrapedTaskGroup(cat, taskName, outlineType);
                        groupedTasks.put(groupKey, grp);
                    }
                    for (String rw : chosenRewards) {
                        if (!grp.rewards.contains(rw)) {
                            grp.rewards.add(rw);
                        }
                    }
                    if (minCp > 0 && (grp.minCp < 0 || minCp < grp.minCp)) grp.minCp = minCp;
                    if (maxCp > 0 && (grp.maxCp < 0 || maxCp > grp.maxCp)) grp.maxCp = maxCp;
                    if (shiny) grp.shiny = true;
                }
            }

            if (groupedTasks.size() < 10) return 0;

            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete("research_tasks", null, null);

                int count = 0;
                for (ScrapedTaskGroup grp : groupedTasks.values()) {
                    StringBuilder rSummary = new StringBuilder();
                    int limit = Math.min(4, grp.rewards.size());
                    for (int j = 0; j < limit; j++) {
                        if (j > 0) rSummary.append(" / ");
                        rSummary.append(grp.rewards.get(j));
                    }
                    if (grp.rewards.size() > 4) {
                        rSummary.append(" (+").append(grp.rewards.size() - 4).append(" more)");
                    }

                    String cpRange = (grp.minCp > 0 && grp.maxCp > 0)
                            ? ("CP " + grp.minCp + "–" + grp.maxCp + " (100% IV)")
                            : "Item / Resource Pool";

                    ins(db, grp.category, grp.taskName, rSummary.toString(), cpRange, grp.shiny ? 1 : 0);
                    count++;
                }

                // Include the Daily Bonus tasks
                ins(db, "Bonus", "Catch 3 Pokémon", "Poké Ball ×5 / Razz Berry ×3 / Stardust ×500", "Daily Bonus", 0);
                ins(db, "Bonus", "Use 3 Berries to help catch Pokémon", "Stardust ×500 / Poké Ball ×5", "Daily Bonus", 0);
                ins(db, "Bonus", "Transfer 10 Pokémon", "Poké Ball ×10 / Stardust ×500", "Daily Bonus", 0);
                ins(db, "Bonus", "Send a Gift to a friend", "Stardust ×500 / Pinap Berry ×2", "Daily Bonus", 0);
                ins(db, "Bonus", "Earn a Heart with your buddy", "Stardust ×500 / Razz Berry ×3", "Daily Bonus", 0);
                ins(db, "Bonus", "Take a snapshot of your buddy", "Stardust ×500 / Great Ball ×3", "Daily Bonus", 0);

                db.setTransactionSuccessful();
                syncLiveEventBoostedShinies();
                return count;
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Checks live active event pages on LeekDuck to detect any newly announced event-boosted shinies
     * ("Increased chance to encounter Shiny <Pokemon>" / "<Pokemon> encounters will have an increased chance of being Shiny").
     */
    private void syncLiveEventBoostedShinies() {
        try {
            URL evUrl = new URL("https://leekduck.com/events/");
            HttpURLConnection conn = (HttpURLConnection) evUrl.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 16; Mobile)");
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = r.readLine()) != null) sb.append(ln).append("\n");
            r.close();

            Matcher linkM = Pattern.compile("href=\"(/events/[^\"]+)\"").matcher(sb.toString());
            List<String> links = new ArrayList<String>();
            while (linkM.find() && links.size() < 6) {
                String lk = linkM.group(1);
                if (!links.contains(lk)) links.add(lk);
            }

            Pattern p1 = Pattern.compile("(?i)increased chance to encounter Shiny\\s+([A-Z][a-zA-Z\\-']+)" );
            Pattern p2 = Pattern.compile("(?i)([A-Z][a-zA-Z\\-']+)\\s+encounters will have an increased chance of being Shiny");

            for (int i = 0; i < links.size(); i++) {
                try {
                    URL subUrl = new URL("https://leekduck.com" + links.get(i));
                    HttpURLConnection c2 = (HttpURLConnection) subUrl.openConnection();
                    c2.setConnectTimeout(4000);
                    c2.setReadTimeout(4000);
                    c2.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 16; Mobile)");
                    BufferedReader r2 = new BufferedReader(new InputStreamReader(c2.getInputStream(), "UTF-8"));
                    StringBuilder sb2 = new StringBuilder();
                    String l2;
                    while ((l2 = r2.readLine()) != null) sb2.append(l2).append(" ");
                    r2.close();
                    String cleanEv = cleanHtml(sb2.toString());
                    Matcher m1 = p1.matcher(cleanEv);
                    while (m1.find()) {
                        String sp = m1.group(1).trim().toLowerCase();
                        if (!sp.equals("featured") && !sp.equals("wild")) {
                            LIVE_EVENT_BOOSTED_SHINIES.add(sp);
                        }
                    }
                    Matcher m2 = p2.matcher(cleanEv);
                    while (m2.find()) {
                        String sp = m2.group(1).trim().toLowerCase();
                        LIVE_EVENT_BOOSTED_SHINIES.add(sp);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private boolean isEventCategoryHeader(String header) {
        if (header == null || header.isEmpty()) return false;
        String h = header.toLowerCase();
        if (h.contains("catching tasks") || h.contains("throwing tasks") || h.contains("battling tasks")
                || h.contains("exploring tasks") || h.contains("training tasks")
                || h.contains("buddy") || h.contains("friendship")
                || h.contains("team go rocket") || h.contains("sponsored")) {
            return false;
        }
        return true;
    }

    private static class ScrapedTaskGroup {
        final String category;
        final String taskName;
        final String outlineType;
        final List<String> rewards = new ArrayList<String>();
        int minCp = -1;
        int maxCp = -1;
        boolean shiny = false;

        ScrapedTaskGroup(String category, String taskName, String outlineType) {
            this.category = category;
            this.taskName = taskName;
            this.outlineType = outlineType;
        }
    }

    private String inferCategory(String taskName) {
        String lower = taskName.toLowerCase();
        if (lower.contains("throw") || lower.contains("curveball")) return "Throw";
        if (lower.contains("raid") || lower.contains("battle") || lower.contains("defeat") || lower.contains("grunt") || lower.contains("purify")) return "Raid";
        if (lower.contains("hatch") || lower.contains("spin") || lower.contains("explore") || lower.contains("snapshot") || lower.contains("stardust") || lower.contains("xp")) return "Explore";
        if (lower.contains("power up") || lower.contains("evolve") || lower.contains("buddy") || lower.contains("send") || lower.contains("trade")) return "Power/Buddy";
        return "Catch";
    }

    private String cleanHtml(String raw) {
        return raw.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&times;", "×")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public void logAction(String actionType, String details) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("action_type", actionType);
        cv.put("details", details);
        db.insert("telemetry_logs", null, cv);
    }
}
