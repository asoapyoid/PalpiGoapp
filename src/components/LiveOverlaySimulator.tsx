import React, { useState, useEffect, useRef } from 'react';
import {
  Play,
  Square,
  Settings,
  ClipboardCheck,
  Camera,
  Minimize2,
  Maximize2,
  GripHorizontal,
  ShieldCheck,
  AlertTriangle,
  Star,
  Gift,
  ArrowLeft,
  RefreshCw,
  ScanSearch,
  Crosshair,
  Target,
  Zap,
  RotateCcw,
  Sparkles,
  X
} from 'lucide-react';
import {
  FriendRecord,
  ResearchTaskRecord,
  AutomationLogRecord,
  TrainerAccountProfile
} from '../data/mockDatabase';
import { getLegacyEvolutionInfo } from '../data/legacyMovesData';

export type AutomationStep =
  | 'IDLE'
  | 'SCANNING_FRIENDS_LIST'
  | 'OPENING_TRAINER_PROFILE'
  | 'WAITING_FOR_PROFILE_LOAD'
  | 'CLICKING_SEND_GIFT'
  | 'SELECTING_FIRST_POSTCARD'
  | 'CONFIRMING_GIFT_SEND'
  | 'RETURNING_TO_FRIENDS_LIST'
  | 'SCROLLING_FOR_MORE_FRIENDS';

interface LiveOverlaySimulatorProps {
  friends: FriendRecord[];
  onUpdateFriend: (friendId: string, updates: Partial<FriendRecord>) => void;
  researchTasks: ResearchTaskRecord[];
  logs: AutomationLogRecord[];
  onAddLog: (log: Omit<AutomationLogRecord, 'id' | 'timestamp'>) => void;
  activeAccount: TrainerAccountProfile;
  amoledMode: boolean;
  onTriggerSnackbar: (message: string, type?: 'success' | 'warning' | 'info') => void;
  onCaptureIvSample: () => void;
  onOpenLegacyHub?: (query?: string) => void;
}

function sampleGaussianDelay(safeMode: boolean): number {
  let u = 0, v = 0;
  while (u === 0) u = Math.random();
  while (v === 0) v = Math.random();
  const z = Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
  if (safeMode) {
    return Math.round(Math.min(2300, Math.max(1450, 1800 + z * 180)));
  }
  return Math.round(Math.min(1200, Math.max(800, 1000 + z * 95)));
}

// Memory-efficient frozen preset roster for catch & throw simulation
export const WILD_POKEMON_PRESETS = Object.freeze([
  {
    name: 'Beldum',
    cp: 842,
    iv: 96,
    atk: 15,
    def: 14,
    sta: 14,
    type: 'Steel / Psychic',
    distance: 'MEDIUM' as const,
    coords: { x: 175, y: 220 },
    outerRingRadius: 65,
    fallbackEmoji: '🦾',
    shiny: true,
    color: '#38BDF8',
  },
  {
    name: 'Applin',
    cp: 430,
    iv: 98,
    atk: 15,
    def: 15,
    sta: 14,
    type: 'Grass / Dragon',
    distance: 'CLOSE' as const,
    coords: { x: 175, y: 235 },
    outerRingRadius: 55,
    fallbackEmoji: '🍎',
    shiny: true,
    color: '#84CC16',
  },
  {
    name: 'Mudkip',
    cp: 615,
    iv: 93,
    atk: 14,
    def: 14,
    sta: 14,
    type: 'Water',
    distance: 'CLOSE' as const,
    coords: { x: 175, y: 230 },
    outerRingRadius: 58,
    fallbackEmoji: '🐸',
    shiny: false,
    color: '#38BDF8',
  },
  {
    name: 'Palpitoad',
    cp: 980,
    iv: 95,
    atk: 15,
    def: 14,
    sta: 14,
    type: 'Water / Ground',
    distance: 'MEDIUM' as const,
    coords: { x: 175, y: 220 },
    outerRingRadius: 62,
    fallbackEmoji: '🔵',
    shiny: false,
    color: '#0EA5E9',
  },
  {
    name: 'Dratini',
    cp: 785,
    iv: 91,
    atk: 14,
    def: 13,
    sta: 14,
    type: 'Dragon',
    distance: 'FAR' as const,
    coords: { x: 175, y: 200 },
    outerRingRadius: 58,
    fallbackEmoji: '🐉',
    shiny: false,
    color: '#60A5FA',
  },
  {
    name: 'Charizard',
    cp: 2450,
    iv: 98,
    atk: 15,
    def: 15,
    sta: 14,
    type: 'Fire / Flying',
    distance: 'FLYING' as const,
    coords: { x: 175, y: 190 },
    outerRingRadius: 75,
    fallbackEmoji: '🔥',
    shiny: false,
    color: '#F97316',
  },
  {
    name: 'Gengar',
    cp: 2140,
    iv: 89,
    atk: 14,
    def: 12,
    sta: 14,
    type: 'Ghost / Poison',
    distance: 'MEDIUM' as const,
    coords: { x: 175, y: 225 },
    outerRingRadius: 72,
    fallbackEmoji: '👻',
    shiny: false,
    color: '#A855F7',
  },
  {
    name: 'Rayquaza',
    cp: 3820,
    iv: 100,
    atk: 15,
    def: 15,
    sta: 15,
    type: 'Dragon / Flying',
    distance: 'FLYING' as const,
    coords: { x: 175, y: 175 },
    outerRingRadius: 78,
    fallbackEmoji: '🐲',
    shiny: true,
    color: '#10B981',
  },
  {
    name: 'Pidgey',
    cp: 210,
    iv: 82,
    atk: 12,
    def: 12,
    sta: 13,
    type: 'Normal / Flying',
    distance: 'CLOSE' as const,
    coords: { x: 175, y: 240 },
    outerRingRadius: 60,
    fallbackEmoji: '🐦',
    shiny: false,
    color: '#FBBF24',
  },
]);

export const LiveOverlaySimulator: React.FC<LiveOverlaySimulatorProps> = ({
  friends,
  onUpdateFriend,
  researchTasks,
  logs,
  onAddLog,
  activeAccount,
  amoledMode,
  onTriggerSnackbar,
  onCaptureIvSample,
  onOpenLegacyHub,
}) => {
  // Floating Overlay state (Bubble vs Expanded Panel)
  const [isOverlayExpanded, setIsOverlayExpanded] = useState<boolean>(true);
  const [overlayTab, setOverlayTab] = useState<'CONTROLS' | 'THROW' | 'RESEARCH' | 'SETTINGS'>('THROW');
  const [overlayResearchMode, setOverlayResearchMode] = useState<'SCREEN_SCAN' | 'ALL_LEEKDUCK'>('SCREEN_SCAN');
  const [bubblePos, setBubblePos] = useState<{ x: number; y: number }>({ x: 14, y: 110 });
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const dragStartRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const phoneContainerRef = useRef<HTMLDivElement | null>(null);

  // Throw Engine & Dynamic Curveball Reticle State
  const [isSpinThrow, setIsSpinThrow] = useState<boolean>(true); // Curveball mode (ON by default)
  const [spinDirection, setSpinDirection] = useState<'CCW' | 'CW'>('CCW'); // CCW = Left hook, CW = Right hook
  const [distanceProfile, setDistanceProfile] = useState<'CLOSE' | 'MEDIUM' | 'FAR' | 'FLYING'>('MEDIUM');
  const [fastCatchEnabled, setFastCatchEnabled] = useState<boolean>(true);
  const [waitExcellentRing, setWaitExcellentRing] = useState<boolean>(true);

  // Interactive Hold-to-Aim Reticle State
  const [isAimingThrow, setIsAimingThrow] = useState<boolean>(false);
  const aimStartTimestampRef = useRef<number>(0);
  const [aimCoords, setAimCoords] = useState<{ x: number; y: number }>({ x: 175, y: 220 });
  const [ringRadius, setRingRadius] = useState<number>(55);
  const [selectedWildPokemonIndex, setSelectedWildPokemonIndex] = useState<number>(0);

  // Ball In-Flight & Throw Impact Animations
  const [ballFlight, setBallFlight] = useState<{
    x: number;
    y: number;
    scale: number;
    rotation: number;
    progress: number;
  } | null>(null);

  const [lastThrowResult, setLastThrowResult] = useState<{
    grade: 'EXCELLENT' | 'GREAT' | 'NICE' | 'HIT' | 'MISS';
    isCurveball: boolean;
    xpEarned: number;
    hitDistance: number;
    fastCatch: boolean;
    timeStr: string;
  } | null>(null);

  const [throwCounters, setThrowCounters] = useState<{
    total: number;
    excellent: number;
    great: number;
    nice: number;
    curveball: number;
  }>({
    total: 12,
    excellent: 9,
    great: 2,
    nice: 1,
    curveball: 12,
  });

  // Automation Engine State
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [currentStep, setCurrentStep] = useState<AutomationStep>('IDLE');
  const [activeFriendIndex, setActiveFriendIndex] = useState<number>(0);
  const [friendsProcessed, setFriendsProcessed] = useState<number>(0);
  const [giftsSent, setGiftsSent] = useState<number>(0);
  const [errorsEncountered, setErrorsEncountered] = useState<number>(0);
  const [lastDelayMs, setLastDelayMs] = useState<number>(986);

  // Settings
  const [maxGiftsPerSession, setMaxGiftsPerSession] = useState<number>(10);
  const [friendGroupFilter, setFriendGroupFilter] = useState<string>('ALL');
  const [skipUnopenedGifts, setSkipUnopenedGifts] = useState<boolean>(true);
  const [safeMode, setSafeMode] = useState<boolean>(false);
  const [showA11yBounds, setShowA11yBounds] = useState<boolean>(true);
  const [researchQuickFilter, setResearchQuickFilter] = useState<'ALL' | 'SHINY' | 'HIGH_VALUE' | 'QUICK'>('ALL');
  const [overlaySearchQuery, setOverlaySearchQuery] = useState<string>('');

  // Simulated Scopely Explore Pokémon GO Screen State
  const [pogoScreen, setPogoScreen] = useState<'CATCH_ENCOUNTER' | 'FRIENDS_LIST' | 'PROFILE' | 'GIFT_PICKER' | 'FIELD_RESEARCH_MENU'>('CATCH_ENCOUNTER');
  const [tapRipple, setTapRipple] = useState<{ x: number; y: number; label: string } | null>(null);

  // The 3 Active Field Research Slots currently in the player's Pokémon GO Research Menu
  const [activeResearchSlotIndices, setActiveResearchSlotIndices] = useState<number[]>([0, 3, 4]);

  const bagPercent = Math.round((activeAccount.bagItemsCount / activeAccount.bagMaxCapacity) * 100);
  const isBagNearlyFull = bagPercent >= 95;

  const filteredFriendsForSession = friends.filter((f) => {
    if (friendGroupFilter === 'ALL') return true;
    return f.friendGroup === friendGroupFilter;
  });

  const currentTargetFriend = filteredFriendsForSession[activeFriendIndex] || filteredFriendsForSession[0];

  // Active 3 Field Research Tasks currently displayed in the player's Research Menu
  const onScreenFieldResearchTasks: ResearchTaskRecord[] = activeResearchSlotIndices
    .map((idx) => researchTasks[idx % Math.max(1, researchTasks.length)])
    .filter(Boolean);

  const [activeAppraisalPokemon, setActiveAppraisalPokemon] = useState<(typeof WILD_POKEMON_PRESETS)[0] | null>(null);

  const currentPokemon = WILD_POKEMON_PRESETS[selectedWildPokemonIndex % WILD_POKEMON_PRESETS.length];

  // Animated Target Ring Shrinking Loop (Nice -> Great -> Excellent)
  useEffect(() => {
    let animId: number;
    let start = performance.now();
    const cycleDuration = 1800; // ms to shrink from 100% to 25%

    const animateRing = (now: number) => {
      const elapsed = (now - start) % cycleDuration;
      const progress = elapsed / cycleDuration;
      const maxR = currentPokemon.outerRingRadius;
      const minR = 18;
      const currentR = maxR - progress * (maxR - minR);
      setRingRadius(currentR);
      animId = requestAnimationFrame(animateRing);
    };

    animId = requestAnimationFrame(animateRing);
    return () => cancelAnimationFrame(animId);
  }, [currentPokemon.outerRingRadius]);

  // Trajectory Calculation
  const ballStartPos = { x: 175, y: 485 };
  const targetEndPos = aimCoords;

  // Magnus Aerodynamic Curve Calculation
  const sweepMagnitude = 85;
  const sweepSign = spinDirection === 'CCW' ? -1 : 1;
  const curveCp1 = {
    x: ballStartPos.x + sweepSign * sweepMagnitude,
    y: ballStartPos.y - 130,
  };
  const curveCp2 = {
    x: targetEndPos.x + sweepSign * (sweepMagnitude * 0.55),
    y: targetEndPos.y + 65,
  };
  const straightCp = {
    x: (ballStartPos.x + targetEndPos.x) / 2,
    y: (ballStartPos.y + targetEndPos.y) / 2 - 30,
  };

  const trajectoryPathD = isSpinThrow
    ? `M ${ballStartPos.x} ${ballStartPos.y} C ${curveCp1.x} ${curveCp1.y}, ${curveCp2.x} ${curveCp2.y}, ${targetEndPos.x} ${targetEndPos.y}`
    : `M ${ballStartPos.x} ${ballStartPos.y} Q ${straightCp.x} ${straightCp.y} ${targetEndPos.x} ${targetEndPos.y}`;

  // Helper to evaluate point along Bézier
  const evalTrajectoryPoint = (t: number) => {
    if (!isSpinThrow) {
      const u = 1 - t;
      return {
        x: u * u * ballStartPos.x + 2 * u * t * straightCp.x + t * t * targetEndPos.x,
        y: u * u * ballStartPos.y + 2 * u * t * straightCp.y + t * t * targetEndPos.y,
      };
    }
    const u = 1 - t;
    const tt = t * t;
    const uu = u * u;
    const uuu = uu * u;
    const ttt = tt * t;
    return {
      x: uuu * ballStartPos.x + 3 * uu * t * curveCp1.x + 3 * u * tt * curveCp2.x + ttt * targetEndPos.x,
      y: uuu * ballStartPos.y + 3 * uu * t * curveCp1.y + 3 * u * tt * curveCp2.y + ttt * targetEndPos.y,
    };
  };

  // Distance from aim point to Pokémon center
  const distFromTargetCenter = Math.hypot(
    aimCoords.x - currentPokemon.coords.x,
    aimCoords.y - currentPokemon.coords.y
  );

  const getAimGrade = (dist: number, ringR: number) => {
    if (dist <= ringR) {
      if (ringR <= 28) return { label: 'EXCELLENT', color: '#10B981', xp: 100 };
      if (ringR <= 48) return { label: 'GREAT', color: '#0EA5E9', xp: 50 };
      return { label: 'NICE', color: '#F59E0B', xp: 20 };
    }
    if (dist <= currentPokemon.outerRingRadius + 15) {
      return { label: 'HIT', color: '#94A3B8', xp: 10 };
    }
    return { label: 'MISS', color: '#EF4444', xp: 0 };
  };

  const currentAimGrade = getAimGrade(distFromTargetCenter, ringRadius);

  // Ball Throw Animation & Result Evaluation
  const executeBallThrow = (target: { x: number; y: number }, useCurve: boolean) => {
    if (ballFlight) return; // Already throwing

    const startTime = performance.now();
    const flightDuration = 600; // ms

    const animateFlight = (now: number) => {
      const elapsed = now - startTime;
      const progress = Math.min(1, elapsed / flightDuration);
      const pos = evalTrajectoryPoint(progress);
      const scale = 1.0 - progress * 0.58; // 1.0 down to 0.42
      const rotation = progress * 1080; // 3 full spins

      setBallFlight({
        x: pos.x,
        y: pos.y,
        scale,
        rotation,
        progress,
      });

      if (progress < 1) {
        requestAnimationFrame(animateFlight);
      } else {
        // Ball Impact!
        setBallFlight(null);
        const dist = Math.hypot(target.x - currentPokemon.coords.x, target.y - currentPokemon.coords.y);
        const gradeInfo = getAimGrade(dist, ringRadius);
        const curveBonus = useCurve ? 100 : 0;
        const totalXp = gradeInfo.xp + curveBonus;

        const result = {
          grade: gradeInfo.label as 'EXCELLENT' | 'GREAT' | 'NICE' | 'HIT' | 'MISS',
          isCurveball: useCurve,
          xpEarned: totalXp,
          hitDistance: Math.round(dist),
          fastCatch: fastCatchEnabled,
          timeStr: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        };

        setLastThrowResult(result);

        setThrowCounters((prev) => ({
          total: prev.total + 1,
          excellent: prev.excellent + (result.grade === 'EXCELLENT' ? 1 : 0),
          great: prev.great + (result.grade === 'GREAT' ? 1 : 0),
          nice: prev.nice + (result.grade === 'NICE' ? 1 : 0),
          curveball: prev.curveball + (useCurve ? 1 : 0),
        }));

        onAddLog({
          actionType: 'THROW_GESTURE',
          trainerName: activeAccount.trainerName,
          delayMs: 38,
          coordinates: `AimedThrow(${Math.round(target.x)}, ${Math.round(target.y)})`,
          success: result.grade !== 'MISS',
          details: `${result.grade} ${useCurve ? '🌀 Curveball' : '⬆️ Straight'} Throw on ${currentPokemon.name} (CP ${currentPokemon.cp}) • Hit: ${result.hitDistance}px from center • +${totalXp} XP ${fastCatchEnabled ? '• Fast Catch ⚡' : ''}`,
        });

        onTriggerSnackbar(
          `${result.grade === 'EXCELLENT' ? '🌟 EXCELLENT!' : result.grade === 'GREAT' ? '🔥 GREAT!' : result.grade === 'NICE' ? '👍 NICE!' : '🎯 HIT!'} ${useCurve ? 'Curveball ' : ''}(+${totalXp} XP)${fastCatchEnabled ? ' • Fast Catch Skipped ⚡' : ''}`,
          result.grade === 'MISS' ? 'warning' : 'success'
        );
      }
    };

    requestAnimationFrame(animateFlight);
  };

  // Hold-to-Aim Pointer Handlers on Any Throw Button
  const handleStartThrowAim = (e: React.PointerEvent, overrideCurve?: boolean) => {
    e.preventDefault();
    if (pogoScreen !== 'CATCH_ENCOUNTER') {
      setPogoScreen('CATCH_ENCOUNTER');
    }
    setIsAimingThrow(true);
    aimStartTimestampRef.current = performance.now();

    const initialAim = { ...currentPokemon.coords };
    setAimCoords(initialAim);

    const onPointerMove = (ev: PointerEvent) => {
      if (!phoneContainerRef.current) return;
      const rect = phoneContainerRef.current.getBoundingClientRect();
      const rawX = ev.clientX - rect.left;
      const rawY = ev.clientY - rect.top;
      // Clamp inside the encounter viewport area
      const clampedX = Math.max(35, Math.min(315, rawX));
      const clampedY = Math.max(90, Math.min(460, rawY));
      setAimCoords({ x: clampedX, y: clampedY });
    };

    const onPointerUp = () => {
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);
      setIsAimingThrow(false);

      const holdDuration = performance.now() - aimStartTimestampRef.current;
      const useCurve = overrideCurve !== undefined ? overrideCurve : isSpinThrow;

      setAimCoords((currentAim) => {
        // If it was just a quick click (< 140ms), aim dead center of the Pokémon
        const finalTarget = holdDuration < 140 ? currentPokemon.coords : currentAim;
        executeBallThrow(finalTarget, useCurve);
        return finalTarget;
      });
    };

    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
  };

  const handleShuffleResearchMenuSlots = () => {
    if (researchTasks.length < 3) return;
    const total = researchTasks.length;
    const i1 = Math.floor(Math.random() * total);
    const i2 = (i1 + 1 + Math.floor(Math.random() * (total - 1))) % total;
    const i3 = (i2 + 1 + Math.floor(Math.random() * (total - 2))) % total;
    setActiveResearchSlotIndices([i1, i2, i3]);
    onAddLog({
      actionType: 'NODE_SCAN',
      trainerName: activeAccount.trainerName,
      delayMs: 84,
      coordinates: 'Window(com.scopely.pokemongo:id/field_research_list)',
      success: true,
      details: `AccessibilityService scanned 3 Field Research slots & calculated LeekDuck reward odds`,
    });
    onTriggerSnackbar('Scanned new Field Research menu slots & calculated reward possibilities', 'info');
  };

  const handleScanPokemonAppraisal = () => {
    setActiveAppraisalPokemon(currentPokemon);
    if (pogoScreen !== 'CATCH_ENCOUNTER') {
      setPogoScreen('CATCH_ENCOUNTER');
    }
    onCaptureIvSample();
  };

  const handleTriggerScreenResearchScan = () => {
    setPogoScreen('FIELD_RESEARCH_MENU');
    setOverlayTab('RESEARCH');
    setOverlayResearchMode('SCREEN_SCAN');
    onAddLog({
      actionType: 'NODE_SCAN',
      trainerName: activeAccount.trainerName,
      delayMs: 92,
      coordinates: 'Rect(40, 380 - 1040, 1680)',
      success: true,
      details: `Read ${onScreenFieldResearchTasks.length} active Field Research tasks from Pokémon GO Research Menu`,
    });
    onTriggerSnackbar(
      `AccessibilityService read ${onScreenFieldResearchTasks.length} Field Research tasks from screen & matched LeekDuck odds`,
      'success'
    );
  };

  // Handle Dragging of Floating Window inside Phone Viewport
  const handlePointerDown = (e: React.PointerEvent) => {
    setIsDragging(true);
    dragStartRef.current = {
      x: e.clientX - bubblePos.x,
      y: e.clientY - bubblePos.y,
    };
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDragging) return;
    const nextX = Math.max(8, Math.min(isOverlayExpanded ? 48 : 295, e.clientX - dragStartRef.current.x));
    const nextY = Math.max(44, Math.min(480, e.clientY - dragStartRef.current.y));
    setBubblePos({ x: nextX, y: nextY });
  };

  const handlePointerUp = () => {
    if (!isDragging) return;
    setIsDragging(false);
    if (!isOverlayExpanded) {
      setBubblePos((prev) => ({
        x: prev.x < 150 ? 12 : 292,
        y: prev.y,
      }));
    }
  };

  const handleStartAutoGift = () => {
    if (isBagNearlyFull) {
      onTriggerSnackbar(
        `Inventory Alert: Bag is ${bagPercent}% full (${activeAccount.bagItemsCount}/${activeAccount.bagMaxCapacity}). Clear space or proceed with caution.`,
        'warning'
      );
    }
    setIsRunning(true);
    setCurrentStep('SCANNING_FRIENDS_LIST');
    setPogoScreen('FRIENDS_LIST');
    onAddLog({
      actionType: 'SESSION_START',
      trainerName: 'System',
      delayMs: 0,
      coordinates: 'Window(com.scopely.pokemongo)',
      success: true,
      details: `Started Auto-Gift (Max=${maxGiftsPerSession}, Group=${friendGroupFilter}, SafeMode=${safeMode})`,
    });
    onTriggerSnackbar('Auto-Gift Engine started • AccessibilityService monitoring Scopely Pokémon GO', 'success');
  };

  const handleStopAutoGift = (reason = 'Stopped by user via Floating Control Panel') => {
    setIsRunning(false);
    setCurrentStep('IDLE');
    setTapRipple(null);
    onTriggerSnackbar(reason, 'info');
  };

  useEffect(() => {
    if (!isRunning) return;

    if (giftsSent >= maxGiftsPerSession) {
      handleStopAutoGift(`Session target reached: ${giftsSent}/${maxGiftsPerSession} gifts sent`);
      return;
    }

    if (filteredFriendsForSession.length === 0) {
      handleStopAutoGift('No friends match the selected friend group filter');
      return;
    }

    const delayMs = sampleGaussianDelay(safeMode);
    setLastDelayMs(delayMs);

    const timer = setTimeout(() => {
      const friend = filteredFriendsForSession[activeFriendIndex % filteredFriendsForSession.length];
      if (!friend) return;

      const jitter = () => Math.round((Math.random() - 0.5) * 14);

      switch (currentStep) {
        case 'SCANNING_FRIENDS_LIST': {
          setPogoScreen('FRIENDS_LIST');
          if (friend.giftableStatus === 'SENT_TODAY') {
            setActiveFriendIndex((prev) => (prev + 1) % filteredFriendsForSession.length);
            setCurrentStep('SCROLLING_FOR_MORE_FRIENDS');
          } else {
            setTapRipple({ x: 175 + jitter(), y: 210 + jitter(), label: `Tap ${friend.name}` });
            setCurrentStep('OPENING_TRAINER_PROFILE');
          }
          break;
        }

        case 'OPENING_TRAINER_PROFILE': {
          setPogoScreen('PROFILE');
          setTapRipple({ x: 180 + jitter(), y: 250 + jitter(), label: 'Wait Profile Load' });
          onAddLog({
            actionType: 'PROFILE_OPEN',
            trainerName: friend.name,
            delayMs,
            coordinates: `Tap(${520 + jitter()}, ${880 + jitter()})`,
            success: true,
            details: `Opened profile for ${friend.name} (Tier ${friend.friendshipLevel})`,
          });
          setCurrentStep('WAITING_FOR_PROFILE_LOAD');
          break;
        }

        case 'WAITING_FOR_PROFILE_LOAD': {
          if (friend.giftableStatus === 'UNOPENED_GIFT' && skipUnopenedGifts) {
            onAddLog({
              actionType: 'SKIP_UNOPENED',
              trainerName: friend.name,
              delayMs,
              coordinates: 'GLOBAL_ACTION_BACK',
              success: true,
              details: `Skipped ${friend.name}: Still holds an unopened gift`,
            });
            setFriendsProcessed((p) => p + 1);
            setCurrentStep('RETURNING_TO_FRIENDS_LIST');
          } else {
            setTapRipple({ x: 112 + jitter(), y: 465 + jitter(), label: 'Click Send Gift' });
            setCurrentStep('CLICKING_SEND_GIFT');
          }
          break;
        }

        case 'CLICKING_SEND_GIFT': {
          setPogoScreen('GIFT_PICKER');
          setTapRipple({ x: 180 + jitter(), y: 310 + jitter(), label: 'Select Postcard #1' });
          setCurrentStep('SELECTING_FIRST_POSTCARD');
          break;
        }

        case 'SELECTING_FIRST_POSTCARD': {
          setTapRipple({ x: 180 + jitter(), y: 505 + jitter(), label: 'Confirm Send' });
          setCurrentStep('CONFIRMING_GIFT_SEND');
          break;
        }

        case 'CONFIRMING_GIFT_SEND': {
          const nowStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
          onUpdateFriend(friend.id, {
            giftableStatus: 'SENT_TODAY',
            lastGiftSent: `Today ${nowStr}`,
            daysToNextTier: Math.max(0, friend.daysToNextTier - 1),
          });
          setFriendsProcessed((p) => p + 1);
          setGiftsSent((g) => g + 1);
          onAddLog({
            actionType: 'GIFT_SENT',
            trainerName: friend.name,
            delayMs,
            coordinates: `Tap(${540 + jitter()}, ${1820 + jitter()})`,
            success: true,
            details: `Sent Postcard Gift to ${friend.name} • Gaussian delay ${delayMs}ms`,
          });
          setCurrentStep('RETURNING_TO_FRIENDS_LIST');
          break;
        }

        case 'RETURNING_TO_FRIENDS_LIST': {
          setPogoScreen('FRIENDS_LIST');
          setTapRipple({ x: 180, y: 565, label: 'GLOBAL_ACTION_BACK' });
          setActiveFriendIndex((prev) => (prev + 1) % filteredFriendsForSession.length);
          setCurrentStep('SCROLLING_FOR_MORE_FRIENDS');
          break;
        }

        case 'SCROLLING_FOR_MORE_FRIENDS': {
          setTapRipple({ x: 185, y: 380, label: 'Bezier Scroll Swipe' });
          onAddLog({
            actionType: 'SCROLL_SWIPE',
            trainerName: 'FriendsList',
            delayMs,
            coordinates: 'Bezier(540,1720 -> 558,680)',
            success: true,
            details: `Curved vertical swipe (${delayMs}ms) to advance visible friend rows`,
          });
          setCurrentStep('SCANNING_FRIENDS_LIST');
          break;
        }
      }
    }, delayMs);

    return () => clearTimeout(timer);
  }, [
    isRunning,
    currentStep,
    activeFriendIndex,
    giftsSent,
    maxGiftsPerSession,
    safeMode,
    skipUnopenedGifts,
    filteredFriendsForSession,
  ]);

  const filteredOverlayResearch = researchTasks.filter((t) => {
    if (researchQuickFilter === 'SHINY' && !t.shinyPossible) return false;
    if (researchQuickFilter === 'HIGH_VALUE' && !t.isHighValue) return false;
    if (researchQuickFilter === 'QUICK' && !t.quickToComplete) return false;
    if (overlaySearchQuery.trim()) {
      const q = overlaySearchQuery.toLowerCase();
      return (
        t.taskName.toLowerCase().includes(q) ||
        t.reward.toLowerCase().includes(q) ||
        (t.category && t.category.toLowerCase().includes(q))
      );
    }
    return true;
  });

  const SEQUENCE_STEPS: { key: AutomationStep; label: string }[] = [
    { key: 'SCANNING_FRIENDS_LIST', label: '1. Parse Friends List Nodes' },
    { key: 'OPENING_TRAINER_PROFILE', label: '2. Tap First Eligible Friend' },
    { key: 'WAITING_FOR_PROFILE_LOAD', label: '3. Verify Gift Button & Storage' },
    { key: 'CLICKING_SEND_GIFT', label: '4. Tap "Send Gift" Button' },
    { key: 'SELECTING_FIRST_POSTCARD', label: '5. Select First Postcard' },
    { key: 'CONFIRMING_GIFT_SEND', label: '6. Confirm & Back to List' },
  ];

  // Helper to compute possibility options for a task
  const getTaskPossibilities = (task: ResearchTaskRecord) => {
    if (task.rewardsList && task.rewardsList.length > 0) {
      const pct = Math.round((100 / task.rewardsList.length) * 10) / 10;
      return task.rewardsList.map((r) => ({
        name: r.name,
        pct,
        canBeShiny: r.canBeShiny,
        cpText: r.minCp && r.maxCp ? `${r.minCp}–${r.maxCp} CP` : r.quantity || '',
        imageUrl: r.imageUrl,
      }));
    }
    const splitNames = task.reward.split('/').map((s) => s.trim()).filter(Boolean);
    const pct = splitNames.length > 0 ? Math.round((100 / splitNames.length) * 10) / 10 : 100;
    return splitNames.map((name) => ({
      name,
      pct,
      canBeShiny: task.shinyPossible,
      cpText: task.cpRange,
      imageUrl: '',
    }));
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
      {/* LEFT 5 COLS: Interactive Android Phone Viewport with Scopely Pokémon GO + Floating Overlay */}
      <div className="lg:col-span-5 flex flex-col items-center">
        <div className="w-full max-w-[382px] rounded-[36px] p-3 border border-slate-800 bg-slate-950 shadow-2xl relative select-none">
          {/* Android Hardware Frame Header */}
          <div className="flex items-center justify-between px-4 py-1.5 text-[11px] font-mono text-slate-400 bg-slate-900/90 rounded-t-[26px] border-b border-slate-800">
            <span>15:59 • 5G</span>
            <div className="w-16 h-3.5 rounded-full bg-black border border-slate-800" />
            <span className="tabular-nums">88% ⚡</span>
          </div>

          {/* Persistent Foreground Service Notification Banner */}
          <div className="px-3 py-1.5 bg-slate-900/95 border-b border-slate-800/80 flex items-center justify-between text-[11px]">
            <div className="flex items-center gap-2 truncate">
              <span className="w-2 h-2 rounded-full bg-emerald-400 shrink-0" />
              <span className="font-medium text-slate-200 truncate">
                FGS: PokéMate Overlay Active
              </span>
              <span className="text-slate-400 font-mono tabular-nums">
                · {giftsSent}/{maxGiftsPerSession} sent
              </span>
            </div>
            <span className="text-[10px] font-mono text-emerald-400 shrink-0">STICKY</span>
          </div>

          {/* Simulated Scopely Explore Pokémon GO Active Window */}
          <div
            ref={phoneContainerRef}
            className="relative h-[600px] w-full overflow-hidden bg-gradient-to-b from-[#0f2942] via-[#133b5c] to-[#0d2238] text-white"
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerLeave={handlePointerUp}
          >
            {/* Pokémon GO Top Game Bar + Screen Switcher */}
            <div className="px-3 py-2 bg-black/35 backdrop-blur-xs border-b border-white/10 space-y-1.5">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-[10px] font-mono text-sky-300">
                    com.scopely.pokemongo (Scopely Explore)
                  </div>
                  <div className="text-xs font-semibold text-white">
                    {pogoScreen === 'CATCH_ENCOUNTER' && `Catch: ${currentPokemon.name} (CP ${currentPokemon.cp})`}
                    {pogoScreen === 'FRIENDS_LIST' && 'Friends List (142 / 400)'}
                    {pogoScreen === 'PROFILE' && `Trainer: ${currentTargetFriend?.name || 'Trainer'}`}
                    {pogoScreen === 'GIFT_PICKER' && 'Select a Gift Postcard'}
                    {pogoScreen === 'FIELD_RESEARCH_MENU' && 'Field Research Menu (3 Active Slots)'}
                  </div>
                </div>
                <button
                  onClick={() => setShowA11yBounds(!showA11yBounds)}
                  className={`px-2 py-0.5 rounded text-[10px] font-mono transition-colors ${
                    showA11yBounds
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                      : 'bg-white/10 text-slate-300'
                  }`}
                >
                  A11y Bounds: {showA11yBounds ? 'ON' : 'OFF'}
                </button>
              </div>

              {/* 3-Screen Switcher: Catch Encounter (Hold to Aim) vs Friends vs Field Research */}
              <div className="grid grid-cols-3 gap-1 bg-black/30 p-1 rounded-lg text-[11px]">
                <button
                  onClick={() => setPogoScreen('CATCH_ENCOUNTER')}
                  className={`py-1 rounded font-medium transition-colors ${
                    pogoScreen === 'CATCH_ENCOUNTER'
                      ? 'bg-emerald-500 text-slate-950 font-bold shadow-sm'
                      : 'text-sky-200 hover:text-white'
                  }`}
                >
                  Encounter 🎯
                </button>
                <button
                  onClick={() => setPogoScreen('FRIENDS_LIST')}
                  className={`py-1 rounded font-medium transition-colors ${
                    pogoScreen === 'FRIENDS_LIST' || pogoScreen === 'PROFILE' || pogoScreen === 'GIFT_PICKER'
                      ? 'bg-emerald-500 text-slate-950 font-bold shadow-sm'
                      : 'text-sky-200 hover:text-white'
                  }`}
                >
                  Friends 👥
                </button>
                <button
                  onClick={handleTriggerScreenResearchScan}
                  className={`py-1 rounded font-medium transition-colors ${
                    pogoScreen === 'FIELD_RESEARCH_MENU'
                      ? 'bg-amber-400 text-slate-950 font-bold shadow-sm'
                      : 'text-sky-200 hover:text-white'
                  }`}
                >
                  Research 🔭
                </button>
              </div>
            </div>

            {/* SCREEN 0: POKÉMON GO WILD CATCH ENCOUNTER & AIMING RETICLE CANVAS */}
            {pogoScreen === 'CATCH_ENCOUNTER' && (
              <div className="relative h-[535px] w-full flex flex-col justify-between overflow-hidden bg-gradient-to-b from-[#1e3a5f] via-[#0f2847] to-[#071626]">
                {/* Pokémon Encounter Header Bar: CP + Name + Shiny + Preset Switcher */}
                <div className="px-3 pt-2 pb-1.5 flex items-center justify-between z-10 bg-black/40 backdrop-blur-xs border-b border-white/10">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <span className="px-2 py-0.5 rounded bg-slate-900/80 border border-slate-700 text-xs font-mono font-bold text-amber-300">
                      CP {currentPokemon.cp}
                    </span>
                    <span className="text-xs font-bold text-white flex items-center gap-1">
                      {currentPokemon.name}
                      {currentPokemon.shiny && <span className="text-amber-300 text-xs">✨</span>}
                    </span>
                    <span className="text-[10px] font-mono text-emerald-300">
                      {currentPokemon.iv}% IV
                    </span>
                    {/* Non-intrusive Legacy Move indicator button when NOT active */}
                    {(() => {
                      const legacy = getLegacyEvolutionInfo(currentPokemon.name);
                      if (legacy && !legacy.isCurrentlyActive) {
                        return (
                          <button
                            onClick={() => onOpenLegacyHub?.(currentPokemon.name)}
                            className="px-1.5 py-0.5 rounded bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-[9px] font-mono text-amber-300 flex items-center gap-1 transition-colors"
                            title="Legacy Evolution Move available during events or via Elite TM"
                          >
                            <Zap className="w-2.5 h-2.5 fill-current" />
                            <span>{legacy.exclusiveMove}</span>
                          </button>
                        );
                      }
                      return null;
                    })()}
                  </div>

                  {/* Switch Wild Pokémon Selector */}
                  <select
                    value={selectedWildPokemonIndex}
                    onChange={(e) => setSelectedWildPokemonIndex(Number(e.target.value))}
                    className="bg-slate-900 border border-slate-700 rounded px-2 py-0.5 text-[10px] text-sky-200 focus:outline-none"
                  >
                    {WILD_POKEMON_PRESETS.map((p, idx) => (
                      <option key={p.name} value={idx}>
                        {p.name} ({p.distance})
                      </option>
                    ))}
                  </select>
                </div>

                {/* Highlighted Live Active Event Banner (ONLY shown if active right now!) */}
                {(() => {
                  const legacy = getLegacyEvolutionInfo(currentPokemon.name);
                  if (legacy && legacy.isCurrentlyActive) {
                    return (
                      <div className="mx-3 mt-1.5 px-2.5 py-1 rounded-lg bg-amber-500/25 border border-amber-400 text-amber-200 text-[10px] font-semibold flex items-center justify-between shadow-lg shadow-amber-950/40 z-10 animate-pulse">
                        <div className="flex items-center gap-1.5 truncate">
                          <Zap className="w-3.5 h-3.5 text-amber-400 fill-current shrink-0" />
                          <span className="truncate">
                            ⚡ <strong>LIVE EVENT:</strong> Evolve into {legacy.speciesName} NOW for <strong>{legacy.exclusiveMove}</strong>!
                          </span>
                        </div>
                        <button
                          onClick={() => onOpenLegacyHub?.(currentPokemon.name)}
                          className="px-2 py-0.5 rounded bg-amber-400 text-slate-950 font-bold text-[9px] shrink-0 hover:bg-amber-300 ml-1.5"
                        >
                          View Move
                        </button>
                      </div>
                    );
                  }
                  return null;
                })()}

                {/* Interactive Appraisal IV & Legacy Move Scanner HUD Overlay */}
                {activeAppraisalPokemon && (
                  <div className="mx-3 mt-1.5 p-3 rounded-2xl bg-slate-950/95 border border-emerald-500/60 shadow-2xl backdrop-blur-md space-y-2 z-30">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                      <div className="flex items-center gap-2">
                        <span className="text-xl">{activeAppraisalPokemon.fallbackEmoji}</span>
                        <div>
                          <div className="text-xs font-bold text-white flex items-center gap-1.5">
                            <span>{activeAppraisalPokemon.name}</span>
                            <span className="text-[10px] font-mono text-amber-300">CP {activeAppraisalPokemon.cp}</span>
                            {activeAppraisalPokemon.shiny && <span className="text-amber-300 text-xs">✨</span>}
                          </div>
                          <div className="text-[10px] text-emerald-400 font-mono font-semibold">
                            {activeAppraisalPokemon.iv}% IV · 3★ Team Leader Appraisal
                          </div>
                        </div>
                      </div>
                      <button
                        onClick={() => setActiveAppraisalPokemon(null)}
                        className="p-1 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-white transition-colors"
                        title="Close Appraisal Overlay"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>

                    {/* IV Stat Bars */}
                    <div className="space-y-1 text-[10px] font-mono">
                      <div className="flex items-center justify-between text-slate-300">
                        <span className="w-16">ATK: {activeAppraisalPokemon.atk || 15}/15</span>
                        <div className="flex-1 max-w-[170px] h-2 rounded-full bg-slate-900 border border-slate-800 overflow-hidden">
                          <div
                            className="h-full bg-rose-500 rounded-full"
                            style={{ width: `${((activeAppraisalPokemon.atk || 15) / 15) * 100}%` }}
                          />
                        </div>
                        <span className="text-rose-400 font-bold ml-1.5">MAX</span>
                      </div>
                      <div className="flex items-center justify-between text-slate-300">
                        <span className="w-16">DEF: {activeAppraisalPokemon.def || 14}/15</span>
                        <div className="flex-1 max-w-[170px] h-2 rounded-full bg-slate-900 border border-slate-800 overflow-hidden">
                          <div
                            className="h-full bg-amber-500 rounded-full"
                            style={{ width: `${((activeAppraisalPokemon.def || 14) / 15) * 100}%` }}
                          />
                        </div>
                        <span className="text-amber-400 ml-1.5">{activeAppraisalPokemon.def || 14}</span>
                      </div>
                      <div className="flex items-center justify-between text-slate-300">
                        <span className="w-16">HP: {activeAppraisalPokemon.sta || 14}/15</span>
                        <div className="flex-1 max-w-[170px] h-2 rounded-full bg-slate-900 border border-slate-800 overflow-hidden">
                          <div
                            className="h-full bg-emerald-500 rounded-full"
                            style={{ width: `${((activeAppraisalPokemon.sta || 14) / 15) * 100}%` }}
                          />
                        </div>
                        <span className="text-emerald-400 ml-1.5">{activeAppraisalPokemon.sta || 14}</span>
                      </div>
                    </div>

                    {/* Smart Legacy Evolution Check: Highlighted if active NOW, subtle if inactive, hidden if none */}
                    {(() => {
                      const legacy = getLegacyEvolutionInfo(activeAppraisalPokemon.name);
                      if (!legacy) return null; // If no legacy move available for this line, do not say anything!

                      if (legacy.isCurrentlyActive) {
                        return (
                          <div className="p-2 rounded-xl bg-amber-500/20 border border-amber-400/80 space-y-1.5 text-[10px]">
                            <div className="flex items-center justify-between">
                              <span className="font-bold text-amber-300 flex items-center gap-1">
                                <Zap className="w-3.5 h-3.5 text-amber-400 fill-amber-400 animate-pulse" />
                                ⚡ ACTIVE EVENT EVOLUTION MOVE!
                              </span>
                              <span className="px-1.5 py-0.5 rounded bg-amber-400 text-slate-950 font-black text-[9px]">
                                ACTIVE NOW
                              </span>
                            </div>
                            <div className="text-white leading-relaxed">
                              Evolve into <strong>{legacy.speciesName}</strong> during this active event to learn{' '}
                              <strong className="text-amber-300 underline underline-offset-2">{legacy.exclusiveMove}</strong>{' '}
                              without using an Elite TM!
                            </div>
                            <div className="text-[9px] text-amber-200/90 font-mono">
                              ⏰ Event: {legacy.activeEventName} {legacy.activeUntil ? `(${legacy.activeUntil})` : ''}
                            </div>
                            {(legacy.greatLeagueCapCp || legacy.ultraLeagueCapCp) && (
                              <div className="pt-1 border-t border-amber-500/30 flex items-center gap-3 text-[9px] font-mono text-slate-300">
                                {legacy.greatLeagueCapCp && <span>GL Cap: ≤{legacy.greatLeagueCapCp} CP</span>}
                                {legacy.ultraLeagueCapCp && <span>UL Cap: ≤{legacy.ultraLeagueCapCp} CP</span>}
                              </div>
                            )}
                            <button
                              onClick={() => {
                                setActiveAppraisalPokemon(null);
                                onOpenLegacyHub?.(legacy.speciesName);
                              }}
                              className="w-full py-1.5 rounded-lg bg-amber-400 hover:bg-amber-300 text-slate-950 font-bold text-[10px] text-center transition-colors flex items-center justify-center gap-1.5"
                            >
                              <Zap className="w-3 h-3 fill-current" />
                              <span>View Full Legacy Move Details &amp; Search Filter →</span>
                            </button>
                          </div>
                        );
                      }

                      // If legacy move exists but NOT currently active
                      return (
                        <div className="p-2 rounded-xl bg-slate-900 border border-slate-800 text-[10px] flex items-center justify-between gap-2">
                          <div className="truncate">
                            <span className="text-slate-400">Legacy Move: </span>
                            <span className="text-amber-300 font-semibold">{legacy.exclusiveMove}</span>
                            <span className="text-slate-500 text-[9px] ml-1">(Elite TM or Event)</span>
                          </div>
                          <button
                            onClick={() => {
                              setActiveAppraisalPokemon(null);
                              onOpenLegacyHub?.(legacy.speciesName);
                            }}
                            className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-[9px] shrink-0"
                          >
                            Legacy Hub
                          </button>
                        </div>
                      );
                    })()}
                  </div>
                )}

                {/* Encounter Arena: Meadow ring ground + Wild Pokémon Sprite + Shrinking Target Rings */}
                <div className="relative flex-1 flex flex-col items-center justify-center">
                  {/* Ground Stadium Ring */}
                  <div className="absolute top-[260px] w-64 h-24 rounded-[100%] bg-emerald-950/40 border border-emerald-500/20 shadow-inner blur-[1px]" />
                  <div className="absolute top-[275px] w-48 h-16 rounded-[100%] bg-emerald-900/30 border border-emerald-400/30" />

                  {/* The Wild Pokémon Container */}
                  <div
                    className="relative flex flex-col items-center justify-center cursor-crosshair transition-all"
                    style={{
                      transform: `translate(${currentPokemon.coords.x - 175}px, ${currentPokemon.coords.y - 220}px)`,
                    }}
                  >
                    {/* Outer Target Circle (Fixed White Ring) */}
                    <div
                      className="absolute rounded-full border border-white/60 pointer-events-none transition-all"
                      style={{
                        width: `${currentPokemon.outerRingRadius * 2}px`,
                        height: `${currentPokemon.outerRingRadius * 2}px`,
                      }}
                    />

                    {/* Inner Target Circle (Shrinking Dynamic Ring - Color reflects Nice / Great / Excellent) */}
                    <div
                      className="absolute rounded-full border-2 pointer-events-none transition-all shadow-sm"
                      style={{
                        width: `${ringRadius * 2}px`,
                        height: `${ringRadius * 2}px`,
                        borderColor: ringRadius <= 28 ? '#10B981' : ringRadius <= 48 ? '#0EA5E9' : '#F59E0B',
                        backgroundColor:
                          ringRadius <= 28
                            ? 'rgba(16, 185, 129, 0.12)'
                            : ringRadius <= 48
                            ? 'rgba(14, 165, 233, 0.08)'
                            : 'rgba(245, 158, 11, 0.06)',
                      }}
                    >
                      {/* Bullseye Center Dot */}
                      <div
                        className="absolute inset-0 m-auto w-2 h-2 rounded-full"
                        style={{
                          backgroundColor:
                            ringRadius <= 28 ? '#10B981' : ringRadius <= 48 ? '#0EA5E9' : '#F59E0B',
                        }}
                      />
                    </div>

                    {/* Pokémon Visual Avatar */}
                    <div className="w-28 h-28 relative flex items-center justify-center filter drop-shadow-2xl animate-pulse">
                      <div
                        className="text-5xl select-none"
                        style={{ filter: `drop-shadow(0 0 16px ${currentPokemon.color})` }}
                      >
                        {currentPokemon.fallbackEmoji}
                      </div>
                    </div>

                    {/* Tag badge under Pokémon */}
                    <div className="mt-1 px-2 py-0.5 rounded-full bg-black/60 border border-white/10 text-[9px] font-mono text-slate-300">
                      {currentPokemon.type} · {currentPokemon.distance}
                    </div>
                  </div>

                  {/* Impact Flash & Rating Overlay */}
                  {lastThrowResult && !ballFlight && (
                    <div className="absolute top-12 z-20 px-4 py-2 rounded-2xl bg-black/85 border border-emerald-400/80 shadow-2xl text-center space-y-1 animate-bounce">
                      <div className="text-xs font-mono font-bold text-white flex items-center justify-center gap-1.5">
                        <Sparkles className="w-3.5 h-3.5 text-amber-300" />
                        <span
                          style={{
                            color:
                              lastThrowResult.grade === 'EXCELLENT'
                                ? '#10B981'
                                : lastThrowResult.grade === 'GREAT'
                                ? '#0EA5E9'
                                : lastThrowResult.grade === 'NICE'
                                ? '#F59E0B'
                                : '#94A3B8',
                          }}
                        >
                          {lastThrowResult.grade === 'EXCELLENT'
                            ? '🌟 EXCELLENT THROW!'
                            : lastThrowResult.grade === 'GREAT'
                            ? '🔥 GREAT THROW!'
                            : lastThrowResult.grade === 'NICE'
                            ? '👍 NICE THROW!'
                            : '🎯 TARGET HIT!'}
                        </span>
                        {lastThrowResult.isCurveball && (
                          <span className="text-amber-300 text-[10px]">🌀 +1.7x Curve</span>
                        )}
                      </div>
                      <div className="text-[10px] text-slate-300 font-mono">
                        +{lastThrowResult.xpEarned} XP Earned · {lastThrowResult.hitDistance}px from center
                      </div>
                      {lastThrowResult.fastCatch && (
                        <div className="text-[9px] font-mono text-emerald-400 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-500/40">
                          ⚡ Fast Catch Skip: 0.8s (14s Cutscene Bypassed)
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Bottom Pokéball Stand with Ready Prompt */}
                <div className="relative px-3 pb-3 flex flex-col items-center justify-center z-10 bg-gradient-to-t from-black/80 to-transparent">
                  {/* The Resting Pokéball */}
                  <div
                    className="relative w-12 h-12 rounded-full border-2 border-white/80 shadow-2xl overflow-hidden cursor-grab active:cursor-grabbing hover:scale-105 transition-transform"
                    style={{
                      background: 'linear-gradient(180deg, #EF4444 48%, #1E293B 48%, #1E293B 52%, #F8FAFC 52%)',
                    }}
                    onPointerDown={(e) => handleStartThrowAim(e)}
                    title="Hold to Aim Reticle • Drag & Release to Throw!"
                  >
                    {/* Pokéball Center Button */}
                    <div className="absolute inset-0 m-auto w-4 h-4 rounded-full bg-white border-2 border-slate-900 shadow-sm flex items-center justify-center">
                      <div className="w-1.5 h-1.5 rounded-full bg-slate-400" />
                    </div>
                  </div>

                  <div className="mt-1.5 text-[10px] font-mono text-slate-300 flex items-center gap-1.5">
                    <Crosshair className="w-3 h-3 text-emerald-400" />
                    <span>Hold any Throw Button to Aim Reticle</span>
                  </div>
                </div>

                {/* ========================================================================= */}
                {/* INTERACTIVE AIMING RETICLE & DYNAMIC CURVEBALL ARC OVERLAY (SVG LAYER)      */}
                {/* ========================================================================= */}
                {isAimingThrow && (
                  <svg className="absolute inset-0 w-full h-full pointer-events-none z-30">
                    <defs>
                      <linearGradient id="curveballGradient" x1="0%" y1="100%" x2="0%" y2="0%">
                        <stop offset="0%" stopColor="#38BDF8" stopOpacity="0.9" />
                        <stop offset="50%" stopColor="#818CF8" stopOpacity="0.95" />
                        <stop offset="100%" stopColor="#34D399" stopOpacity="1" />
                      </linearGradient>
                      <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
                        <feGaussianBlur stdDeviation="3" result="blur" />
                        <feComposite in="SourceGraphic" in2="blur" operator="over" />
                      </filter>
                    </defs>

                    {/* The Full Dynamic Trajectory Arc */}
                    <path
                      d={trajectoryPathD}
                      fill="none"
                      stroke="url(#curveballGradient)"
                      strokeWidth="3.5"
                      strokeDasharray="6,4"
                      filter="url(#glow)"
                      className="animate-pulse"
                    />

                    {/* Aerodynamic Magnus Spin Particle Markers along the Curve */}
                    {[0.25, 0.5, 0.75].map((tVal, idx) => {
                      const pt = evalTrajectoryPoint(tVal);
                      return (
                        <g key={idx}>
                          <circle cx={pt.x} cy={pt.y} r="3.5" fill="#38BDF8" filter="url(#glow)" />
                          {isSpinThrow && (
                            <text
                              x={pt.x + (spinDirection === 'CCW' ? -12 : 6)}
                              y={pt.y - 4}
                              fontSize="8"
                              fill="#93C5FD"
                              fontFamily="monospace"
                            >
                              {spinDirection === 'CCW' ? '↺' : '↻'}
                            </text>
                          )}
                        </g>
                      );
                    })}

                    {/* Reticle Target Crosshairs at aimCoords (x, y) */}
                    <g transform={`translate(${aimCoords.x}, ${aimCoords.y})`}>
                      {/* Outer Aiming Circle */}
                      <circle
                        cx="0"
                        cy="0"
                        r="24"
                        fill="none"
                        stroke={currentAimGrade.color}
                        strokeWidth="2"
                        strokeDasharray="4,2"
                        className="animate-spin"
                        style={{ animationDuration: '6s' }}
                      />

                      {/* 4 Corner Targeting Brackets */}
                      <path
                        d="M -28 -14 L -28 -28 L -14 -28 M 14 -28 L 28 -28 L 28 -14 M 28 14 L 28 28 L 14 28 M -14 28 L -28 28 L -28 14"
                        fill="none"
                        stroke={currentAimGrade.color}
                        strokeWidth="2"
                      />

                      {/* Crosshair Tick Marks */}
                      <line x1="-34" y1="0" x2="-14" y2="0" stroke={currentAimGrade.color} strokeWidth="1.5" />
                      <line x1="14" y1="0" x2="34" y2="0" stroke={currentAimGrade.color} strokeWidth="1.5" />
                      <line x1="0" y1="-34" x2="0" y2="-14" stroke={currentAimGrade.color} strokeWidth="1.5" />
                      <line x1="0" y1="14" x2="0" y2="34" stroke={currentAimGrade.color} strokeWidth="1.5" />

                      {/* Center Bullseye Dot */}
                      <circle cx="0" cy="0" r="3.5" fill="#EF4444" />

                      {/* Live Aim Readout Badge */}
                      <foreignObject x="-75" y="32" width="150" height="48">
                        <div
                          className="px-2 py-1 rounded bg-black/90 border text-center shadow-lg pointer-events-none"
                          style={{ borderColor: currentAimGrade.color }}
                        >
                          <div
                            className="text-[10px] font-mono font-bold leading-tight"
                            style={{ color: currentAimGrade.color }}
                          >
                            {currentAimGrade.label === 'EXCELLENT'
                              ? '🎯 EXCELLENT! (+100)'
                              : currentAimGrade.label === 'GREAT'
                              ? '🎯 GREAT! (+50)'
                              : currentAimGrade.label === 'NICE'
                              ? '🎯 NICE! (+20)'
                              : '🎯 HIT (+10)'}
                          </div>
                          <div className="text-[8px] font-mono text-slate-300">
                            [{Math.round(aimCoords.x)}, {Math.round(aimCoords.y)}] · {Math.round(distFromTargetCenter)}px
                          </div>
                        </div>
                      </foreignObject>
                    </g>
                  </svg>
                )}

                {/* Top Aiming HUD Status Notice */}
                {isAimingThrow && (
                  <div className="absolute top-10 left-3 right-3 z-40 px-3 py-1.5 rounded-xl bg-slate-950/95 border border-cyan-400 shadow-2xl flex items-center justify-between text-[11px] font-mono animate-pulse">
                    <div className="flex items-center gap-1.5 text-cyan-300">
                      <Target className="w-3.5 h-3.5 text-cyan-400" />
                      <span className="font-bold">Aiming Reticle Active</span>
                    </div>
                    <span className="text-[10px] text-amber-300">
                      {isSpinThrow ? `🌀 Curve (${spinDirection})` : '⬆️ Straight'}
                    </span>
                  </div>
                )}

                {/* Animated Flying Ball */}
                {ballFlight && (
                  <div
                    className="absolute z-40 pointer-events-none"
                    style={{
                      left: `${ballFlight.x}px`,
                      top: `${ballFlight.y}px`,
                      transform: `translate(-50%, -50%) scale(${ballFlight.scale}) rotate(${ballFlight.rotation}deg)`,
                    }}
                  >
                    <div
                      className="w-12 h-12 rounded-full border-2 border-white shadow-2xl overflow-hidden"
                      style={{
                        background: 'linear-gradient(180deg, #EF4444 48%, #1E293B 48%, #1E293B 52%, #F8FAFC 52%)',
                      }}
                    >
                      <div className="absolute inset-0 m-auto w-4 h-4 rounded-full bg-white border-2 border-slate-900 flex items-center justify-center">
                        <div className="w-1.5 h-1.5 rounded-full bg-slate-400" />
                      </div>
                    </div>
                    {/* Spin sparkle trail */}
                    {isSpinThrow && (
                      <div className="absolute -inset-2 rounded-full border border-cyan-400/50 animate-ping" />
                    )}
                  </div>
                )}
              </div>
            )}

            {/* SCREEN 1: POKÉMON GO FRIENDS LIST */}
            {pogoScreen === 'FRIENDS_LIST' && (
              <div className="p-3 space-y-2">
                <div
                  className={`px-3 py-1.5 rounded-lg bg-white/10 text-xs text-sky-100 flex items-center justify-between ${
                    showA11yBounds ? 'outline-1 outline-dashed outline-sky-400/60' : ''
                  }`}
                >
                  <span>Search Friends (giftable&amp;!lucky)</span>
                  <span className="font-mono text-[10px] text-sky-300">Sort: Gift</span>
                </div>

                <div className="space-y-2 pt-1">
                  {filteredFriendsForSession.slice(0, 6).map((friend, idx) => {
                    const isCurrentTarget =
                      isRunning && idx === activeFriendIndex % filteredFriendsForSession.length;
                    return (
                      <div
                        key={friend.id}
                        onClick={() => {
                          setActiveFriendIndex(idx);
                          setPogoScreen('PROFILE');
                        }}
                        className={`p-2.5 rounded-xl transition-all cursor-pointer flex items-center justify-between ${
                          isCurrentTarget
                            ? 'bg-emerald-500/25 border border-emerald-400'
                            : 'bg-white/10 hover:bg-white/15 border border-white/10'
                        } ${showA11yBounds ? 'outline-1 outline-dashed outline-emerald-400/70' : ''}`}
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <div
                            className="w-9 h-9 rounded-full flex items-center justify-center font-bold text-xs text-slate-950 shrink-0"
                            style={{ backgroundColor: friend.avatarColor }}
                          >
                            {friend.name.slice(0, 2).toUpperCase()}
                          </div>
                          <div className="min-w-0">
                            <div className="text-xs font-semibold text-white truncate flex items-center gap-1.5">
                              <span>{friend.name}</span>
                              {showA11yBounds && (
                                <span className="text-[9px] font-mono text-emerald-300">
                                  [Node#{idx + 1}]
                                </span>
                              )}
                            </div>
                            <div className="text-[11px] text-sky-200/80 flex items-center gap-1.5">
                              <span>
                                {'♥'.repeat(friend.friendshipLevel)}
                                {'♡'.repeat(4 - friend.friendshipLevel)}
                              </span>
                              <span>·</span>
                              <span>Buddy: {friend.buddyPokemon}</span>
                            </div>
                          </div>
                        </div>

                        <div className="text-right shrink-0">
                          {friend.giftableStatus === 'GIFTABLE' && (
                            <span className="text-[11px] font-mono text-emerald-300 flex items-center gap-1">
                              <Gift className="w-3.5 h-3.5" /> Can Receive
                            </span>
                          )}
                          {friend.giftableStatus === 'SENT_TODAY' && (
                            <span className="text-[11px] font-mono text-slate-400">
                              Sent Today ✓
                            </span>
                          )}
                          {friend.giftableStatus === 'UNOPENED_GIFT' && (
                            <span className="text-[11px] font-mono text-amber-300">
                              Unopened Gift
                            </span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* SCREEN 2: TRAINER PROFILE VIEW */}
            {pogoScreen === 'PROFILE' && currentTargetFriend && (
              <div className="p-4 flex flex-col justify-between h-[480px]">
                <div>
                  <button
                    onClick={() => setPogoScreen('FRIENDS_LIST')}
                    className="text-xs text-sky-300 flex items-center gap-1 mb-3 hover:underline"
                  >
                    <ArrowLeft className="w-3.5 h-3.5" /> Back to Friends List
                  </button>
                  <div className="p-4 rounded-2xl bg-white/10 border border-white/15 text-center space-y-2">
                    <div
                      className="w-16 h-16 rounded-full mx-auto flex items-center justify-center text-lg font-bold text-slate-950"
                      style={{ backgroundColor: currentTargetFriend.avatarColor }}
                    >
                      {currentTargetFriend.name.slice(0, 2).toUpperCase()}
                    </div>
                    <div className="text-base font-bold">{currentTargetFriend.name}</div>
                    <div className="text-xs text-sky-200">
                      Buddy: {currentTargetFriend.buddyPokemon} · {currentTargetFriend.daysToNextTier}d to next tier
                    </div>
                  </div>
                </div>

                <div className="space-y-3">
                  <button
                    onClick={() => setPogoScreen('GIFT_PICKER')}
                    disabled={currentTargetFriend.giftableStatus !== 'GIFTABLE'}
                    className={`w-full py-3.5 px-4 rounded-2xl font-semibold text-sm flex items-center justify-center gap-2 transition-all ${
                      currentTargetFriend.giftableStatus === 'GIFTABLE'
                        ? 'bg-emerald-500 text-slate-950 hover:bg-emerald-400'
                        : 'bg-slate-700/60 text-slate-400 cursor-not-allowed'
                    } ${showA11yBounds ? 'outline-2 outline-dashed outline-amber-300' : ''}`}
                  >
                    <Gift className="w-4 h-4" />
                    <span>
                      {currentTargetFriend.giftableStatus === 'GIFTABLE'
                        ? 'Send Gift (contentDesc="Send Gift")'
                        : 'Gift Already Sent / Unopened'}
                    </span>
                  </button>
                  <div className="grid grid-cols-2 gap-2 text-xs text-center text-sky-200/80">
                    <div className="py-2 rounded-xl bg-white/10">Battle</div>
                    <div className="py-2 rounded-xl bg-white/10">Trade</div>
                  </div>
                </div>
              </div>
            )}

            {/* SCREEN 3: POSTCARD GIFT PICKER */}
            {pogoScreen === 'GIFT_PICKER' && currentTargetFriend && (
              <div className="p-4 flex flex-col justify-between h-[480px]">
                <div className="space-y-3">
                  <div className="text-xs text-sky-200">
                    Choose a PokéStop Postcard for <span className="font-semibold text-white">{currentTargetFriend.name}</span>:
                  </div>
                  <div
                    className={`p-4 rounded-2xl bg-gradient-to-br from-amber-500/20 to-emerald-500/20 border border-emerald-400/50 space-y-2 ${
                      showA11yBounds ? 'outline-2 outline-dashed outline-emerald-300' : ''
                    }`}
                  >
                    <div className="text-xs font-mono text-emerald-300">POSTCARD #1 (AUTO-SELECTED)</div>
                    <div className="text-sm font-bold text-white">Shibuya PokéCenter Fountain</div>
                    <div className="text-xs text-slate-300">Tokyo, Japan · Gifts Remaining: {activeAccount.giftsInInventory}</div>
                  </div>
                </div>

                <div className="space-y-2">
                  <button
                    onClick={() => {
                      onUpdateFriend(currentTargetFriend.id, {
                        giftableStatus: 'SENT_TODAY',
                        lastGiftSent: 'Just now',
                      });
                      setGiftsSent((g) => g + 1);
                      setFriendsProcessed((p) => p + 1);
                      setPogoScreen('FRIENDS_LIST');
                      onTriggerSnackbar(`Manual gift sent to ${currentTargetFriend.name}`, 'success');
                    }}
                    className={`w-full py-3.5 rounded-2xl bg-emerald-400 text-slate-950 font-bold text-sm hover:bg-emerald-300 transition-colors ${
                      showA11yBounds ? 'outline-2 outline-dashed outline-white' : ''
                    }`}
                  >
                    SEND (id/btn_confirm_gift)
                  </button>
                  <button
                    onClick={() => setPogoScreen('PROFILE')}
                    className="w-full py-2 text-xs text-sky-200 hover:text-white"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}

            {/* SCREEN 4: IN-GAME FIELD RESEARCH MENU (3 ACTIVE SLOTS) */}
            {pogoScreen === 'FIELD_RESEARCH_MENU' && (
              <div className="p-3 space-y-2.5">
                <div className="flex items-center justify-between px-1">
                  <span className="text-xs font-bold uppercase tracking-wider text-amber-300">
                    Field Research (In-Game Menu)
                  </span>
                  <button
                    onClick={handleShuffleResearchMenuSlots}
                    className="px-2 py-1 rounded bg-white/15 hover:bg-white/25 text-[10px] font-mono text-white flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" /> Spin New PokéStop Tasks
                  </button>
                </div>

                <div className="space-y-2">
                  {onScreenFieldResearchTasks.map((task, slotIdx) => (
                    <div
                      key={`${task.id}-slot-${slotIdx}`}
                      className={`p-3 rounded-xl bg-amber-500/15 border border-amber-400/40 flex items-center justify-between gap-2 ${
                        showA11yBounds ? 'outline-2 outline-dashed outline-emerald-300' : ''
                      }`}
                    >
                      <div className="min-w-0">
                        <div className="text-[10px] font-mono text-emerald-300">
                          [A11y Slot #{slotIdx + 1} Read by Overlay]
                        </div>
                        <div className="text-xs font-bold text-white mt-0.5">
                          {task.taskName}
                        </div>
                        <div className="text-[10px] text-amber-200/80 mt-0.5">
                          In-game shows: Mystery Grass / Item Icon (?)
                        </div>
                      </div>
                      <div className="w-10 h-10 rounded-xl bg-amber-400/20 border border-amber-300/40 flex items-center justify-center text-sm font-bold text-amber-300 shrink-0">
                        ?
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* VISUAL ACCESSIBILITY TAP / GESTURE INDICATOR */}
            {tapRipple && (
              <div
                style={{
                  transform: `translate(${tapRipple.x}px, ${tapRipple.y}px)`,
                }}
                className="pointer-events-none absolute top-0 left-0 z-20 transition-transform duration-200"
              >
                <div className="relative -translate-x-1/2 -translate-y-1/2 flex flex-col items-center">
                  <div className="w-8 h-8 rounded-full bg-emerald-400/40 border-2 border-emerald-300 animate-ping absolute" />
                  <div className="w-4 h-4 rounded-full bg-emerald-400 border-2 border-slate-950 shadow-lg" />
                  <span className="mt-1 px-1.5 py-0.5 rounded bg-slate-950/90 border border-emerald-500/50 text-[10px] font-mono text-emerald-300 whitespace-nowrap">
                    {tapRipple.label} ({lastDelayMs}ms)
                  </span>
                </div>
              </div>
            )}

            {/* =====================================================================
                DRAGGABLE FLOATING OVERLAY WINDOW (TYPE_APPLICATION_OVERLAY)
               ===================================================================== */}
            <div
              style={{
                transform: `translate(${bubblePos.x}px, ${bubblePos.y}px)`,
              }}
              className="absolute top-0 left-0 z-30 transition-shadow"
            >
              {!isOverlayExpanded ? (
                /* SLEEK COMPACT FLOATING ORB / PILL */
                <div
                  className={`rounded-full flex items-center shadow-2xl border-2 transition-all p-1 gap-1 ${
                    amoledMode
                      ? 'bg-black/95 border-emerald-500 text-white'
                      : 'bg-slate-950/95 border-emerald-400 text-white'
                  }`}
                >
                  <button
                    onPointerDown={handlePointerDown}
                    onClick={() => {
                      setIsOverlayExpanded(true);
                      setBubblePos({ x: 14, y: 105 });
                    }}
                    title="Tap to expand PokéMate HUD"
                    className="px-2.5 py-1 rounded-full flex items-center gap-1.5 cursor-grab active:cursor-grabbing hover:bg-white/10"
                  >
                    <span
                      className={`w-2 h-2 rounded-full ${
                        isRunning ? 'bg-emerald-400 animate-ping' : 'bg-emerald-400'
                      }`}
                    />
                    <span className="text-xs font-mono font-bold tabular-nums">
                      PalpiGO
                    </span>
                  </button>

                  <button
                    onPointerDown={(e) => handleStartThrowAim(e)}
                    className="px-2 py-1 rounded-full bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold font-mono flex items-center gap-1 shadow-sm select-none cursor-grab active:cursor-grabbing"
                    title="Hold to Aim Reticle • Release to Throw!"
                  >
                    <Target className="w-3 h-3 fill-current" />
                    <span>Throw</span>
                  </button>
                </div>
              ) : (
                /* EXPANDED MATERIAL 3 FLOATING CONTROL PANEL */
                <div
                  className={`w-[328px] rounded-2xl shadow-2xl border backdrop-blur-md transition-colors ${
                    amoledMode
                      ? 'bg-black/95 border-slate-800 text-slate-100'
                      : 'bg-slate-950/95 border-slate-700/80 text-slate-100'
                  }`}
                >
                  {/* Draggable Handle Header */}
                  <div
                    onPointerDown={handlePointerDown}
                    className="px-3 py-2 border-b border-slate-800 flex items-center justify-between cursor-grab active:cursor-grabbing"
                  >
                    <div className="flex items-center gap-2">
                      <GripHorizontal className="w-4 h-4 text-slate-400" />
                      <span className="text-xs font-bold tracking-tight text-white">
                        PalpiGO Overlay HUD <span className="text-[10px] text-emerald-400 font-mono">v1.2.2026</span>
                      </span>
                    </div>
                    <div className="flex items-center gap-1">
                      {(() => {
                        const currentLegacy = getLegacyEvolutionInfo(currentPokemon.name);
                        const isActive = currentLegacy?.isCurrentlyActive;
                        return (
                          <button
                            onClick={() => onOpenLegacyHub?.(currentPokemon.name)}
                            className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors flex items-center gap-1 ${
                              isActive
                                ? 'bg-amber-500/30 text-amber-300 font-bold border border-amber-400 animate-pulse shadow-sm shadow-amber-950'
                                : 'bg-amber-500/15 text-amber-300 hover:bg-amber-500/25 border border-amber-500/30'
                            }`}
                            title={
                              isActive
                                ? `⚡ ACTIVE EVENT MOVE: Evolve for ${currentLegacy.exclusiveMove}!`
                                : 'Legacy & Event Evolution Moves Hub'
                            }
                          >
                            <Zap className="w-3 h-3 fill-current" />
                            <span>{isActive ? `⚡ ${currentLegacy.exclusiveMove}` : 'Moves'}</span>
                          </button>
                        );
                      })()}
                      <button
                        onClick={() => {
                          setOverlayTab('THROW');
                          if (pogoScreen !== 'CATCH_ENCOUNTER') setPogoScreen('CATCH_ENCOUNTER');
                        }}
                        className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors ${
                          overlayTab === 'THROW'
                            ? 'bg-amber-500/25 text-amber-300 font-bold border border-amber-500/40'
                            : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        Throw 🎯
                      </button>
                      <button
                        onClick={() => setOverlayTab('CONTROLS')}
                        className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors ${
                          overlayTab === 'CONTROLS'
                            ? 'bg-emerald-500/20 text-emerald-300'
                            : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        Gift
                      </button>
                      <button
                        onClick={handleTriggerScreenResearchScan}
                        className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors ${
                          overlayTab === 'RESEARCH'
                            ? 'bg-emerald-500/20 text-emerald-300'
                            : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        Research Scan
                      </button>
                      <button
                        onClick={() => setOverlayTab('SETTINGS')}
                        className={`p-1 rounded text-slate-400 hover:text-white ${
                          overlayTab === 'SETTINGS' ? 'text-emerald-300' : ''
                        }`}
                        title="Overlay Settings"
                      >
                        <Settings className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => setIsOverlayExpanded(false)}
                        className="p-1 rounded text-slate-400 hover:text-white"
                        title="Collapse into Draggable Bubble"
                      >
                        <Minimize2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>

                  {/* TAB 0: INTERACTIVE THROW ENGINE & RETICLE AIM CONTROLS */}
                  {overlayTab === 'THROW' && (
                    <div className="p-3 space-y-2.5">
                      {/* Hold-to-Aim Primary Throw Buttons */}
                      <div className="grid grid-cols-2 gap-2">
                        <button
                          onPointerDown={(e) => handleStartThrowAim(e)}
                          className="py-2.5 px-3 rounded-xl bg-amber-500 hover:bg-amber-400 active:bg-amber-600 text-slate-950 font-bold text-xs flex flex-col items-center justify-center gap-0.5 shadow-md cursor-grab active:cursor-grabbing transition-all select-none"
                          title="HOLD to show Aiming Reticle • RELEASE to Throw"
                        >
                          <div className="flex items-center gap-1.5">
                            <Target className="w-3.5 h-3.5 fill-current" />
                            <span>🎯 Throw Ball</span>
                          </div>
                          <span className="text-[9px] font-normal opacity-85">
                            Hold to Aim • Release
                          </span>
                        </button>

                        <button
                          onPointerDown={(e) => handleStartThrowAim(e, true)}
                          className="py-2.5 px-3 rounded-xl bg-cyan-500 hover:bg-cyan-400 active:bg-cyan-600 text-slate-950 font-bold text-xs flex flex-col items-center justify-center gap-0.5 shadow-md cursor-grab active:cursor-grabbing transition-all select-none"
                          title="HOLD to show Curveball Reticle • RELEASE to Throw"
                        >
                          <div className="flex items-center gap-1.5">
                            <RotateCcw className="w-3.5 h-3.5" />
                            <span>🌀 Curve Throw</span>
                          </div>
                          <span className="text-[9px] font-normal opacity-85">
                            +1.7x Catch Multiplier
                          </span>
                        </button>
                      </div>

                      {/* Quick Curveball & Spin Toggles Row */}
                      <div className="grid grid-cols-2 gap-2 text-[11px]">
                        <button
                          onClick={() => setIsSpinThrow(!isSpinThrow)}
                          className={`py-1.5 px-2.5 rounded-lg border font-medium flex items-center justify-between transition-colors ${
                            isSpinThrow
                              ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/40'
                              : 'bg-slate-900 text-slate-400 border-slate-800'
                          }`}
                        >
                          <span>Spin Style:</span>
                          <span className="font-bold">{isSpinThrow ? '🌀 Curveball' : '⬆️ Straight'}</span>
                        </button>

                        {isSpinThrow ? (
                          <button
                            onClick={() => setSpinDirection(spinDirection === 'CCW' ? 'CW' : 'CCW')}
                            className="py-1.5 px-2.5 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-200 font-medium flex items-center justify-between transition-colors"
                          >
                            <span>Hook Arc:</span>
                            <span className="font-bold text-sky-300">
                              {spinDirection === 'CCW' ? '↺ Left (CCW)' : '↻ Right (CW)'}
                            </span>
                          </button>
                        ) : (
                          <div className="py-1.5 px-2.5 rounded-lg bg-slate-900/50 border border-slate-800 text-slate-500 text-center font-mono text-[10px]">
                            Spin Disabled
                          </div>
                        )}
                      </div>

                      {/* Distance Profile & Fast Catch Row */}
                      <div className="grid grid-cols-2 gap-2 text-[11px]">
                        <button
                          onClick={() => {
                            const profiles: ('CLOSE' | 'MEDIUM' | 'FAR' | 'FLYING')[] = ['CLOSE', 'MEDIUM', 'FAR', 'FLYING'];
                            const next = profiles[(profiles.indexOf(distanceProfile) + 1) % profiles.length];
                            setDistanceProfile(next);
                          }}
                          className="py-1.5 px-2.5 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-200 font-medium flex items-center justify-between transition-colors"
                        >
                          <span>Distance:</span>
                          <span className="font-bold text-amber-300 font-mono">{distanceProfile}</span>
                        </button>

                        <button
                          onClick={() => setFastCatchEnabled(!fastCatchEnabled)}
                          className={`py-1.5 px-2.5 rounded-lg border font-medium flex items-center justify-between transition-colors ${
                            fastCatchEnabled
                              ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                              : 'bg-slate-900 text-slate-400 border-slate-800'
                          }`}
                        >
                          <span className="flex items-center gap-1">
                            <Zap className="w-3 h-3 text-amber-400" /> Fast Catch:
                          </span>
                          <span className="font-bold">{fastCatchEnabled ? 'ON ⚡' : 'OFF'}</span>
                        </button>
                      </div>

                      {/* Target Circle Lock Switch */}
                      <button
                        onClick={() => setWaitExcellentRing(!waitExcellentRing)}
                        className={`w-full py-1.5 px-2.5 rounded-lg border text-[11px] font-medium flex items-center justify-between transition-colors ${
                          waitExcellentRing
                            ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                            : 'bg-slate-900 text-slate-400 border-slate-800'
                        }`}
                      >
                        <span className="flex items-center gap-1">
                          <Target className="w-3 h-3 text-emerald-400" /> Smallest Circle Lock:
                        </span>
                        <span className="font-bold">{waitExcellentRing ? 'EXCELLENT LOCK' : 'INSTANT'}</span>
                      </button>

                      {/* Throw Calibration & Accuracy Stats */}
                      <div className="pt-2 border-t border-slate-800/90 grid grid-cols-4 gap-1 text-center">
                        <div className="bg-slate-900/60 p-1.5 rounded-lg">
                          <div className="text-[9px] text-slate-400">Throws</div>
                          <div className="text-xs font-mono font-bold text-white tabular-nums">
                            {throwCounters.total}
                          </div>
                        </div>
                        <div className="bg-slate-900/60 p-1.5 rounded-lg">
                          <div className="text-[9px] text-emerald-400">Excellent</div>
                          <div className="text-xs font-mono font-bold text-emerald-400 tabular-nums">
                            {throwCounters.excellent}
                          </div>
                        </div>
                        <div className="bg-slate-900/60 p-1.5 rounded-lg">
                          <div className="text-[9px] text-sky-400">Great</div>
                          <div className="text-xs font-mono font-bold text-sky-400 tabular-nums">
                            {throwCounters.great}
                          </div>
                        </div>
                        <div className="bg-slate-900/60 p-1.5 rounded-lg">
                          <div className="text-[9px] text-amber-400">Curve %</div>
                          <div className="text-xs font-mono font-bold text-amber-400 tabular-nums">
                            {Math.round((throwCounters.curveball / Math.max(1, throwCounters.total)) * 100)}%
                          </div>
                        </div>
                      </div>

                      {/* Aim Instruction Tip */}
                      <div className="text-[10px] font-mono text-slate-400 bg-slate-900/80 p-2 rounded-lg border border-slate-800 flex items-center gap-1.5">
                        <Crosshair className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                        <span>
                          Press &amp; hold either throw button to activate reticle. Drag to aim, release to throw!
                        </span>
                      </div>
                    </div>
                  )}

                  {/* TAB 1: AUTO-GIFT CONTROLS & MINI STATUS DISPLAY */}
                  {overlayTab === 'CONTROLS' && (
                    <div className="p-3 space-y-3">
                      <div className="grid grid-cols-2 gap-2">
                        <button
                          onClick={handleStartAutoGift}
                          disabled={isRunning}
                          className={`py-2 px-3 rounded-xl font-semibold text-xs flex items-center justify-center gap-1.5 transition-all whitespace-nowrap ${
                            isRunning
                              ? 'bg-emerald-950/50 text-emerald-500/50 cursor-not-allowed'
                              : 'bg-emerald-500 text-slate-950 hover:bg-emerald-400 shadow-sm'
                          }`}
                        >
                          <Play className="w-3.5 h-3.5 fill-current" />
                          <span>Start Auto-Gift</span>
                        </button>

                        <button
                          onClick={() => handleStopAutoGift()}
                          disabled={!isRunning}
                          className={`py-2 px-3 rounded-xl font-semibold text-xs flex items-center justify-center gap-1.5 transition-all whitespace-nowrap ${
                            !isRunning
                              ? 'bg-rose-950/40 text-rose-400/40 cursor-not-allowed'
                              : 'bg-rose-600 text-white hover:bg-rose-500 shadow-sm'
                          }`}
                        >
                          <Square className="w-3.5 h-3.5 fill-current" />
                          <span>Stop Automation</span>
                        </button>
                      </div>

                      <div className="grid grid-cols-2 gap-2">
                        <button
                          onClick={handleTriggerScreenResearchScan}
                          className="py-1.5 px-2.5 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-medium text-slate-200 flex items-center justify-center gap-1.5 whitespace-nowrap"
                        >
                          <ScanSearch className="w-3.5 h-3.5 text-emerald-400" />
                          <span>🔭 Scan Research</span>
                        </button>
                        <button
                          onClick={handleScanPokemonAppraisal}
                          className="py-1.5 px-2.5 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-medium text-slate-200 flex items-center justify-center gap-1.5 whitespace-nowrap"
                        >
                          <Camera className="w-3.5 h-3.5 text-amber-400" />
                          <span>🧬 Scan Pokémon</span>
                        </button>
                      </div>

                      <div className="pt-2 border-t border-slate-800/90 grid grid-cols-3 gap-2 text-center">
                        <div>
                          <div className="text-[10px] text-slate-400">Processed</div>
                          <div className="text-sm font-mono font-bold text-white tabular-nums">
                            {friendsProcessed}
                          </div>
                        </div>
                        <div>
                          <div className="text-[10px] text-slate-400">Gifts Sent</div>
                          <div className="text-sm font-mono font-bold text-emerald-400 tabular-nums">
                            {giftsSent}/{maxGiftsPerSession}
                          </div>
                        </div>
                        <div>
                          <div className="text-[10px] text-slate-400">Errors</div>
                          <div className="text-sm font-mono font-bold text-rose-400 tabular-nums">
                            {errorsEncountered}
                          </div>
                        </div>
                      </div>

                      <div className="text-[11px] font-mono text-slate-400 flex items-center justify-between pt-1 border-t border-slate-900">
                        <span className="truncate">Step: {currentStep}</span>
                        <span className="text-emerald-400 shrink-0 tabular-nums">
                          {lastDelayMs}ms
                        </span>
                      </div>
                    </div>
                  )}

                  {/* TAB 2: OVERLAY FIELD RESEARCH SCANNER & REWARD POSSIBILITY HUD */}
                  {overlayTab === 'RESEARCH' && (
                    <div className="p-3 space-y-2.5">
                      {/* Switch between On-Screen Active Slots vs All LeekDuck Tasks */}
                      <div className="grid grid-cols-2 gap-1 bg-slate-900 p-1 rounded-lg text-[10px]">
                        <button
                          onClick={() => setOverlayResearchMode('SCREEN_SCAN')}
                          className={`py-1 rounded font-semibold transition-colors ${
                            overlayResearchMode === 'SCREEN_SCAN'
                              ? 'bg-emerald-500 text-slate-950'
                              : 'text-slate-400 hover:text-white'
                          }`}
                        >
                          On-Screen Menu (3 Slots)
                        </button>
                        <button
                          onClick={() => setOverlayResearchMode('ALL_LEEKDUCK')}
                          className={`py-1 rounded font-semibold transition-colors ${
                            overlayResearchMode === 'ALL_LEEKDUCK'
                              ? 'bg-emerald-500 text-slate-950'
                              : 'text-slate-400 hover:text-white'
                          }`}
                        >
                          All Research ({researchTasks.length})
                        </button>
                      </div>

                      {overlayResearchMode === 'SCREEN_SCAN' ? (
                        /* ON-SCREEN ACTIVE RESEARCH SLOTS + REWARD POSSIBILITY ODDS */
                        <div className="max-h-[240px] overflow-y-auto space-y-2 pr-1">
                          <div className="flex items-center justify-between text-[10px] text-slate-400">
                            <span>Detected from Scopely Research Menu:</span>
                            <button
                              onClick={handleShuffleResearchMenuSlots}
                              className="text-emerald-400 hover:underline font-mono"
                            >
                              Rescan Screen
                            </button>
                          </div>

                          {onScreenFieldResearchTasks.map((task, idx) => {
                            const possibilities = getTaskPossibilities(task);
                            return (
                              <div
                                key={`${task.id}-hud-${idx}`}
                                className="p-2.5 rounded-xl bg-slate-900/95 border border-slate-800 space-y-1.5 text-xs"
                              >
                                <div className="flex items-center justify-between gap-1">
                                  <span className="font-semibold text-white truncate">
                                    Slot #{idx + 1}: {task.taskName}
                                  </span>
                                  {task.isHighValue && (
                                    <Star className="w-3 h-3 text-amber-400 fill-amber-400 shrink-0" />
                                  )}
                                </div>

                                <div className="text-[10px] text-slate-400 font-mono">
                                  Possible Rewards ({possibilities.length} outcome{possibilities.length > 1 ? 's' : ''}):
                                </div>

                                <div className="space-y-1">
                                  {possibilities.map((opt, oIdx) => (
                                    <div
                                      key={oIdx}
                                      className="flex items-center justify-between text-[11px] bg-slate-950/90 px-2 py-1 rounded border border-slate-800/80"
                                    >
                                      <div className="flex items-center gap-1.5 truncate">
                                        {opt.imageUrl && (
                                          <img
                                            src={opt.imageUrl}
                                            alt={opt.name}
                                            referrerPolicy="no-referrer"
                                            className="w-4 h-4 object-contain shrink-0"
                                            onError={(e) => {
                                              (e.currentTarget as HTMLImageElement).style.display = 'none';
                                            }}
                                          />
                                        )}
                                        <span className="text-emerald-300 font-medium truncate">
                                          {opt.name}
                                        </span>
                                        {opt.canBeShiny && (
                                          <span className="text-[10px] text-amber-300">✨</span>
                                        )}
                                      </div>
                                      <div className="font-mono text-[10px] text-sky-300 shrink-0 tabular-nums">
                                        {opt.pct}% {opt.cpText ? `· ${opt.cpText}` : ''}
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      ) : (
                        /* BROWSE & SEARCH ALL LIVE LEEKDUCK TASKS IN OVERLAY */
                        <div className="space-y-2">
                          <input
                            type="text"
                            value={overlaySearchQuery}
                            onChange={(e) => setOverlaySearchQuery(e.target.value)}
                            placeholder="🔍 Search task or reward (e.g. catch, throw, dratini)..."
                            className="w-full px-2.5 py-1.5 rounded-lg bg-slate-900 border border-slate-700 text-[11px] text-white placeholder:text-slate-500 focus:outline-none focus:border-emerald-500"
                          />
                          <div className="flex items-center gap-1 bg-slate-900 p-1 rounded-lg">
                            {(['ALL', 'SHINY', 'HIGH_VALUE', 'QUICK'] as const).map((f) => (
                              <button
                                key={f}
                                onClick={() => setResearchQuickFilter(f)}
                                className={`flex-1 py-1 rounded text-[10px] font-medium transition-colors whitespace-nowrap ${
                                  researchQuickFilter === f
                                    ? 'bg-emerald-500 text-slate-950 font-semibold'
                                    : 'text-slate-400 hover:text-white'
                                }`}
                              >
                                {f === 'ALL' && 'All'}
                                {f === 'SHINY' && 'Shiny ✨'}
                                {f === 'HIGH_VALUE' && '★ Best'}
                                {f === 'QUICK' && 'Quick'}
                              </button>
                            ))}
                          </div>

                          <div className="max-h-[190px] overflow-y-auto space-y-2 pr-1">
                            {filteredOverlayResearch.map((task) => {
                              const possibilities = getTaskPossibilities(task);
                              return (
                                <div
                                  key={task.id}
                                  className="p-2.5 rounded-xl bg-slate-900/95 border border-slate-800 text-xs space-y-1"
                                >
                                  <div className="flex items-center justify-between text-[10px] font-mono text-sky-400">
                                    <span className="truncate">{task.category || 'Research'}</span>
                                    <span className="shrink-0 tabular-nums">
                                      {possibilities[0]?.pct || 100}% chance ea
                                    </span>
                                  </div>
                                  <div className="font-semibold text-white">
                                    {task.taskName}
                                  </div>
                                  <div className="text-[11px] text-emerald-300 flex items-center justify-between gap-1">
                                    <span className="truncate">→ {task.reward}</span>
                                    {task.shinyPossible && (
                                      <span className="text-[10px] text-amber-300 shrink-0">✨</span>
                                    )}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {/* TAB 3: OVERLAY AUTOMATION SETTINGS */}
                  {overlayTab === 'SETTINGS' && (
                    <div className="p-3 space-y-2.5 text-xs">
                      <div className="flex items-center justify-between">
                        <span className="text-slate-300">Max Gifts / Session</span>
                        <select
                          value={maxGiftsPerSession}
                          onChange={(e) => setMaxGiftsPerSession(Number(e.target.value))}
                          className="bg-slate-900 border border-slate-700 rounded px-2 py-1 text-xs font-mono text-white"
                        >
                          <option value={5}>5 Gifts</option>
                          <option value={10}>10 Gifts</option>
                          <option value={20}>20 Gifts (Daily Cap)</option>
                        </select>
                      </div>

                      <div className="flex items-center justify-between">
                        <span className="text-slate-300">Friend Group Filter</span>
                        <select
                          value={friendGroupFilter}
                          onChange={(e) => setFriendGroupFilter(e.target.value)}
                          className="bg-slate-900 border border-slate-700 rounded px-2 py-1 text-xs text-white"
                        >
                          <option value="ALL">All Friends</option>
                          <option value="ULTRA_BEST_PUSH">Ultra/Best Push</option>
                          <option value="LUCKY_CANDIDATES">Lucky Candidates</option>
                          <option value="LOCAL_RAIDERS">Local Raiders</option>
                        </select>
                      </div>

                      <label className="flex items-center justify-between cursor-pointer pt-1">
                        <span className="text-slate-300">Skip Full / Unopened Gifts</span>
                        <input
                          type="checkbox"
                          checked={skipUnopenedGifts}
                          onChange={(e) => setSkipUnopenedGifts(e.target.checked)}
                          className="accent-emerald-500"
                        />
                      </label>

                      <label className="flex items-center justify-between cursor-pointer">
                        <span className="text-slate-300">Safe Mode (1.5s–2.3s Gaussian)</span>
                        <input
                          type="checkbox"
                          checked={safeMode}
                          onChange={(e) => setSafeMode(e.target.checked)}
                          className="accent-emerald-500"
                        />
                      </label>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Android Navigation Bar */}
          <div className="py-2 bg-slate-950 rounded-b-[26px] flex items-center justify-around text-slate-500 border-t border-slate-900">
            <button
              onClick={() => setPogoScreen('FRIENDS_LIST')}
              className="text-xs hover:text-white px-3 py-0.5"
              title="Android Back Action"
            >
              ◀
            </button>
            <div className="w-24 h-1 rounded-full bg-slate-700" />
            <span className="text-[10px] font-mono">API 34</span>
          </div>
        </div>
      </div>

      {/* RIGHT 7 COLS: On-Screen Research Possibility Breakdown + Gift Automation Pipeline + Room Logs */}
      <div className="lg:col-span-7 space-y-6">
        {/* NEW: Live Screen Research Menu Reader & Reward Possibility Analyzer */}
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
            <div>
              <h2 className="text-base font-semibold text-white">
                01. On-Screen Research Menu Reader &amp; Reward Possibility Analyzer
              </h2>
              <p className="text-xs text-slate-400">
                Reads your 3 active Field Research slots in <code className="text-emerald-300">com.scopely.pokemongo</code> and calculates exact LeekDuck reward odds
              </p>
            </div>
            <button
              onClick={handleTriggerScreenResearchScan}
              className="px-3 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold text-xs flex items-center gap-1.5 whitespace-nowrap"
            >
              <ScanSearch className="w-3.5 h-3.5" />
              <span>Scan Active Research Menu</span>
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {onScreenFieldResearchTasks.map((task, idx) => {
              const possibilities = getTaskPossibilities(task);
              return (
                <div
                  key={`${task.id}-card-${idx}`}
                  className="p-3.5 rounded-lg bg-slate-950/80 border border-slate-800 flex flex-col justify-between space-y-2.5"
                >
                  <div>
                    <div className="flex items-center justify-between text-[11px] font-mono text-slate-400">
                      <span>RESEARCH SLOT #{idx + 1}</span>
                      <select
                        value={activeResearchSlotIndices[idx] % Math.max(1, researchTasks.length)}
                        onChange={(e) => {
                          const updated = [...activeResearchSlotIndices];
                          updated[idx] = Number(e.target.value);
                          setActiveResearchSlotIndices(updated);
                        }}
                        className="bg-slate-900 border border-slate-800 rounded px-1.5 py-0.5 text-[10px] text-emerald-300 max-w-[110px] truncate"
                        title="Simulate different task in this slot"
                      >
                        {researchTasks.map((rt, rIdx) => (
                          <option key={rt.id} value={rIdx}>
                            {rt.taskName}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="text-xs font-semibold text-white mt-1.5">
                      {task.taskName}
                    </div>
                  </div>

                  <div className="space-y-1.5 pt-2 border-t border-slate-900">
                    <div className="text-[10px] font-mono text-slate-400">
                      Possibility Breakdown ({possibilities.length} pool):
                    </div>
                    {possibilities.slice(0, 4).map((opt, oIdx) => (
                      <div
                        key={oIdx}
                        className="flex items-center justify-between text-xs"
                      >
                        <div className="flex items-center gap-1.5 truncate pr-1">
                          {opt.imageUrl && (
                            <img
                              src={opt.imageUrl}
                              alt={opt.name}
                              referrerPolicy="no-referrer"
                              className="w-4 h-4 object-contain shrink-0"
                              onError={(e) => {
                                (e.currentTarget as HTMLImageElement).style.display = 'none';
                              }}
                            />
                          )}
                          <span className="text-emerald-400 font-medium truncate">
                            {opt.name}
                          </span>
                          {opt.canBeShiny && (
                            <span className="text-[10px] text-amber-300">✨</span>
                          )}
                        </div>
                        <span className="font-mono text-[11px] text-sky-300 shrink-0 tabular-nums">
                          {opt.pct}%
                        </span>
                      </div>
                    ))}
                    {possibilities.length > 4 && (
                      <div className="text-[10px] font-mono text-slate-500">
                        +{possibilities.length - 4} more ({possibilities[0].pct}% each)
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* 6-Step Accessibility Sequence Pipeline */}
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
            <div>
              <h2 className="text-base font-semibold text-white">
                02. AccessibilityService Gift Sequence Pipeline
              </h2>
              <p className="text-xs text-slate-400">
                Real-time state machine inside <code className="text-emerald-300">GiftAutomationEngine.kt</code>
              </p>
            </div>
            <div className="flex items-center gap-3 text-xs font-mono text-slate-400">
              <span>
                Delay Mode:{' '}
                <strong className="text-emerald-400">
                  {safeMode ? 'Safe (1450–2300ms)' : 'Standard (800–1200ms)'}
                </strong>
              </span>
              <span>·</span>
              <span className="tabular-nums">Last: {lastDelayMs}ms</span>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2.5">
            {SEQUENCE_STEPS.map((s) => {
              const active = isRunning && currentStep === s.key;
              return (
                <div
                  key={s.key}
                  className={`p-3 rounded-lg border text-xs transition-colors ${
                    active
                      ? 'bg-emerald-500/15 border-emerald-400 text-white'
                      : 'bg-slate-950/60 border-slate-800/80 text-slate-400'
                  }`}
                >
                  <div className="font-medium flex items-center justify-between">
                    <span>{s.label}</span>
                    {active && <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />}
                  </div>
                  <div className="text-[11px] font-mono text-slate-500 mt-1">
                    {s.key}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Live Room Database Table: automation_logs */}
        <div
          className={`p-5 rounded-xl border ${
            amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="flex items-center justify-between mb-3">
            <div>
              <h3 className="text-sm font-semibold text-white">
                03. Live Room Database Stream (`automation_logs` table)
              </h3>
              <p className="text-xs text-slate-400">
                Every node scan, tap coordinate, and Gaussian delay persisted via Coroutines
              </p>
            </div>
            <span className="text-xs font-mono text-slate-400 tabular-nums">
              {logs.length} records
            </span>
          </div>

          <div className="overflow-x-auto max-h-[200px] overflow-y-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-slate-400 font-mono text-[11px]">
                  <th className="py-2 pr-3">Timestamp</th>
                  <th className="py-2 pr-3">Action</th>
                  <th className="py-2 pr-3">Target</th>
                  <th className="py-2 pr-3 text-right">Delay</th>
                  <th className="py-2">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 font-mono">
                {logs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-800/30">
                    <td className="py-2 pr-3 text-slate-400 tabular-nums whitespace-nowrap">
                      {log.timestamp}
                    </td>
                    <td className="py-2 pr-3 whitespace-nowrap">
                      <span
                        className={
                          log.actionType === 'GIFT_SENT'
                            ? 'text-emerald-400 font-semibold'
                            : log.actionType === 'SKIP_UNOPENED'
                            ? 'text-amber-400'
                            : 'text-sky-400'
                        }
                      >
                        {log.actionType}
                      </span>
                    </td>
                    <td className="py-2 pr-3 text-slate-200 whitespace-nowrap">
                      {log.trainerName}
                    </td>
                    <td className="py-2 pr-3 text-right text-slate-300 tabular-nums whitespace-nowrap">
                      {log.delayMs > 0 ? `${log.delayMs}ms` : '—'}
                    </td>
                    <td className="py-2 text-slate-400 font-sans">
                      {log.details}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
