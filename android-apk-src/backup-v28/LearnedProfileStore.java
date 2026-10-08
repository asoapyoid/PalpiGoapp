package com.pokemate.companion;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean, unified persistent store & throw engine for PalpiGO v27.0:
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
    public static final int GRADE_SHORT = -1;
    public static final int GRADE_FAR = -2;

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

        public CalibratedStroke(List<PointSample> points, long durationMs, float releaseVelocityPxPerMs) {
            this(points, durationMs, releaseVelocityPxPerMs, false);
        }

        public CalibratedStroke(List<PointSample> points, long durationMs, float releaseVelocityPxPerMs, boolean isSpinThrow) {
            this.points = points;
            this.durationMs = durationMs;
            this.releaseVelocityPxPerMs = releaseVelocityPxPerMs;
            this.isSpinThrow = isSpinThrow;
        }
    }

    private static List<PointSample> lastCandidatePoints = null;
    private static long lastCandidateDurationMs = 175L;
    private static float lastCandidateRingX = 540f;
    private static float lastCandidateRingY = 980f;
    private static float lastCandidateRingRadius = 185f;
    private static int lastCandidateScreenW = 1080;
    private static int lastCandidateScreenH = 2400;
    private static boolean lastCandidateDistanceInformed = false;

    public static boolean isNonFinalDistanceModifierGrade(int grade) {
        return grade == GRADE_SHORT || grade == GRADE_FAR;
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
     * Checks whether the stroke contains a true circular spin revolution (>= 220 degrees of angular windup)
     * required by Pokémon GO to register a Curveball / Spin Throw.
     */
    public static boolean hasTrueCircularSpinLoop(List<PointSample> pts, int screenW) {
        if (pts == null || pts.size() < 8) return false;
        int w = screenW > 0 ? screenW : 1080;
        int spinSampleEnd = Math.max(6, (int) (pts.size() * 0.72f));
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < spinSampleEnd; i++) {
            sumX += pts.get(i).x;
            sumY += pts.get(i).y;
        }
        float cx = sumX / spinSampleEnd;
        float cy = sumY / spinSampleEnd;

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
            totalAbsAngle += Math.abs(dTheta);
            prevAngle = angle;
        }
        return totalAbsAngle >= 3.85f;
    }

    /**
     * Guarantees that when '🌀 Spin: ON' is enabled, the stroke ALWAYS starts with 1 clean circular spin
     * and then launches tangentially to the stroke's exact release point (endPt.x, endPt.y).
     * If the stroke already has a true circular spin loop, returns it 100% untouched!
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

        PointSample firstPt = pts.get(0);
        PointSample endPt = pts.get(pts.size() - 1);
        float ballX = (firstPt.x >= w * 0.25f && firstPt.x <= w * 0.75f) ? firstPt.x : (w * 0.50f);
        float ballY = (firstPt.y >= h * 0.65f && firstPt.y <= h * 0.95f) ? firstPt.y : (h * 0.865f);
        float spinRad = w * 0.102f;
        float spinCenterX = ballX;
        float spinCenterY = ballY - spinRad * 0.72f;

        float releaseX = endPt.x;
        // If the user drew a dead-center straight line while Spin Mode is ON, angle the release point
        // slightly to the side so the spin hook curves it into the center of the Pokémon!
        if (Math.abs(releaseX - ballX) < w * 0.06f) {
            float sideSign = cwSpin ? -1.0f : 1.0f;
            releaseX = Math.max(w * 0.18f, Math.min(w * 0.82f, ballX + sideSign * w * 0.155f));
        }
        float releaseY = Math.max(h * 0.08f, Math.min(h * 0.48f, endPt.y));

        List<PointSample> spinPts = new ArrayList<PointSample>(48);
        int spinSamples = 24;
        int flickSamples = 24;
        float startAng = (float) (Math.PI * 0.5);
        float oneSpinAngle = (cwSpin ? 1.0f : -1.0f) * (float) (Math.PI * 2.38);

        for (int i = 0; i < spinSamples; i++) {
            float t = (float) i / (float) spinSamples;
            float ang = startAng + oneSpinAngle * t;
            float sx = spinCenterX + spinRad * (float) Math.cos(ang);
            float sy = spinCenterY + spinRad * (float) Math.sin(ang);
            spinPts.add(new PointSample(sx, sy));
        }

        float exitAng = startAng + oneSpinAngle;
        float exitX = spinCenterX + spinRad * (float) Math.cos(exitAng);
        float exitY = spinCenterY + spinRad * (float) Math.sin(exitAng);
        float tanDirSign = cwSpin ? 1.0f : -1.0f;
        float tanX = -tanDirSign * (float) Math.sin(exitAng);
        float tanY = tanDirSign * (float) Math.cos(exitAng);

        float distToRelease = (float) Math.hypot(releaseX - exitX, releaseY - exitY);
        float ctrlDist = distToRelease * 0.38f;
        float ctrlX = exitX + tanX * ctrlDist;
        float ctrlY = exitY + tanY * ctrlDist;

        for (int i = 1; i <= flickSamples; i++) {
            float u = (float) i / (float) flickSamples;
            float inv = 1.0f - u;
            float bx = inv * inv * exitX + 2f * inv * u * ctrlX + u * u * releaseX;
            float by = inv * inv * exitY + 2f * inv * u * ctrlY + u * u * releaseY;
            spinPts.add(new PointSample(
                    Math.max(8f, Math.min(w - 8f, bx)),
                    Math.max(20f, Math.min(h - 10f, by))
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
        boolean isSpin = (spinDir != 0);

        // 3. Find the bottom of the final spin loop (where the continuous upward throw climb begins).
        //    If the user spun multiple circles in place while warming up, walk backward 1 full revolution (2.1*PI)
        //    from bottomIdx so we keep 100% their real drawn final spin loop + their real drawn throw flick!
        int bottomIdx = moveStartIdx;
        float maxBottomY = -1f;
        int searchLimit = moveStartIdx + Math.max(1, (int) ((endIdx - moveStartIdx) * 0.72f));
        for (int i = moveStartIdx; i <= searchLimit; i++) {
            if (rawPts.get(i).y >= maxBottomY) {
                maxBottomY = rawPts.get(i).y;
                bottomIdx = i;
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
            float accumAngle = 0f;
            float prevAng = (float) Math.atan2(rawPts.get(bottomIdx).y - cy, rawPts.get(bottomIdx).x - cx);
            for (int i = bottomIdx - 1; i >= moveStartIdx; i--) {
                float dx = rawPts.get(i).x - cx;
                float dy = rawPts.get(i).y - cy;
                if (Math.hypot(dx, dy) < w * 0.015f) continue;
                float ang = (float) Math.atan2(dy, dx);
                float dTheta = ang - prevAng;
                while (dTheta > Math.PI) dTheta -= (float) (2.0 * Math.PI);
                while (dTheta < -Math.PI) dTheta += (float) (2.0 * Math.PI);
                accumAngle += Math.abs(dTheta);
                prevAng = ang;
                if (accumAngle >= (float) (Math.PI * 2.15)) {
                    moveStartIdx = i;
                    break;
                }
            }
        }

        // 4. Keep 100% of the user's drawn points from moveStartIdx to endIdx
        long startMoveT = rawPts.get(moveStartIdx).tMs;
        int localBottomIdx = Math.max(0, bottomIdx - moveStartIdx);
        List<PointSample> activePts = new ArrayList<PointSample>(endIdx - moveStartIdx + 1);
        for (int i = moveStartIdx; i <= endIdx; i++) {
            PointSample p = rawPts.get(i);
            activePts.add(new PointSample(p.x, p.y, Math.max(0L, p.tMs - startMoveT)));
        }

        if (activePts.size() > 86) {
            localBottomIdx = Math.min(78, Math.round(((float) localBottomIdx / Math.max(1, activePts.size() - 1)) * 79f));
            activePts = resamplePointsUniformly(activePts, 80);
        }

        long activeMoveDurationMs = activePts.get(activePts.size() - 1).tMs;
        if (activeMoveDurationMs < 45L) {
            activeMoveDurationMs = totalTouchDurationMs;
        }

        float totalArcLen = computeArcLength(activePts);

        // 5. Measure the user's PURE upward release flick velocity by finding the exact point where
        //    the finger has climbed 30% of the vertical distance from the bottom of the spin loop (bottomY)
        //    toward the release point (endY). Because MotionEvent samples at uniform time intervals (60-120Hz),
        //    using spatial Y-elevation completely excludes slow spin samples from the flick speed calculation!
        float bottomY = activePts.get(Math.min(localBottomIdx, activePts.size() - 1)).y;
        float endY = activePts.get(activePts.size() - 1).y;
        float climbStartY = bottomY - Math.max(dpPx(w, 24f), (bottomY - endY) * 0.30f);

        int releaseStartIdx = Math.min(localBottomIdx, Math.max(0, activePts.size() - 2));
        for (int i = releaseStartIdx; i < activePts.size() - 2; i++) {
            if (activePts.get(i).y <= climbStartY) {
                releaseStartIdx = i;
                break;
            }
        }

        float releaseLen = 0f;
        for (int i = releaseStartIdx; i < activePts.size() - 1; i++) {
            releaseLen += (float) Math.hypot(
                    activePts.get(i + 1).x - activePts.get(i).x,
                    activePts.get(i + 1).y - activePts.get(i).y
            );
        }
        long releaseDt = Math.max(18L, activePts.get(activePts.size() - 1).tMs - activePts.get(releaseStartIdx).tMs);
        float measuredReleaseVel = (releaseLen >= w * 0.08f)
                ? (releaseLen / (float) releaseDt)
                : (totalArcLen / (float) Math.max(45L, activeMoveDurationMs));

        float clampedReleaseVel = Math.max(5.8f, Math.min(13.5f, measuredReleaseVel));
        long effectiveDurationMs = Math.max(105L, Math.min(340L, Math.round(totalArcLen / clampedReleaseVel)));
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
        // Comfortable thumb-sized single spin circle (radius = 10.2% screen width, ~110px on 1080p)
        float spinRad = w * 0.102f;
        float spinCenterX = ballX;
        float spinCenterY = ballY - spinRad * 0.72f;

        float followThroughAboveRing = h * 0.195f;
        float baseTargetClimb = (ballY - safeRingY) + followThroughAboveRing;
        float sizeBoost = Math.max(0.92f, Math.min(1.18f, safeRad / (w * 0.165f)));

        CalibratedStroke fallbackSpinStroke = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            int step = (missStreak + attempt) % 5;
            float stepScale = (step == 0) ? 1.00f
                    : (step == 1) ? 1.14f
                    : (step == 2) ? 1.26f
                    : (step == 3) ? 0.90f : 1.07f;
            float hookStep = (step == 0) ? 1.00f
                    : (step == 1) ? 0.92f
                    : (step == 2) ? 1.08f
                    : (step == 3) ? 0.96f : 1.04f;

            float climb = baseTargetClimb * sizeBoost * powerMult * stepScale;
            float releaseY = Math.max(h * 0.09f, Math.min(h * 0.40f, ballY - climb));

            // CCW spin hooks LEFT in flight -> aim slightly RIGHT of ring center (+1).
            // CW spin hooks RIGHT in flight -> aim slightly LEFT of ring center (-1).
            float sideSign = cwSpin ? -1.0f : 1.0f;
            float releaseX = Math.max(w * 0.18f, Math.min(w * 0.82f, safeRingX + sideSign * w * 0.158f * hookMult * hookStep));

            List<PointSample> spinPts = new ArrayList<PointSample>(48);
            int spinSamples = 24; // 1 SINGLE SPIN REVOLUTION
            int flickSamples = 24; // Clean straight tangent launch right out of the single spin

            // Start at +PI/2 (bottom of spin circle = PokéBall grab spot).
            // Rotate 1 full turn (2*PI) + 0.38*PI so at exitAng, the circle's velocity tangent
            // points directly up-and-out toward (releaseX, releaseY) in a straight tangent launch!
            float startAng = (float) (Math.PI * 0.5);
            float oneSpinAngle = (cwSpin ? 1.0f : -1.0f) * (float) (Math.PI * 2.38);

            for (int i = 0; i < spinSamples; i++) {
                float t = (float) i / (float) spinSamples;
                float ang = startAng + oneSpinAngle * t;
                float sx = spinCenterX + spinRad * (float) Math.cos(ang);
                float sy = spinCenterY + spinRad * (float) Math.sin(ang);
                spinPts.add(new PointSample(sx, sy));
            }

            // Exact exit point and unit tangent vector at the end of the 1 single spin:
            float exitAng = startAng + oneSpinAngle;
            float exitX = spinCenterX + spinRad * (float) Math.cos(exitAng);
            float exitY = spinCenterY + spinRad * (float) Math.sin(exitAng);
            float tanDirSign = cwSpin ? 1.0f : -1.0f;
            float tanX = -tanDirSign * (float) Math.sin(exitAng);
            float tanY = tanDirSign * (float) Math.cos(exitAng);

            // Single control point along the exact circle tangent so the throw launches straight out of the 1 spin
            // with 100% C1 tangent continuity and zero S-curve!
            float distToRelease = (float) Math.hypot(releaseX - exitX, releaseY - exitY);
            float ctrlDist = distToRelease * 0.38f;
            float ctrlX = exitX + tanX * ctrlDist;
            float ctrlY = exitY + tanY * ctrlDist;

            for (int i = 1; i <= flickSamples; i++) {
                float u = (float) i / (float) flickSamples;
                // Smooth quadratic Bézier from (exitX, exitY) -> (ctrlX, ctrlY) -> (releaseX, releaseY)
                float inv = 1.0f - u;
                float bx = inv * inv * exitX + 2f * inv * u * ctrlX + u * u * releaseX;
                float by = inv * inv * exitY + 2f * inv * u * ctrlY + u * u * releaseY;
                spinPts.add(new PointSample(
                        Math.max(8f, Math.min(w - 8f, bx)),
                        Math.max(20f, Math.min(h - 10f, by))
                ));
            }

            float arcLen = computeArcLength(spinPts);
            // Total duration ~220ms..260ms (~85ms for the 1 spin + ~145ms for the upward throw)
            float flickVelPxPerMs = (7.4f + 1.6f * (climb / (h * 0.68f))) * speedMult * stepScale;
            flickVelPxPerMs = Math.max(5.8f, Math.min(11.5f, flickVelPxPerMs));
            long durMs = Math.max(195L, Math.min(280L, Math.round(arcLen / flickVelPxPerMs)));

            CalibratedStroke candidate = new CalibratedStroke(spinPts, durMs, flickVelPxPerMs, true);
            if (fallbackSpinStroke == null) {
                fallbackSpinStroke = candidate;
            }
            if (ctx == null || !isStrokeBannedByMissHistory(ctx, spinPts, durMs, w)) {
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
            float baseVelPxPerMs = (8.6f + 2.2f * (climb / (h * 0.70f))) * speedMult * stepScale;
            baseVelPxPerMs = Math.max(6.2f, Math.min(13.5f, baseVelPxPerMs));
            long durMs = Math.max(105L, Math.min(175L, Math.round(arcLen / baseVelPxPerMs)));

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
        return new CalibratedStroke(fallbackPts, 130L, 9.5f, false);
    }

    /**
     * TWO-STAGE RATING ENGINE:
     * - Stage 1 (Inform Distance — NON-FINAL):
     *   Tapping '⬆ Short (Too Close)' or '⬇ Far (Too Far)' immediately adjusts the throw's reach & speed
     *   both in SharedPreferences AND on the in-memory `lastCandidatePoints`, while keeping the rating
     *   session open so you can then tap the final outcome ('👍 Nice', '🔥 Great', '🌟 Exc', '⚪ Hit', or '❌ Miss')!
     * - Stage 2 (Final Outcome — CLOSES POPUP):
     *   Tapping '🌟 Exc', '🔥 Great', '👍 Nice', '⚪ Hit', or '❌ Miss' records the final result (preserving any
     *   Stage 1 distance adjustment you just informed it about!) and NEVER switches Spin Mode to Straight Mode!
     */
    public static synchronized String rateLastExecutedThrow(Context ctx, int grade) {
        SharedPreferences sp = getPrefs(ctx);
        boolean spinMode = isSpinThrowEnabled(ctx);

        // STAGE 1: NON-FINAL DISTANCE MODIFIERS ('⬆ Too Close / Short' & '⬇ Too Far')
        if (grade == GRADE_SHORT) {
            adjustThrowPowerMultiplier(ctx, 0.16f);
            adjustThrowSpeedMultiplier(ctx, 0.14f);
            mutateSavedAndCandidateStrokeForDistance(ctx, +0.16f, 0.88f);
            lastCandidateDistanceInformed = true;
            int reachPct = Math.round(getThrowPowerMultiplier(ctx) * 100f);
            return "⬆ Informed: Too Close (+16% Reach → " + reachPct + "%)! Now tap Nice / Great / Exc / Hit / Miss";
        }

        if (grade == GRADE_FAR) {
            adjustThrowPowerMultiplier(ctx, -0.15f);
            adjustThrowSpeedMultiplier(ctx, -0.14f);
            mutateSavedAndCandidateStrokeForDistance(ctx, -0.15f, 1.14f);
            lastCandidateDistanceInformed = true;
            int reachPct = Math.round(getThrowPowerMultiplier(ctx) * 100f);
            return "⬇ Informed: Too Far (-15% Reach → " + reachPct + "%)! Now tap Nice / Great / Exc / Hit / Miss";
        }

        // STAGE 2: FINAL OUTCOMES ('⚪ Hit', '❌ Miss', '👍 Nice', '🔥 Great', '🌟 Exc')
        if (grade == GRADE_HIT_NO_BONUS) {
            if (!lastCandidateDistanceInformed) {
                adjustThrowPowerMultiplier(ctx, 0.10f);
                adjustThrowSpeedMultiplier(ctx, 0.10f);
                mutateSavedAndCandidateStrokeForDistance(ctx, +0.10f, 0.92f);
            }
            if (spinMode) {
                adjustSpinHookMultiplier(ctx, -0.06f);
            }
            boolean hadDistInfo = lastCandidateDistanceInformed;
            lastCandidateDistanceInformed = false;
            lastCandidatePoints = null;
            String styleTag = spinMode ? "🌀 Spin" : "⬆️ Straight";
            return hadDistInfo
                    ? ("✓ Hit + Distance Saved (" + styleTag + " Aimed Tighter for Bonus Circle!)")
                    : ("🔧 Hit Tuned (" + styleTag + " +10% & Aimed Tighter for Bonus Circle!)");
        }

        if (grade == GRADE_MISS) {
            int missStreak = sp.getInt("consecutive_miss_count", 0) + 1;
            sp.edit().putInt("consecutive_miss_count", missStreak).apply();

            boolean hadDistInfo = lastCandidateDistanceInformed;
            if (!hadDistInfo) {
                // User tapped Miss directly without tapping Short/Far first -> nudge reach & spin hook,
                // NEVER delete their taught Spin/Straight throw and NEVER switch Spin to Straight!
                adjustThrowPowerMultiplier(ctx, 0.12f);
                adjustThrowSpeedMultiplier(ctx, 0.12f);
                mutateSavedAndCandidateStrokeForDistance(ctx, +0.12f, 0.90f);
                if (spinMode) {
                    float curHook = getSpinHookMultiplier(ctx);
                    setSpinHookMultiplier(ctx, curHook > 1.06f ? 0.90f : (curHook + 0.06f));
                }
            }

            lastCandidateDistanceInformed = false;
            lastCandidatePoints = null;
            String modeTag = spinMode ? "🌀 Spin Throw" : "⬆️ Straight Throw";
            return hadDistInfo
                    ? ("✓ Miss Logged with Your Distance Adjustment! Keeping " + modeTag)
                    : ("🔧 Miss Adjusted (+12% Reach) — Keeping " + modeTag + "!");
        }

        if (lastCandidatePoints != null && lastCandidatePoints.size() >= 2) {
            // If user rated Nice or Great WITHOUT already informing Too Close / Too Far,
            // apply a subtle refinement (+4% for Nice, +2% for Great) toward Excellent!
            if (!lastCandidateDistanceInformed) {
                if (grade == GRADE_NICE) {
                    mutateSavedAndCandidateStrokeForDistance(ctx, +0.04f, 0.97f);
                } else if (grade == GRADE_GREAT) {
                    mutateSavedAndCandidateStrokeForDistance(ctx, +0.02f, 0.99f);
                }
            }
            List<PointSample> ptsCopy = new ArrayList<PointSample>(lastCandidatePoints);
            long durCopy = lastCandidateDurationMs;
            boolean hadDistInfo = lastCandidateDistanceInformed;
            lastCandidateDistanceInformed = false;
            lastCandidatePoints = null;
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
            if (hadDistInfo) {
                String gradeLbl = (grade == GRADE_EXCELLENT) ? "🌟 Exc"
                        : (grade == GRADE_GREAT) ? "🔥 Great" : "👍 Nice";
                return "✓ " + gradeLbl + " + Distance Adjustment Saved (" + (spinMode ? "🌀 Spin" : "⬆️ Straight") + ")!";
            }
            return savedMsg;
        }

        lastCandidateDistanceInformed = false;
        if (grade == GRADE_EXCELLENT) {
            return "🌟 Excellent Confirmed! Exact Throw & Speed Locked.";
        } else if (grade == GRADE_GREAT) {
            return "🔥 Great Confirmed! Throw & Speed Locked.";
        } else {
            return "👍 Nice Confirmed! Throw & Speed Saved.";
        }
    }

    private static String getModeStoragePrefix(boolean spinMode) {
        return spinMode ? "spin_throw_" : "throw_";
    }

    private static List<PointSample> scaleStrokePointsVertically(List<PointSample> pts, float verticalBoostFraction) {
        if (pts == null || pts.size() < 2) return pts;
        float startY = pts.get(0).y;
        List<PointSample> updated = new ArrayList<PointSample>(pts.size());
        for (int i = 0; i < pts.size(); i++) {
            PointSample p = pts.get(i);
            float progress = (float) i / (float) Math.max(1, pts.size() - 1);
            float dyFromStart = startY - p.y;
            float newY = p.y;
            if (dyFromStart > 15f) {
                float boost = 1.0f + verticalBoostFraction * (0.35f + 0.65f * progress);
                newY = Math.max(35f, Math.min(2350f, startY - dyFromStart * boost));
            }
            updated.add(new PointSample(p.x, newY, p.tMs));
        }
        return updated;
    }

    private static void mutateSavedAndCandidateStrokeForDistance(Context ctx, float verticalBoostFraction, float durationScale) {
        SharedPreferences sp = getPrefs(ctx);
        boolean spinMode = isSpinThrowEnabled(ctx);
        int activeBucket = getActiveDistanceProfile(ctx);
        boolean cwSpin = isClockwiseSpinPreferred(ctx);
        int w = lastCandidateScreenW > 0 ? lastCandidateScreenW : 1080;
        int h = lastCandidateScreenH > 0 ? lastCandidateScreenH : 2400;

        // 1. Update in-memory candidate points & duration so a subsequent Nice/Great/Exc/Hit/Miss tap preserves this adjustment!
        if (lastCandidatePoints != null && lastCandidatePoints.size() >= 2) {
            List<PointSample> scaledCandidate = scaleStrokePointsVertically(lastCandidatePoints, verticalBoostFraction);
            if (spinMode) {
                scaledCandidate = ensureSingleSpinLoopBeforeThrow(scaledCandidate, cwSpin, w, h);
            }
            lastCandidatePoints = scaledCandidate;
            lastCandidateDurationMs = Math.max(90L, Math.min(480L, Math.round(lastCandidateDurationMs * durationScale)));
        }

        // 2. Also update the saved stroke in SharedPreferences immediately
        String bPfx = getModeStoragePrefix(spinMode) + "b" + activeBucket + "_";
        List<PointSample> pts = parsePointsString(sp.getString(bPfx + "points", ""));
        long oldDur = sp.getLong(bPfx + "dur", getLearnedThrowDurationForMode(ctx, spinMode));
        if (pts.size() < 2) {
            pts = loadLearnedThrowPointsForMode(ctx, spinMode);
            oldDur = getLearnedThrowDurationForMode(ctx, spinMode);
        }
        if (pts.size() < 2) {
            if (lastCandidatePoints != null && lastCandidatePoints.size() >= 2) {
                SharedPreferences.Editor ed = savePointsListToPrefsForMode(sp.edit(), spinMode, lastCandidatePoints, lastCandidateDurationMs);
                saveBucketPointsToPrefsForMode(ed, spinMode, activeBucket, lastCandidatePoints, lastCandidateDurationMs, lastCandidateRingX, lastCandidateRingY, lastCandidateRingRadius);
                ed.apply();
            }
            return;
        }

        List<PointSample> updated = scaleStrokePointsVertically(pts, verticalBoostFraction);
        if (spinMode) {
            updated = ensureSingleSpinLoopBeforeThrow(updated, cwSpin, w, h);
        }
        long newDur = Math.max(90L, Math.min(480L, Math.round(oldDur * durationScale)));
        SharedPreferences.Editor ed = savePointsListToPrefsForMode(sp.edit(), spinMode, updated, newDur);
        saveBucketPointsToPrefsForMode(ed, spinMode, activeBucket, updated, newDur, lastCandidateRingX, lastCandidateRingY, lastCandidateRingRadius);
        ed.apply();
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
        long def = spinMode ? 230L : 140L;
        return Math.max(95L, Math.min(520L, sp.getLong(pfx + "duration_ms", def)));
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
            finalPoints = new ArrayList<PointSample>(rawPoints);
            finalDuration = Math.max(85L, Math.min(480L, durationMs));
            releaseVel = computeArcLength(finalPoints) / (float) Math.max(1L, finalDuration);
        }

        int detectedSpinDir = detectSpinDirectionFromPoints(finalPoints, w, h);
        boolean hasSpinLoop = hasTrueCircularSpinLoop(finalPoints, w);
        boolean isSpin = isSpinThrowEnabled(ctx);

        if (isExplicitTeachSession) {
            if (hasSpinLoop || detectedSpinDir != 0) {
                setClockwiseSpinPreferred(ctx, detectedSpinDir > 0);
                // If user spun the ball in Teach Throw, enable Spin Mode automatically!
                if (!isSpin && hasSpinLoop) {
                    isSpin = true;
                    setSpinThrowEnabled(ctx, true);
                }
            }
        }

        // CRITICAL GUARANTEE: If Spin Throw is ON, ensure the saved stroke ALWAYS has a true spin loop!
        if (isSpin) {
            boolean cwSpin = isClockwiseSpinPreferred(ctx);
            finalPoints = ensureSingleSpinLoopBeforeThrow(finalPoints, cwSpin, w, h);
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

        float startY = basePts.get(0).y;
        float minY = startY;
        for (int i = 0; i < basePts.size(); i++) {
            if (basePts.get(i).y < minY) minY = basePts.get(i).y;
        }
        float totalClimb = Math.max(dpPx(w, 40f), startY - minY);
        float shiftPx = horizontalShiftFraction * w;

        List<PointSample> nudgedPts = new ArrayList<PointSample>(basePts.size());
        for (int i = 0; i < basePts.size(); i++) {
            PointSample p = basePts.get(i);
            float dyUp = Math.max(0f, startY - p.y);
            // Weight the nudge smoothly along the upward climb so the PokéBall start point never moves!
            float climbWeight = Math.min(1.0f, dyUp / totalClimb);
            float newY = p.y;
            if (Math.abs(verticalReachDelta) > 0.001f && dyUp > dpPx(w, 12f)) {
                float scaledClimb = dyUp * (1.0f + verticalReachDelta * (0.35f + 0.65f * climbWeight));
                newY = Math.max(h * 0.05f, Math.min(h * 0.94f, startY - scaledClimb));
            }
            float newX = Math.max(w * 0.04f, Math.min(w * 0.96f, p.x + shiftPx * (climbWeight * climbWeight)));
            nudgedPts.add(new PointSample(newX, newY, p.tMs));
        }

        long newDur = Math.max(85L, Math.min(380L, current.durationMs + durationDeltaMs));
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

        // CRITICAL GUARANTEE: Whenever '🌀 Spin: ON' is enabled, ensure basePts has a true circular spin loop!
        if (spinMode && !hasTrueCircularSpinLoop(basePts, w)) {
            basePts = ensureSingleSpinLoopBeforeThrow(basePts, isClockwiseSpinPreferred(ctx), w, h);
        }

        float powerMult = getThrowPowerMultiplier(ctx) * distanceAutoScale;
        float speedMult = getThrowSpeedMultiplier(ctx) * (bucket == 0 ? 0.90f : (bucket == 2 ? 1.12f : 1.0f));

        // If powerMult and speedMult are 1.0 (default), replay the user's taught throw 100% 1:1 untouched!
        if (Math.abs(powerMult - 1.0f) < 0.01f && Math.abs(speedMult - 1.0f) < 0.01f) {
            float vel = computeArcLength(basePts) / (float) Math.max(1L, baseDur);
            return new CalibratedStroke(basePts, baseDur, vel, spinMode);
        }

        float startY = basePts.get(0).y;
        List<PointSample> adaptedPts = new ArrayList<PointSample>(basePts.size());
        for (int i = 0; i < basePts.size(); i++) {
            PointSample p = basePts.get(i);
            float progress = (float) i / (float) Math.max(1, basePts.size() - 1);
            float dyUp = startY - p.y;
            float scaledY = (dyUp > 10f)
                    ? (startY - dyUp * (1.0f + (powerMult - 1.0f) * progress))
                    : p.y;
            adaptedPts.add(new PointSample(
                    Math.max(5f, Math.min(w - 5f, p.x)),
                    Math.max(20f, Math.min(h - 10f, scaledY)),
                    p.tMs
            ));
        }

        long adaptedDur = Math.round(baseDur / Math.max(0.65f, Math.min(1.65f, speedMult)));
        adaptedDur = Math.max(95L, Math.min(480L, adaptedDur));
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

    public static float[] getGiftStepPoint(Context ctx, int stepIndex, float defaultX, float defaultY) {
        SharedPreferences sp = getPrefs(ctx);
        float x = sp.getFloat("gift_step_" + stepIndex + "_x", defaultX);
        float y = sp.getFloat("gift_step_" + stepIndex + "_y", defaultY);
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
