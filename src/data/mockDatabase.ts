export interface FriendRecord {
  id: string;
  name: string;
  trainerCode: string;
  friendshipLevel: 1 | 2 | 3 | 4; // 1=Good, 2=Great, 3=Ultra, 4=Best
  daysToNextTier: number;
  lastGiftSent: string | null;
  giftableStatus: 'GIFTABLE' | 'SENT_TODAY' | 'UNOPENED_GIFT';
  friendGroup: 'ULTRA_BEST_PUSH' | 'LUCKY_CANDIDATES' | 'LOCAL_RAIDERS' | 'GENERAL';
  buddyPokemon: string;
  avatarColor: string;
}

export interface RewardSubItem {
  name: string;
  rewardType: 'ENCOUNTER' | 'ITEM' | 'MEGA_ENERGY';
  imageUrl: string;
  canBeShiny: boolean;
  minCp?: number;
  maxCp?: number;
  quantity?: string;
}

export interface ResearchTaskRecord {
  id: string;
  category?: string;
  taskName: string;
  reward: string;
  rewardType: 'ENCOUNTER' | 'ITEM' | 'MEGA_ENERGY';
  cpRange: string;
  shinyPossible: boolean;
  isHighValue: boolean;
  quickToComplete: boolean;
  source: 'Leek Duck' | 'Silph Road' | 'Manual Entry';
  dateAdded: string;
  isActive: boolean;
  rewardsList?: RewardSubItem[];
}

export interface AutomationLogRecord {
  id: string;
  timestamp: string;
  actionType: 'SESSION_START' | 'NODE_SCAN' | 'PROFILE_OPEN' | 'GIFT_SENT' | 'SKIP_UNOPENED' | 'SCROLL_SWIPE' | 'IV_SCREENSHOT' | 'THROW_GESTURE' | 'ERROR';
  trainerName: string;
  delayMs: number;
  coordinates: string;
  success: boolean;
  details: string;
}

export interface EggIncubatorSlot {
  id: string;
  eggTierKm: 2 | 5 | 7 | 10 | 12;
  incubatorType: 'INFINITE' | 'LIMITED_3X' | 'SUPER';
  currentKm: number;
  targetKm: number;
  possibleHatchPool: string[];
}

export interface TrainerAccountProfile {
  id: string;
  trainerName: string;
  team: 'Mystic' | 'Valor' | 'Instinct';
  level: number;
  bagItemsCount: number;
  bagMaxCapacity: number;
  giftsInInventory: number;
  dailyGiftsSentToday: number;
  buddyName: string;
  buddyCurrentKm: number;
  buddyTargetKm: number;
}

export const INITIAL_ACCOUNTS: TrainerAccountProfile[] = [
  {
    id: 'acc-1',
    trainerName: 'KantoVanguard99',
    team: 'Mystic',
    level: 48,
    bagItemsCount: 1840,
    bagMaxCapacity: 2000,
    giftsInInventory: 18,
    dailyGiftsSentToday: 42,
    buddyName: 'Shadow Metagross',
    buddyCurrentKm: 3.8,
    buddyTargetKm: 5.0,
  },
  {
    id: 'acc-2',
    trainerName: 'ShinyHunterKalos',
    team: 'Valor',
    level: 44,
    bagItemsCount: 1475,
    bagMaxCapacity: 1500, // 98.3% full - triggers Inventory Monitor alert!
    giftsInInventory: 12,
    dailyGiftsSentToday: 15,
    buddyName: 'Larvesta',
    buddyCurrentKm: 4.4,
    buddyTargetKm: 5.0,
  }
];

export const INITIAL_FRIENDS: FriendRecord[] = [
  {
    id: 'fr-1',
    name: 'AceTrainerElena',
    trainerCode: '4829 1049 8821',
    friendshipLevel: 3,
    daysToNextTier: 1,
    lastGiftSent: '2026-09-29 19:14',
    giftableStatus: 'GIFTABLE',
    friendGroup: 'ULTRA_BEST_PUSH',
    buddyPokemon: 'Rayquaza',
    avatarColor: '#10B981',
  },
  {
    id: 'fr-2',
    name: 'MysticHiroshiJP',
    trainerCode: '7731 9024 1152',
    friendshipLevel: 2,
    daysToNextTier: 2,
    lastGiftSent: '2026-09-29 08:40',
    giftableStatus: 'GIFTABLE',
    friendGroup: 'ULTRA_BEST_PUSH',
    buddyPokemon: 'Lucario',
    avatarColor: '#38BDF8',
  },
  {
    id: 'fr-3',
    name: 'ValorCaptainRex',
    trainerCode: '9104 3381 6620',
    friendshipLevel: 3,
    daysToNextTier: 14,
    lastGiftSent: '2026-09-28 22:10',
    giftableStatus: 'UNOPENED_GIFT',
    friendGroup: 'LOCAL_RAIDERS',
    buddyPokemon: 'Garchomp',
    avatarColor: '#F59E0B',
  },
  {
    id: 'fr-4',
    name: 'SylveonQueen94',
    trainerCode: '3019 5582 7419',
    friendshipLevel: 4,
    daysToNextTier: 0,
    lastGiftSent: '2026-09-29 14:22',
    giftableStatus: 'GIFTABLE',
    friendGroup: 'LUCKY_CANDIDATES',
    buddyPokemon: 'Shiny Sylveon',
    avatarColor: '#EC4899',
  },
  {
    id: 'fr-5',
    name: 'BerlinerRaider',
    trainerCode: '6420 8819 0043',
    friendshipLevel: 2,
    daysToNextTier: 1,
    lastGiftSent: '2026-09-29 11:05',
    giftableStatus: 'GIFTABLE',
    friendGroup: 'ULTRA_BEST_PUSH',
    buddyPokemon: 'Necrozma',
    avatarColor: '#8B5CF6',
  },
  {
    id: 'fr-6',
    name: 'PokeProfOakley',
    trainerCode: '1198 4402 9581',
    friendshipLevel: 3,
    daysToNextTier: 3,
    lastGiftSent: '2026-09-29 17:50',
    giftableStatus: 'GIFTABLE',
    friendGroup: 'ULTRA_BEST_PUSH',
    buddyPokemon: 'Frigibax',
    avatarColor: '#14B8A6',
  },
  {
    id: 'fr-7',
    name: 'TokyoDriftSnorlax',
    trainerCode: '5541 2290 3817',
    friendshipLevel: 4,
    daysToNextTier: 0,
    lastGiftSent: '2026-09-30 09:12',
    giftableStatus: 'SENT_TODAY',
    friendGroup: 'LUCKY_CANDIDATES',
    buddyPokemon: 'Snorlax',
    avatarColor: '#6366F1',
  },
  {
    id: 'fr-8',
    name: 'HoennChampionMay',
    trainerCode: '8390 1746 2910',
    friendshipLevel: 3,
    daysToNextTier: 7,
    lastGiftSent: '2026-09-28 16:30',
    giftableStatus: 'GIFTABLE',
    friendGroup: 'LOCAL_RAIDERS',
    buddyPokemon: 'Kyogre',
    avatarColor: '#06B6D4',
  }
];

export const INITIAL_RESEARCH_TASKS: ResearchTaskRecord[] = [
  {
    id: 'ld-1-1',
    category: 'Harvest Festival 2026: Applin Picking Tasks',
    taskName: 'Catch 5 Grass-type Pokémon',
    reward: 'Foongus / Skwovet / Smoliv',
    rewardType: 'ENCOUNTER',
    cpRange: '386 - 419 CP (100% IV)',
    shinyPossible: true,
    isHighValue: true,
    quickToComplete: true,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  },
  {
    id: 'ld-1-2',
    category: 'Harvest Festival 2026: Applin Picking Tasks',
    taskName: 'Catch 15 Grass-type Pokémon',
    reward: 'Applin',
    rewardType: 'ENCOUNTER',
    cpRange: '277 - 306 CP (100% IV)',
    shinyPossible: false,
    isHighValue: true,
    quickToComplete: false,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  },
  {
    id: 'ld-1-3',
    category: 'Harvest Festival 2026: Applin Picking Tasks',
    taskName: 'Use 20 Berries to help catch Pokémon',
    reward: 'Sweet Apple ×1 / Syrupy Apple ×1 / Tart Apple ×1',
    rewardType: 'ITEM',
    cpRange: 'Resource ×1',
    shinyPossible: false,
    isHighValue: true,
    quickToComplete: false,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  },
  {
    id: 'ld-2-1',
    category: 'Catching Tasks',
    taskName: 'Catch 7 Pokémon',
    reward: 'Magikarp / Stufful / Wimpod',
    rewardType: 'ENCOUNTER',
    cpRange: '101 - 117 CP (100% IV)',
    shinyPossible: true,
    isHighValue: false,
    quickToComplete: true,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  },
  {
    id: 'ld-2-2',
    category: 'Catching Tasks',
    taskName: 'Catch a Dragon-type Pokémon',
    reward: 'Dratini / Bagon / Axew / Rare Candy ×3',
    rewardType: 'ENCOUNTER',
    cpRange: '397 - 430 CP (100% IV)',
    shinyPossible: true,
    isHighValue: true,
    quickToComplete: true,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  },
  {
    id: 'ld-4-1',
    category: 'Battling Tasks',
    taskName: 'Battle in a raid',
    reward: 'Rare Candy ×1',
    rewardType: 'ITEM',
    cpRange: 'Resource ×1',
    shinyPossible: false,
    isHighValue: true,
    quickToComplete: true,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  },
  {
    id: 'ld-5-1',
    category: 'Exploring Tasks',
    taskName: 'Earn 10000 Stardust',
    reward: 'Silver Pinap Berry ×1',
    rewardType: 'ITEM',
    cpRange: 'Resource ×1',
    shinyPossible: false,
    isHighValue: true,
    quickToComplete: false,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  },
  {
    id: 'ld-6-2',
    category: 'Training Tasks',
    taskName: 'Power up Pokémon 15 times',
    reward: 'Rare Candy ×3',
    rewardType: 'ITEM',
    cpRange: 'Resource ×3',
    shinyPossible: false,
    isHighValue: true,
    quickToComplete: false,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  },
  {
    id: 'ld-9-2',
    category: 'Sponsored Tasks',
    taskName: 'Send 3 Gifts to friends',
    reward: 'Rare Candy ×1 / Stardust ×1000',
    rewardType: 'ITEM',
    cpRange: 'Resource ×1',
    shinyPossible: false,
    isHighValue: true,
    quickToComplete: true,
    source: 'Leek Duck',
    dateAdded: '2026-09-30',
    isActive: true,
  }
];

export const INITIAL_AUTOMATION_LOGS: AutomationLogRecord[] = [
  {
    id: 'log-1',
    timestamp: '15:24:11.402',
    actionType: 'NODE_SCAN',
    trainerName: 'System',
    delayMs: 0,
    coordinates: 'Rect(0, 0 - 1080, 2400)',
    success: true,
    details: 'Parsed com.nianticlabs.pokemongo Friends List: 8 trainer nodes visible',
  },
  {
    id: 'log-2',
    timestamp: '15:24:12.388',
    actionType: 'GIFT_SENT',
    trainerName: 'TokyoDriftSnorlax',
    delayMs: 986,
    coordinates: 'Tap(542, 1814)',
    success: true,
    details: 'Dispatched postcard with Gaussian delay 986ms (σ=95ms)',
  }
];

export const INITIAL_EGG_SLOTS: EggIncubatorSlot[] = [
  {
    id: 'egg-1',
    eggTierKm: 10,
    incubatorType: 'SUPER',
    currentKm: 5.9,
    targetKm: 6.7,
    possibleHatchPool: ['Charcadet', 'Frigibax', 'Larvesta', 'Dreepy'],
  },
  {
    id: 'egg-2',
    eggTierKm: 2,
    incubatorType: 'INFINITE',
    currentKm: 1.7,
    targetKm: 2.0,
    possibleHatchPool: ['Togepi', 'Pichu', 'Smoliv', 'Cleffa'],
  },
  {
    id: 'egg-3',
    eggTierKm: 12,
    incubatorType: 'LIMITED_3X',
    currentKm: 9.4,
    targetKm: 12.0,
    possibleHatchPool: ['Female Salandit', 'Varoom', 'Sandile', 'Pawniard'],
  },
  {
    id: 'egg-4',
    eggTierKm: 7,
    incubatorType: 'LIMITED_3X',
    currentKm: 6.8,
    targetKm: 7.0,
    possibleHatchPool: ['Galarian Corsola', 'Hisuian Zorua', 'Alolan Vulpix'],
  }
];
