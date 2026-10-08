package com.pokemate.companion;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PokemonGoAccessibilityService extends AccessibilityService {

    public interface StatusListener {
        void onStatusChanged(String statusText, boolean isRunning);
    }

    public interface OcrScanCallback {
        void onScreenshotCaptured();
        void onScanResult(String matchedQueryForSearchBar, int matchedCount, String errorOrRaw);
    }

    public interface PokemonScanCallback {
        void onScreenshotCaptured();
        void onPokemonScanned(PokemonMetaAnalyzer.PokemonScanReport report, String rawOcrText, String errorMsg);
    }

    public interface RingProbeCallback {
        void onRingDetected(TargetRingState ringState);
    }

    public static class TargetRingState {
        public final boolean isCircleUp;
        public final float centerX;
        public final float centerY;
        public final float innerColoredRadius;
        public final float outerWhiteRadius;
        public final float shrinkRatio; // 1.0 = Full Nice, 0.5 = Great, ~0.20 = Smallest Excellent!
        public final Bitmap capturedScreenBitmap; // Exact screenshot taken while holding the PokéBall!

        public TargetRingState(
                boolean isCircleUp,
                float centerX,
                float centerY,
                float innerColoredRadius,
                float outerWhiteRadius,
                float shrinkRatio
        ) {
            this(isCircleUp, centerX, centerY, innerColoredRadius, outerWhiteRadius, shrinkRatio, null);
        }

        public TargetRingState(
                boolean isCircleUp,
                float centerX,
                float centerY,
                float innerColoredRadius,
                float outerWhiteRadius,
                float shrinkRatio,
                Bitmap capturedScreenBitmap
        ) {
            this.isCircleUp = isCircleUp;
            this.centerX = centerX;
            this.centerY = centerY;
            this.innerColoredRadius = innerColoredRadius;
            this.outerWhiteRadius = outerWhiteRadius;
            this.shrinkRatio = shrinkRatio;
            this.capturedScreenBitmap = capturedScreenBitmap;
        }
    }

    public static final int MODE_PIN_OPEN_AND_SEND = 0;
    public static final int MODE_PIN_AND_OPEN_ONLY = 1;
    public static final int MODE_SEND_GIFT_ONLY = 2;

    public static volatile PokemonGoAccessibilityService instance;
    public static volatile StatusListener statusListener;
    public static volatile boolean isPokemonGoInForeground = false;
    public static volatile boolean isAutoGiftRunning = false;
    public static volatile boolean isThrowRunning = false;
    public static volatile int giftWorkflowMode = MODE_PIN_OPEN_AND_SEND;
    public static volatile int friendsProcessed = 0;
    public static volatile int giftsSent = 0;
    public static volatile String currentStepText = "Ready • Idle";

    private final Handler giftHandler = new Handler(Looper.getMainLooper());
    private final Handler throwHandler = new Handler(Looper.getMainLooper());
    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private int currentRowSlot = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                // Strictly listen ONLY to TYPE_WINDOW_STATE_CHANGED (never TYPE_WINDOWS_CHANGED!) so scrolling never flashes HUD
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
                info.notificationTimeout = 120;
                setServiceInfo(info);
            }
        } catch (Exception ignored) {}
        notifyStatus("Accessibility Ready ✓");
    }

    public void notifyStatus(String text) {
        currentStepText = text;
        final StatusListener listener = statusListener;
        if (listener != null) {
            giftHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onStatusChanged(currentStepText, isAutoGiftRunning);
                }
            });
        }
    }

    public int[] getTrueScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (wm != null) {
                    Rect b = wm.getMaximumWindowMetrics().getBounds();
                    if (b.width() > 0 && b.height() > 0) {
                        return new int[]{b.width(), b.height()};
                    }
                }
            } catch (Exception ignored) {}
        }
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        return new int[]{w > 0 ? w : 1080, h > 0 ? h : 2400};
    }

    private long pace(long baseMs) {
        if (LearnedProfileStore.isSafeHumanDelayEnabled(this)) {
            return Math.round(baseMs * 1.28f);
        }
        return baseMs;
    }

    // =========================================================================
    // ENGINE 1: VISION CIRCLE-LOCK + EXCELLENT TIMING + HUMAN-VARIANCE THROW
    // =========================================================================

    public void stopActiveThrow() {
        isThrowRunning = false;
        throwHandler.removeCallbacksAndMessages(null);
        if (FloatingViewService.instance != null) {
            FloatingViewService.instance.onThrowSequenceFinished(false);
        }
        notifyStatus("■ Throw Stopped • Ready");
    }

    /**
     * Returns the saved/calibrated Target Ring position immediately WITHOUT pre-touching
     * or dropping the PokéBall!
     */
    public void probeTargetRingForTeaching(final RingProbeCallback callback) {
        final int[] dim = getTrueScreenDimensions();
        final int w = dim[0];
        final int h = dim[1];
        float refX = LearnedProfileStore.getReferenceRingX(this, w * 0.50f);
        float refY = LearnedProfileStore.getReferenceRingY(this, h * 0.40f);
        float refRad = LearnedProfileStore.getReferenceRingRadius(this, w * 0.175f);
        TargetRingState state = new TargetRingState(true, refX, refY, refRad * 0.35f, refRad, 0.35f, null);
        if (callback != null) {
            callback.onRingDetected(state);
        }
    }

    public void performAutoExcellentSpinThrow(final Runnable onBeforeScreenshotRestore) {
        if (isThrowRunning) {
            stopActiveThrow();
            return;
        }

        throwHandler.removeCallbacksAndMessages(null);
        isThrowRunning = true;

        final int[] dim = getTrueScreenDimensions();
        final int w = dim[0];
        final int h = dim[1];

        float refX = LearnedProfileStore.getReferenceRingX(this, w * 0.50f);
        float refY = LearnedProfileStore.getReferenceRingY(this, h * 0.40f);
        float refRad = LearnedProfileStore.getReferenceRingRadius(this, w * 0.175f);
        final TargetRingState ring = new TargetRingState(true, refX, refY, refRad * 0.35f, refRad, 0.35f);

        throwHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isThrowRunning) return;
                executeSkilledHumanCurveball(w, h, ring);
            }
        }, 35L);
    }

    private void executeSkilledHumanCurveball(final int w, final int h, TargetRingState ring) {
        if (!isThrowRunning) return;

        final boolean useQuickCatch = LearnedProfileStore.isQuickCatchEnabled(this);

        // Load either the verified adapted throw or the clean 2-Phase 1-Spin-Then-Throw Curveball
        LearnedProfileStore.CalibratedStroke chosenThrow = LearnedProfileStore.getAdaptedLearnedThrowForRing(
                this,
                ring.centerX,
                ring.centerY,
                ring.outerWhiteRadius,
                w,
                h
        );

        if (LearnedProfileStore.isRoboticOrInvalidStroke(
                this,
                chosenThrow.points,
                chosenThrow.durationMs,
                ring.centerX,
                ring.centerY,
                w,
                h
        )) {
            chosenThrow = LearnedProfileStore.buildNaturalHumanGoodThrow(
                    this,
                    ring.centerX,
                    ring.centerY,
                    ring.outerWhiteRadius,
                    w,
                    h
            );
        }

        LearnedProfileStore.setLastExecutedThrowCandidateWithRadius(
                chosenThrow.points,
                chosenThrow.durationMs,
                ring.centerX,
                ring.centerY,
                ring.outerWhiteRadius,
                w,
                h
        );

        String bucketTag = LearnedProfileStore.getActiveDistanceProfileShortTag(this);
        String styleTag = LearnedProfileStore.isSpinThrowEnabled(this) ? "🌀 Spin" : "⬆️ Straight";
        String fastTag = useQuickCatch ? " • ⚡ Fast" : "";
        float velTag = Math.round(chosenThrow.releaseVelocityPxPerMs * 10f) / 10f;
        notifyStatus("🎯 " + styleTag + fastTag + " (" + bucketTag + " • " + velTag + " px/ms • " + chosenThrow.durationMs + "ms)");

        executeTwoPhaseChainedThrow(
                w,
                h,
                chosenThrow,
                useQuickCatch,
                true,
                LearnedProfileStore.isWaitForExcellentEnabled(this)
        );
    }

    public void performAimedThrow(final float targetX, final float targetY, final boolean isCurveball) {
        if (isThrowRunning) {
            stopActiveThrow();
            return;
        }

        throwHandler.removeCallbacksAndMessages(null);
        isThrowRunning = true;

        final int[] dim = getTrueScreenDimensions();
        final int w = dim[0];
        final int h = dim[1];

        final boolean useQuickCatch = LearnedProfileStore.isQuickCatchEnabled(this);
        float ballStartX = w * 0.50f;
        float ballStartY = h * 0.88f;

        List<LearnedProfileStore.PointSample> strokePoints = new ArrayList<LearnedProfileStore.PointSample>();

        if (isCurveball) {
            // Magnus Curveball Arc: Initial spin circle at base, then curve out left and hook into target
            float spinRadius = w * 0.085f;
            float cx = ballStartX;
            float cy = ballStartY - spinRadius;

            int steps = 14;
            for (int i = 0; i <= steps; i++) {
                double angle = -Math.PI / 2.0 - (i / (double) steps) * 2.0 * Math.PI;
                float px = cx + (float) (Math.cos(angle) * spinRadius);
                float py = cy + (float) (Math.sin(angle) * spinRadius);
                strokePoints.add(new LearnedProfileStore.PointSample(px, py));
            }

            float sweepOutX = ballStartX - w * 0.22f;
            float sweepOutY = (ballStartY + targetY) * 0.55f;
            int flickSteps = 16;
            for (int i = 1; i <= flickSteps; i++) {
                float t = i / (float) flickSteps;
                float u = 1f - t;
                float px = u * u * ballStartX + 2f * u * t * sweepOutX + t * t * targetX;
                float py = u * u * ballStartY + 2f * u * t * sweepOutY + t * t * targetY;
                strokePoints.add(new LearnedProfileStore.PointSample(px, py));
            }
        } else {
            int flickSteps = 16;
            for (int i = 0; i <= flickSteps; i++) {
                float t = i / (float) flickSteps;
                float px = ballStartX + t * (targetX - ballStartX);
                float py = ballStartY + t * (targetY - ballStartY);
                strokePoints.add(new LearnedProfileStore.PointSample(px, py));
            }
        }

        long dur = isCurveball ? 465L : 260L;
        notifyStatus("🎯 Aimed " + (isCurveball ? "Curveball" : "Straight") + " Throw -> (" + Math.round(targetX) + ", " + Math.round(targetY) + ")");
        replayStrokePoints(strokePoints, dur, 0f, useQuickCatch, true);
    }

    public void replayStrokePoints(
            List<LearnedProfileStore.PointSample> pts,
            long durationMs,
            float jitterStdDev,
            final boolean useQuickCatch
    ) {
        replayStrokePoints(pts, durationMs, jitterStdDev, useQuickCatch, false);
    }

    public void replayStrokePoints(
            List<LearnedProfileStore.PointSample> pts,
            long durationMs,
            float jitterStdDev,
            final boolean useQuickCatch,
            final boolean showPostThrowRatePopup
    ) {
        if (pts == null || pts.size() < 2) {
            isThrowRunning = false;
            if (FloatingViewService.instance != null) {
                FloatingViewService.instance.onThrowSequenceFinished(false);
            }
            notifyStatus("Ready • Idle");
            return;
        }

        throwHandler.removeCallbacksAndMessages(null);
        isThrowRunning = true;

        final int[] dim = getTrueScreenDimensions();
        final int w = dim[0];
        final int h = dim[1];

        List<LearnedProfileStore.PointSample> activePts = pts;
        boolean spinModeOn = LearnedProfileStore.isSpinThrowEnabled(this);
        if (spinModeOn) {
            activePts = LearnedProfileStore.ensureSingleSpinLoopBeforeThrow(activePts, true, w, h);
        }

        boolean isSpin = spinModeOn
                || LearnedProfileStore.hasTrueCircularSpinLoop(activePts, w)
                || (LearnedProfileStore.detectSpinDirectionFromPoints(activePts, w, h) != 0);
        long minSafeDur = isSpin ? 430L : 225L;
        long maxSafeDur = isSpin ? 505L : 435L;
        long safeDurationMs = Math.max(minSafeDur, Math.min(maxSafeDur, durationMs));

        // Replay the throw as a single continuous, uninterrupted hardware stroke so angular velocity never drops!
        Path singlePath = buildClampedPathFromPoints(activePts, w, h);
        dispatchThrowPathWithOptionalQuickCatch(w, h, singlePath, safeDurationMs, useQuickCatch, showPostThrowRatePopup);
    }

    /**
     * SMART THROW DISPATCHER:
     * Always dispatches the full calibrated throw (including the 1.75-revolution circular spin windup
     * and same-direction curved tangent launch when Spin Mode is enabled) as a SINGLE continuous,
     * uninterrupted hardware StrokeDescription. This eliminates the binder IPC pause between
     * continueStroke() phases that previously caused Pokémon GO's physics engine to drop the ball's
     * angular spin velocity and throw straight.
     */
    private void executeTwoPhaseChainedThrow(
            final int w,
            final int h,
            final LearnedProfileStore.CalibratedStroke initialStroke,
            final boolean useQuickCatch,
            final boolean showPostThrowRatePopup,
            final boolean enableLiveExcellentTiming
    ) {
        boolean spinModeOn = LearnedProfileStore.isSpinThrowEnabled(this) || initialStroke.isSpinThrow;
        List<LearnedProfileStore.PointSample> strokePts = initialStroke.points;
        if (spinModeOn && strokePts != null && strokePts.size() >= 2) {
            strokePts = LearnedProfileStore.ensureSingleSpinLoopBeforeThrow(strokePts, true, w, h);
        }

        long minSafeTotalDur = spinModeOn ? 430L : 225L;
        long maxSafeTotalDur = spinModeOn ? 505L : 435L;
        final long safeSingleDur = Math.max(minSafeTotalDur, Math.min(maxSafeTotalDur, initialStroke.durationMs));

        Path singlePath = buildClampedPathFromPoints(strokePts, w, h);
        dispatchThrowPathWithOptionalQuickCatch(w, h, singlePath, safeSingleDur, useQuickCatch, showPostThrowRatePopup);
    }

    private void dispatchPhase2TangentFlick(
            final int w,
            final int h,
            final LearnedProfileStore.CalibratedStroke stroke,
            final GestureDescription.StrokeDescription prevBallStroke,
            final float exactExitX,
            final float exactExitY,
            final boolean showPostThrowRatePopup
    ) {
        List<LearnedProfileStore.PointSample> flickPts = stroke.flickPoints;
        Path flickPath = new Path();
        flickPath.moveTo(exactExitX, exactExitY);
        for (int i = 1; i < flickPts.size(); i++) {
            flickPath.lineTo(clampX(flickPts.get(i).x, w), clampY(flickPts.get(i).y, h));
        }
        if (flickPts.size() == 1) {
            flickPath.lineTo(exactExitX, Math.max(30f, exactExitY - h * 0.35f));
        }

        final long flickDurMs = Math.max(195L, Math.min(295L, stroke.flickDurationMs));

        try {
            GestureDescription.StrokeDescription ballFlickStroke =
                    prevBallStroke.continueStroke(flickPath, 0L, flickDurMs, false);
            GestureDescription g2 = new GestureDescription.Builder().addStroke(ballFlickStroke).build();

            boolean ok2 = dispatchGesture(g2, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    if (!isThrowRunning) return;
                    isThrowRunning = false;
                    throwHandler.removeCallbacksAndMessages(null);
                    notifyStatus("✓ Throw Complete • Rate below to train AI");
                    if (FloatingViewService.instance != null) {
                        FloatingViewService.instance.onThrowSequenceFinished(showPostThrowRatePopup);
                    }
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    if (!isThrowRunning) return;
                    isThrowRunning = false;
                    throwHandler.removeCallbacksAndMessages(null);
                    notifyStatus("■ Throw Cancelled • Ready");
                    if (FloatingViewService.instance != null) {
                        FloatingViewService.instance.onThrowSequenceFinished(false);
                    }
                }
            }, null);

            if (ok2) {
                throwHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isThrowRunning) {
                            isThrowRunning = false;
                            notifyStatus("✓ Throw Complete • Rate below to train AI");
                            if (FloatingViewService.instance != null) {
                                FloatingViewService.instance.onThrowSequenceFinished(showPostThrowRatePopup);
                            }
                        }
                    }
                }, flickDurMs + 180L);
                return;
            }
        } catch (Throwable ignored) {}

        dispatchStandalonePhase2Fallback(w, h, stroke, false, showPostThrowRatePopup);
    }

    private void dispatchStandalonePhase2Fallback(
            final int w,
            final int h,
            final LearnedProfileStore.CalibratedStroke stroke,
            final boolean useQuickCatch,
            final boolean showPostThrowRatePopup
    ) {
        Path fallbackPath = buildClampedPathFromPoints(stroke.points, w, h);
        long minSafe = stroke.isSpinThrow ? 350L : 225L;
        long safeDur = Math.max(minSafe, Math.min(480L, stroke.durationMs));
        dispatchThrowPathWithOptionalQuickCatch(w, h, fallbackPath, safeDur, useQuickCatch, showPostThrowRatePopup);
    }

    private Path buildClampedPathFromPoints(List<LearnedProfileStore.PointSample> pts, int w, int h) {
        Path p = new Path();
        if (pts == null || pts.isEmpty()) {
            p.moveTo(w * 0.50f, h * 0.865f);
            p.lineTo(w * 0.50f, h * 0.45f);
            return p;
        }
        float firstX = clampX(pts.get(0).x, w);
        float firstY = clampY(pts.get(0).y, h);
        p.moveTo(firstX, firstY);
        for (int i = 1; i < pts.size(); i++) {
            float prevX = clampX(pts.get(i - 1).x, w);
            float prevY = clampY(pts.get(i - 1).y, h);
            float currX = clampX(pts.get(i).x, w);
            float currY = clampY(pts.get(i).y, h);
            p.quadTo(prevX, prevY, (prevX + currX) * 0.5f, (prevY + currY) * 0.5f);
        }
        LearnedProfileStore.PointSample last = pts.get(pts.size() - 1);
        p.lineTo(clampX(last.x, w), clampY(last.y, h));
        return p;
    }

    private float clampX(float x, int w) {
        return Math.max(8f, Math.min(w - 8f, x));
    }

    private float clampY(float y, int h) {
        return Math.max(16f, Math.min(h - 12f, y));
    }

    /**
     * SMART QUICK-CATCH BALL-HIT DETECTOR & ENCOUNTER EXIT:
     * - NEVER rushes or exits while the PokéBall is still flying through the air!
     * - Waits for the realistic ball flight time after finger release (1080ms Close / 1240ms Med / 1420ms Far),
     *   then checks on-device vision (Android 11+) to confirm the Target Ring is gone and the ball has hit
     *   the Pokémon before dismissing the Berry tray and tapping the Top-Left Run button!
     */
    private void waitForBallHitThenQuickCatchExit(
            final int w,
            final int h,
            final long gestureEndOffsetMs,
            final boolean showPostThrowRatePopup
    ) {
        int bucket = LearnedProfileStore.getActiveDistanceProfile(this);
        // Realistic flight time in Pokémon GO from ball release (ACTION_UP) until the ball physically hits the Pokémon:
        final long flightTimeAfterReleaseMs = (bucket == 0) ? 1080L : (bucket == 2) ? 1420L : 1240L;
        final long totalWaitUntilImpactCheckMs = gestureEndOffsetMs + flightTimeAfterReleaseMs;

        throwHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isThrowRunning) return;
                notifyStatus("⚡ Ball in Flight • Waiting for Pokémon Hit...");
            }
        }, gestureEndOffsetMs + 60L);

        throwHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isThrowRunning) return;

                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    try {
                        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                            @Override
                            public void onSuccess(ScreenshotResult screenshot) {
                                if (!isThrowRunning) return;
                                boolean stillRingVisible = false;
                                try {
                                    HardwareBuffer hb = screenshot.getHardwareBuffer();
                                    ColorSpace cs = screenshot.getColorSpace();
                                    Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                                    if (hwBmp != null) {
                                        Bitmap softBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                                        hwBmp.recycle();
                                        if (softBmp != null) {
                                            TargetRingState ring = detectLiveTargetRingOnScreen(softBmp, w, h);
                                            stillRingVisible = (ring != null && ring.isCircleUp);
                                            softBmp.recycle();
                                        }
                                    }
                                    hb.close();
                                } catch (Exception ignored) {}

                                if (stillRingVisible) {
                                    // Ball is still in the air approaching the Pokémon; wait an extra 380ms for impact!
                                    notifyStatus("⏳ Waiting for Ball Impact (+380ms)...");
                                    throwHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            executeQuickCatchExitTapsNow(w, h, showPostThrowRatePopup);
                                        }
                                    }, 380L);
                                } else {
                                    // Target ring is gone -> Ball has hit the Pokémon! Exit encounter now!
                                    executeQuickCatchExitTapsNow(w, h, showPostThrowRatePopup);
                                }
                            }

                            @Override
                            public void onFailure(int errorCode) {
                                executeQuickCatchExitTapsNow(w, h, showPostThrowRatePopup);
                            }
                        });
                        return;
                    } catch (Throwable ignored) {}
                }

                executeQuickCatchExitTapsNow(w, h, showPostThrowRatePopup);
            }
        }, totalWaitUntilImpactCheckMs);
    }

    private void executeQuickCatchExitTapsNow(
            final int w,
            final int h,
            final boolean showPostThrowRatePopup
    ) {
        if (!isThrowRunning) return;
        notifyStatus("⚡ Ball Hit! Dismissing Berry Tray...");
        dispatchHumanTap(w * 0.50f, h * 0.46f, 4f);

        throwHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isThrowRunning) return;
                notifyStatus("⚡ Quick Catch: Tapping Top-Left Run...");
                // Tap Top-Left Run / Flee button ONCE to exit encounter cleanly
                dispatchHumanTap(w * 0.105f, h * 0.082f, 3f);

                throwHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isThrowRunning) return;
                        isThrowRunning = false;
                        throwHandler.removeCallbacksAndMessages(null);
                        notifyStatus("✓ Quick Catch Complete • Rate throw below");
                        if (FloatingViewService.instance != null) {
                            FloatingViewService.instance.onThrowSequenceFinished(showPostThrowRatePopup);
                        }
                    }
                }, 260L);
            }
        }, pace(260L));
    }

    /**
     * ON-DEVICE RADIAL BAND-PASS TARGET RING DETECTOR (<6ms):
     * Finds the concentric Inner Shrinking Colored Ring (Green/Yellow/Orange/Red) and Outer White Ring
     * while ignoring solid-colored Pokémon bodies (using radial band-pass contrast: peak(r) - 0.5*(bg(r-d)+bg(r+d))).
     */
    private TargetRingState detectLiveTargetRingOnScreen(Bitmap bmp, int w, int h) {
        try {
            int bw = bmp.getWidth();
            int bh = bmp.getHeight();
            if (bw < 200 || bh < 300) return null;

            float refX = LearnedProfileStore.getReferenceRingX(this, bw * 0.50f);
            float refY = LearnedProfileStore.getReferenceRingY(this, bh * 0.40f);
            float refRad = LearnedProfileStore.getReferenceRingRadius(this, bw * 0.175f);

            // Scan 8 diagonal radial spokes (avoiding horizontal/vertical text & UI bars)
            final float[] cosA = new float[]{0.906f, 0.423f, -0.423f, -0.906f, -0.906f, -0.423f, 0.423f, 0.906f};
            final float[] sinA = new float[]{-0.423f, -0.906f, -0.906f, -0.423f, 0.423f, 0.906f, 0.906f, 0.423f};

            int minCx = Math.max(40, (int) (bw * 0.36f));
            int maxCx = Math.min(bw - 40, (int) (bw * 0.64f));
            int minCy = Math.max(60, (int) (bh * 0.22f));
            int maxCy = Math.min(bh - 100, (int) (bh * 0.54f));
            int stepC = Math.max(10, bw / 72);

            int minInnerR = Math.max(18, (int) (bw * 0.028f));
            int maxInnerR = Math.min(bw / 3, (int) (bw * 0.23f));
            int stepR = Math.max(4, bw / 180);
            int bandDelta = Math.max(7, bw / 110);

            int bestScore = -1;
            int bestCx = (int) refX;
            int bestCy = (int) refY;
            int bestInnerR = (int) (refRad * 0.45f);

            for (int cy = minCy; cy <= maxCy; cy += stepC) {
                for (int cx = minCx; cx <= maxCx; cx += stepC) {
                    for (int r = minInnerR; r <= maxInnerR; r += stepR) {
                        int matchingSpokes = 0;
                        int ringContrastSum = 0;
                        for (int s = 0; s < 8; s++) {
                            int px = cx + Math.round(r * cosA[s]);
                            int py = cy + Math.round(r * sinA[s]);
                            if (px < 12 || px >= bw - 12 || py < 12 || py >= bh - 12) continue;

                            int cPeak = coloredRingPixelScore(bmp.getPixel(px, py));
                            if (cPeak < 95) continue;

                            int inX = cx + Math.round((r - bandDelta) * cosA[s]);
                            int inY = cy + Math.round((r - bandDelta) * sinA[s]);
                            int outX = cx + Math.round((r + bandDelta) * cosA[s]);
                            int outY = cy + Math.round((r + bandDelta) * sinA[s]);
                            int cIn = (inX >= 4 && inX < bw - 4 && inY >= 4 && inY < bh - 4)
                                    ? coloredRingPixelScore(bmp.getPixel(inX, inY)) : 0;
                            int cOut = (outX >= 4 && outX < bw - 4 && outY >= 4 && outY < bh - 4)
                                    ? coloredRingPixelScore(bmp.getPixel(outX, outY)) : 0;

                            int contrast = cPeak - ((cIn + cOut) / 2);
                            if (contrast >= 55) {
                                matchingSpokes++;
                                ringContrastSum += contrast;
                            }
                        }
                        if (matchingSpokes >= 5) {
                            // Favor rings near horizontal center
                            int centerPenalty = Math.abs(cx - (bw / 2)) / 3;
                            int totalScore = ringContrastSum + matchingSpokes * 40 - centerPenalty;
                            if (totalScore > bestScore) {
                                bestScore = totalScore;
                                bestCx = cx;
                                bestCy = cy;
                                bestInnerR = r;
                            }
                        }
                    }
                }
            }

            if (bestScore < 320) {
                return null;
            }

            // Search outward from (bestCx, bestCy) for the concentric Outer White Ring radius
            int bestOuterR = Math.max((int) (bestInnerR * 1.08f), (int) refRad);
            int bestWhiteScore = -1;
            int minOuterR = Math.max(bestInnerR + 4, (int) (bw * 0.105f));
            int maxOuterR = Math.min(bw / 3, (int) (bw * 0.265f));
            for (int rOut = minOuterR; rOut <= maxOuterR; rOut += 4) {
                int whiteSpokes = 0;
                int whiteContrastSum = 0;
                for (int s = 0; s < 8; s++) {
                    int px = bestCx + Math.round(rOut * cosA[s]);
                    int py = bestCy + Math.round(rOut * sinA[s]);
                    if (px < 12 || px >= bw - 12 || py < 12 || py >= bh - 12) continue;
                    int wPeak = whiteRingPixelScore(bmp.getPixel(px, py));
                    if (wPeak < 195) continue;

                    int inX = bestCx + Math.round((rOut - bandDelta) * cosA[s]);
                    int inY = bestCy + Math.round((rOut - bandDelta) * sinA[s]);
                    int outX = bestCx + Math.round((rOut + bandDelta) * cosA[s]);
                    int outY = bestCy + Math.round((rOut + bandDelta) * sinA[s]);
                    int wIn = (inX >= 4 && inX < bw - 4 && inY >= 4 && inY < bh - 4)
                            ? whiteRingPixelScore(bmp.getPixel(inX, inY)) : 0;
                    int wOut = (outX >= 4 && outX < bw - 4 && outY >= 4 && outY < bh - 4)
                            ? whiteRingPixelScore(bmp.getPixel(outX, outY)) : 0;

                    int wContrast = wPeak - ((wIn + wOut) / 2);
                    if (wContrast >= 32) {
                        whiteSpokes++;
                        whiteContrastSum += wContrast;
                    }
                }
                if (whiteSpokes >= 4 && whiteContrastSum > bestWhiteScore) {
                    bestWhiteScore = whiteContrastSum;
                    bestOuterR = rOut;
                }
            }

            if (bestOuterR < bestInnerR) {
                bestOuterR = bestInnerR;
            }
            float shrinkRatio = Math.max(0.15f, Math.min(1.00f, (float) bestInnerR / (float) Math.max(1, bestOuterR)));
            return new TargetRingState(true, bestCx, bestCy, bestInnerR, bestOuterR, shrinkRatio);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int coloredRingPixelScore(int px) {
        int r = Color.red(px);
        int g = Color.green(px);
        int b = Color.blue(px);
        int maxRG = Math.max(r, g);
        // Pokémon GO inner target ring is vibrant neon Green, Yellow, Orange, or Red (high R/G, low B)
        if (maxRG < 185 || b > 130 || (maxRG - b) < 88) {
            return 0;
        }
        return maxRG - b;
    }

    private int whiteRingPixelScore(int px) {
        int r = Color.red(px);
        int g = Color.green(px);
        int b = Color.blue(px);
        int minC = Math.min(r, Math.min(g, b));
        int maxC = Math.max(r, Math.max(g, b));
        if (minC < 195 || (maxC - minC) > 36) {
            return 0;
        }
        return minC;
    }

    private void dispatchThrowPathWithOptionalQuickCatch(
            final int w,
            final int h,
            Path throwPath,
            final long safeThrowDuration,
            boolean useQuickCatch,
            final boolean showPostThrowRatePopup
    ) {
        if (!useQuickCatch) {
            GestureDescription.StrokeDescription throwStroke =
                    new GestureDescription.StrokeDescription(throwPath, 0L, safeThrowDuration);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(throwStroke).build();
            dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    if (!isThrowRunning) return;
                    isThrowRunning = false;
                    throwHandler.removeCallbacksAndMessages(null);
                    notifyStatus("✓ Throw Complete • Rate below to train AI");
                    if (FloatingViewService.instance != null) {
                        FloatingViewService.instance.onThrowSequenceFinished(showPostThrowRatePopup);
                    }
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    if (!isThrowRunning) return;
                    isThrowRunning = false;
                    throwHandler.removeCallbacksAndMessages(null);
                    notifyStatus("■ Throw Cancelled • Ready");
                    if (FloatingViewService.instance != null) {
                        FloatingViewService.instance.onThrowSequenceFinished(false);
                    }
                }
            }, null);

            throwHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isThrowRunning) {
                        isThrowRunning = false;
                        notifyStatus("✓ Throw Complete • Rate below to train AI");
                        if (FloatingViewService.instance != null) {
                            FloatingViewService.instance.onThrowSequenceFinished(showPostThrowRatePopup);
                        }
                    }
                }
            }, safeThrowDuration + 160L);
            return;
        }

        // 2-FINGER FAST CATCH (Quick Catch):
        // Finger 1 touches the Berry icon first at t = 0ms and slides gently right (staying <= 25% W so it
        // never comes near the PokéBall spin loop). Finger 2 grabs the PokéBall at t = 55ms and executes the
        // full un-rushed throw. Then we wait for the ball to physically hit the Pokémon before exiting!
        float[] berryPt = LearnedProfileStore.getBerryButtonPoint(this, w * 0.135f, h * 0.885f);
        float berryStartX = clampX(berryPt[0], w);
        float berryStartY = clampY(berryPt[1], h);
        float berryPullX = clampX(Math.min(w * 0.25f, berryStartX + w * 0.095f), w);

        Path berryHoldPath = new Path();
        berryHoldPath.moveTo(berryStartX, berryStartY);
        berryHoldPath.lineTo(berryPullX, berryStartY);

        long throwStartOffsetMs = 55L;
        long berryHoldTotalMs = throwStartOffsetMs + safeThrowDuration + 150L;
        GestureDescription.StrokeDescription berryStroke =
                new GestureDescription.StrokeDescription(berryHoldPath, 0L, berryHoldTotalMs);
        GestureDescription.StrokeDescription throwStroke =
                new GestureDescription.StrokeDescription(throwPath, throwStartOffsetMs, safeThrowDuration);

        GestureDescription multiGesture = new GestureDescription.Builder()
                .addStroke(berryStroke)
                .addStroke(throwStroke)
                .build();
        dispatchGesture(multiGesture, null, null);

        waitForBallHitThenQuickCatchExit(w, h, throwStartOffsetMs + safeThrowDuration, showPostThrowRatePopup);
    }

    // =========================================================================
    // ENGINE 2: SMART ON-DEVICE VISION AUTO-GIFT STATE MACHINE
    // - Visually distinguishes Incoming Gift Postcard vs Trainer Profile vs Gift Bag vs Send Preview
    // - Visually locates 📌 Pin, 🎁 OPEN, left 🎁 SEND GIFT, and green 🚀 SEND buttons on screen
    // - Takes its time Pinning -> Unpinning -> Claiming Incoming Gifts
    // - Checks if Left 'Send Gift' button is active (colorful) vs grayed out (never hits Trade!)
    // =========================================================================

    private static final class GiftScreenVisionResult {
        final boolean isIncomingGiftScreen;
        final boolean isTrainerProfileWhiteCardVisible;
        final boolean isFriendsListVisible;
        final boolean isSendGiftActive;
        final float pinX;
        final float pinY;
        final float openX;
        final float openY;
        final float sendGiftX;
        final float sendGiftY;

        GiftScreenVisionResult(
                boolean isIncomingGiftScreen,
                boolean isTrainerProfileWhiteCardVisible,
                boolean isFriendsListVisible,
                boolean isSendGiftActive,
                float pinX,
                float pinY,
                float openX,
                float openY,
                float sendGiftX,
                float sendGiftY
        ) {
            this.isIncomingGiftScreen = isIncomingGiftScreen;
            this.isTrainerProfileWhiteCardVisible = isTrainerProfileWhiteCardVisible;
            this.isFriendsListVisible = isFriendsListVisible;
            this.isSendGiftActive = isSendGiftActive;
            this.pinX = pinX;
            this.pinY = pinY;
            this.openX = openX;
            this.openY = openY;
            this.sendGiftX = sendGiftX;
            this.sendGiftY = sendGiftY;
        }
    }

    public void startActiveGiftLoop() {
        isAutoGiftRunning = true;
        // Always start from the 1st Friend at the TOP of the Friends List
        currentRowSlot = 0;
        int learnedSessions = LearnedProfileStore.getLearnedGiftCount(this);
        notifyStatus(learnedSessions > 0
                ? ("▶ Smart Auto-Gift: Starting at Top Row #1 (Trained ×" + learnedSessions + ")...")
                : "▶ Smart Auto-Gift: Starting at Top Row #1...");
        giftHandler.removeCallbacksAndMessages(null);
        scheduleOpenNextFriend(500L);
    }

    public void stopActiveGiftLoop() {
        isAutoGiftRunning = false;
        giftHandler.removeCallbacksAndMessages(null);
        notifyStatus("Stopped • Idle");
    }

    private static final int VISIBLE_FRIENDS_PER_SCREEN = 4;

    private static final class FriendRowAtAGlance {
        final boolean isValidFriendRow;
        final boolean hasIncomingGift;
        final boolean hasAlreadySentGrayArrow;
        final float tapX;
        final float tapY;

        FriendRowAtAGlance(
                boolean isValidFriendRow,
                boolean hasIncomingGift,
                boolean hasAlreadySentGrayArrow,
                float tapX,
                float tapY
        ) {
            this.isValidFriendRow = isValidFriendRow;
            this.hasIncomingGift = hasIncomingGift;
            this.hasAlreadySentGrayArrow = hasAlreadySentGrayArrow;
            this.tapX = tapX;
            this.tapY = tapY;
        }
    }

    /**
     * AT-A-GLANCE FRIENDS LIST INSPECTOR:
     * Before tapping a row, looks at the Friends List screen to see for each visible friend row (0..3):
     * 1. Did they send us a gift ('*** sent you a Gift' / colorful gift box icon)? -> Open to Pin -> Unpin -> Collect -> Check Send Gift!
     * 2. Do they have the Gray Circle with an Arrow Pointing Right (➔ = already sent them a gift) and no incoming gift?
     *    -> Skip them immediately at a glance without even tapping their profile!
     * 3. No incoming gift AND no gray arrow (➔)? -> We haven't sent them a gift yet! Open -> Check Send Gift -> Send Gift!
     */
    private void scheduleOpenNextFriend(long delayMs) {
        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                final int[] dim = getTrueScreenDimensions();
                final int w = dim[0];
                final int h = dim[1];

                if (currentRowSlot >= VISIBLE_FRIENDS_PER_SCREEN) {
                    scrollFriendsListAfterAllOnScreenDone(w, h);
                    return;
                }

                if (android.os.Build.VERSION.SDK_INT < 30) {
                    tapFriendRowSlotDirectly(w, h, currentRowSlot, "👤 Opening Friend " + (currentRowSlot + 1) + "/4...");
                    return;
                }

                notifyStatus("👁 Checking Friends List at a Glance (Row " + (currentRowSlot + 1) + "/4)...");
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult screenshot) {
                        if (!isAutoGiftRunning) return;
                        Bitmap softBmp = null;
                        try {
                            HardwareBuffer hb = screenshot.getHardwareBuffer();
                            ColorSpace cs = screenshot.getColorSpace();
                            Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                            if (hwBmp != null) {
                                softBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                                hwBmp.recycle();
                            }
                            hb.close();
                        } catch (Exception ignored) {}

                        if (softBmp == null) {
                            tapFriendRowSlotDirectly(w, h, currentRowSlot, "👤 Opening Friend " + (currentRowSlot + 1) + "/4...");
                            return;
                        }

                        final int bw = softBmp.getWidth();
                        final int bh = softBmp.getHeight();

                        // First verify we are actually on the Friends List (if a Trainer Profile or modal is still open, close it!)
                        GiftScreenVisionResult currentScreenCheck = analyzeGiftScreenOnDevice(softBmp, bw, bh);
                        if (currentScreenCheck.isIncomingGiftScreen || (currentScreenCheck.isTrainerProfileWhiteCardVisible && !currentScreenCheck.isFriendsListVisible)) {
                            softBmp.recycle();
                            float[] learnedClose = LearnedProfileStore.getGiftStepPoint(PokemonGoAccessibilityService.this, 6, bw * 0.50f, bh * 0.905f);
                            notifyStatus("✕ Returning to Friends List first...");
                            dispatchHumanTap(learnedClose[0], learnedClose[1], 4f);
                            scheduleOpenNextFriend(pace(1500L));
                            return;
                        }

                        // Scan visible friend rows starting at currentRowSlot (0..3) at a glance!
                        int chosenSlot = -1;
                        FriendRowAtAGlance chosenRowInfo = null;

                        while (currentRowSlot < VISIBLE_FRIENDS_PER_SCREEN) {
                            FriendRowAtAGlance rowGlance = analyzeFriendRowOnFriendsList(softBmp, bw, bh, currentRowSlot);

                            // Case A: Friend sent us a gift ("*** sent you a Gift!")
                            if (rowGlance.hasIncomingGift && giftWorkflowMode != MODE_SEND_GIFT_ONLY) {
                                chosenSlot = currentRowSlot;
                                chosenRowInfo = rowGlance;
                                break;
                            }

                            // Case B: Friend has the Gray Circle with an Arrow Pointing Right (➔ = Already Sent Gift!)
                            // and no incoming gift to collect -> Skip immediately at a glance!
                            if (rowGlance.hasAlreadySentGrayArrow && !rowGlance.hasIncomingGift) {
                                notifyStatus("⏭ Row #" + (currentRowSlot + 1) + ": Gray Arrow (➔ Already Sent) • Skipping!");
                                friendsProcessed++;
                                currentRowSlot++;
                                continue;
                            }

                            // Case C: If mode is ONLY collecting incoming gifts, and this row has no incoming gift, skip at a glance!
                            if (giftWorkflowMode == MODE_PIN_AND_OPEN_ONLY && !rowGlance.hasIncomingGift) {
                                notifyStatus("⏭ Row #" + (currentRowSlot + 1) + ": No Incoming Gift • Skipping!");
                                friendsProcessed++;
                                currentRowSlot++;
                                continue;
                            }

                            // Case D: Friend did NOT send us a gift, AND does NOT have the gray ➔ arrow -> We CAN send them a gift!
                            chosenSlot = currentRowSlot;
                            chosenRowInfo = rowGlance;
                            break;
                        }

                        softBmp.recycle();

                        // If all remaining visible rows on this screen already had the gray ➔ arrow, scroll down immediately!
                        if (chosenSlot < 0 || chosenRowInfo == null || currentRowSlot >= VISIBLE_FRIENDS_PER_SCREEN) {
                            scrollFriendsListAfterAllOnScreenDone(bw, bh);
                            return;
                        }

                        String glanceReason = chosenRowInfo.hasIncomingGift
                                ? ("🎁 Row #" + (chosenSlot + 1) + " Sent You a Gift! Opening to Pin & Collect...")
                                : ("💌 Row #" + (chosenSlot + 1) + " Can Receive Gift (No ➔ Arrow) • Opening...");
                        notifyStatus(glanceReason);
                        dispatchHumanTap(chosenRowInfo.tapX, chosenRowInfo.tapY, 5f);

                        // Wait 2550ms for the Trainer Profile or Incoming Gift Postcard to open, then inspect the screen!
                        giftHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAutoGiftRunning) return;
                                inspectFriendScreenFastOnDevice(false, 0);
                            }
                        }, pace(2550L));
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        if (!isAutoGiftRunning) return;
                        tapFriendRowSlotDirectly(w, h, currentRowSlot, "👤 Opening Friend " + (currentRowSlot + 1) + "/4...");
                    }
                });
            }
        }, delayMs);
    }

    private void tapFriendRowSlotDirectly(int w, int h, int slotIndex, String statusMsg) {
        float[] learnedRow = LearnedProfileStore.getGiftStepPoint(this, 0, w * 0.38f, h * 0.275f);
        int slotInPage = Math.max(0, Math.min(VISIBLE_FRIENDS_PER_SCREEN - 1, slotIndex));
        float baseRowX = Math.max(w * 0.26f, Math.min(w * 0.52f, learnedRow[0]));
        float topRowY = Math.max(h * 0.23f, Math.min(h * 0.32f, learnedRow[1]));
        float baseRowY = topRowY + slotInPage * (h * 0.142f);
        if (baseRowY > h * 0.76f) {
            baseRowY = h * (0.275f + slotInPage * 0.142f);
        }
        notifyStatus(statusMsg);
        dispatchHumanTap(baseRowX, baseRowY, 5f);
        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                inspectFriendScreenFastOnDevice(false, 0);
            }
        }, pace(2550L));
    }

    /**
     * Inspects a single Friend Row (`slotIndex = 0..3`) on the Friends List screenshot to detect at a glance:
     * 1. `hasIncomingGift`: Colorful Pink/Magenta/Yellow Gift Box icon & "*** sent you a Gift!" text on the row.
     * 2. `hasAlreadySentGrayArrow`: The Gray Circle with an Arrow Pointing Right (➔) indicating we already sent them a gift.
     */
    private FriendRowAtAGlance analyzeFriendRowOnFriendsList(Bitmap bmp, int w, int h, int slotIndex) {
        float[] learnedRow = LearnedProfileStore.getGiftStepPoint(this, 0, w * 0.38f, h * 0.275f);
        float baseRowX = Math.max(w * 0.28f, Math.min(w * 0.52f, learnedRow[0]));
        float topRowY = Math.max(h * 0.23f, Math.min(h * 0.31f, learnedRow[1]));
        float rowCenterY = topRowY + slotIndex * (h * 0.142f);
        if (rowCenterY > h * 0.76f) {
            rowCenterY = h * (0.275f + slotIndex * 0.142f);
        }

        int yTop = Math.max(8, (int) (rowCenterY - h * 0.052f));
        int yBot = Math.min(h - 8, (int) (rowCenterY + h * 0.052f));
        int ySubtextTop = Math.max(8, (int) (rowCenterY + h * 0.004f));
        int ySubtextBot = Math.min(h - 8, (int) (rowCenterY + h * 0.054f));

        // 1. Check if there is a white Friend Card at this row Y
        int whiteCardSamples = 0;
        int totalCardSamples = 0;
        for (int y = yTop; y <= yBot; y += 6) {
            for (int x = (int) (w * 0.45f); x <= (int) (w * 0.72f); x += 14) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                totalCardSamples++;
                if (r > 225 && g > 225 && b > 222) {
                    whiteCardSamples++;
                }
            }
        }
        boolean isValidRow = totalCardSamples > 0 && ((float) whiteCardSamples / totalCardSamples) >= 0.40f;

        // 2. Detect Incoming Gift ("*** sent you a Gift!") on this row:
        //    In Pokémon GO, when a friend sent you a gift, a colorful Pink/Magenta & Yellow Gift Box icon
        //    appears in the status line under the hearts (x = 0.25w..0.43w, y = rowCenterY..rowCenterY+0.054h)
        //    and/or on the right side of the row (x = 0.72w..0.93w, y = yTop..yBot).
        //    Note: Friendship hearts (orange-red) are in the UPPER half of the row (y < rowCenterY - 0.004h),
        //    so scanning ySubtextTop..ySubtextBot (lower half) and x = 0.72w..0.93w (right side) never hits hearts!
        int giftColorPixels = 0;
        for (int y = ySubtextTop; y <= ySubtextBot; y += 3) {
            for (int x = (int) (w * 0.25f); x <= (int) (w * 0.43f); x += 3) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                boolean isMagentaPink = (r >= 165 && b >= 110 && (r - g) >= 35 && sat >= 42);
                boolean isGiftGold = (r >= 205 && g >= 155 && b <= 105 && (r - b) >= 100);
                boolean isRibbonTeal = (g >= 150 && b >= 140 && (g - r) >= 30 && sat >= 40);
                if (isMagentaPink || isGiftGold || isRibbonTeal) {
                    giftColorPixels++;
                }
            }
        }
        for (int y = yTop; y <= yBot; y += 3) {
            for (int x = (int) (w * 0.72f); x <= (int) (w * 0.93f); x += 3) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                boolean isMagentaPink = (r >= 165 && b >= 110 && (r - g) >= 35 && sat >= 42);
                boolean isGiftGold = (r >= 205 && g >= 155 && b <= 105 && (r - b) >= 100);
                boolean isRibbonTeal = (g >= 150 && b >= 140 && (g - r) >= 30 && sat >= 40);
                if (isMagentaPink || isGiftGold || isRibbonTeal) {
                    giftColorPixels++;
                }
            }
        }
        boolean hasIncomingGift = giftColorPixels >= 8;

        // 3. Detect the Gray Circle with an Arrow Pointing Right (➔ = Already Sent Gift Today!)
        //    In Pokémon GO, on the right side of the friend row (x = 0.75w..0.92w, y = rowCenterY - 0.040h .. rowCenterY + 0.040h):
        //    - If NO gift has been sent (and no incoming gift icon is there), this area is flat pure white card (lum >= 242, sat <= 14).
        //    - If a gift WAS already sent, there is a filled neutral Gray Circle (lum = 165..234, sat <= 22)
        //      containing a bright white right-pointing arrow (➔) inside it!
        int grayCirclePixels = 0;
        int pureWhiteBgPixels = 0;
        int rightZoneSamples = 0;
        int grayToWhiteArrowTransitions = 0;

        int rx0 = (int) (w * 0.75f);
        int rx1 = (int) (w * 0.92f);
        int ry0 = Math.max(8, (int) (rowCenterY - h * 0.038f));
        int ry1 = Math.min(h - 8, (int) (rowCenterY + h * 0.038f));

        for (int y = ry0; y <= ry1; y += 3) {
            boolean seenGrayInRow = false;
            boolean seenWhiteInsideGray = false;
            for (int x = rx0; x <= rx1; x += 3) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int lum = (r + g + b) / 3;
                int sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                rightZoneSamples++;

                if (lum >= 162 && lum <= 235 && sat <= 22) {
                    grayCirclePixels++;
                    if (seenWhiteInsideGray) {
                        // Gray -> White Arrow -> Gray transition on the same horizontal scanline!
                        grayToWhiteArrowTransitions++;
                    }
                    seenGrayInRow = true;
                } else if (lum >= 240 && sat <= 16) {
                    pureWhiteBgPixels++;
                    if (seenGrayInRow) {
                        seenWhiteInsideGray = true;
                    }
                }
            }
        }

        float grayCircleRatio = rightZoneSamples > 0 ? ((float) grayCirclePixels / rightZoneSamples) : 0f;
        boolean hasAlreadySentGrayArrow = !hasIncomingGift
                && grayCirclePixels >= 10
                && grayCircleRatio >= 0.065f
                && grayCircleRatio <= 0.68f
                && grayToWhiteArrowTransitions >= 2;

        return new FriendRowAtAGlance(isValidRow, hasIncomingGift, hasAlreadySentGrayArrow, baseRowX, rowCenterY);
    }

    /**
     * SMART ON-DEVICE VISION SCREEN INSPECTOR:
     * Looks at the live screen after opening a friend and executes the exact smart sequence:
     * 1. Incoming Gift Postcard on screen -> Pin (📌) -> Wait -> Unpin (📌) -> Wait -> Collect Gift ('OPEN')
     *    -> Wait for Trainer Profile -> Check screen again to see if we can ALSO send a gift!
     * 2. Trainer Profile with ACTIVE (colorful) Left 'Send Gift' button -> Send Gift -> ✕ -> Check Friends List again!
     * 3. Trainer Profile with GRAYED OUT Left 'Send Gift' button -> Tap ✕ -> Check Friends List again!
     */
    private void inspectFriendScreenFastOnDevice(
            final boolean alreadyHandledIncomingGift,
            final int settleRetryCount
    ) {
        if (!isAutoGiftRunning) return;
        notifyStatus("👁 Looking at Screen (Checking Gift & Send Status)...");

        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                if (!isAutoGiftRunning) return;
                Bitmap softBmp = null;
                try {
                    HardwareBuffer hb = screenshot.getHardwareBuffer();
                    ColorSpace cs = screenshot.getColorSpace();
                    Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                    if (hwBmp != null) {
                        softBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                        hwBmp.recycle();
                    }
                    hb.close();
                } catch (Exception ignored) {}

                int[] dim = getTrueScreenDimensions();
                final int w = softBmp != null ? softBmp.getWidth() : dim[0];
                final int h = softBmp != null ? softBmp.getHeight() : dim[1];

                float[] fallbackSend = LearnedProfileStore.getGiftStepPoint(
                        PokemonGoAccessibilityService.this, 3, w * 0.185f, h * 0.74f
                );

                if (softBmp == null) {
                    executeVisionGuidedSendGiftSequence(w, h, fallbackSend[0], fallbackSend[1], 0);
                    return;
                }

                final GiftScreenVisionResult vision = analyzeGiftScreenOnDevice(softBmp, w, h);
                softBmp.recycle();

                // CASE 1: INCOMING GIFT POSTCARD IS ON SCREEN!
                if (vision.isIncomingGiftScreen && !alreadyHandledIncomingGift) {
                    if (giftWorkflowMode == MODE_SEND_GIFT_ONLY) {
                        notifyStatus("⏭ Dismissing Incoming Gift -> Opening Profile...");
                        dispatchHumanTap(w * 0.50f, h * 0.90f, 4f);
                        giftHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAutoGiftRunning) return;
                                inspectFriendScreenFastOnDevice(true, 0);
                            }
                        }, pace(1650L));
                        return;
                    }

                    // Take our time to Pin -> Unpin -> Collect the Incoming Gift using the visually located buttons!
                    final float pinX = vision.pinX;
                    final float pinY = vision.pinY;
                    final float openX = vision.openX;
                    final float openY = vision.openY;

                    notifyStatus("👁 Gift Sent to You! 📌 Pinning (" + Math.round(pinX) + "," + Math.round(pinY) + ")...");
                    dispatchHumanTap(pinX, pinY, 3f);

                    // Wait a patient 1400ms so Pokémon GO registers the Postcard Pin for Scatterbug/Vivillon!
                    giftHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAutoGiftRunning) return;
                            notifyStatus("📌 Unpinning Postcard (" + Math.round(pinX) + "," + Math.round(pinY) + ")...");
                            dispatchHumanTap(pinX, pinY, 3f);

                            // Wait 1250ms after Unpinning before tapping 'OPEN' to collect the gift
                            giftHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAutoGiftRunning) return;
                                    notifyStatus("🎁 Collecting Gift 'OPEN' (" + Math.round(openX) + "," + Math.round(openY) + ")...");
                                    dispatchHumanTap(openX, openY, 4f);

                                    // Wait 1800ms for gift box open animation, then tap twice gently to skip item bursts
                                    giftHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!isAutoGiftRunning) return;
                                            notifyStatus("⏩ Finishing Gift Collect Animation...");
                                            dispatchHumanTap(w * 0.50f, h * 0.84f, 5f);
                                            giftHandler.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!isAutoGiftRunning) return;
                                                    dispatchHumanTap(w * 0.50f, h * 0.84f, 5f);
                                                    // Wait 2250ms for the Trainer Profile screen to settle underneath
                                                    giftHandler.postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (!isAutoGiftRunning) return;
                                                            if (giftWorkflowMode == MODE_PIN_AND_OPEN_ONLY) {
                                                                giftsSent++;
                                                                friendsProcessed++;
                                                                closeProfileAndAdvance(w, h);
                                                            } else {
                                                                // Now check the Trainer Profile screen to see if we can ALSO send them a gift!
                                                                notifyStatus("👁 Gift Collected! Checking if We Can Send Gift...");
                                                                inspectFriendScreenFastOnDevice(true, 0);
                                                            }
                                                        }
                                                    }, pace(2250L));
                                                }
                                            }, pace(950L));
                                        }
                                    }, pace(1800L));
                                }
                            }, pace(1250L));
                        }
                    }, pace(1400L));
                    return;
                }

                // CASE 2: We already tried claiming the Incoming Gift, but it's STILL on the Incoming Gift screen (e.g. Item Bag Full!)
                if (vision.isIncomingGiftScreen && alreadyHandledIncomingGift) {
                    notifyStatus("🎒 Item Bag Full / Dismissing Unopened Gift...");
                    dispatchHumanTap(w * 0.50f, h * 0.90f, 4f);
                    giftHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAutoGiftRunning) return;
                            if (settleRetryCount < 2) {
                                inspectFriendScreenFastOnDevice(true, settleRetryCount + 1);
                            } else {
                                closeProfileAndAdvance(w, h);
                            }
                        }
                    }, pace(1650L));
                    return;
                }

                // CASE 3: If the screen is still transitioning (neither Incoming Gift nor Trainer Profile card settled yet),
                // wait 1050ms and look again rather than acting blindly!
                if (!vision.isTrainerProfileWhiteCardVisible && !vision.isSendGiftActive && settleRetryCount < 2) {
                    notifyStatus("⏳ Waiting for Friend Profile to finish loading...");
                    giftHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAutoGiftRunning) return;
                            inspectFriendScreenFastOnDevice(alreadyHandledIncomingGift, settleRetryCount + 1);
                        }
                    }, pace(1050L));
                    return;
                }

                // CASE 4: ON TRAINER PROFILE — Check if Left 'SEND GIFT' button is ACTIVE (colorful) or GRAYED OUT!
                if (!vision.isSendGiftActive) {
                    notifyStatus("👁 Send Gift is Grayed Out • Closing Profile (✕)!");
                    friendsProcessed++;
                    giftHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAutoGiftRunning) return;
                            closeProfileAndAdvance(w, h);
                        }
                    }, pace(550L));
                    return;
                }

                // CASE 5: ON TRAINER PROFILE & 'SEND GIFT' IS ACTIVE (NOT GRAYED OUT)!
                // Click the visually verified Left 'Send Gift' button (strictly in x = 0.12w..0.28w, NEVER Trade!)
                executeVisionGuidedSendGiftSequence(w, h, vision.sendGiftX, vision.sendGiftY, 0);
            }

            @Override
            public void onFailure(int errorCode) {
                int[] dim = getTrueScreenDimensions();
                float[] learnedSend = LearnedProfileStore.getGiftStepPoint(
                        PokemonGoAccessibilityService.this, 3, dim[0] * 0.185f, dim[1] * 0.74f
                );
                executeVisionGuidedSendGiftSequence(dim[0], dim[1], learnedSend[0], learnedSend[1], 0);
            }
        });
    }

    /**
     * SMART ON-DEVICE COMPUTER VISION ANALYZER (<12ms):
     * Examines the screenshot Bitmap to distinguish:
     * 1. Incoming Gift Postcard screen:
     *    - Wide horizontal Teal-Green 'OPEN' pill button spanning center-left to center-right (x = 0.36w..0.64w, y = 0.60h..0.76h)
     *    - Large rectangular White/Cream Postcard frame in center (y = 0.34h..0.54h)
     *    - Circular '📌 PIN' button to the right of 'OPEN' (x = 0.71w..0.89w)
     *    - Works whether the background glow is bright OR dark!
     * 2. Trainer Profile screen:
     *    - 3 separate circular buttons across the lower white card: Left = Send Gift (0.18w), Center = Battle (0.50w), Right = Trade (0.80w)
     *    - Checks ONLY the Left Send Gift button (x = 0.10w..0.30w, y = 0.57h..0.83h) to see if it is Active (colored) vs Grayed Out!
     */
    public GiftScreenVisionResult analyzeGiftScreenOnDevice(Bitmap bmp, int w, int h) {
        float[] learnedPin = LearnedProfileStore.getGiftStepPoint(this, 1, w * 0.785f, h * 0.69f);
        float[] learnedOpen = LearnedProfileStore.getGiftStepPoint(this, 2, w * 0.50f, h * 0.69f);
        float[] learnedSend = LearnedProfileStore.getGiftStepPoint(this, 3, w * 0.185f, h * 0.74f);

        // 1. Scan the center Incoming Gift 'OPEN' pill zone (x = 0.35w..0.65w, y = 0.60h..0.76h)
        // On an Incoming Gift screen, 'OPEN' is a wide horizontal Teal-Green pill button that spans
        // across BOTH the left half of the pill (0.37w..0.46w) AND the right half of the pill (0.54w..0.63w)!
        // On a Trainer Profile screen, the center 'BATTLE' button is a small circular icon at 0.50w with white card at 0.37w & 0.63w.
        int openTealCount = 0;
        int openLeftHalfTeal = 0;
        int openRightHalfTeal = 0;
        float sumOpenX = 0f;
        float sumOpenY = 0f;
        int ox0 = Math.max(8, (int) (w * 0.35f));
        int ox1 = Math.min(w - 8, (int) (w * 0.65f));
        int oy0 = Math.max(8, (int) (h * 0.60f));
        int oy1 = Math.min(h - 8, (int) (h * 0.76f));

        for (int y = oy0; y <= oy1; y += 4) {
            for (int x = ox0; x <= ox1; x += 5) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                // Pokémon GO Teal-Green / Lime-Teal pill button ('OPEN')
                if (g >= 150 && b >= 95 && r <= 180 && (g - r) >= 22) {
                    openTealCount++;
                    sumOpenX += x;
                    sumOpenY += y;
                    if (x <= w * 0.45f) openLeftHalfTeal++;
                    if (x >= w * 0.55f) openRightHalfTeal++;
                }
            }
        }
        boolean hasWideTealOpenPill = (openTealCount >= 18 && openLeftHalfTeal >= 4 && openRightHalfTeal >= 4);

        // Also check if the large Postcard white card is covering the middle of the screen (x = 0.22w..0.78w, y = 0.41h..0.53h)
        int postcardBannerBright = 0;
        int postcardBannerTotal = 0;
        for (int y = (int) (h * 0.41f); y <= (int) (h * 0.53f); y += 8) {
            for (int x = (int) (w * 0.22f); x <= (int) (w * 0.78f); x += 14) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                postcardBannerTotal++;
                if (r > 218 && g > 218 && b > 210 && sat < 28) {
                    postcardBannerBright++;
                }
            }
        }
        float postcardBannerRatio = postcardBannerTotal > 0 ? ((float) postcardBannerBright / postcardBannerTotal) : 0f;

        // Check if the Trainer Profile's full-width white card is visible across the lower half (y = 0.62h..0.82h)
        int whiteCardBgPixels = 0;
        int cardBgSamples = 0;
        float[] marginXFracs = new float[]{0.07f, 0.10f, 0.33f, 0.36f, 0.64f, 0.67f, 0.89f, 0.92f};
        for (int yi = 0; yi < 8; yi++) {
            int py = Math.min(h - 4, Math.max(4, (int) (h * (0.62f + yi * 0.025f))));
            for (int xi = 0; xi < marginXFracs.length; xi++) {
                int px = Math.min(w - 4, Math.max(4, (int) (w * marginXFracs[xi])));
                int c = bmp.getPixel(px, py);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                cardBgSamples++;
                if (r > 220 && g > 220 && b > 216 && sat < 24) {
                    whiteCardBgPixels++;
                }
            }
        }
        float whiteCardBgRatio = cardBgSamples > 0 ? ((float) whiteCardBgPixels / cardBgSamples) : 0f;

        // Incoming Gift Screen is TRUE whenever we see the wide Teal-Green 'OPEN' pill button in the center,
        // OR when the middle Postcard card is visible and the Trainer Profile white card is not underneath!
        boolean isIncomingGiftScreen = hasWideTealOpenPill
                || (openTealCount >= 12 && whiteCardBgRatio < 0.50f)
                || (postcardBannerRatio >= 0.52f && whiteCardBgRatio < 0.38f);

        boolean isTrainerProfileWhiteCard = !isIncomingGiftScreen && (whiteCardBgRatio >= 0.40f);

        float finalOpenX = (openTealCount >= 10) ? (sumOpenX / openTealCount) : learnedOpen[0];
        float finalOpenY = (openTealCount >= 10) ? (sumOpenY / openTealCount) : learnedOpen[1];
        finalOpenX = Math.max(w * 0.38f, Math.min(w * 0.62f, finalOpenX));
        finalOpenY = Math.max(h * 0.62f, Math.min(h * 0.75f, finalOpenY));

        // 2. Visually locate the circular '📌 PIN' button to the right of 'OPEN' (x = 0.70w..0.90w, y = finalOpenY ± 0.055h)
        // If the user explicitly trained Step 1 (Pin) in Teach Gift, honor their trained Pin button coordinate!
        float sumPinX = 0f;
        float sumPinY = 0f;
        int pinFeaturePixels = 0;
        int px0 = Math.max(8, (int) (w * 0.70f));
        int px1 = Math.min(w - 8, (int) (w * 0.90f));
        int py0 = Math.max(8, (int) (finalOpenY - h * 0.055f));
        int py1 = Math.min(h - 8, (int) (finalOpenY + h * 0.055f));
        for (int y = py0; y <= py1; y += 4) {
            for (int x = px0; x <= px1; x += 4) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                // Pushpin icon inside the white circle has bright red/orange/yellow/teal pin colors (sat >= 40)
                if (sat >= 40 && Math.max(r, g) >= 150) {
                    pinFeaturePixels++;
                    sumPinX += x;
                    sumPinY += y;
                }
            }
        }
        boolean hasUserTrainedGifts = LearnedProfileStore.hasLearnedGiftWorkflow(this);
        float finalPinX = hasUserTrainedGifts ? learnedPin[0]
                : ((pinFeaturePixels >= 6) ? (sumPinX / pinFeaturePixels) : learnedPin[0]);
        float finalPinY = hasUserTrainedGifts ? learnedPin[1]
                : ((pinFeaturePixels >= 6) ? (sumPinY / pinFeaturePixels) : finalOpenY);
        finalPinX = Math.max(w * 0.70f, Math.min(w * 0.89f, finalPinX));
        finalPinY = Math.max(h * 0.61f, Math.min(h * 0.76f, finalPinY));

        // 3. Scan ONLY the LEFT Button Column on the Trainer Profile (x = 0.10w..0.30w, y = 0.57h..0.83h)
        // for the '🎁 SEND GIFT' button!
        // - Active 'Send Gift' has teal/cyan/pink/gold icon pixels (sat >= 20 && maxC >= 115, or G-R >= 15, or R-G >= 18).
        // - Grayed-out 'Send Gift' is 100% monochrome gray (sat < 18).
        // - Strictly scanning x = 0.10w..0.30w guarantees it can NEVER detect or click Battle (0.50w) or Trade (0.80w)!
        int sx0 = Math.max(8, (int) (w * 0.10f));
        int sx1 = Math.min(w - 8, (int) (w * 0.30f));
        int sy0 = Math.max(8, (int) (h * 0.57f));
        int sy1 = Math.min(h - 8, (int) (h * 0.83f));

        int colorfulSendPixels = 0;
        int sendTotalSamples = 0;
        float sumSendX = 0f;
        float sumSendY = 0f;

        for (int y = sy0; y <= sy1; y += 4) {
            for (int x = sx0; x <= sx1; x += 4) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int maxC = Math.max(r, Math.max(g, b));
                int minC = Math.min(r, Math.min(g, b));
                int sat = maxC - minC;
                sendTotalSamples++;
                if ((sat >= 22 && maxC >= 115 && maxC <= 245)
                        || ((g - r) >= 16 && g >= 130)
                        || ((r - g) >= 20 && r >= 150)) {
                    colorfulSendPixels++;
                    sumSendX += x;
                    sumSendY += y;
                }
            }
        }

        float colorfulSendRatio = sendTotalSamples > 0 ? ((float) colorfulSendPixels / sendTotalSamples) : 0f;
        boolean isSendGiftActive = !isIncomingGiftScreen
                && (colorfulSendPixels >= 10 && colorfulSendRatio >= 0.020f);

        // If user trained Step 3 (Send Gift) in the valid left column, use their exact trained point or the visual centroid!
        float finalSendX = hasUserTrainedGifts ? learnedSend[0]
                : ((colorfulSendPixels >= 10) ? (sumSendX / colorfulSendPixels) : learnedSend[0]);
        float finalSendY = hasUserTrainedGifts ? learnedSend[1]
                : ((colorfulSendPixels >= 10) ? (sumSendY / colorfulSendPixels) : learnedSend[1]);
        // Hard-clamp Send Gift X to the Left Column [0.12w .. 0.28w] so it is physically impossible to hit Battle or Trade!
        finalSendX = Math.max(w * 0.12f, Math.min(w * 0.28f, finalSendX));
        finalSendY = Math.max(h * 0.59f, Math.min(h * 0.82f, finalSendY));

        boolean friendsListVisible = isFriendsListScreenVisible(bmp);

        return new GiftScreenVisionResult(
                isIncomingGiftScreen,
                isTrainerProfileWhiteCard,
                friendsListVisible,
                isSendGiftActive,
                finalPinX,
                finalPinY,
                finalOpenX,
                finalOpenY,
                finalSendX,
                finalSendY
        );
    }

    /**
     * VISION-GUIDED SEND GIFT WORKFLOW:
     * 1. Taps the visually verified Left '🎁 SEND GIFT' button (never Battle or Trade).
     * 2. Waits 2100ms, then visually checks that the Gift Postcard Bag actually opened (if still on Trainer Profile,
     *    retries once or closes cleanly if out of gifts).
     * 3. Taps a Gift Postcard from the bag, waits 1850ms, then visually locates the Teal-Green 'SEND' button
     *    and taps it!
     * 4. Waits for the screen to return to the Trainer Profile, then closes the profile with '✕' and works down the list!
     */
    private void executeVisionGuidedSendGiftSequence(
            final int w,
            final int h,
            final float sendGiftX,
            final float sendGiftY,
            final int retryAttempt
    ) {
        if (!isAutoGiftRunning) return;
        // Guarantee sendGiftX is strictly in the Left Send Gift column [0.12w .. 0.28w]
        final float safeSendX = Math.max(w * 0.12f, Math.min(w * 0.28f, sendGiftX));
        final float safeSendY = Math.max(h * 0.65f, Math.min(h * 0.82f, sendGiftY));

        notifyStatus("👁 Send Gift Active! 🎁 Clicking Send Gift (" + Math.round(safeSendX) + "," + Math.round(safeSendY) + ")");
        dispatchHumanTap(safeSendX, safeSendY, 3f);

        // Wait a patient 2100ms for the Gift Postcard Bag picker to slide up
        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                verifyGiftBagOpenedAndSelectPostcard(w, h, safeSendX, safeSendY, retryAttempt);
            }
        }, pace(2100L));
    }

    private void verifyGiftBagOpenedAndSelectPostcard(
            final int w,
            final int h,
            final float safeSendX,
            final float safeSendY,
            final int retryAttempt
    ) {
        if (!isAutoGiftRunning) return;
        final float[] learnedCard = LearnedProfileStore.getGiftStepPoint(this, 4, w * 0.50f, h * 0.36f);

        if (android.os.Build.VERSION.SDK_INT < 30) {
            tapPostcardAndProceedToGreenSend(w, h, learnedCard[0], learnedCard[1]);
            return;
        }

        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                if (!isAutoGiftRunning) return;
                Bitmap softBmp = null;
                try {
                    HardwareBuffer hb = screenshot.getHardwareBuffer();
                    ColorSpace cs = screenshot.getColorSpace();
                    Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                    if (hwBmp != null) {
                        softBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                        hwBmp.recycle();
                    }
                    hb.close();
                } catch (Exception ignored) {}

                if (softBmp != null) {
                    GiftScreenVisionResult postTapVision = analyzeGiftScreenOnDevice(softBmp, w, h);
                    boolean stillOnTrainerProfile = postTapVision.isTrainerProfileWhiteCardVisible
                            && isUpperSkyTrainerVisible(softBmp, w, h);
                    softBmp.recycle();

                    if (stillOnTrainerProfile) {
                        if (retryAttempt < 1 && postTapVision.isSendGiftActive) {
                            notifyStatus("⏳ Retrying Left 'Send Gift' Tap...");
                            executeVisionGuidedSendGiftSequence(
                                    w, h, postTapVision.sendGiftX, postTapVision.sendGiftY, retryAttempt + 1
                            );
                            return;
                        } else {
                            // Gift Bag didn't open (e.g. 0 Gifts left in player's Item Bag!) -> Close profile cleanly!
                            notifyStatus("🎒 No Gifts Available in Bag • Closing Profile");
                            friendsProcessed++;
                            closeProfileAndAdvance(w, h);
                            return;
                        }
                    }
                }

                tapPostcardAndProceedToGreenSend(w, h, learnedCard[0], learnedCard[1]);
            }

            @Override
            public void onFailure(int errorCode) {
                if (!isAutoGiftRunning) return;
                tapPostcardAndProceedToGreenSend(w, h, learnedCard[0], learnedCard[1]);
            }
        });
    }

    /**
     * Checks if the Gift Bag Postcard Picker has NOT covered the upper screen (i.e. we are still looking
     * at the Trainer Profile's top 3D character/sky instead of the white/cream Gift Bag Postcard list).
     */
    private boolean isUpperSkyTrainerVisible(Bitmap bmp, int w, int h) {
        // When the Gift Bag Postcard list opens, the region x = 0.20w..0.80w, y = 0.24h..0.48h is covered
        // by wide white/cream Postcard rows (high brightness > 215 across most of the row).
        int whitePostcardListPixels = 0;
        int samples = 0;
        for (int y = (int) (h * 0.25f); y <= (int) (h * 0.46f); y += 10) {
            for (int x = (int) (w * 0.55f); x <= (int) (w * 0.82f); x += 12) {
                int c = bmp.getPixel(x, y);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                samples++;
                if (r > 222 && g > 222 && b > 218) {
                    whitePostcardListPixels++;
                }
            }
        }
        float postcardListRatio = samples > 0 ? ((float) whitePostcardListPixels / samples) : 0f;
        // If less than 28% of the upper-right card area is white postcard rows, the Gift Bag is NOT open!
        return postcardListRatio < 0.28f;
    }

    private void tapPostcardAndProceedToGreenSend(final int w, final int h, final float cardX, final float cardY) {
        if (!isAutoGiftRunning) return;
        float safeCardX = Math.max(w * 0.22f, Math.min(w * 0.78f, cardX));
        float safeCardY = Math.max(h * 0.24f, Math.min(h * 0.60f, cardY));

        notifyStatus("💌 Selecting Gift Postcard (" + Math.round(safeCardX) + "," + Math.round(safeCardY) + ")...");
        dispatchHumanTap(safeCardX, safeCardY, 4f);

        // Wait 1850ms for the Postcard Send Preview and Teal-Green 'SEND' button to appear
        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                verifyGiftPreviewAndTapGreenSend(w, h);
            }
        }, pace(1850L));
    }

    private void verifyGiftPreviewAndTapGreenSend(final int w, final int h) {
        if (!isAutoGiftRunning) return;
        final float[] learnedConfirm = LearnedProfileStore.getGiftStepPoint(this, 5, w * 0.50f, h * 0.81f);

        if (android.os.Build.VERSION.SDK_INT < 30) {
            finishGreenSendAndReturnToProfile(w, h, learnedConfirm[0], learnedConfirm[1]);
            return;
        }

        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                if (!isAutoGiftRunning) return;
                float confirmX = learnedConfirm[0];
                float confirmY = learnedConfirm[1];

                try {
                    HardwareBuffer hb = screenshot.getHardwareBuffer();
                    ColorSpace cs = screenshot.getColorSpace();
                    Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                    if (hwBmp != null) {
                        Bitmap softBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                        hwBmp.recycle();
                        if (softBmp != null) {
                            // Visually locate the Teal-Green 'SEND' pill button (x = 0.34w..0.66w, y = 0.73h..0.87h)
                            float sumX = 0f;
                            float sumY = 0f;
                            int tealSendCount = 0;
                            for (int y = (int) (h * 0.73f); y <= (int) (h * 0.87f); y += 5) {
                                for (int x = (int) (w * 0.34f); x <= (int) (w * 0.66f); x += 6) {
                                    int c = softBmp.getPixel(x, y);
                                    int r = Color.red(c);
                                    int g = Color.green(c);
                                    int b = Color.blue(c);
                                    if (g >= 155 && b >= 105 && r <= 175 && (g - r) >= 25) {
                                        tealSendCount++;
                                        sumX += x;
                                        sumY += y;
                                    }
                                }
                            }
                            if (tealSendCount >= 12) {
                                confirmX = sumX / tealSendCount;
                                confirmY = sumY / tealSendCount;
                            }
                            softBmp.recycle();
                        }
                    }
                    hb.close();
                } catch (Exception ignored) {}

                finishGreenSendAndReturnToProfile(w, h, confirmX, confirmY);
            }

            @Override
            public void onFailure(int errorCode) {
                if (!isAutoGiftRunning) return;
                finishGreenSendAndReturnToProfile(w, h, learnedConfirm[0], learnedConfirm[1]);
            }
        });
    }

    private void finishGreenSendAndReturnToProfile(final int w, final int h, float confirmX, float confirmY) {
        if (!isAutoGiftRunning) return;
        float safeConfirmX = Math.max(w * 0.36f, Math.min(w * 0.64f, confirmX));
        float safeConfirmY = Math.max(h * 0.74f, Math.min(h * 0.87f, confirmY));

        notifyStatus("🚀 Clicking Green 'SEND' (" + Math.round(safeConfirmX) + "," + Math.round(safeConfirmY) + ")...");
        dispatchHumanTap(safeConfirmX, safeConfirmY, 4f);
        giftsSent++;
        friendsProcessed++;
        PokeMateDatabase.getInstance(PokemonGoAccessibilityService.this)
                .logAction("GIFT_SENT", "Sent Gift #" + giftsSent);

        // After 1350ms, tap once near bottom-center to skip the flying gift box animation
        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                notifyStatus("⏩ Finishing Gift Send Animation...");
                dispatchHumanTap(w * 0.50f, h * 0.85f, 5f);
            }
        }, pace(1350L));

        // Wait 3400ms total after SEND so the screen completely returns to the Trainer Profile before closing!
        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                closeProfileAndAdvance(w, h);
            }
        }, pace(3400L));
    }

    /**
     * Closes the open Trainer Profile with the bottom '✕' button, verifies via on-device vision
     * that we are back on the Friends List (tapping '✕' once more if still inside the profile),
     * and works down the next friend on screen (slots 0 -> 1 -> 2 -> 3).
     * Only scrolls down a bit AFTER all 4 friends on screen have been processed!
     */
    private void closeProfileAndAdvance(final int w, final int h) {
        final float[] learnedClose = LearnedProfileStore.getGiftStepPoint(this, 6, w * 0.50f, h * 0.905f);
        currentRowSlot++;
        final int doneOnScreen = Math.min(currentRowSlot, VISIBLE_FRIENDS_PER_SCREEN);
        notifyStatus("✕ Closing Profile (" + doneOnScreen + "/" + VISIBLE_FRIENDS_PER_SCREEN + " on screen)");
        dispatchHumanTap(learnedClose[0], learnedClose[1], 5f);

        // Wait 1500ms for the profile to close, then verify we are back on the Friends List before continuing
        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                verifyReturnedToFriendsListBeforeNextStep(w, h, learnedClose[0], learnedClose[1]);
            }
        }, pace(1500L));
    }

    private void verifyReturnedToFriendsListBeforeNextStep(
            final int w,
            final int h,
            final float closeX,
            final float closeY
    ) {
        if (!isAutoGiftRunning) return;
        if (android.os.Build.VERSION.SDK_INT < 30) {
            proceedAfterProfileClosed(w, h);
            return;
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshot) {
                    if (!isAutoGiftRunning) return;
                    boolean backOnFriendsList = true;
                    try {
                        HardwareBuffer hb = screenshot.getHardwareBuffer();
                        ColorSpace cs = screenshot.getColorSpace();
                        Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                        if (hwBmp != null) {
                            Bitmap softBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                            hwBmp.recycle();
                            if (softBmp != null) {
                                backOnFriendsList = isFriendsListScreenVisible(softBmp);
                                softBmp.recycle();
                            }
                        }
                        hb.close();
                    } catch (Throwable ignored) {}

                    if (!backOnFriendsList) {
                        // Still inside the Trainer Profile or a sub-modal -> tap Close '✕' once more!
                        notifyStatus("✕ Closing Profile Again -> Returning to Friends List...");
                        dispatchHumanTap(closeX, closeY, 4f);
                        giftHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAutoGiftRunning) return;
                                proceedAfterProfileClosed(w, h);
                            }
                        }, pace(1500L));
                    } else {
                        proceedAfterProfileClosed(w, h);
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    if (!isAutoGiftRunning) return;
                    proceedAfterProfileClosed(w, h);
                }
            });
        } catch (Throwable t) {
            proceedAfterProfileClosed(w, h);
        }
    }

    private void proceedAfterProfileClosed(final int w, final int h) {
        if (!isAutoGiftRunning) return;
        if (currentRowSlot >= VISIBLE_FRIENDS_PER_SCREEN) {
            scrollFriendsListAfterAllOnScreenDone(w, h);
        } else {
            // Move straight down to the next visible person on screen without scrolling!
            scheduleOpenNextFriend(pace(550L));
        }
    }

    private void scrollFriendsListAfterAllOnScreenDone(final int w, final int h) {
        if (!isAutoGiftRunning) return;
        currentRowSlot = 0;
        notifyStatus("📜 All 4 on Screen Done • Scrolling Down a Bit ↓");
        performCurvedBezierScroll(w, h);
        scheduleOpenNextFriend(pace(1550L));
    }

    /**
     * Checks if the current screen is the Friends List (bright white horizontal friend cards across
     * the right-middle column x = 0.52..0.84, y = 0.25..0.72) rather than an open Trainer Profile
     * (which has a 3D Trainer + Buddy standing on a colored team/sky background).
     */
    private boolean isFriendsListScreenVisible(Bitmap bmp) {
        int bw = bmp.getWidth();
        int bh = bmp.getHeight();
        int whiteCardPixels = 0;
        int totalSamples = 0;
        for (int yi = 0; yi < 12; yi++) {
            int py = (int) (bh * (0.25f + yi * 0.04f));
            for (int xi = 0; xi < 6; xi++) {
                int px = (int) (bw * (0.52f + xi * 0.055f));
                int c = bmp.getPixel(px, py);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                totalSamples++;
                if (r > 232 && g > 234 && b > 230 && Math.abs(r - b) < 16) {
                    whiteCardPixels++;
                }
            }
        }
        return totalSamples > 0 && ((float) whiteCardPixels / totalSamples) >= 0.46f;
    }

    // =========================================================================
    // ENGINE 3: ON-DEMAND SCREEN OCR FOR 'SCAN GAME' RESEARCH BUTTON
    // =========================================================================

    public void scanGameScreenWithOcr(final OcrScanCallback callback) {
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                if (callback != null) callback.onScreenshotCaptured();

                Bitmap softBmp = null;
                try {
                    HardwareBuffer hb = screenshot.getHardwareBuffer();
                    ColorSpace cs = screenshot.getColorSpace();
                    Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                    if (hwBmp != null) {
                        softBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                        hwBmp.recycle();
                    }
                    hb.close();
                } catch (Exception ignored) {}

                if (softBmp == null) {
                    if (callback != null) callback.onScanResult("", 0, "Could not read screenshot buffer");
                    return;
                }

                final Bitmap finalBmp = softBmp;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        runCloudOcrOnBitmap(finalBmp, callback);
                    }
                }).start();
            }

            @Override
            public void onFailure(int errorCode) {
                if (callback != null) {
                    callback.onScreenshotCaptured();
                    callback.onScanResult("", 0, "Screenshot rate-limited (wait 1s and tap Scan again)");
                }
            }
        });
    }

    private void runCloudOcrOnBitmap(Bitmap bmp, final OcrScanCallback callback) {
        try {
            int targetW = 720;
            int targetH = Math.round(((float) bmp.getHeight() / bmp.getWidth()) * targetW);
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true);
            bmp.recycle();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos);

            String base64Img = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            String postBody = "apikey=helloworld"
                    + "&language=eng"
                    + "&OCREngine=2"
                    + "&isOverlayRequired=true"
                    + "&base64Image=" + URLEncoder.encode(base64Img, "UTF-8");

            URL url = new URL("https://api.ocr.space/parse/image");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(9000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            OutputStream os = conn.getOutputStream();
            os.write(postBody.getBytes("UTF-8"));
            os.flush();
            os.close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            String json = sb.toString();
            Matcher m = Pattern.compile("\"ParsedText\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
            String rawParsedText = "";
            if (m.find()) {
                rawParsedText = m.group(1)
                        .replace("\\r\\n", "\n")
                        .replace("\\n", "\n")
                        .replace("\\t", " ")
                        .replace("\\\"", "\"");
            }

            final String matchedPipeQuery = PokeMateDatabase.getInstance(this)
                    .matchTasksFromScreenOcrWithVision(json, rawParsedText, scaled);
            scaled.recycle();
            final int count = matchedPipeQuery.length() > 0 ? matchedPipeQuery.split("\\|").length : 0;
            final String rawFallback = rawParsedText.replace("\n", " ").trim();

            giftHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onScanResult(matchedPipeQuery, count, rawFallback);
                    }
                }
            });
        } catch (final Exception e) {
            giftHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onScanResult("", 0, e.getMessage());
                    }
                }
            });
        }
    }

    // =========================================================================
    // ENGINE 4: 'SCAN POKÉMON' — ON-DEVICE IV BAR VISION + OCR META EVALUATOR
    // =========================================================================

    public void scanPokemonScreenWithOcr(final PokemonScanCallback callback) {
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                if (callback != null) callback.onScreenshotCaptured();

                Bitmap softBmp = null;
                try {
                    HardwareBuffer hb = screenshot.getHardwareBuffer();
                    ColorSpace cs = screenshot.getColorSpace();
                    Bitmap hwBmp = Bitmap.wrapHardwareBuffer(hb, cs);
                    if (hwBmp != null) {
                        softBmp = hwBmp.copy(Bitmap.Config.ARGB_8888, false);
                        hwBmp.recycle();
                    }
                    hb.close();
                } catch (Exception ignored) {}

                if (softBmp == null) {
                    if (callback != null) {
                        callback.onPokemonScanned(null, "", "Could not read screenshot buffer");
                    }
                    return;
                }

                final Bitmap finalBmp = softBmp;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        runPokemonOcrAndVisionOnBitmap(finalBmp, callback);
                    }
                }).start();
            }

            @Override
            public void onFailure(int errorCode) {
                if (callback != null) {
                    callback.onScreenshotCaptured();
                    callback.onPokemonScanned(null, "", "Screenshot rate-limited (wait 1s and tap Scan Pokémon again)");
                }
            }
        });
    }

    private void runPokemonOcrAndVisionOnBitmap(Bitmap bmp, final PokemonScanCallback callback) {
        try {
            int targetW = 720;
            int targetH = Math.round(((float) bmp.getHeight() / bmp.getWidth()) * targetW);
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true);
            bmp.recycle();

            // Build a composite OCR image that appends a High-Contrast Auto-Leveled Name Strip (y=0.35h..0.53h)
            // so even when the dark Team Leader Appraisal scrim dims the Pokémon Name, OCR reads it on Pass 1!
            Bitmap compositeOcrBmp = buildContrastEnhancedPokemonOcrBitmap(scaled);

            // Pass 1: OCREngine=2 on composite screenshot + contrast-restored Name strip
            String rawParsedText = requestOcrSpaceText(compositeOcrBmp, "2");

            PokemonMetaAnalyzer.PokemonScanReport report =
                    PokemonMetaAnalyzer.analyzePokemonScreen(rawParsedText, scaled);

            // Pass 2: If species was not matched on Pass 1, run OCREngine=1 on the contrast-enhanced image and combine transcripts!
            if (report == null) {
                String pass2Text = requestOcrSpaceText(compositeOcrBmp, "1");
                String combinedText = (rawParsedText != null ? rawParsedText : "") + "\n" + (pass2Text != null ? pass2Text : "");
                rawParsedText = combinedText;
                report = PokemonMetaAnalyzer.analyzePokemonScreen(combinedText, scaled);
            }
            if (compositeOcrBmp != scaled) {
                compositeOcrBmp.recycle();
            }

            // Pass 3: Even if the Pokémon has a custom nickname and Appraisal covers the Candy name,
            // extract the Appraisal IV bars & CP so the Full-Page Overlay opens with the exact scanned IVs!
            final PokemonMetaAnalyzer.AppraisalBarVisionResult fallbackAppraisal =
                    (report == null) ? PokemonMetaAnalyzer.extractAppraisalBarsFromBitmap(scaled) : report.appraisal;
            scaled.recycle();

            if (report == null && fallbackAppraisal != null && fallbackAppraisal.appraisalCardDetected) {
                List<PokemonMetaAnalyzer.PokemonScanReport> defaults = PokemonMetaAnalyzer.searchPokemonByName("Rayquaza");
                if (!defaults.isEmpty()) {
                    report = new PokemonMetaAnalyzer.PokemonScanReport(
                            defaults.get(0).entry,
                            0,
                            0,
                            fallbackAppraisal.shadowAuraDetected,
                            false,
                            false,
                            fallbackAppraisal
                    );
                }
            }

            final PokemonMetaAnalyzer.PokemonScanReport finalReport = report;
            final String cleanOcr = rawParsedText != null ? rawParsedText.replace("\n", " ").trim() : "";

            giftHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onPokemonScanned(finalReport, cleanOcr, null);
                    }
                }
            });
        } catch (final Exception e) {
            giftHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onPokemonScanned(null, "", e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Appends a contrast-stretched (auto-leveled) copy of the Pokémon Name band (y = 0.35h .. 0.53h)
     * directly below the screenshot so that even when the Pokémon GO Appraisal overlay dims the Name card,
     * the appended strip restores crisp black text on a bright white background for 100% reliable OCR!
     */
    private Bitmap buildContrastEnhancedPokemonOcrBitmap(Bitmap scaled) {
        try {
            int w = scaled.getWidth();
            int h = scaled.getHeight();
            int bandY = Math.max(0, (int) (h * 0.35f));
            int bandH = Math.min(h - bandY, (int) (h * 0.19f));
            if (bandH <= 20) return scaled;

            int[] pixels = new int[w * bandH];
            scaled.getPixels(pixels, 0, w, 0, bandY, w, bandH);

            int minLum = 255;
            int maxLum = 0;
            for (int i = 0; i < pixels.length; i += 4) {
                int c = pixels[i];
                int lum = (((c >> 16) & 0xFF) * 30 + ((c >> 8) & 0xFF) * 59 + (c & 0xFF) * 11) / 100;
                if (lum < minLum) minLum = lum;
                if (lum > maxLum) maxLum = lum;
            }
            int range = Math.max(25, maxLum - minLum);
            for (int i = 0; i < pixels.length; i++) {
                int c = pixels[i];
                int lum = (((c >> 16) & 0xFF) * 30 + ((c >> 8) & 0xFF) * 59 + (c & 0xFF) * 11) / 100;
                int stretched = ((lum - minLum) * 255) / range;
                // S-curve contrast boost so dark-teal text becomes jet black and dimmed gray card becomes pure white
                if (stretched < 115) {
                    stretched = Math.max(0, stretched / 2);
                } else {
                    stretched = Math.min(255, 160 + (stretched - 115));
                }
                pixels[i] = 0xFF000000 | (stretched << 16) | (stretched << 8) | stretched;
            }

            Bitmap combined = Bitmap.createBitmap(w, h + bandH, Bitmap.Config.ARGB_8888);
            int[] origPixels = new int[w * h];
            scaled.getPixels(origPixels, 0, w, 0, 0, w, h);
            combined.setPixels(origPixels, 0, w, 0, 0, w, h);
            combined.setPixels(pixels, 0, w, 0, h, w, bandH);
            return combined;
        } catch (Exception e) {
            return scaled;
        }
    }

    private static final String[] OCR_API_KEYS = new String[]{
            "helloworld",
            "K85989477888957",
            "K81893588288957"
    };

    private String requestOcrSpaceText(Bitmap bmp, String ocrEngine) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, baos);
            String base64Img = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            String encodedImage = URLEncoder.encode(base64Img, "UTF-8");

            for (int k = 0; k < OCR_API_KEYS.length; k++) {
                try {
                    String postBody = "apikey=" + OCR_API_KEYS[k]
                            + "&language=eng"
                            + "&OCREngine=" + ocrEngine
                            + "&scale=true"
                            + "&isOverlayRequired=false"
                            + "&base64Image=" + encodedImage;

                    URL url = new URL("https://api.ocr.space/parse/image");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(7500);
                    conn.setReadTimeout(7500);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
                    );
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    OutputStream os = conn.getOutputStream();
                    os.write(postBody.getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    if (code != 200) continue;

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    String json = sb.toString();
                    Matcher m = Pattern.compile("\"ParsedText\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
                    StringBuilder allParsed = new StringBuilder();
                    while (m.find()) {
                        allParsed.append(m.group(1)
                                .replace("\\r\\n", "\n")
                                .replace("\\n", "\n")
                                .replace("\\t", " ")
                                .replace("\\\"", "\"")).append("\n");
                    }
                    String res = allParsed.toString().trim();
                    if (!res.isEmpty()) {
                        return res;
                    }
                } catch (Exception ignored) {}
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public void dispatchHumanTap(float baseX, float baseY, float stdDevPx) {
        float jitterX = stdDevPx > 0 ? (float) (random.nextGaussian() * stdDevPx) : 0f;
        float jitterY = stdDevPx > 0 ? (float) (random.nextGaussian() * stdDevPx) : 0f;
        float tapX = Math.max(12f, baseX + jitterX);
        float tapY = Math.max(12f, baseY + jitterY);
        long duration = Math.min(105L, Math.max(48L, 60L + Math.round(Math.abs(random.nextGaussian()) * 18)));

        Path path = new Path();
        path.moveTo(tapX, tapY);
        path.lineTo(tapX + 1.5f, tapY + 1.5f);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0L, duration);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, null, null);
    }

    private void performCurvedBezierScroll(int w, int h) {
        // Gentle controlled upward drag on the Friends List (from 0.70h to 0.31h over 540ms)
        // so it scrolls down a bit (~4 friend rows) without flinging past friends.
        float startX = w * 0.50f + (random.nextFloat() * 16f - 8f);
        float startY = h * 0.70f;
        float endX = startX + (random.nextFloat() * 14f - 7f);
        float endY = h * 0.31f;

        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.quadTo((startX + endX) / 2f + 14f, (startY + endY) / 2f, endX, endY);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(swipePath, 0L, 540L);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, null, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) {
            return;
        }
        String pkg = event.getPackageName().toString().toLowerCase();

        // Always ignore our own package, keyboards, system UI, and Google Play / background services so the HUD NEVER flashes!
        if (pkg.equals("com.pokemate.companion")
                || pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg.contains("gboard")
                || pkg.equals("android") || pkg.equals("com.android.systemui")
                || pkg.contains("permissioncontroller") || pkg.contains("gms")
                || pkg.contains("gsf") || pkg.contains("play.games") || pkg.contains("vending")
                || pkg.contains("webview") || pkg.contains("googlequicksearchbox")
                || pkg.contains("tts") || pkg.contains("gametools") || pkg.contains("gos")) {
            return;
        }

        boolean nowInPoGo = pkg.contains("pokemongo")
                || pkg.equals("com.nianticlabs.pokemongo")
                || pkg.equals("com.scopely.pokemongo")
                || pkg.equals("com.pokemongo.samsung");

        if (nowInPoGo) {
            autoHideHandler.removeCallbacksAndMessages(null);
            if (!isPokemonGoInForeground) {
                isPokemonGoInForeground = true;
                final FloatingViewService hud = FloatingViewService.instance;
                if (hud != null) {
                    hud.onForegroundAppChanged(true);
                }
            }
            return;
        }

        // Only consider leaving Pokémon GO if the new window is a full-screen Activity or Home Launcher
        boolean isRealFullScreenOrLauncher = event.isFullScreen()
                || pkg.contains("launcher") || pkg.contains("home") || pkg.contains("trebuchet");
        if (!isRealFullScreenOrLauncher) {
            return;
        }

        autoHideHandler.removeCallbacksAndMessages(null);
        autoHideHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPokemonGoInForeground) {
                    isPokemonGoInForeground = false;
                    if (LearnedProfileStore.isAutoHideOutsidePoGoEnabled(PokemonGoAccessibilityService.this)) {
                        if (isThrowRunning) stopActiveThrow();
                        if (isAutoGiftRunning) stopActiveGiftLoop();
                    }
                    final FloatingViewService hud = FloatingViewService.instance;
                    if (hud != null) {
                        hud.onForegroundAppChanged(false);
                    }
                }
            }
        }, 450L);
    }

    @Override
    public void onInterrupt() {
        stopActiveThrow();
        stopActiveGiftLoop();
    }

    @Override
    public void onDestroy() {
        stopActiveThrow();
        stopActiveGiftLoop();
        instance = null;
        super.onDestroy();
    }
}
