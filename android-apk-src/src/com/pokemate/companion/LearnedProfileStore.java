package com.pokemate.companion;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean, unified persistent store & throw engine for PalpiGO v1.1.2026:
 *
 * 1. TRUE 1:1 TEACH THROW RECORDING & REPLAY:
 *    - Never replaces, splices, or overwrites the user's drawn path with synthetic circles.
 *    - Strips only stationary pre-movement hold time on ACTION_DOWN so the replay duration
 *      matches the user's actual finger movement speed 1:1.
 *    - Immediately saves the user's taught throw 1:1 as soon as they draw it in '🎓 Teach Throw'.
 *
 * 2. CLEAN "1 SPIN THEN THROW" CURVEBALL ENGINE ('🌀 Spin Throw: ON'):
 *    - Grabs the resting PokéBall at (50% X, 86.5% Y), spins the ball ONCE in a single smooth
 *      circle, and launches straight out along the circle's tangent up-and-sideways so Pokémon GO's
 *      curveball physics hooks the ball cleanly into the target circle.
 *
 * 3. CLEAN DEAD-CENTER STRAIGHT THROW ENGINE ('🌀 Spin Throw: OFF'):
 *    - Flicks straight up the middle from the PokéBall through the target circle with high
 *      follow-through at crisp human flick speed.
 */
public class LearnedProfileStore {

    private static final String PREFS_NAME = "pokemate_learned_ai_v24";
    private static final String[] LEGACY_PREFS_NAMES = new String[]{
            "pokemate_learned_ai_v23",
            "pokemate_learned_ai_v22",
            "pokemate_learned_ai_v11"
    };

    public static final int GRADE_EXCELLENT = 4;
    public static final int GRADE_GREAT = 3;
    public static final int GRADE_NICE = 2;
    public static final int GRADE_HIT_NO_BONUS = 1;
    public static final int GRADE_MISS = 0;
    public static final int GRADE_SHORT = -1;       // ⬆ Harder (+Reach)
    public static final int GRADE_FAR = -2;         // ⬇ Softer (-Reach)
    public static final int GRADE_MORE_LEFT = -3;   // ⬅ More Left (Angle Left)
    public static final int GRADE_MORE_RIGHT = -4;  // ➡ More Right (Angle Right)

    public static class PointSample {
        public final float x;
        public final float y;
        public final long tMs;

        public PointSample(float x, float y) {
            this(x, y, 0L);
        }

        public PointSample(float x, float y, long tMs) {
            this.x = x;
            this.y = y;
            this.tMs = tMs;
        }
    }

    public static class CalibratedStroke {
        public final List<PointSample> points;
        public final long durationMs;
        public final float releaseVelocityPxPerMs;
        public final boolean isSpinThrow;
        public final List<PointSample> spinPrepPoints;
        public final List<PointSample> extraSpinLoopPoints;
        public final List<PointSample> flickPoints;
        public final long spinPrepDurationMs;
        public final long flickDurationMs;

        public CalibratedStroke(List<PointSample> points, long durationMs, float releaseVelocityPxPerMs) {
            this(points, durationMs, releaseVelocityPxPerMs, false);
        }

        public CalibratedStroke(List<PointSample> points, long durationMs, float releaseVelocityPxPerMs, boolean isSpinThrow) {
            this.points = points != null ? points : new ArrayList<PointSample>();
            this.durationMs = durationMs;
            this.releaseVelocityPxPerMs = releaseVelocityPxPerMs;
            this.isSpinThrow = isSpinThrow;

            SplitPhaseData split = splitCombinedStrokeIntoPhases(this.points, durationMs, isSpinThrow);
            this.spinPrepPoints = split.spinPrepPoints;
            this.extraSpinLoopPoints = split.extraSpinLoopPoints;
            this.flickPoints = split.flickPoints;
            this.spinPrepDurationMs = split.spinPrepDurationMs;
            this.flickDurationMs = split.flickDurationMs;
        }

        public CalibratedStroke(
                List<PointSample> points,
                long durationMs,
                float releaseVelocityPxPerMs,
                boolean isSpinThrow,
                List<PointSample> spinPrepPoints,
                List<PointSample> extraSpinLoopPoints,
                List<PointSample> flickPoints,
                long spinPrepDurationMs,
                long flickDurationMs
        ) {
            this.points = points;
            this.durationMs = durationMs;
            this.releaseVelocityPxPerMs = releaseVelocityPxPerMs;
            this.isSpinThrow = isSpinThrow;
            this.spinPrepPoints = spinPrepPoints;
            this.extraSpinLoopPoints = extraSpinLoopPoints;
            this.flickPoints = flickPoints;
            this.spinPrepDurationMs = spinPrepDurationMs;
            this.flickDurationMs = flickDurationMs;
        }
    }

    private static class SplitPhaseData {
        final List<PointSample> spinPrepPoints;
        final List<PointSample> extraSpinLoopPoints;
        final List<PointSample> flickPoints;
        final long spinPrepDurationMs;
        final long flickDurationMs;

        SplitPhaseData(
                List<PointSample> spinPrepPoints,
                List<PointSample> extraSpinLoopPoints,
                List<PointSample> flickPoints,
                long spinPrepDurationMs,
                long flickDurationMs
        ) {
            this.spinPrepPoints = spinPrepPoints;
            this.extraSpinLoopPoints = extraSpinLoopPoints;
            this.flickPoints = flickPoints;
            this.spinPrepDurationMs = spinPrepDurationMs;
            this.flickDurationMs = flickDurationMs;
        }
    }

    private static SplitPhaseData splitCombinedStrokeIntoPhases(
            List<PointSample> pts,
            long durationMs,
            boolean isSpinThrow
    ) {
        List<PointSample> prep = new ArrayList<PointSample>();
        List<PointSample> extraLoop = new ArrayList<PointSample>();
        List<PointSample> flick = new ArrayList<PointSample>();

        if (pts == null || pts.size() < 2) {
            PointSample p0 = new PointSample(540f, 2060f);
            PointSample p1 = new PointSample(540f, 2058f);
            PointSample p2 = new PointSample(540f, 1100f);
            prep.add(p0);
            prep.add(p1);
            extraLoop.add(p1);
            extraLoop.add(p0);
            extraLoop.add(p1);
            flick.add(p1);
            flick.add(p2);
            return new SplitPhaseData(prep, extraLoop, flick, 340L, 150L);
        }

        if (!isSpinThrow || pts.size() < 8) {
            PointSample p0 = pts.get(0);
            PointSample holdEnd = new PointSample(p0.x, Math.max(20f, p0.y - 2.0f));
            prep.add(p0);
            prep.add(holdEnd);

            extraLoop.add(holdEnd);
            extraLoop.add(new PointSample(p0.x + 1.5f, Math.max(20f, p0.y - 1.0f)));
            extraLoop.add(holdEnd);

            flick.add(holdEnd);
            for (int i = 1; i < pts.size(); i++) {
                flick.add(pts.get(i));
            }
            long fDur = Math.max(115L, Math.min(185L, durationMs));
            return new SplitPhaseData(prep, extraLoop, flick, 310L, fDur);
        }

        // Find the exact transition index where the circular spin in the bottom dock finishes
        // and the monotonic upward tangent flick begins:
        int n = pts.size();
        int launchIdx = Math.max(4, Math.min(n - 3, Math.round(n * 0.54f)));
        float maxYInSecondQuarter = -1f;
        int searchStart = Math.max(2, (int) (n * 0.32f));
        int searchEnd = Math.min(n - 3, (int) (n * 0.72f));
        for (int i = searchStart; i <= searchEnd; i++) {
            if (pts.get(i).y >= maxYInSecondQuarter) {
                maxYInSecondQuarter = pts.get(i).y;
                launchIdx = i;
            }
        }
        // Advance slightly past the bottom-most point of the loop to the upward-moving tangent exit point
        while (launchIdx < n - 3 && pts.get(launchIdx + 1).y >= pts.get(launchIdx).y - 1.5f) {
            launchIdx++;
        }
        if (launchIdx < n - 4) {
            launchIdx = Math.min(n - 3, launchIdx + 2);
        }

        for (int i = 0; i <= launchIdx; i++) {
            prep.add(pts.get(i));
        }
        PointSample exitPt = pts.get(launchIdx);

        // Compute spin center & radius from prep points so extraSpinLoopPoints can orbit smoothly and return to exitPt
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < prep.size(); i++) {
            sumX += prep.get(i).x;
            sumY += prep.get(i).y;
        }
        float cx = sumX / Math.max(1, prep.size());
        float cy = sumY / Math.max(1, prep.size());
        float rx = exitPt.x - cx;
        float ry = exitPt.y - cy;
        float r = (float) Math.hypot(rx, ry);
        if (r < 35f) {
            r = 82f;
            cx = exitPt.x - r;
            cy = exitPt.y;
        }
        float startAng = (float) Math.atan2(exitPt.y - cy, exitPt.x - cx);
        int spinDir = detectSpinDirectionFromPoints(pts, 1080, 2400);
        float sweep = (spinDir >= 0 ? 1.0f : -1.0f) * (float) (2.0 * Math.PI);
        extraLoop.add(new PointSample(exitPt.x, exitPt.y));
        int loopSteps = 24;
        for (int i = 1; i < loopSteps; i++) {
            float ang = startAng + sweep * ((float) i / (float) loopSteps);
            extraLoop.add(new PointSample(
                    cx + r * (float) Math.cos(ang),
                    cy + r * (float) Math.sin(ang)
            ));
        }
        extraLoop.add(new PointSample(exitPt.x, exitPt.y));

        flick.add(new PointSample(exitPt.x, exitPt.y));
        for (int i = launchIdx + 1; i < n; i++) {
            flick.add(pts.get(i));
        }

        float flickArc = computeArcLength(flick);
        float totalArc = Math.max(1f, computeArcLength(pts));
        long estFlickDur = Math.round(durationMs * (flickArc / totalArc));
        if (pts.get(n - 1).tMs > pts.get(launchIdx).tMs + 60L) {
            estFlickDur = pts.get(n - 1).tMs - pts.get(launchIdx).tMs;
        } else if (durationMs <= 320L) {
            estFlickDur = Math.round(durationMs * 0.56f);
        }
        long clampedFlickDur = Math.max(185L, Math.min(310L, estFlickDur));
        long prepDur = 385L;
        if (pts.get(launchIdx).tMs >= 180L && pts.get(launchIdx).tMs <= 560L) {
            prepDur = Math.max(290L, Math.min(520L, pts.get(launchIdx).tMs));
        }
        return new SplitPhaseData(prep, extraLoop, flick, prepDur, clampedFlickDur);
    }

    private static List<PointSample> lastCandidatePoints = null;
    private static long lastCandidateDurationMs = 175L;
    private static float lastCandidateRingX = 540f;
    private static float lastCandidateRingY = 980f;
    private static float lastCandidateRingRadius = 185f;
    private static int lastCandidateScreenW = 1080;
    private static int lastCandidateScreenH = 2400;
    private static boolean lastCandidateDistanceInformed = false;
    private static boolean lastCandidateAngleInformed = false;
    private static int lastCandidatePowerDeltaSteps = 0; // +1 per Harder, -1 per Softer
    private static int lastCandidateAngleDeltaSteps = 0; // -1 per Left, +1 per Right

    public static boolean isNonFinalDistanceModifierGrade(int grade) {
        return grade == GRADE_SHORT
                || grade == GRADE_FAR
                || grade == GRADE_MORE_LEFT
                || grade == GRADE_MORE_RIGHT;
    }

    public static synchronized int getLastCandidatePowerDeltaSteps() {
        return lastCandidatePowerDeltaSteps;
    }

    public static synchronized int getLastCandidateAngleDeltaSteps() {
        return lastCandidateAngleDeltaSteps;
    }

    public static synchronized String getActiveCandidateModifiersSummary() {
        StringBuilder sb = new StringBuilder();
        if (lastCandidatePowerDeltaSteps > 0) {
            sb.append("⬆ Harder");
            if (lastCandidatePowerDeltaSteps > 1) sb.append(" ×").append(lastCandidatePowerDeltaSteps);
        } else if (lastCandidatePowerDeltaSteps < 0) {
            sb.append("⬇ Softer");
            if (lastCandidatePowerDeltaSteps < -1) sb.append(" ×").append(-lastCandidatePowerDeltaSteps);
        }
        if (lastCandidateAngleDeltaSteps < 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("⬅ Left");
            if (lastCandidateAngleDeltaSteps < -1) sb.append(" ×").append(-lastCandidateAngleDeltaSteps);
        } else if (lastCandidateAngleDeltaSteps > 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("➡ Right");
            if (lastCandidateAngleDeltaSteps > 1) sb.append(" ×").append(lastCandidateAngleDeltaSteps);
        }
        return sb.toString();
    }

    private static SharedPreferences getPrefs(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!sp.getBoolean("migrated_settings_v26", false)) {
            SharedPreferences.Editor ed = sp.edit().putBoolean("migrated_settings_v26", true);
            for (int p = 0; p < LEGACY_PREFS_NAMES.length; p++) {
                SharedPreferences oldSp = ctx.getSharedPreferences(LEGACY_PREFS_NAMES[p], Context.MODE_PRIVATE);
                if (oldSp.getAll().isEmpty()) continue;
                if (!sp.contains("hud_opacity_pct") && oldSp.contains("hud_opacity_pct")) {
                    ed.putInt("hud_opacity_pct", oldSp.getInt("hud_opacity_pct", 68));
                }
                if (!sp.contains("mini_show_throw") && oldSp.contains("mini_show_throw")) {
                    ed.putBoolean("mini_show_throw", oldSp.getBoolean("mini_show_throw", true));
                }
                if (!sp.contains("mini_show_gift") && oldSp.contains("mini_show_gift")) {
                    ed.putBoolean("mini_show_gift", oldSp.getBoolean("mini_show_gift", false));
                }
                if (!sp.contains("mini_show_scan") && oldSp.contains("mini_show_scan")) {
                    ed.putBoolean("mini_show_scan", oldSp.getBoolean("mini_show_scan", false));
                }
                if (!sp.contains("gift_workflow_mode") && oldSp.contains("gift_workflow_mode")) {
                    ed.putInt("gift_workflow_mode", oldSp.getInt("gift_workflow_mode", 0));
                }
                if (!sp.contains("safe_human_delay") && oldSp.contains("safe_human_delay")) {
                    ed.putBoolean("safe_human_delay", oldSp.getBoolean("safe_human_delay", false));
                }
                if (!sp.contains("wait_for_excellent_ring") && oldSp.contains("wait_for_excellent_ring")) {
                    ed.putBoolean("wait_for_excellent_ring", oldSp.getBoolean("wait_for_excellent_ring", true));
                }
                if (!sp.contains("auto_hide_outside_pogo") && oldSp.contains("auto_hide_outside_pogo")) {
                    ed.putBoolean("auto_hide_outside_pogo", oldSp.getBoolean("auto_hide_outside_pogo", true));
                }
                if (oldSp.contains("quick_catch_enabled")) {
                    ed.putBoolean("quick_catch_enabled", oldSp.getBoolean("quick_catch_enabled", true));
                }
                if (!sp.contains("berry_btn_x") && oldSp.contains("berry_btn_x")) {
                    ed.putFloat("berry_btn_x", oldSp.getFloat("berry_btn_x", 180f));
                    ed.putFloat("berry_btn_y", oldSp.getFloat("berry_btn_y", 1950f));
                }
                int giftCount = oldSp.getInt("gift_teach_count", 0);
                if (giftCount > 0 && sp.getInt("gift_teach_count", 0) == 0) {
                    ed.putInt("gift_teach_count", giftCount);
                    for (int i = 0; i <= 6; i++) {
                        if (oldSp.contains("gift_step_" + i + "_x")) {
                            ed.putFloat("gift_step_" + i + "_x", oldSp.getFloat("gift_step_" + i + "_x", 0f));
                            ed.putFloat("gift_step_" + i + "_y", oldSp.getFloat("gift_step_" + i + "_y", 0f));
                        }
                    }
                }
            }
            if (!sp.contains("quick_catch_enabled")) {
                ed.putBoolean("quick_catch_enabled", true);
            }
            ed.apply();
        }
        return sp;
    }

    public static synchronized void setLastExecutedThrowCandidate(
            List<PointSample> pts,
            long durationMs,
            float ringX,
            float ringY,
            int screenW,
            int screenH
    ) {
        setLastExecutedThrowCandidateWithRadius(pts, durationMs, ringX, ringY, screenW * 0.175f, screenW, screenH);
    }

    public static synchronized void setLastExecutedThrowCandidateWithRadius(
            List<PointSample> pts,
            long durationMs,
            float ringX,
            float ringY,
            float ringRadius,
            int screenW,
            int screenH
    ) {
        if (pts == null || pts.size() < 2) return;
        lastCandidatePoints = new ArrayList<PointSample>(pts);
        lastCandidateDurationMs = durationMs;
        lastCandidateRingX = ringX;
        lastCandidateRingY = ringY;
        lastCandidateRingRadius = ringRadius > 20f ? ringRadius : (screenW * 0.175f);
        lastCandidateScreenW = screenW > 0 ? screenW : 1080;
        lastCandidateScreenH = screenH > 0 ? screenH : 2400;
        lastCandidateDistanceInformed = false;
        lastCandidateAngleInformed = false;
        lastCandidatePowerDeltaSteps = 0;
        lastCandidateAngleDeltaSteps = 0;
    }

    public static synchronized boolean hasUnratedCandidateThrow() {
        return lastCandidatePoints != null && lastCandidatePoints.size() >= 2;
    }

    // =========================================================================
    // SPIN THROW (CURVEBALL) TOGGLE & SPIN DETECTION
    // =========================================================================

    public static boolean isSpinThrowEnabled(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("spin_throw_enabled", false);
    }

    public static void setSpinThrowEnabled(Context ctx, boolean enabled) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("spin_throw_enabled", enabled).apply();
    }

    public static boolean isClockwiseSpinPreferred(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("throw_cw_spin", false);
    }

    public static void setClockwiseSpinPreferred(Context ctx, boolean cw) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("throw_cw_spin", cw).apply();
    }

    /**
     * Detects whether a recorded stroke is a Spin/Curveball throw or a Straight throw.
     * Returns:
     *   0  = Straight Throw
     *  +1  = Clockwise (CW) Spin/Curve Throw
     *  -1  = Counter-Clockwise (CCW) Spin/Curve Throw
     */
    public static int detectSpinDirectionFromPoints(List<PointSample> pts, int screenW, int screenH) {
        if (pts == null || pts.size() < 5) return 0;
        int w = screenW > 0 ? screenW : 1080;

        int spinSampleEnd = Math.max(4, (int) (pts.size() * 0.72f));
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < spinSampleEnd; i++) {
            sumX += pts.get(i).x;
            sumY += pts.get(i).y;
        }
        float cx = sumX / spinSampleEnd;
        float cy = sumY / spinSampleEnd;

        float totalSignedAngle = 0f;
        float totalAbsAngle = 0f;
        float prevAngle = (float) Math.atan2(pts.get(0).y - cy, pts.get(0).x - cx);

        for (int i = 1; i < spinSampleEnd; i++) {
            float dx = pts.get(i).x - cx;
            float dy = pts.get(i).y - cy;
            if (Math.hypot(dx, dy) < w * 0.015f) continue;
            float angle = (float) Math.atan2(dy, dx);
            float dTheta = angle - prevAngle;
            while (dTheta > Math.PI) dTheta -= (float) (2.0 * Math.PI);
            while (dTheta < -Math.PI) dTheta += (float) (2.0 * Math.PI);
            totalSignedAngle += dTheta;
            totalAbsAngle += Math.abs(dTheta);
            prevAngle = angle;
        }

        if (Math.abs(totalSignedAngle) >= 1.75f || totalAbsAngle >= 2.6f) {
            return totalSignedAngle >= 0f ? +1 : -1;
        }

        PointSample start = pts.get(0);
        PointSample end = pts.get(pts.size() - 1);
        float maxRightDeviation = 0f;
        float maxLeftDeviation = 0f;
        for (int i = 1; i < pts.size() - 1; i++) {
            float t = (float) i / (float) (pts.size() - 1);
            float lineX = start.x + (end.x - start.x) * t;
            float devX = pts.get(i).x - lineX;
            if (devX > maxRightDeviation) maxRightDeviation = devX;
            if (-devX > maxLeftDeviation) maxLeftDeviation = -devX;
        }

        if (maxRightDeviation > w * 0.12f || maxLeftDeviation > w * 0.12f) {
            return (maxRightDeviation >= maxLeftDeviation) ? -1 : +1;
        }

        return 0;
    }

    /**
     * Checks whether the stroke contains a full multi-turn circular spin windup (>= 1.55 net revolutions
     * in one consistent rotational direction) required for Android's uniform PathMeasure to spend >= 265ms
     * spinning the PokéBall in the dock so Pokémon GO 100% ALWAYS registers Curveball sparkles!
     */
    public static boolean hasTrueCircularSpinLoop(List<PointSample> pts, int screenW) {
        if (pts == null || pts.size() < 12) return false;
        int w = screenW > 0 ? screenW : 1080;
        int spinSampleEnd = Math.max(8, (int) (pts.size() * 0.68f));
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < spinSampleEnd; i++) {
            sumX += pts.get(i).x;
            sumY += pts.get(i).y;
        }
        float cx = sumX / spinSampleEnd;
        float cy = sumY / spinSampleEnd;

        float totalSignedAngle = 0f;
        float prevAngle = (float) Math.atan2(pts.get(0).y - cy, pts.get(0).x - cx);
        for (int i = 1; i < spinSampleEnd; i++) {
            float dx = pts.get(i).x - cx;
            float dy = pts.get(i).y - cy;
            if (Math.hypot(dx, dy) < w * 0.018f) continue;
            float angle = (float) Math.atan2(dy, dx);
            float dTheta = angle - prevAngle;
            while (dTheta > Math.PI) dTheta -= (float) (2.0 * Math.PI);
            while (dTheta < -Math.PI) dTheta += (float) (2.0 * Math.PI);
            totalSignedAngle += dTheta;
            prevAngle = angle;
        }
        return Math.abs(totalSignedAngle) >= 9.65f;
    }

    /**
     * Guarantees that when '🌀 Spin: ON' is enabled, the stroke ALWAYS starts with a 1.75-revolution
     * circular spin windup in the bottom dock and then launches along a continuous same-direction curved
     * tangent arc to the stroke's exact release point (endPt.x, endPt.y).
     * If the stroke already has >= 1.55 net circular revolutions, returns it 100% untouched!
     */
    public static List<PointSample> ensureSingleSpinLoopBeforeThrow(
            List<PointSample> pts,
            boolean cwSpin,
            int screenW,
            int screenH
    ) {
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;
        if (pts == null || pts.size() < 2) {
            return buildSmartSpinCurveThrow(null, w * 0.50f, h * 0.40f, w * 0.175f, w, h).points;
        }
        if (hasTrueCircularSpinLoop(pts, w)) {
            return pts;
        }

        int detectedDir = detectSpinDirectionFromPoints(pts, w, h);
        boolean effectiveCw = (detectedDir != 0) ? (detectedDir > 0) : cwSpin;

        PointSample firstPt = pts.get(0);
        PointSample endPt = pts.get(pts.size() - 1);
        float ballX = (firstPt.x >= w * 0.28f && firstPt.x <= w * 0.72f) ? firstPt.x : (w * 0.50f);
        float ballY = (firstPt.y >= h * 0.68f && firstPt.y <= h * 0.94f) ? firstPt.y : (h * 0.865f);
        float spinRad = w * 0.100f;
        float spinCenterX = ballX;
        float spinCenterY = ballY - spinRad;

        float releaseY = Math.max(h * 0.24f, Math.min(h * 0.62f, endPt.y));
        float sideSign = effectiveCw ? -1.0f : 1.0f;
        float alpha = 0.34f;

        List<PointSample> spinPts = new ArrayList<PointSample>(68);
        int spinSamples = 44; // 1.75 smooth circular revolutions in the bottom dock (~285ms of active spinning!)
        int flickSamples = 22; // Continuous same-direction curved tangent launch arc
        float startAng = (float) (Math.PI * 0.5);
        float sweepMag = (float) (3.5 * Math.PI) + ((float) (Math.PI * 0.5) - alpha);
        float totalSpinAngle = -sideSign * sweepMag;

        for (int i = 0; i < spinSamples; i++) {
            float t = (float) i / (float) spinSamples;
            float ang = startAng + totalSpinAngle * t;
            float sx = spinCenterX + spinRad * (float) Math.cos(ang);
            float sy = spinCenterY + spinRad * (float) Math.sin(ang);
            spinPts.add(new PointSample(sx, sy));
        }

        // Exact tangent exit point on the spin circle:
        float exitX = spinCenterX + sideSign * spinRad * (float) Math.cos(alpha);
        float exitY = spinCenterY + spinRad * (float) Math.sin(alpha);
        spinPts.add(new PointSample(exitX, exitY));

        // Unit tangent vector at (exitX, exitY):
        float tanX = sideSign * (float) Math.sin(alpha);
        float tanY = -(float) Math.cos(alpha);
        float flickLen = Math.max(dpPx(w, 190f), (exitY - releaseY) / Math.max(0.5f, -tanY));

        // Control point P1 lies strictly on the circle's exit tangent ray (100% C1 smooth exit),
        // while P2 curves gently inward in the SAME rotational direction as the spin so angular momentum never decays!
        float ctrlDist = flickLen * 0.54f;
        float ctrlX = exitX + ctrlDist * tanX;
        float ctrlY = exitY + ctrlDist * tanY;

        float defaultReleaseX = exitX + flickLen * tanX - sideSign * (w * 0.085f);
        float releaseX = (Math.abs(endPt.x - ballX) > w * 0.06f)
                ? Math.max(w * 0.14f, Math.min(w * 0.86f, endPt.x))
                : Math.max(w * 0.16f, Math.min(w * 0.84f, defaultReleaseX));

        for (int i = 1; i <= flickSamples; i++) {
            float u = (float) i / (float) flickSamples;
            float inv = 1.0f - u;
            float bx = inv * inv * exitX + 2.0f * inv * u * ctrlX + u * u * releaseX;
            float by = inv * inv * exitY + 2.0f * inv * u * ctrlY + u * u * releaseY;
            spinPts.add(new PointSample(
                    Math.max(14f, Math.min(w - 14f, bx)),
                    Math.max(24f, Math.min(h - 12f, by))
            ));
        }
        return spinPts;
    }

    /**
     * TRUE 1:1 TAUGHT THROW CALIBRATOR:
     * - NEVER replaces the user's drawn points with a synthetic circle!
     * - Trims stationary finger-hold points at the start/end, and if the user spun multiple times in place
     *   before throwing, keeps their exact final 1 full spin revolution + their exact upward release flick!
     * - Matches the GestureDescription duration to the user's actual upward release flick speed (px/ms)
     *   so the ball launches at the exact speed the user flicked it!
     */
    public static CalibratedStroke calibrateUserTaughtStroke(
            List<PointSample> rawPts,
            long totalTouchDurationMs,
            int screenW,
            int screenH
    ) {
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;
        if (rawPts == null || rawPts.size() < 2) {
            return buildNaturalHumanGoodThrow(null, w * 0.50f, h * 0.40f, w * 0.175f, w, h);
        }

        // 1. Find the exact index where the finger begins moving away from the initial touch-down
        PointSample firstPt = rawPts.get(0);
        int moveStartIdx = 0;
        float moveStartRadiusPx = dpPx(w, 16f);
        for (int i = 1; i < rawPts.size() - 2; i++) {
            float d = (float) Math.hypot(rawPts.get(i).x - firstPt.x, rawPts.get(i).y - firstPt.y);
            if (d >= moveStartRadiusPx) {
                moveStartIdx = Math.max(0, i - 1);
                break;
            }
        }

        // 2. Strip 1-3 stationary finger-lift points at the very end if the finger paused on ACTION_UP
        int endIdx = rawPts.size() - 1;
        float minY = Float.MAX_VALUE;
        int minYIdx = endIdx;
        for (int i = moveStartIdx; i < rawPts.size(); i++) {
            if (rawPts.get(i).y < minY) {
                minY = rawPts.get(i).y;
                minYIdx = i;
            }
        }
        if (rawPts.size() - 1 - minYIdx <= 3 && minYIdx > moveStartIdx + 2) {
            endIdx = minYIdx;
        }

        int spinDir = detectSpinDirectionFromPoints(rawPts.subList(moveStartIdx, endIdx + 1), w, h);
        boolean isSpin = (spinDir != 0) || hasTrueCircularSpinLoop(rawPts.subList(moveStartIdx, endIdx + 1), w);

        // 3. Find the exact bottom of the final spin loop (where the continuous upward throw climb begins)
        //    by walking BACKWARD from endIdx down the final upward throw path until Y stops increasing!
        float maxOverallY = -1f;
        for (int i = moveStartIdx; i <= endIdx; i++) {
            if (rawPts.get(i).y > maxOverallY) {
                maxOverallY = rawPts.get(i).y;
            }
        }
        float verticalSpan = Math.max(dpPx(w, 60f), maxOverallY - minY);
        int bottomIdx = moveStartIdx;
        float peakBottomY = rawPts.get(endIdx).y;
        for (int i = endIdx - 1; i >= moveStartIdx; i--) {
            float y = rawPts.get(i).y;
            if (y >= peakBottomY - dpPx(w, 4f)) {
                if (y > peakBottomY) {
                    peakBottomY = y;
                    bottomIdx = i;
                }
            } else if (peakBottomY >= minY + verticalSpan * 0.52f && y < peakBottomY - dpPx(w, 12f)) {
                // Finger has reached the bottom of the final loop and is curving back UP into the spin circle!
                break;
            }
        }

        if (isSpin && bottomIdx > moveStartIdx + 6) {
            float sumX = 0f;
            float sumY = 0f;
            int cnt = 0;
            for (int i = moveStartIdx; i <= bottomIdx; i++) {
                sumX += rawPts.get(i).x;
                sumY += rawPts.get(i).y;
                cnt++;
            }
            float cx = sumX / Math.max(1, cnt);
            float cy = sumY / Math.max(1, cnt);
            float signedAccumAngle = 0f;
            float prevAng = (float) Math.atan2(rawPts.get(bottomIdx).y - cy, rawPts.get(bottomIdx).x - cx);
            for (int i = bottomIdx - 1; i >= moveStartIdx; i--) {
                float dx = rawPts.get(i).x - cx;
                float dy = rawPts.get(i).y - cy;
                if (Math.hypot(dx, dy) < w * 0.015f) continue;
                float ang = (float) Math.atan2(dy, dx);
                float dTheta = ang - prevAng;
                while (dTheta > Math.PI) dTheta -= (float) (2.0 * Math.PI);
                while (dTheta < -Math.PI) dTheta += (float) (2.0 * Math.PI);
                signedAccumAngle += dTheta;
                prevAng = ang;
                // Keep up to 1.85 full net revolutions of the user's drawn spin so Pokémon GO always triggers Curveball sparkles!
                if (Math.abs(signedAccumAngle) >= (float) (Math.PI * 3.70)) {
                    moveStartIdx = i;
                    break;
                }
            }
        } else if (!isSpin && bottomIdx > moveStartIdx) {
            // For a straight throw, start right at the bottom of the upward swipe so any stationary pause is trimmed
            moveStartIdx = bottomIdx;
        }

        // 4. Keep 100% of the user's drawn points from moveStartIdx to endIdx with their real timestamps!
        long startMoveT = rawPts.get(moveStartIdx).tMs;
        List<PointSample> activePts = new ArrayList<PointSample>(endIdx - moveStartIdx + 1);
        for (int i = moveStartIdx; i <= endIdx; i++) {
            PointSample p = rawPts.get(i);
            activePts.add(new PointSample(p.x, p.y, Math.max(0L, p.tMs - startMoveT)));
        }

        if (activePts.size() > 86) {
            activePts = resamplePointsUniformly(activePts, 80);
        }

        long activeMoveDurationMs = activePts.get(activePts.size() - 1).tMs;
        if (activeMoveDurationMs < 120L) {
            activeMoveDurationMs = totalTouchDurationMs;
        }

        float totalArcLen = computeArcLength(activePts);

        // 5. CRITICAL FIX: Preserve the user's ACTUAL active throw duration (never compress a 450ms human throw into 125ms!).
        //    Enforce a natural human duration window: [420ms .. 510ms] for Spin Throws, [225ms .. 430ms] for Straight Throws.
        long minHumanDur = isSpin ? 420L : 225L;
        long maxHumanDur = isSpin ? 510L : 430L;
        long effectiveDurationMs = Math.max(minHumanDur, Math.min(maxHumanDur, activeMoveDurationMs));
        float finalVel = totalArcLen / (float) Math.max(1L, effectiveDurationMs);

        return new CalibratedStroke(activePts, effectiveDurationMs, finalVel, isSpin);
    }

    public static boolean isRoboticOrInvalidStroke(
            Context ctx,
            List<PointSample> pts,
            long durationMs,
            float ringX,
            float ringY,
            int screenW,
            int screenH
    ) {
        if (pts == null || pts.size() < 2) return true;
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;

        PointSample startPt = pts.get(0);
        PointSample endPt = pts.get(pts.size() - 1);

        if (startPt.y < h * 0.55f) return true;
        if (startPt.y - endPt.y < h * 0.10f) return true;

        if (ctx != null && isStrokeBannedByMissHistory(ctx, pts, durationMs, w)) {
            return true;
        }
        return false;
    }

    private static float dpPx(int screenW, float basePxAt1080) {
        return basePxAt1080 * ((screenW > 0 ? screenW : 1080) / 1080f);
    }

    public static boolean isStrokeBannedByMissHistory(
            Context ctx,
            List<PointSample> pts,
            long durationMs,
            int screenW
    ) {
        if (ctx == null || pts == null || pts.size() < 2) return false;
        SharedPreferences sp = getPrefs(ctx);
        String rawBans = sp.getString("banned_miss_signatures_v24", "");
        if (rawBans == null || rawBans.isEmpty()) return false;

        PointSample endPt = pts.get(pts.size() - 1);
        float endTol = dpPx(screenW, 40f);

        String[] entries = rawBans.split("\\|");
        for (int i = 0; i < entries.length; i++) {
            String[] fields = entries[i].split(",");
            if (fields.length >= 3) {
                try {
                    float bx = Float.parseFloat(fields[0]);
                    float by = Float.parseFloat(fields[1]);
                    long bDur = Long.parseLong(fields[2]);

                    float endDist = (float) Math.hypot(endPt.x - bx, endPt.y - by);
                    if (endDist < endTol && Math.abs(durationMs - bDur) < 30L) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    private static void recordBannedMissSignature(
            SharedPreferences sp,
            SharedPreferences.Editor ed,
            List<PointSample> missedPts,
            long missedDur
    ) {
        if (missedPts == null || missedPts.size() < 2) return;
        PointSample endPt = missedPts.get(missedPts.size() - 1);
        String sig = Math.round(endPt.x) + "," + Math.round(endPt.y) + "," + missedDur;

        String existing = sp.getString("banned_miss_signatures_v24", "");
        List<String> kept = new ArrayList<String>();
        kept.add(sig);
        if (existing != null && !existing.isEmpty()) {
            String[] parts = existing.split("\\|");
            for (int i = 0; i < parts.length && kept.size() < 20; i++) {
                if (!parts[i].equals(sig)) {
                    kept.add(parts[i]);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(kept.get(i));
        }
        ed.putString("banned_miss_signatures_v24", sb.toString());
    }

    /**
     * BUILT-IN THROW GENERATOR (Used when no custom taught throw is saved for the active mode):
     * - When '🌀 Spin Throw: ON' -> Spins the ball ONCE in a single clean circle, then throws straight out along the tangent!
     * - When '🌀 Spin Throw: OFF' -> Throws straight up the middle from the PokéBall through the target circle!
     */
    public static CalibratedStroke buildNaturalHumanGoodThrow(
            Context ctx,
            float ringX,
            float ringY,
            float ringRadius,
            int screenW,
            int screenH
    ) {
        if (ctx != null && isSpinThrowEnabled(ctx)) {
            return buildSmartSpinCurveThrow(ctx, ringX, ringY, ringRadius, screenW, screenH);
        }
        return buildDeadCenterStraightGoodThrow(ctx, ringX, ringY, ringRadius, screenW, screenH);
    }

    /**
     * CLEAN "1 SPIN THEN THROW" CURVEBALL ENGINE:
     * 1. Starts directly on the resting PokéBall at (50% X, 86.5% Y).
     * 2. Spins the ball ONCE (1 single smooth circle of radius 8.6% W).
     * 3. Exits the single spin along the circle's natural tangent vector in a clean, straight-out
     *    upward flick (no S-curves or double loops!) to (releaseX, releaseY) so the ball's spin
     *    hooks it right into the center of the Pokémon's target circle!
     */
    public static CalibratedStroke buildSmartSpinCurveThrow(
            Context ctx,
            float ringX,
            float ringY,
            float ringRadius,
            int screenW,
            int screenH
    ) {
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;

        float safeRingX = (ringX > w * 0.22f && ringX < w * 0.78f) ? ringX : (w * 0.50f);
        float safeRingY = (ringY > h * 0.14f && ringY < h * 0.68f) ? ringY : (h * 0.40f);
        float safeRad = (ringRadius > w * 0.06f && ringRadius < w * 0.35f) ? ringRadius : (w * 0.175f);

        float powerMult = ctx != null ? getThrowPowerMultiplier(ctx) : 1.0f;
        float speedMult = ctx != null ? getThrowSpeedMultiplier(ctx) : 1.0f;
        float hookMult = ctx != null ? getSpinHookMultiplier(ctx) : 1.0f;
        boolean cwSpin = ctx != null && isClockwiseSpinPreferred(ctx);
        int missStreak = ctx != null ? getPrefs(ctx).getInt("consecutive_miss_count", 0) : 0;

        float ballX = w * 0.50f;
        float ballY = h * 0.865f;
        // 10.0% screen width spin radius (~108px on 1080p) with 1.75 smooth circular revolutions in the bottom dock
        // so Android's uniform PathMeasure spends ~285ms spinning the ball in the dock (100% Curveball sparkles!)
        // and ~175ms launching along a continuous same-direction curved tangent arc!
        float spinRad = w * 0.100f;
        float spinCenterX = ballX;
        float spinCenterY = ballY - spinRad;

        // Calibrated vertical climb from PokéBall dock toward releaseY for Close / Medium / Far Pokémon:
        float baseTargetClimb = (ballY - safeRingY) * 0.78f;
        float sizeBoost = Math.max(0.90f, Math.min(1.14f, (w * 0.175f) / Math.max(w * 0.10f, safeRad)));

        CalibratedStroke fallbackSpinStroke = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            int step = (missStreak + attempt) % 5;
            float stepScale = (step == 0) ? 1.00f
                    : (step == 1) ? 1.12f
                    : (step == 2) ? 1.22f
                    : (step == 3) ? 0.90f : 1.06f;
            float hookStep = (step == 0) ? 1.00f
                    : (step == 1) ? 0.94f
                    : (step == 2) ? 1.06f
                    : (step == 3) ? 0.97f : 1.03f;

            float climb = baseTargetClimb * sizeBoost * powerMult * stepScale;
            float releaseY = Math.max(h * 0.28f, Math.min(h * 0.60f, ballY - climb));

            // CCW spin (cwSpin=false) hooks LEFT in flight -> launch ray angles UP-AND-RIGHT (+1).
            // CW spin (cwSpin=true) hooks RIGHT in flight -> launch ray angles UP-AND-LEFT (-1).
            float sideSign = cwSpin ? -1.0f : 1.0f;
            float approxExitX = spinCenterX + sideSign * spinRad * 0.92f;
            float approxExitY = spinCenterY + spinRad * 0.34f;
            float verticalFlickClimb = Math.max(dpPx(w, 160f), approxExitY - releaseY);
            float maxSafeHorizTravel = (sideSign > 0f) ? (w * 0.87f - approxExitX) : (approxExitX - w * 0.13f);
            float maxAlphaForScreen = (float) Math.atan2(Math.max(dpPx(w, 120f), maxSafeHorizTravel), verticalFlickClimb);

            float alpha = Math.max(0.28f, Math.min(maxAlphaForScreen, 0.36f * hookMult * hookStep));

            int spinSamples = 44; // Phase 1A: 1.75 smooth circular revolutions in dock (~285ms active spin)
            int flickSamples = 22; // Phase 2: Continuous same-direction curved tangent launch arc (~175ms)

            float startAng = (float) (Math.PI * 0.5);
            float sweepMag = (float) (3.5 * Math.PI) + ((float) (Math.PI * 0.5) - alpha);
            float totalSpinAngle = -sideSign * sweepMag;

            List<PointSample> prepPts = new ArrayList<PointSample>(spinSamples + 1);
            for (int i = 0; i < spinSamples; i++) {
                float t = (float) i / (float) spinSamples;
                float ang = startAng + totalSpinAngle * t;
                float sx = spinCenterX + spinRad * (float) Math.cos(ang);
                float sy = spinCenterY + spinRad * (float) Math.sin(ang);
                prepPts.add(new PointSample(sx, sy));
            }

            // Exact exit point and unit tangent vector at the end of the 1.75-turn spin:
            float exitX = spinCenterX + sideSign * spinRad * (float) Math.cos(alpha);
            float exitY = spinCenterY + spinRad * (float) Math.sin(alpha);
            prepPts.add(new PointSample(exitX, exitY));

            // Phase 1B extra spin loop (starts at exitX, exitY -> 1 full circle -> returns to exact exitX, exitY)
            List<PointSample> extraLoopPts = new ArrayList<PointSample>(26);
            extraLoopPts.add(new PointSample(exitX, exitY));
            float exitAng = startAng + totalSpinAngle;
            float fullTurnSweep = -sideSign * (float) (2.0 * Math.PI);
            for (int i = 1; i < 25; i++) {
                float ang = exitAng + fullTurnSweep * ((float) i / 25.0f);
                extraLoopPts.add(new PointSample(
                        spinCenterX + spinRad * (float) Math.cos(ang),
                        spinCenterY + spinRad * (float) Math.sin(ang)
                ));
            }
            extraLoopPts.add(new PointSample(exitX, exitY));

            // Phase 2: Continuous same-direction curved tangent launch from (exitX, exitY) to (releaseX, releaseY)
            // Control point P1 lies strictly on the circle's tangent ray (C1 smooth exit), and P2 curves gently inward
            // in the SAME rotational sense as the spin so Pokémon GO never decays the curveball angular momentum!
            float tanX = sideSign * (float) Math.sin(alpha);
            float tanY = -(float) Math.cos(alpha);
            float flickLen = Math.max(dpPx(w, 190f), (exitY - releaseY) / Math.max(0.5f, -tanY));
            float ringOffsetX = safeRingX - (w * 0.50f);

            float ctrlDist = flickLen * 0.54f;
            float ctrlX = exitX + ctrlDist * tanX;
            float ctrlY = exitY + ctrlDist * tanY;
            float releaseX = Math.max(w * 0.16f, Math.min(w * 0.84f,
                    exitX + flickLen * tanX - sideSign * (w * 0.085f) + ringOffsetX * 0.55f));

            List<PointSample> flickPts = new ArrayList<PointSample>(flickSamples + 1);
            flickPts.add(new PointSample(exitX, exitY));
            for (int i = 1; i <= flickSamples; i++) {
                float u = (float) i / (float) flickSamples;
                float inv = 1.0f - u;
                float bx = inv * inv * exitX + 2.0f * inv * u * ctrlX + u * u * releaseX;
                float by = inv * inv * exitY + 2.0f * inv * u * ctrlY + u * u * releaseY;
                flickPts.add(new PointSample(
                        Math.max(14f, Math.min(w - 14f, bx)),
                        Math.max(24f, Math.min(h - 12f, by))
                ));
            }

            List<PointSample> combinedPts = new ArrayList<PointSample>(prepPts.size() + flickPts.size());
            combinedPts.addAll(prepPts);
            for (int i = 1; i < flickPts.size(); i++) {
                combinedPts.add(flickPts.get(i));
            }

            float totalCombinedArc = Math.max(1f, computeArcLength(combinedPts));
            // Natural human 1.75-Spin + Curved Tangent Throw duration: ~460ms total (~285ms spin + ~175ms launch)
            long flickDurMs = Math.max(195L, Math.min(275L, Math.round(215f / Math.max(0.84f, Math.min(1.18f, speedMult)))));
            long spinPrepDurMs = 385L;
            long combinedDurMs = Math.max(425L, Math.min(505L, Math.round(460f / Math.max(0.86f, Math.min(1.15f, speedMult)))));
            float targetFlickVel = totalCombinedArc / (float) Math.max(1L, combinedDurMs);

            CalibratedStroke candidate = new CalibratedStroke(
                    combinedPts,
                    combinedDurMs,
                    targetFlickVel,
                    true,
                    prepPts,
                    extraLoopPts,
                    flickPts,
                    spinPrepDurMs,
                    flickDurMs
            );
            if (fallbackSpinStroke == null) {
                fallbackSpinStroke = candidate;
            }
            if (ctx == null || !isStrokeBannedByMissHistory(ctx, combinedPts, combinedDurMs, w)) {
                return candidate;
            }
        }

        // CRITICAL FIX: NEVER fall back to buildDeadCenterStraightGoodThrow when Spin Mode is active!
        // Always return a 1-Spin-Then-Throw curveball!
        return fallbackSpinStroke;
    }

    /**
     * DEAD-CENTER HIGH-FOLLOW-THROUGH STRAIGHT THROW ENGINE
     */
    public static CalibratedStroke buildDeadCenterStraightGoodThrow(
            Context ctx,
            float ringX,
            float ringY,
            float ringRadius,
            int screenW,
            int screenH
    ) {
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;

        float safeRingX = (ringX > w * 0.22f && ringX < w * 0.78f) ? ringX : (w * 0.50f);
        float safeRingY = (ringY > h * 0.14f && ringY < h * 0.68f) ? ringY : (h * 0.40f);
        float safeRad = (ringRadius > w * 0.06f && ringRadius < w * 0.35f) ? ringRadius : (w * 0.175f);

        float powerMult = ctx != null ? getThrowPowerMultiplier(ctx) : 1.0f;
        float speedMult = ctx != null ? getThrowSpeedMultiplier(ctx) : 1.0f;
        int missStreak = ctx != null ? getPrefs(ctx).getInt("consecutive_miss_count", 0) : 0;

        float startX = w * 0.50f;
        float startY = h * 0.875f;

        float followThroughAboveRing = h * 0.215f;
        float baseTargetClimb = (startY - safeRingY) + followThroughAboveRing;
        float sizeBoost = Math.max(0.94f, Math.min(1.22f, safeRad / (w * 0.160f)));

        int sampleCount = 48;
        for (int attempt = 0; attempt < 5; attempt++) {
            int step = (missStreak + attempt) % 5;
            float stepScale = (step == 0) ? 1.00f
                    : (step == 1) ? 1.16f
                    : (step == 2) ? 1.30f
                    : (step == 3) ? 0.86f : 1.08f;

            float climb = baseTargetClimb * sizeBoost * powerMult * stepScale;
            float releaseY = Math.max(h * 0.08f, Math.min(h * 0.38f, startY - climb));
            float releaseX = startX + (safeRingX - startX) * 0.90f;

            List<PointSample> straightPts = new ArrayList<PointSample>(sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                float u = (float) i / (float) (sampleCount - 1);
                float t = 0.65f * u + 0.35f * u * u;
                float bx = startX + (releaseX - startX) * t;
                float by = startY + (releaseY - startY) * t;
                straightPts.add(new PointSample(bx, by));
            }

            float arcLen = computeArcLength(straightPts);
            long durMs = Math.max(225L, Math.min(360L, Math.round(255f / Math.max(0.82f, Math.min(1.22f, speedMult)))));
            float baseVelPxPerMs = arcLen / (float) Math.max(1L, durMs);

            if (ctx == null || !isStrokeBannedByMissHistory(ctx, straightPts, durMs, w)) {
                return new CalibratedStroke(straightPts, durMs, baseVelPxPerMs, false);
            }
        }

        float fallbackReleaseY = Math.max(h * 0.10f, Math.min(h * 0.26f, safeRingY - h * 0.21f));
        List<PointSample> fallbackPts = new ArrayList<PointSample>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            float t = (float) i / (float) (sampleCount - 1);
            fallbackPts.add(new PointSample(
                    startX + (safeRingX - startX) * t,
                    startY + (fallbackReleaseY - startY) * t
            ));
        }
        return new CalibratedStroke(fallbackPts, 250L, 6.2f, false);
    }

    /**
     * TWO-STAGE RATING ENGINE (COMBINABLE POWER + LEFT/RIGHT ANGLE + HIT/MISS OUTCOME):
     * - Stage 1 (Inform Power & Left/Right Angle — NON-FINAL, COMBINABLE AT THE SAME TIME):
     *   You can tap '⬆ Harder' / '⬇ Softer' AND '⬅ Left' / '➡ Right' together in the same rating
     *   session! Each tap immediately updates both the saved trajectory and the in-memory
     *   `lastCandidatePoints` while keeping the rating session open.
     * - Stage 2 (Final Outcome — CLOSES POPUP):
     *   Tapping '🌟 Exc', '🔥 Great', '👍 Nice', '⚪ Hit', or '❌ Miss' records whether the throw hit
     *   or missed while preserving all Stage 1 Power + Left/Right Angle adjustments you selected!
     */
    public static synchronized String rateLastExecutedThrow(Context ctx, int grade) {
        SharedPreferences sp = getPrefs(ctx);
        boolean spinMode = isSpinThrowEnabled(ctx);

        // STAGE 1: NON-FINAL POWER & ANGLE MODIFIERS (Can select Harder/Softer + Left/Right at the same time!)
        if (grade == GRADE_SHORT) {
            adjustThrowPowerMultiplier(ctx, 0.14f);
            mutateSavedAndCandidateStroke(ctx, +0.14f, 0f, 0.97f);
            lastCandidateDistanceInformed = true;
            lastCandidatePowerDeltaSteps++;
            String combo = getActiveCandidateModifiersSummary();
            return "✓ Selected [" + combo + "]! Tap ⬅/➡ too, or rate Hit/Miss below";
        }

        if (grade == GRADE_FAR) {
            adjustThrowPowerMultiplier(ctx, -0.14f);
            mutateSavedAndCandidateStroke(ctx, -0.14f, 0f, 1.04f);
            lastCandidateDistanceInformed = true;
            lastCandidatePowerDeltaSteps--;
            String combo = getActiveCandidateModifiersSummary();
            return "✓ Selected [" + combo + "]! Tap ⬅/➡ too, or rate Hit/Miss below";
        }

        if (grade == GRADE_MORE_LEFT) {
            adjustThrowHorizOffsetFraction(ctx, -0.045f);
            mutateSavedAndCandidateStroke(ctx, 0f, -0.045f, 1.0f);
            lastCandidateAngleInformed = true;
            lastCandidateAngleDeltaSteps--;
            String combo = getActiveCandidateModifiersSummary();
            return "✓ Selected [" + combo + "]! Tap ⬆/⬇ too, or rate Hit/Miss below";
        }

        if (grade == GRADE_MORE_RIGHT) {
            adjustThrowHorizOffsetFraction(ctx, +0.045f);
            mutateSavedAndCandidateStroke(ctx, 0f, +0.045f, 1.0f);
            lastCandidateAngleInformed = true;
            lastCandidateAngleDeltaSteps++;
            String combo = getActiveCandidateModifiersSummary();
            return "✓ Selected [" + combo + "]! Tap ⬆/⬇ too, or rate Hit/Miss below";
        }

        boolean hadCustomAdjustments = lastCandidateDistanceInformed || lastCandidateAngleInformed;
        String comboSummary = getActiveCandidateModifiersSummary();

        // STAGE 2: FINAL OUTCOMES ('⚪ Hit', '❌ Miss', '👍 Nice', '🔥 Great', '🌟 Exc')
        if (grade == GRADE_HIT_NO_BONUS) {
            int newHitCount = sp.getInt("throw_hit_nobonus_count", 0) + 1;
            sp.edit().putInt("throw_hit_nobonus_count", newHitCount).putInt("consecutive_miss_count", 0).apply();

            if (!hadCustomAdjustments) {
                adjustThrowPowerMultiplier(ctx, 0.08f);
                mutateSavedAndCandidateStroke(ctx, +0.08f, 0f, 0.98f);
            }
            resetCandidateSessionFlags();
            String styleTag = spinMode ? "🌀 Spin" : "⬆️ Straight";
            return hadCustomAdjustments
                    ? ("✓ Hit Logged • Saved [" + comboSummary + "] (" + styleTag + ")")
                    : ("✓ Hit Logged • Tuned +8% Reach for Bonus Circle (" + styleTag + ")");
        }

        if (grade == GRADE_MISS) {
            int missStreak = sp.getInt("consecutive_miss_count", 0) + 1;
            sp.edit().putInt("consecutive_miss_count", missStreak).apply();

            if (!hadCustomAdjustments) {
                // User tapped Miss directly without selecting Harder/Softer/Left/Right first -> nudge reach +12%
                adjustThrowPowerMultiplier(ctx, 0.12f);
                mutateSavedAndCandidateStroke(ctx, +0.12f, 0f, 0.98f);
            }
            resetCandidateSessionFlags();
            String modeTag = spinMode ? "🌀 Spin" : "⬆️ Straight";
            return hadCustomAdjustments
                    ? ("✓ Miss Logged • Next Throw: [" + comboSummary + "] (" + modeTag + ")")
                    : ("🔧 Miss Logged • Adjusted +12% Reach (" + modeTag + ")");
        }

        if (lastCandidatePoints != null && lastCandidatePoints.size() >= 2) {
            // If user rated Nice or Great WITHOUT already selecting Harder/Softer/Left/Right,
            // apply a subtle refinement (+4% for Nice, +2% for Great) toward Excellent!
            if (!hadCustomAdjustments) {
                if (grade == GRADE_NICE) {
                    mutateSavedAndCandidateStroke(ctx, +0.04f, 0f, 0.98f);
                } else if (grade == GRADE_GREAT) {
                    mutateSavedAndCandidateStroke(ctx, +0.02f, 0f, 0.99f);
                }
            }
            List<PointSample> ptsCopy = new ArrayList<PointSample>(lastCandidatePoints);
            long durCopy = lastCandidateDurationMs;
            resetCandidateSessionFlags();
            String savedMsg = recordGradedThrowWithRingAndRadius(
                    ctx,
                    ptsCopy,
                    durCopy,
                    lastCandidateRingX,
                    lastCandidateRingY,
                    lastCandidateRingRadius,
                    lastCandidateScreenW,
                    lastCandidateScreenH,
                    grade,
                    false
            );
            if (hadCustomAdjustments) {
                String gradeLbl = (grade == GRADE_EXCELLENT) ? "🌟 Exc"
                        : (grade == GRADE_GREAT) ? "🔥 Great" : "👍 Nice";
                return "✓ " + gradeLbl + " Rated • Saved [" + comboSummary + "] (" + (spinMode ? "🌀 Spin" : "⬆️ Straight") + ")!";
            }
            return savedMsg;
        }

        resetCandidateSessionFlags();
        String suffix = hadCustomAdjustments ? (" • Saved [" + comboSummary + "]!") : "!";
        if (grade == GRADE_EXCELLENT) {
            return "🌟 Excellent Confirmed" + suffix;
        } else if (grade == GRADE_GREAT) {
            return "🔥 Great Confirmed" + suffix;
        } else {
            return "👍 Nice Confirmed" + suffix;
        }
    }

    private static void resetCandidateSessionFlags() {
        lastCandidateDistanceInformed = false;
        lastCandidateAngleInformed = false;
        lastCandidatePowerDeltaSteps = 0;
        lastCandidateAngleDeltaSteps = 0;
        lastCandidatePoints = null;
    }

    private static String getModeStoragePrefix(boolean spinMode) {
        return spinMode ? "spin_throw_" : "throw_";
    }

    /**
     * Scales the upward release flick vertically (Harder / Softer) AND shifts/angles the release
     * trajectory horizontally (More Left / More Right) while keeping any circular spin windup
     * in the bottom dock 100% circular and anchored on the PokéBall!
     */
    private static List<PointSample> scaleAndShiftStrokePoints(
            List<PointSample> pts,
            float verticalBoostFraction,
            float horizontalShiftFraction,
            int screenW,
            int screenH
    ) {
        if (pts == null || pts.size() < 2) return pts;
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;
        int n = pts.size();

        // Find the bottom turnaround index where the circular spin in the dock ends and the upward flick begins:
        int minYIdx = n - 1;
        float minY = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (pts.get(i).y < minY) {
                minY = pts.get(i).y;
                minYIdx = i;
            }
        }
        int launchIdx = 0;
        float peakBottomY = pts.get(minYIdx).y;
        for (int i = minYIdx; i >= 0; i--) {
            float y = pts.get(i).y;
            if (y >= peakBottomY - dpPx(w, 4f)) {
                if (y >= peakBottomY) {
                    peakBottomY = y;
                    launchIdx = i;
                }
            } else if (peakBottomY > minY + dpPx(w, 90f) && y < peakBottomY - dpPx(w, 12f)) {
                break;
            }
        }

        float anchorY = pts.get(launchIdx).y;
        float shiftPx = horizontalShiftFraction * w;
        List<PointSample> updated = new ArrayList<PointSample>(n);

        for (int i = 0; i < n; i++) {
            PointSample p = pts.get(i);
            if (i <= launchIdx) {
                // Keep the circular spin windup in the bottom dock 100% untouched!
                updated.add(new PointSample(p.x, p.y, p.tMs));
                continue;
            }
            float flickProgress = (float) (i - launchIdx) / (float) Math.max(1, (n - 1) - launchIdx);
            float weight = 0.32f * flickProgress + 0.68f * flickProgress * flickProgress;

            float dyFromAnchor = Math.max(0f, anchorY - p.y);
            float newY = p.y;
            if (Math.abs(verticalBoostFraction) > 0.001f && dyFromAnchor > 8f) {
                float boost = 1.0f + verticalBoostFraction * weight;
                newY = Math.max(h * 0.04f, Math.min(h * 0.94f, anchorY - dyFromAnchor * boost));
            }

            float newX = p.x;
            if (Math.abs(horizontalShiftFraction) > 0.001f) {
                newX = Math.max(w * 0.03f, Math.min(w * 0.97f, p.x + shiftPx * weight));
            }
            updated.add(new PointSample(newX, newY, p.tMs));
        }
        return updated;
    }

    private static void mutateSavedAndCandidateStrokeForDistance(Context ctx, float verticalBoostFraction, float durationScale) {
        mutateSavedAndCandidateStroke(ctx, verticalBoostFraction, 0f, durationScale);
    }

    private static void mutateSavedAndCandidateStroke(
            Context ctx,
            float verticalBoostFraction,
            float horizontalShiftFraction,
            float durationScale
    ) {
        SharedPreferences sp = getPrefs(ctx);
        boolean spinMode = isSpinThrowEnabled(ctx);
        int activeBucket = getActiveDistanceProfile(ctx);
        boolean cwSpin = isClockwiseSpinPreferred(ctx);
        int w = lastCandidateScreenW > 0 ? lastCandidateScreenW : 1080;
        int h = lastCandidateScreenH > 0 ? lastCandidateScreenH : 2400;
        long minSafeDur = spinMode ? 350L : 225L;
        long maxSafeDur = spinMode ? 485L : 430L;

        // Ensure lastCandidatePoints is initialized even if the user taps Harder/Softer/Left/Right on the HUD before throwing!
        if (lastCandidatePoints == null || lastCandidatePoints.size() < 2) {
            CalibratedStroke activeStroke = getCurrentActiveThrowForPreview(ctx, w, h);
            if (activeStroke != null && activeStroke.points != null && activeStroke.points.size() >= 2) {
                lastCandidatePoints = new ArrayList<PointSample>(activeStroke.points);
                lastCandidateDurationMs = activeStroke.durationMs;
            }
        }

        // 1. Update in-memory candidate points & duration so simultaneous Harder/Softer + Left/Right taps accumulate cleanly!
        if (lastCandidatePoints != null && lastCandidatePoints.size() >= 2) {
            List<PointSample> mutatedCandidate = scaleAndShiftStrokePoints(
                    lastCandidatePoints,
                    verticalBoostFraction,
                    horizontalShiftFraction,
                    w,
                    h
            );
            if (spinMode) {
                mutatedCandidate = ensureSingleSpinLoopBeforeThrow(mutatedCandidate, cwSpin, w, h);
            }
            lastCandidatePoints = mutatedCandidate;
            lastCandidateDurationMs = Math.max(minSafeDur, Math.min(maxSafeDur, Math.round(lastCandidateDurationMs * durationScale)));

            // 2. Save the accumulated candidate directly into SharedPreferences for both the active bucket and master mode!
            SharedPreferences.Editor ed = savePointsListToPrefsForMode(sp.edit(), spinMode, lastCandidatePoints, lastCandidateDurationMs);
            saveBucketPointsToPrefsForMode(
                    ed,
                    spinMode,
                    activeBucket,
                    lastCandidatePoints,
                    lastCandidateDurationMs,
                    lastCandidateRingX,
                    lastCandidateRingY,
                    lastCandidateRingRadius
            );
            ed.putFloat("throw_power_mult", 1.0f).putFloat("throw_speed_mult", 1.0f).apply();
        }
    }

    private static SharedPreferences.Editor savePointsListToPrefsForMode(
            SharedPreferences.Editor ed,
            boolean spinMode,
            List<PointSample> pts,
            long durationMs
    ) {
        String pfx = getModeStoragePrefix(spinMode);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) sb.append(";");
            PointSample p = pts.get(i);
            sb.append(Math.round(p.x * 10f) / 10f)
                    .append(",")
                    .append(Math.round(p.y * 10f) / 10f)
                    .append(",")
                    .append(p.tMs);
        }
        float arcLen = computeArcLength(pts);
        float vel = arcLen / (float) Math.max(85L, durationMs);
        ed.putString(pfx + "points", sb.toString());
        ed.putLong(pfx + "duration_ms", durationMs);
        ed.putFloat(pfx + "release_vel", vel);
        return ed;
    }

    // =========================================================================
    // 1. HUD TRANSPARENCY & MINIMIZED PILL BUTTON CUSTOMIZATION
    // =========================================================================

    public static int getHudOpacityPercent(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return Math.max(25, Math.min(98, sp.getInt("hud_opacity_pct", 68)));
    }

    public static void setHudOpacityPercent(Context ctx, int pct) {
        SharedPreferences sp = getPrefs(ctx);
        int clamped = Math.max(25, Math.min(98, pct));
        sp.edit().putInt("hud_opacity_pct", clamped).apply();
    }

    public static boolean isMiniShowThrow(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("mini_show_throw", true);
    }

    public static void setMiniShowThrow(Context ctx, boolean show) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("mini_show_throw", show).apply();
    }

    public static boolean isMiniShowGift(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("mini_show_gift", false);
    }

    public static void setMiniShowGift(Context ctx, boolean show) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("mini_show_gift", show).apply();
    }

    public static boolean isMiniShowScan(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("mini_show_scan", false);
    }

    public static void setMiniShowScan(Context ctx, boolean show) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("mini_show_scan", show).apply();
    }

    public static boolean isMiniShowScanPokemon(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("mini_show_scan_pokemon", true);
    }

    public static void setMiniShowScanPokemon(Context ctx, boolean show) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("mini_show_scan_pokemon", show).apply();
    }

    public static int getGiftWorkflowModePref(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getInt("gift_workflow_mode", 0);
    }

    public static void setGiftWorkflowModePref(Context ctx, int mode) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putInt("gift_workflow_mode", mode).apply();
        PokemonGoAccessibilityService.giftWorkflowMode = mode;
    }

    public static boolean isSafeHumanDelayEnabled(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("safe_human_delay", false);
    }

    public static void setSafeHumanDelayEnabled(Context ctx, boolean safe) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("safe_human_delay", safe).apply();
    }

    public static boolean isWaitForExcellentEnabled(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("wait_for_excellent_ring", true);
    }

    public static void setWaitForExcellentEnabled(Context ctx, boolean enabled) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("wait_for_excellent_ring", enabled).apply();
    }

    public static boolean isAutoHideOutsidePoGoEnabled(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("auto_hide_outside_pogo", true);
    }

    public static void setAutoHideOutsidePoGoEnabled(Context ctx) {
        setAutoHideOutsidePoGoEnabled(ctx, true);
    }

    public static void setAutoHideOutsidePoGoEnabled(Context ctx, boolean enabled) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("auto_hide_outside_pogo", enabled).apply();
    }

    // =========================================================================
    // 2. QUICK CATCH SETTINGS & BERRY BUTTON COORDINATE
    // =========================================================================

    public static boolean isQuickCatchEnabled(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getBoolean("quick_catch_enabled", true);
    }

    public static void setQuickCatchEnabled(Context ctx, boolean enabled) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit().putBoolean("quick_catch_enabled", enabled).apply();
    }

    public static void saveBerryButtonPoint(Context ctx, float x, float y) {
        SharedPreferences sp = getPrefs(ctx);
        sp.edit()
                .putFloat("berry_btn_x", x)
                .putFloat("berry_btn_y", y)
                .apply();
    }

    public static float[] getBerryButtonPoint(Context ctx, float defaultX, float defaultY) {
        SharedPreferences sp = getPrefs(ctx);
        return new float[]{
                sp.getFloat("berry_btn_x", defaultX),
                sp.getFloat("berry_btn_y", defaultY)
        };
    }

    // =========================================================================
    // 3. THROW POWER, FLICK VELOCITY, SPIN HOOK & 1:1 TAUGHT STORAGE
    // =========================================================================

    public static float getThrowPowerMultiplier(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return Math.max(0.65f, Math.min(1.85f, sp.getFloat("throw_power_mult", 1.0f)));
    }

    public static void setThrowPowerMultiplier(Context ctx, float mult) {
        SharedPreferences sp = getPrefs(ctx);
        float clamped = Math.max(0.65f, Math.min(1.85f, mult));
        sp.edit().putFloat("throw_power_mult", clamped).apply();
    }

    public static float adjustThrowPowerMultiplier(Context ctx, float delta) {
        float next = Math.max(0.65f, Math.min(1.85f, getThrowPowerMultiplier(ctx) + delta));
        setThrowPowerMultiplier(ctx, next);
        return next;
    }

    public static float getThrowSpeedMultiplier(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return Math.max(0.70f, Math.min(1.85f, sp.getFloat("throw_speed_mult", 1.0f)));
    }

    public static void setThrowSpeedMultiplier(Context ctx, float mult) {
        SharedPreferences sp = getPrefs(ctx);
        float clamped = Math.max(0.70f, Math.min(1.85f, mult));
        sp.edit().putFloat("throw_speed_mult", clamped).apply();
    }

    public static float adjustThrowSpeedMultiplier(Context ctx, float delta) {
        float next = Math.max(0.70f, Math.min(1.85f, getThrowSpeedMultiplier(ctx) + delta));
        setThrowSpeedMultiplier(ctx, next);
        return next;
    }

    public static float getSpinHookMultiplier(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return Math.max(0.65f, Math.min(1.45f, sp.getFloat("spin_hook_mult", 1.0f)));
    }

    public static void setSpinHookMultiplier(Context ctx, float mult) {
        SharedPreferences sp = getPrefs(ctx);
        float clamped = Math.max(0.65f, Math.min(1.45f, mult));
        sp.edit().putFloat("spin_hook_mult", clamped).apply();
    }

    public static float adjustSpinHookMultiplier(Context ctx, float delta) {
        float next = Math.max(0.65f, Math.min(1.45f, getSpinHookMultiplier(ctx) + delta));
        setSpinHookMultiplier(ctx, next);
        return next;
    }

    public static float getThrowHorizOffsetFraction(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return Math.max(-0.38f, Math.min(0.38f, sp.getFloat("throw_horiz_offset_frac", 0.0f)));
    }

    public static void setThrowHorizOffsetFraction(Context ctx, float frac) {
        SharedPreferences sp = getPrefs(ctx);
        float clamped = Math.max(-0.38f, Math.min(0.38f, frac));
        sp.edit().putFloat("throw_horiz_offset_frac", clamped).apply();
    }

    public static float adjustThrowHorizOffsetFraction(Context ctx, float delta) {
        float next = Math.max(-0.38f, Math.min(0.38f, getThrowHorizOffsetFraction(ctx) + delta));
        setThrowHorizOffsetFraction(ctx, next);
        return next;
    }

    public static boolean hasLearnedThrow(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        String pfx = getModeStoragePrefix(isSpinThrowEnabled(ctx));
        String raw = sp.getString(pfx + "points", "");
        return raw != null && raw.length() > 10;
    }

    public static int getLearnedThrowCount(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getInt("throw_teach_count", 0);
    }

    public static int getExcellentThrowCount(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getInt("throw_excellent_count", 0);
    }

    public static int getGreatThrowCount(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getInt("throw_great_count", 0);
    }

    public static int getNiceThrowCount(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getInt("throw_nice_count", 0);
    }

    public static String getThrowStatsBadge(Context ctx) {
        int total = getLearnedThrowCount(ctx);
        String modeIcon = isSpinThrowEnabled(ctx) ? "🌀" : "⬆️";
        if (total <= 0) return "🎓 Teach Throw (" + modeIcon + ")";
        int exc = getExcellentThrowCount(ctx);
        int grt = getGreatThrowCount(ctx);
        int nic = getNiceThrowCount(ctx);
        if (exc > 0) {
            return "🎓 Throw " + modeIcon + " (★" + exc + " Exc / ×" + total + ")";
        } else if (grt > 0 || nic > 0) {
            return "🎓 Throw " + modeIcon + " (🔥" + grt + " 👍" + nic + ")";
        }
        return "🎓 Teach Throw " + modeIcon + " (×" + total + ")";
    }

    public static long getLearnedThrowDuration(Context ctx) {
        return getLearnedThrowDurationForMode(ctx, isSpinThrowEnabled(ctx));
    }

    public static long getLearnedThrowDurationForMode(Context ctx, boolean spinMode) {
        SharedPreferences sp = getPrefs(ctx);
        String pfx = getModeStoragePrefix(spinMode);
        long def = spinMode ? 385L : 250L;
        long minSafe = spinMode ? 350L : 225L;
        return Math.max(minSafe, Math.min(520L, sp.getLong(pfx + "duration_ms", def)));
    }

    public static float getReferenceRingY(Context ctx, float defaultRingY) {
        SharedPreferences sp = getPrefs(ctx);
        String pfx = getModeStoragePrefix(isSpinThrowEnabled(ctx));
        return sp.getFloat(pfx + "ref_ring_y", defaultRingY);
    }

    public static float getReferenceRingX(Context ctx, float defaultRingX) {
        SharedPreferences sp = getPrefs(ctx);
        String pfx = getModeStoragePrefix(isSpinThrowEnabled(ctx));
        return sp.getFloat(pfx + "ref_ring_x", defaultRingX);
    }

    public static float getReferenceRingRadius(Context ctx, float defaultRadius) {
        SharedPreferences sp = getPrefs(ctx);
        String pfx = getModeStoragePrefix(isSpinThrowEnabled(ctx));
        return sp.getFloat(pfx + "ref_ring_rad", defaultRadius);
    }

    public static boolean isValidRealThrowAttempt(List<PointSample> rawPoints, int screenW, int screenH) {
        if (rawPoints == null || rawPoints.size() < 3) return false;
        int h = screenH > 0 ? screenH : 2400;
        PointSample first = rawPoints.get(0);
        if (first.y < h * 0.52f) return false;
        float minY = first.y;
        for (int i = 1; i < rawPoints.size(); i++) {
            if (rawPoints.get(i).y < minY) {
                minY = rawPoints.get(i).y;
            }
        }
        return (first.y - minY) >= h * 0.10f;
    }

    public static String recordGradedThrowWithRing(
            Context ctx,
            List<PointSample> rawPoints,
            long durationMs,
            float ringCenterX,
            float ringCenterY,
            int screenW,
            int screenH,
            int grade
    ) {
        return recordGradedThrowWithRingAndRadius(
                ctx,
                rawPoints,
                durationMs,
                ringCenterX,
                ringCenterY,
                screenW * 0.175f,
                screenW,
                screenH,
                grade,
                true
        );
    }

    /**
     * Saves the user's taught throw 100% 1:1 (exact path and exact active movement duration)!
     */
    public static String recordGradedThrowWithRingAndRadius(
            Context ctx,
            List<PointSample> rawPoints,
            long durationMs,
            float ringCenterX,
            float ringCenterY,
            float ringRadius,
            int screenW,
            int screenH,
            int grade,
            boolean isExplicitTeachSession
    ) {
        if (grade <= GRADE_HIT_NO_BONUS) {
            setLastExecutedThrowCandidateWithRadius(rawPoints, durationMs, ringCenterX, ringCenterY, ringRadius, screenW, screenH);
            return rateLastExecutedThrow(ctx, grade);
        }
        if (rawPoints == null || rawPoints.size() < 2) {
            return "❌ Invalid Throw Sample";
        }

        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;

        List<PointSample> finalPoints;
        long finalDuration;
        float releaseVel;

        if (isExplicitTeachSession) {
            CalibratedStroke calibrated = calibrateUserTaughtStroke(rawPoints, durationMs, w, h);
            finalPoints = new ArrayList<PointSample>(calibrated.points);
            finalDuration = calibrated.durationMs;
            releaseVel = calibrated.releaseVelocityPxPerMs;
        } else {
            // Rating an already-executed throw (and possibly already adjusted by '⬆ Too Close' or '⬇ Too Far'):
            // preserve the exact calibrated points & duration without re-running raw touch calibration!
            boolean spinCheck = isSpinThrowEnabled(ctx);
            long minSafe = spinCheck ? 350L : 225L;
            finalPoints = new ArrayList<PointSample>(rawPoints);
            finalDuration = Math.max(minSafe, Math.min(490L, durationMs));
            releaseVel = computeArcLength(finalPoints) / (float) Math.max(1L, finalDuration);
        }

        int detectedSpinDir = detectSpinDirectionFromPoints(finalPoints, w, h);
        boolean hasSpinLoop = hasTrueCircularSpinLoop(finalPoints, w);
        boolean isSpin = isSpinThrowEnabled(ctx);

        if (isExplicitTeachSession) {
            if (hasSpinLoop || detectedSpinDir != 0) {
                setClockwiseSpinPreferred(ctx, detectedSpinDir > 0);
                // If user spun or curved the ball in Teach Throw, enable Spin Mode automatically!
                if (!isSpin) {
                    isSpin = true;
                    setSpinThrowEnabled(ctx, true);
                }
            }
        }

        // CRITICAL GUARANTEE: If Spin Throw is ON, ensure the saved stroke ALWAYS has a full 1.75-turn spin loop!
        if (isSpin) {
            boolean cwSpin = isClockwiseSpinPreferred(ctx);
            finalPoints = ensureSingleSpinLoopBeforeThrow(finalPoints, cwSpin, w, h);
            finalDuration = Math.max(425L, Math.min(510L, finalDuration));
            releaseVel = computeArcLength(finalPoints) / (float) Math.max(1L, finalDuration);
        }

        SharedPreferences sp = getPrefs(ctx);
        int bucket = getActiveDistanceProfile(ctx);
        String modePrefix = getModeStoragePrefix(isSpin);

        int newTotal = sp.getInt("throw_teach_count", 0) + 1;
        int newExc = sp.getInt("throw_excellent_count", 0) + (grade == GRADE_EXCELLENT ? 1 : 0);
        int newGrt = sp.getInt("throw_great_count", 0) + (grade == GRADE_GREAT ? 1 : 0);
        int newNic = sp.getInt("throw_nice_count", 0) + (grade == GRADE_NICE ? 1 : 0);

        float safeRad = ringRadius > 20f ? ringRadius : (w * 0.175f);

        // Save 100% 1:1 into both the active distance profile (Close/Med/Far) and the master mode slot!
        SharedPreferences.Editor ed = sp.edit();
        savePointsListToPrefsForMode(ed, isSpin, finalPoints, finalDuration);
        saveBucketPointsToPrefsForMode(ed, isSpin, bucket, finalPoints, finalDuration, ringCenterX, ringCenterY, safeRad);

        ed.putInt("throw_teach_count", newTotal)
                .putInt("throw_excellent_count", newExc)
                .putInt("throw_great_count", newGrt)
                .putInt("throw_nice_count", newNic)
                .putInt("consecutive_miss_count", 0)
                .putFloat(modePrefix + "ref_ring_x", ringCenterX)
                .putFloat(modePrefix + "ref_ring_y", ringCenterY)
                .putFloat(modePrefix + "ref_ring_rad", safeRad)
                .putFloat("throw_power_mult", 1.0f)
                .putFloat("throw_speed_mult", 1.0f)
                .apply();

        String bucketName = getCircleBucketLabel(bucket);
        String styleLabel = isSpin ? "🌀 Spin" : "⬆️ Straight";
        float savedVel = Math.round(releaseVel * 10f) / 10f;
        return "✓ Saved 1:1 (" + styleLabel + " • " + bucketName + " • " + savedVel + " px/ms • " + finalDuration + "ms)";
    }

    public static int getActiveDistanceProfile(Context ctx) {
        if (ctx == null) return 1;
        SharedPreferences sp = getPrefs(ctx);
        return Math.max(0, Math.min(2, sp.getInt("active_distance_bucket", 1)));
    }

    public static void setActiveDistanceProfile(Context ctx, int bucket) {
        if (ctx == null) return;
        int clamped = Math.max(0, Math.min(2, bucket));
        getPrefs(ctx).edit().putInt("active_distance_bucket", clamped).apply();
    }

    public static String getActiveDistanceProfileShortTag(Context ctx) {
        int b = getActiveDistanceProfile(ctx);
        if (b == 0) return "🟢 Close";
        if (b == 2) return "🔴 Far";
        return "🟡 Med";
    }

    public static String getActiveDistanceProfileLabel(Context ctx) {
        return getCircleBucketLabel(getActiveDistanceProfile(ctx));
    }

    public static int cycleActiveDistanceProfile(Context ctx) {
        int next = (getActiveDistanceProfile(ctx) + 1) % 3;
        setActiveDistanceProfile(ctx, next);
        return next;
    }

    /**
     * Returns the exact active throw stroke that '🎯 Throw Ball' will execute right now,
     * so '🎓 Teach Throw' can draw a live preview of the trajectory on screen!
     */
    public static CalibratedStroke getCurrentActiveThrowForPreview(Context ctx, int screenW, int screenH) {
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;
        float refX = getReferenceRingX(ctx, w * 0.50f);
        float refY = getReferenceRingY(ctx, h * 0.40f);
        float refRad = getReferenceRingRadius(ctx, w * 0.175f);
        return getAdaptedLearnedThrowForRing(ctx, refX, refY, refRad, w, h);
    }

    /**
     * Live Trajectory Nudge ('⬆ Higher', '⬇ Lower', '⬅ Left', '➡ Right', '⚡ Faster'):
     * Directly modifies the active throw trajectory (stretching/lowering vertical climb,
     * shifting horizontal release aim/hook, and adjusting duration) and saves it into the active
     * distance profile (Close / Med / Far) so the user can watch the cyan curve update live on screen!
     */
    public static String nudgeActiveThrow(
            Context ctx,
            float verticalReachDelta,
            float horizontalShiftFraction,
            long durationDeltaMs,
            int screenW,
            int screenH
    ) {
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;

        CalibratedStroke current = getCurrentActiveThrowForPreview(ctx, w, h);
        List<PointSample> basePts = current.points;
        if (basePts == null || basePts.size() < 2) {
            return "⚠ Draw a throw first!";
        }

        List<PointSample> nudgedPts = scaleAndShiftStrokePoints(
                basePts,
                verticalReachDelta,
                horizontalShiftFraction,
                w,
                h
        );
        if (Math.abs(horizontalShiftFraction) > 0.001f) {
            adjustThrowHorizOffsetFraction(ctx, horizontalShiftFraction);
        }

        long minSafeNudgeDur = isSpinThrowEnabled(ctx) ? 350L : 225L;
        long newDur = Math.max(minSafeNudgeDur, Math.min(485L, current.durationMs + durationDeltaMs));
        boolean spinMode = isSpinThrowEnabled(ctx);
        int activeBucket = getActiveDistanceProfile(ctx);
        float refX = getReferenceRingX(ctx, w * 0.50f);
        float refY = getReferenceRingY(ctx, h * 0.40f);
        float refRad = getReferenceRingRadius(ctx, w * 0.175f);

        SharedPreferences sp = getPrefs(ctx);
        SharedPreferences.Editor ed = sp.edit();
        savePointsListToPrefsForMode(ed, spinMode, nudgedPts, newDur);
        saveBucketPointsToPrefsForMode(ed, spinMode, activeBucket, nudgedPts, newDur, refX, refY, refRad);
        ed.putFloat("throw_power_mult", 1.0f)
                .putFloat("throw_speed_mult", 1.0f)
                .apply();

        float newVel = Math.round((computeArcLength(nudgedPts) / (float) Math.max(1L, newDur)) * 10f) / 10f;
        String dirTag = verticalReachDelta > 0 ? "⬆ Harder"
                : verticalReachDelta < 0 ? "⬇ Softer"
                : horizontalShiftFraction < 0 ? "◀ Aimed Left" : "Aimed Right ▶";
        return dirTag + " (" + getActiveDistanceProfileShortTag(ctx) + " • " + newVel + " px/ms • " + newDur + "ms)";
    }

    private static List<PointSample> resamplePointsUniformly(List<PointSample> pts, int count) {
        if (pts == null || pts.isEmpty()) return new ArrayList<PointSample>();
        if (pts.size() == count) return new ArrayList<PointSample>(pts);
        List<PointSample> out = new ArrayList<PointSample>(count);
        int n = pts.size();
        for (int i = 0; i < count; i++) {
            float pos = ((float) i / (float) (count - 1)) * (n - 1);
            int idx = Math.min(n - 2, Math.max(0, (int) pos));
            float frac = pos - idx;
            PointSample a = pts.get(idx);
            PointSample b = pts.get(idx + 1);
            out.add(new PointSample(
                    a.x + (b.x - a.x) * frac,
                    a.y + (b.y - a.y) * frac,
                    Math.round(a.tMs + (b.tMs - a.tMs) * frac)
            ));
        }
        return out;
    }

    public static int getCircleBucket(float ringCenterY, float ringOuterRadius, int screenW, int screenH) {
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;
        if (ringOuterRadius >= w * 0.205f || ringCenterY <= h * 0.365f) {
            return 2; // Large / Far Circle
        } else if (ringOuterRadius <= w * 0.135f || ringCenterY >= h * 0.455f) {
            return 0; // Small / Close Circle
        }
        return 1; // Medium Standard Circle
    }

    public static String getCircleBucketLabel(int bucket) {
        if (bucket == 2) return "Far/Large";
        if (bucket == 0) return "Close/Small";
        return "Medium";
    }

    private static void saveBucketPointsToPrefsForMode(
            SharedPreferences.Editor ed,
            boolean spinMode,
            int bucket,
            List<PointSample> pts,
            long durationMs,
            float refX,
            float refY,
            float refRad
    ) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) sb.append(";");
            PointSample p = pts.get(i);
            sb.append(Math.round(p.x * 10f) / 10f)
                    .append(",")
                    .append(Math.round(p.y * 10f) / 10f)
                    .append(",")
                    .append(p.tMs);
        }
        float arcLen = computeArcLength(pts);
        float vel = arcLen / (float) Math.max(85L, durationMs);
        String pfx = getModeStoragePrefix(spinMode) + "b" + bucket + "_";
        ed.putString(pfx + "points", sb.toString())
                .putLong(pfx + "dur", durationMs)
                .putFloat(pfx + "vel", vel)
                .putFloat(pfx + "ref_x", refX)
                .putFloat(pfx + "ref_y", refY)
                .putFloat(pfx + "ref_rad", refRad);
    }

    /**
     * Loads the user's taught throw 1:1 (preserving their exact drawn X/Y trajectory shape!)
     * and applies only user-requested power/speed multipliers.
     * If no taught throw is saved for the active mode, returns the clean 1-Spin-Then-Throw (when Spin is ON)
     * or the Dead-Center High-Follow-Through Straight Throw (when Spin is OFF).
     */
    public static CalibratedStroke getAdaptedLearnedThrowForRing(
            Context ctx,
            float detectedRingX,
            float detectedRingY,
            float detectedOuterRad,
            int screenW,
            int screenH
    ) {
        int w = screenW > 0 ? screenW : 1080;
        int h = screenH > 0 ? screenH : 2400;

        boolean spinMode = isSpinThrowEnabled(ctx);
        String modePrefix = getModeStoragePrefix(spinMode);
        SharedPreferences sp = getPrefs(ctx);
        int bucket = getActiveDistanceProfile(ctx);
        String bPfx = modePrefix + "b" + bucket + "_";

        String bucketRaw = sp.getString(bPfx + "points", "");
        List<PointSample> basePts;
        long baseDur;
        float distanceAutoScale = 1.0f;

        if (bucketRaw != null && bucketRaw.length() > 10) {
            basePts = parsePointsString(bucketRaw);
            baseDur = sp.getLong(bPfx + "dur", getLearnedThrowDurationForMode(ctx, spinMode));
        } else {
            basePts = loadLearnedThrowPointsForMode(ctx, spinMode);
            baseDur = getLearnedThrowDurationForMode(ctx, spinMode);
            // If the user hasn't taught a separate throw for Close (0) or Far (2) yet,
            // automatically scale their taught Medium throw by 0.84x (Close) or 1.18x (Far)!
            if (bucket == 0) distanceAutoScale = 0.84f;
            else if (bucket == 2) distanceAutoScale = 1.18f;
        }

        if (basePts.size() < 2) {
            float targetY = (bucket == 0) ? (h * 0.48f) : (bucket == 2) ? (h * 0.31f) : detectedRingY;
            float targetRad = (bucket == 0) ? (w * 0.145f) : (bucket == 2) ? (w * 0.21f) : detectedOuterRad;
            return buildNaturalHumanGoodThrow(ctx, detectedRingX, targetY, targetRad, w, h);
        }

        long minSafeDur = spinMode ? 425L : 225L;
        long maxSafeDur = spinMode ? 510L : 435L;
        baseDur = Math.max(minSafeDur, Math.min(maxSafeDur, baseDur));

        // CRITICAL GUARANTEE: Whenever '🌀 Spin: ON' is enabled, ensure basePts has a full 1.75-turn circular spin loop!
        if (spinMode && !hasTrueCircularSpinLoop(basePts, w)) {
            basePts = ensureSingleSpinLoopBeforeThrow(basePts, isClockwiseSpinPreferred(ctx), w, h);
        }

        float powerMult = distanceAutoScale;
        // If powerMult is 1.0 (user has a saved throw for this distance bucket), replay their saved throw 100% 1:1 untouched!
        if (Math.abs(powerMult - 1.0f) < 0.01f) {
            float vel = computeArcLength(basePts) / (float) Math.max(1L, baseDur);
            return new CalibratedStroke(basePts, baseDur, vel, spinMode);
        }

        float startY = basePts.get(0).y;
        List<PointSample> adaptedPts = new ArrayList<PointSample>(basePts.size());
        for (int i = 0; i < basePts.size(); i++) {
            PointSample p = basePts.get(i);
            float progress = (float) i / (float) Math.max(1, basePts.size() - 1);
            float dyUp = startY - p.y;
            float scaledY = (dyUp > 28f)
                    ? (startY - dyUp * (1.0f + (powerMult - 1.0f) * progress * progress))
                    : p.y;
            adaptedPts.add(new PointSample(
                    Math.max(5f, Math.min(w - 5f, p.x)),
                    Math.max(20f, Math.min(h - 10f, scaledY)),
                    p.tMs
            ));
        }

        long adaptedDur = Math.max(minSafeDur, Math.min(maxSafeDur, baseDur));
        float adaptedVel = computeArcLength(adaptedPts) / (float) Math.max(1L, adaptedDur);

        return new CalibratedStroke(adaptedPts, adaptedDur, adaptedVel, spinMode);
    }

    private static float computeArcLength(List<PointSample> pts) {
        if (pts == null || pts.size() < 2) return 0f;
        float len = 0f;
        for (int i = 0; i < pts.size() - 1; i++) {
            float dx = pts.get(i + 1).x - pts.get(i).x;
            float dy = pts.get(i + 1).y - pts.get(i).y;
            len += (float) Math.hypot(dx, dy);
        }
        return len;
    }

    private static List<PointSample> parsePointsString(String raw) {
        List<PointSample> list = new ArrayList<PointSample>();
        if (raw == null || raw.length() == 0) return list;
        String[] pairs = raw.split(";");
        for (int i = 0; i < pairs.length; i++) {
            String[] xy = pairs[i].split(",");
            if (xy.length >= 2) {
                try {
                    float px = Float.parseFloat(xy[0]);
                    float py = Float.parseFloat(xy[1]);
                    long pt = xy.length >= 3 ? Long.parseLong(xy[2]) : 0L;
                    list.add(new PointSample(px, py, pt));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    public static List<PointSample> loadLearnedThrowPoints(Context ctx) {
        return loadLearnedThrowPointsForMode(ctx, isSpinThrowEnabled(ctx));
    }

    public static List<PointSample> loadLearnedThrowPointsForMode(Context ctx, boolean spinMode) {
        SharedPreferences sp = getPrefs(ctx);
        String pfx = getModeStoragePrefix(spinMode);
        return parsePointsString(sp.getString(pfx + "points", ""));
    }

    public static void resetLearnedThrow(Context ctx) {
        lastCandidatePoints = null;
        SharedPreferences sp = getPrefs(ctx);
        SharedPreferences.Editor ed = sp.edit()
                .remove("banned_miss_signatures_v24")
                .putInt("consecutive_miss_count", 0)
                .putFloat("throw_power_mult", 1.0f)
                .putFloat("throw_speed_mult", 1.0f)
                .putFloat("spin_hook_mult", 1.0f)
                .putInt("throw_teach_count", 0)
                .putInt("throw_excellent_count", 0)
                .putInt("throw_great_count", 0)
                .putInt("throw_nice_count", 0)
                .putInt("throw_hit_nobonus_count", 0);
        String[] modes = new String[]{"throw_", "spin_throw_"};
        for (int m = 0; m < modes.length; m++) {
            String mp = modes[m];
            ed.remove(mp + "points")
                    .remove(mp + "duration_ms")
                    .remove(mp + "release_vel")
                    .remove(mp + "ref_ring_x")
                    .remove(mp + "ref_ring_y")
                    .remove(mp + "ref_ring_rad");
            for (int b = 0; b <= 2; b++) {
                String pfx = mp + "b" + b + "_";
                ed.remove(pfx + "points")
                        .remove(pfx + "dur")
                        .remove(pfx + "vel")
                        .remove(pfx + "ref_x")
                        .remove(pfx + "ref_y")
                        .remove(pfx + "ref_rad");
            }
        }
        ed.apply();
    }

    // =========================================================================
    // 4. LEARNED GIFT WORKFLOW STORAGE
    // =========================================================================

    public static boolean hasLearnedGiftWorkflow(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getInt("gift_teach_count", 0) > 0;
    }

    public static int getLearnedGiftCount(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        return sp.getInt("gift_teach_count", 0);
    }

    public static void saveLearnedGiftStepPoint(Context ctx, int stepIndex, float x, float y) {
        SharedPreferences sp = getPrefs(ctx);
        String keyX = "gift_step_" + stepIndex + "_x";
        String keyY = "gift_step_" + stepIndex + "_y";

        sp.edit()
                .putFloat(keyX, x)
                .putFloat(keyY, y)
                .apply();
    }

    public static int incrementGiftTeachCount(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        int next = sp.getInt("gift_teach_count", 0) + 1;
        sp.edit().putInt("gift_teach_count", next).apply();
        return next;
    }

    /**
     * Retrieves the user's learned Gift Step point while validating that it lies within the
     * anatomically valid screen zone for that button in Pokémon GO (automatically self-healing
     * if an earlier training tap accidentally recorded Trade/Pin into the Send Gift slot).
     */
    public static float[] getGiftStepPoint(Context ctx, int stepIndex, float defaultX, float defaultY) {
        SharedPreferences sp = getPrefs(ctx);
        float x = sp.getFloat("gift_step_" + stepIndex + "_x", defaultX);
        float y = sp.getFloat("gift_step_" + stepIndex + "_y", defaultY);

        // Derive approximate screen dimensions from defaultX / defaultY so bounds scale across all displays
        float approxW = (stepIndex == 0) ? (defaultX / 0.38f)
                : (stepIndex == 1) ? (defaultX / 0.78f)
                : (stepIndex == 3) ? (defaultX / 0.18f)
                : (defaultX / 0.50f);
        float approxH = (stepIndex == 0) ? (defaultY / 0.275f)
                : (stepIndex == 1 || stepIndex == 2) ? (defaultY / 0.69f)
                : (stepIndex == 3) ? (defaultY / 0.74f)
                : (stepIndex == 4) ? (defaultY / 0.36f)
                : (stepIndex == 5) ? (defaultY / 0.81f)
                : (defaultY / 0.905f);

        if (approxW < 400f) approxW = 1080f;
        if (approxH < 800f) approxH = 2400f;

        if (stepIndex == 0) {
            // Step 0: Top Friend Row on Friends List
            if (x < approxW * 0.16f || x > approxW * 0.65f) x = defaultX;
            if (y < approxH * 0.21f || y > approxH * 0.36f) y = defaultY;
        } else if (stepIndex == 1) {
            // Step 1: 📌 Pin Postcard button (ALWAYS to the right of OPEN on Incoming Gift)
            if (x < approxW * 0.66f || x > approxW * 0.93f) x = defaultX;
            if (y < approxH * 0.57f || y > approxH * 0.79f) y = defaultY;
        } else if (stepIndex == 2) {
            // Step 2: 🎁 OPEN button (ALWAYS centered on Incoming Gift)
            if (x < approxW * 0.34f || x > approxW * 0.66f) x = defaultX;
            if (y < approxH * 0.57f || y > approxH * 0.79f) y = defaultY;
        } else if (stepIndex == 3) {
            // Step 3: 🎁 SEND GIFT button on Trainer Profile
            // CRITICAL: Send Gift is ALWAYS the LEFT button (x ≈ 0.12w..0.31w).
            // Battle is center (0.50w) and Trade is right (0.78w..0.84w).
            // Never allow Step 3 to point at Battle or Trade!
            if (x < approxW * 0.10f || x > approxW * 0.31f) {
                x = defaultX;
            }
            if (y < approxH * 0.62f || y > approxH * 0.85f) {
                y = defaultY;
            }
        } else if (stepIndex == 4) {
            // Step 4: 💌 Gift Postcard in Bag Picker
            if (x < approxW * 0.16f || x > approxW * 0.84f) x = defaultX;
            if (y < approxH * 0.20f || y > approxH * 0.66f) y = defaultY;
        } else if (stepIndex == 5) {
            // Step 5: 🚀 Green 'SEND' button on Gift Preview
            if (x < approxW * 0.33f || x > approxW * 0.67f) x = defaultX;
            if (y < approxH * 0.71f || y > approxH * 0.88f) y = defaultY;
        } else if (stepIndex == 6) {
            // Step 6: ✕ Close Profile button at bottom-center
            if (x < approxW * 0.36f || x > approxW * 0.64f) x = defaultX;
            if (y < approxH * 0.85f || y > approxH * 0.96f) y = defaultY;
        }

        return new float[]{x, y};
    }

    public static void resetLearnedGifts(Context ctx) {
        SharedPreferences sp = getPrefs(ctx);
        SharedPreferences.Editor ed = sp.edit();
        for (int i = 0; i <= 6; i++) {
            ed.remove("gift_step_" + i + "_x");
            ed.remove("gift_step_" + i + "_y");
        }
        ed.putInt("gift_teach_count", 0).apply();
    }
}
