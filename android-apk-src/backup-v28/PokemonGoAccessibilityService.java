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
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) {
                Rect b = wm.getMaximumWindowMetrics().getBounds();
                if (b.width() > 0 && b.height() > 0) {
                    return new int[]{b.width(), b.height()};
                }
            }
        } catch (Exception ignored) {}
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

        // Always sanitize any legacy/corrupted stroke and load either the verified adapted throw
        // or the clean, C2-smooth Natural Human Good/Great/Excellent Throw for this Pokémon's live circle!
        LearnedProfileStore.CalibratedStroke chosenThrow = LearnedProfileStore.getAdaptedLearnedThrowForRing(
                this,
                ring.centerX,
                ring.centerY,
                ring.outerWhiteRadius,
                w,
                h
        );

        // Final pre-flight anti-robotic & banned-miss guard: never attempt a robotic or banned move!
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
        replayStrokePoints(chosenThrow.points, chosenThrow.durationMs, 0f, useQuickCatch, true);
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

        Path throwPath = new Path();
        float jx = jitterStdDev > 0 ? (float) (random.nextGaussian() * jitterStdDev) : 0f;
        float jy = jitterStdDev > 0 ? (float) (random.nextGaussian() * jitterStdDev) : 0f;
        throwPath.moveTo(
                Math.max(5f, Math.min(w - 5f, pts.get(0).x + jx)),
                Math.max(5f, Math.min(h - 5f, pts.get(0).y + jy))
        );

        for (int i = 1; i < pts.size(); i++) {
            float px = Math.max(5f, Math.min(w - 5f, pts.get(i).x + jx));
            float py = Math.max(5f, Math.min(h - 5f, pts.get(i).y + jy));
            throwPath.lineTo(px, py);
        }

        final long safeThrowDuration = Math.max(85L, Math.min(550L, durationMs));
        dispatchThrowPathWithOptionalQuickCatch(w, h, throwPath, safeThrowDuration, useQuickCatch, showPostThrowRatePopup);
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
        // Finger 1 grabs the Berry button (bottom-left) and pulls it right, holding until 150ms AFTER Finger 2 releases the ball.
        float[] berryPt = LearnedProfileStore.getBerryButtonPoint(this, w * 0.135f, h * 0.885f);
        float berryStartX = berryPt[0];
        float berryStartY = berryPt[1];
        float berryPullX = Math.min(w * 0.42f, berryStartX + w * 0.22f);

        Path berryHoldPath = new Path();
        berryHoldPath.moveTo(berryStartX, berryStartY);
        berryHoldPath.lineTo(berryPullX, berryStartY);

        long throwStartOffsetMs = 40L;
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

        // Wait 520ms after the ball is released so the ball physically hits the Pokémon
        // BEFORE dismissing the Berry tray and tapping Top-Left Run once (NEVER call GLOBAL_ACTION_BACK!)
        long ballFlightWaitMs = throwStartOffsetMs + safeThrowDuration + 520L;
        throwHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isThrowRunning) return;
                notifyStatus("⚡ Fast Catch: Dismissing Berry Tray...");
                dispatchHumanTap(w * 0.50f, h * 0.46f, 4f);

                throwHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isThrowRunning) return;
                        notifyStatus("⚡ Fast Catch: Tapping Top-Left Run...");
                        // Tap Top-Left Run / Flee button ONCE (never trigger GLOBAL_ACTION_BACK, which opens the exit prompt!)
                        dispatchHumanTap(w * 0.105f, h * 0.082f, 3f);

                        isThrowRunning = false;
                        throwHandler.removeCallbacksAndMessages(null);
                        notifyStatus("✓ Fast Catch Complete • Rate throw below");
                        if (FloatingViewService.instance != null) {
                            FloatingViewService.instance.onThrowSequenceFinished(showPostThrowRatePopup);
                        }
                    }
                }, pace(240L));
            }
        }, ballFlightWaitMs);
    }

    // =========================================================================
    // ENGINE 2: SMART ON-DEVICE VISION AUTO-GIFT ENGINE (STARTS AT TOP + SKIPS SENT)
    // =========================================================================

    public void startActiveGiftLoop() {
        isAutoGiftRunning = true;
        // CRITICAL FIX: Always reset currentRowSlot to 0 so we ALWAYS start from the 1st Friend at the TOP of the list!
        currentRowSlot = 0;
        int learnedSessions = LearnedProfileStore.getLearnedGiftCount(this);
        notifyStatus(learnedSessions > 0
                ? ("▶ Auto-Gift: Starting at Top Row #1 (Trained ×" + learnedSessions + ")...")
                : "▶ Auto-Gift: Starting at Top Row #1...");
        giftHandler.removeCallbacksAndMessages(null);
        scheduleOpenNextFriend(450L);
    }

    public void stopActiveGiftLoop() {
        isAutoGiftRunning = false;
        giftHandler.removeCallbacksAndMessages(null);
        notifyStatus("Stopped • Idle");
    }

    private void scheduleOpenNextFriend(long delayMs) {
        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                final int[] dim = getTrueScreenDimensions();
                final int w = dim[0];
                final int h = dim[1];

                // Default top row in Pokémon GO Friends List is y = h * 0.28f (or user's learned Step 0)
                float[] learnedRow = LearnedProfileStore.getGiftStepPoint(
                        PokemonGoAccessibilityService.this, 0, w * 0.45f, h * 0.285f
                );
                int slotInPage = currentRowSlot % 3;
                float baseRowX = learnedRow[0];
                float baseRowY = learnedRow[1] + slotInPage * (h * 0.135f);
                if (baseRowY > h * 0.78f) {
                    baseRowY = h * (0.285f + slotInPage * 0.135f);
                }

                notifyStatus("👤 Opening Friend Row #" + (slotInPage + 1) + " (" + Math.round(baseRowX) + "," + Math.round(baseRowY) + ")");
                dispatchHumanTap(baseRowX, baseRowY, 6f);

                giftHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAutoGiftRunning) return;
                        inspectFriendScreenFastOnDevice(false);
                    }
                }, pace(2150L));
            }
        }, delayMs);
    }

    /**
     * 100% On-Device Vision Screen Inspector (<15ms, no flaky Cloud OCR delay!):
     * Determines whether we are looking at:
     * 1. An Incoming Gift Postcard screen (with OPEN & 📌 Pin buttons)
     * 2. A Trainer Profile where 'Send Gift' is ACTIVE (colorful pink/teal button)
     * 3. A Trainer Profile where 'Send Gift' is GREYED OUT (Friend ALREADY received a gift today -> Smart Skip!)
     */
    private void inspectFriendScreenFastOnDevice(final boolean alreadyHandledIncomingGift) {
        if (!isAutoGiftRunning) return;
        notifyStatus("👁 Checking Friend Status (Incoming vs Already Sent)...");

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
                int w = softBmp != null ? softBmp.getWidth() : dim[0];
                int h = softBmp != null ? softBmp.getHeight() : dim[1];

                float[] learnedPin = LearnedProfileStore.getGiftStepPoint(PokemonGoAccessibilityService.this, 1, w * 0.78f, h * 0.69f);
                float[] learnedOpen = LearnedProfileStore.getGiftStepPoint(PokemonGoAccessibilityService.this, 2, w * 0.50f, h * 0.69f);
                float[] learnedSend = LearnedProfileStore.getGiftStepPoint(PokemonGoAccessibilityService.this, 3, w * 0.18f, h * 0.74f);

                if (softBmp == null) {
                    executeLeftSendGiftSequence(w, h, learnedSend[0], learnedSend[1]);
                    return;
                }

                // 1. Check if Incoming Gift Postcard is currently covering the screen:
                // On the Incoming Gift overlay, the top-left/top-right sky (y = 0.10h) is darkened (< 95 brightness)
                // while the center postcard & Pin button on the right (x ≈ 0.78w, y ≈ 0.69h) are bright!
                boolean isIncomingGiftScreen = detectIncomingGiftOverlayOnDevice(softBmp, w, h, learnedOpen[0], learnedOpen[1]);

                // 2. Check if the Left 'SEND GIFT' button on the Trainer Profile is active (colorful) or greyed out (already sent)!
                boolean isSendGiftButtonColorful = isRegionColorful(softBmp, (int) learnedSend[0], (int) learnedSend[1], (int) (w * 0.065f));

                softBmp.recycle();

                if (isIncomingGiftScreen && !alreadyHandledIncomingGift) {
                    if (giftWorkflowMode == MODE_SEND_GIFT_ONLY) {
                        // Dismiss incoming gift with bottom X so we can access the Send Gift button underneath!
                        notifyStatus("⏭ Skipping Incoming Gift -> Opening Profile...");
                        dispatchHumanTap(w * 0.50f, h * 0.90f, 5f);
                        giftHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAutoGiftRunning) return;
                                inspectFriendScreenFastOnDevice(true);
                            }
                        }, pace(1300L));
                        return;
                    }

                    // Pin -> Unpin -> Open Incoming Gift!
                    final float pinX = learnedPin[0];
                    final float pinY = learnedPin[1];
                    final float openX = learnedOpen[0];
                    final float openY = learnedOpen[1];
                    final int fw = w;
                    final int fh = h;

                    notifyStatus("📌 Pinning Postcard (" + Math.round(pinX) + "," + Math.round(pinY) + ")");
                    dispatchHumanTap(pinX, pinY, 4f);

                    giftHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAutoGiftRunning) return;
                            notifyStatus("📌 Unpinning Postcard (" + Math.round(pinX) + "," + Math.round(pinY) + ")");
                            dispatchHumanTap(pinX, pinY, 4f);

                            giftHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAutoGiftRunning) return;
                                    notifyStatus("🎁 Tapping 'OPEN' (" + Math.round(openX) + "," + Math.round(openY) + ")");
                                    dispatchHumanTap(openX, openY, 5f);

                                    giftHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!isAutoGiftRunning) return;
                                            notifyStatus("⏩ Skipping Open Animation...");
                                            dispatchHumanTap(fw * 0.50f, fh * 0.84f, 6f);
                                            giftHandler.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!isAutoGiftRunning) return;
                                                    dispatchHumanTap(fw * 0.50f, fh * 0.84f, 6f);
                                                    giftHandler.postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (!isAutoGiftRunning) return;
                                                            if (giftWorkflowMode == MODE_PIN_AND_OPEN_ONLY) {
                                                                giftsSent++;
                                                                friendsProcessed++;
                                                                closeProfileAndAdvance(fw, fh);
                                                            } else {
                                                                // Now we are back on the Trainer Profile -> check if we can send a gift!
                                                                inspectFriendScreenFastOnDevice(true);
                                                            }
                                                        }
                                                    }, pace(1550L));
                                                }
                                            }, pace(650L));
                                        }
                                    }, pace(1350L));
                                }
                            }, pace(750L));
                        }
                    }, pace(850L));
                    return;
                }

                // If we already tried opening an incoming gift, but it's STILL on the incoming gift screen (e.g., Item Bag Full!),
                // tap bottom X to dismiss the incoming gift so we can still send them a gift!
                if (isIncomingGiftScreen && alreadyHandledIncomingGift) {
                    notifyStatus("🎒 Bag Full / Dismissing Incoming Gift...");
                    dispatchHumanTap(w * 0.50f, h * 0.90f, 5f);
                    final int fw = w;
                    final int fh = h;
                    final float sx = learnedSend[0];
                    final float sy = learnedSend[1];
                    giftHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAutoGiftRunning) return;
                            executeLeftSendGiftSequence(fw, fh, sx, sy);
                        }
                    }, pace(1250L));
                    return;
                }

                // SMART CHECK: Did this friend ALREADY receive a gift today?
                // If the Send Gift button area is desaturated grey (!isSendGiftButtonColorful), skip them immediately!
                if (!isSendGiftButtonColorful) {
                    notifyStatus("⏭ Friend Already Has Gift • Smart Skipping!");
                    friendsProcessed++;
                    final int fw = w;
                    final int fh = h;
                    giftHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAutoGiftRunning) return;
                            closeProfileAndAdvance(fw, fh);
                        }
                    }, pace(550L));
                    return;
                }

                // Friend CAN receive a gift -> Execute 3-step Send Gift sequence using exact learned coordinates!
                executeLeftSendGiftSequence(w, h, learnedSend[0], learnedSend[1]);
            }

            @Override
            public void onFailure(int errorCode) {
                int[] dim = getTrueScreenDimensions();
                float[] learnedSend = LearnedProfileStore.getGiftStepPoint(
                        PokemonGoAccessibilityService.this, 3, dim[0] * 0.18f, dim[1] * 0.74f
                );
                executeLeftSendGiftSequence(dim[0], dim[1], learnedSend[0], learnedSend[1]);
            }
        });
    }

    /**
     * Detects if the Incoming Gift overlay is currently displayed.
     * On the Incoming Gift screen:
     * - The top sky backdrop (y = 0.11h) is darkened by the modal scrim (average brightness < 98)
     * - The center postcard (y = 0.38h, x = 0.50w) and the OPEN pill (openX, openY) are bright!
     */
    private boolean detectIncomingGiftOverlayOnDevice(Bitmap bmp, int w, int h, float openX, float openY) {
        long topBrightnessSum = 0;
        int topSamples = 0;
        int topY = (int) (h * 0.11f);
        for (int x = (int) (w * 0.15f); x <= (int) (w * 0.85f); x += 16) {
            int px = bmp.getPixel(x, topY);
            int lum = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3;
            topBrightnessSum += lum;
            topSamples++;
        }
        float avgTopLum = topSamples > 0 ? ((float) topBrightnessSum / topSamples) : 150f;

        // Check if there is a bright pill button around openX, openY
        int openBrightPixels = 0;
        int totalOpenSamples = 0;
        int oxStart = Math.max(10, (int) (openX - w * 0.12f));
        int oxEnd = Math.min(w - 10, (int) (openX + w * 0.12f));
        int oyStart = Math.max(10, (int) (openY - h * 0.025f));
        int oyEnd = Math.min(h - 10, (int) (openY + h * 0.025f));

        for (int y = oyStart; y <= oyEnd; y += 5) {
            for (int x = oxStart; x <= oxEnd; x += 6) {
                int px = bmp.getPixel(x, y);
                int r = Color.red(px);
                int g = Color.green(px);
                int b = Color.blue(px);
                if (r > 195 && g > 210 && b > 180) {
                    openBrightPixels++;
                }
                totalOpenSamples++;
            }
        }

        float openBrightRatio = totalOpenSamples > 0 ? ((float) openBrightPixels / totalOpenSamples) : 0f;
        return (avgTopLum < 105f && openBrightRatio > 0.18f);
    }

    /**
     * Checks if the 'SEND GIFT' button region on the Trainer Profile has colorful pixels (pink/magenta/teal gift icon)
     * vs being greyed out (friend already received a gift today).
     */
    private boolean isRegionColorful(Bitmap bmp, int cx, int cy, int radius) {
        int x0 = Math.max(8, cx - radius);
        int x1 = Math.min(bmp.getWidth() - 8, cx + radius);
        int y0 = Math.max(8, cy - radius);
        int y1 = Math.min(bmp.getHeight() - 8, cy + radius);

        int colorfulPixelCount = 0;
        int totalSamples = 0;

        for (int y = y0; y <= y1; y += 4) {
            for (int x = x0; x <= x1; x += 4) {
                int px = bmp.getPixel(x, y);
                int r = Color.red(px);
                int g = Color.green(px);
                int b = Color.blue(px);
                int maxC = Math.max(r, Math.max(g, b));
                int minC = Math.min(r, Math.min(g, b));
                int sat = maxC - minC;
                // Active Send Gift button has vibrant pink/magenta or bright teal-green icon pixels (sat > 42, maxC > 135)
                if (sat > 42 && maxC > 135) {
                    colorfulPixelCount++;
                }
                totalSamples++;
            }
        }

        if (totalSamples == 0) return true;
        float colorfulRatio = (float) colorfulPixelCount / totalSamples;
        // If at least 8% of the button region has vibrant color, the Send Gift button is active!
        return colorfulRatio >= 0.075f;
    }

    /**
     * Executes the 3-step Send Gift workflow using the user's exact learned coordinates (or calibrated defaults):
     * 1. Tap 'Send Gift' button on Profile (Step 3)
     * 2. Wait for Gift Postcard Bag to open -> Tap Gift Postcard (Step 4)
     * 3. Wait for Postcard Preview -> Tap green 'SEND' button (Step 5)
     * 4. Wait for Send animation -> Tap Close Profile '✕' (Step 6)
     */
    private void executeLeftSendGiftSequence(final int w, final int h, final float sendGiftX, final float sendGiftY) {
        final float[] learnedCard = LearnedProfileStore.getGiftStepPoint(this, 4, w * 0.50f, h * 0.36f);
        final float[] learnedConfirm = LearnedProfileStore.getGiftStepPoint(this, 5, w * 0.50f, h * 0.81f);

        notifyStatus("🎁 1/3: Tapping 'Send Gift' (" + Math.round(sendGiftX) + "," + Math.round(sendGiftY) + ")");
        dispatchHumanTap(sendGiftX, sendGiftY, 4f);

        giftHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAutoGiftRunning) return;
                notifyStatus("💌 2/3: Selecting Postcard (" + Math.round(learnedCard[0]) + "," + Math.round(learnedCard[1]) + ")");
                dispatchHumanTap(learnedCard[0], learnedCard[1], 5f);

                giftHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAutoGiftRunning) return;
                        notifyStatus("🚀 3/3: Tapping Green 'SEND' (" + Math.round(learnedConfirm[0]) + "," + Math.round(learnedConfirm[1]) + ")");
                        dispatchHumanTap(learnedConfirm[0], learnedConfirm[1], 5f);
                        giftsSent++;
                        friendsProcessed++;
                        PokeMateDatabase.getInstance(PokemonGoAccessibilityService.this)
                                .logAction("GIFT_SENT", "Sent Gift #" + giftsSent);

                        giftHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAutoGiftRunning) return;
                                closeProfileAndAdvance(w, h);
                            }
                        }, pace(2150L));
                    }
                }, pace(1450L));
            }
        }, pace(1750L));
    }

    private void closeProfileAndAdvance(final int w, final int h) {
        float[] learnedClose = LearnedProfileStore.getGiftStepPoint(this, 6, w * 0.50f, h * 0.905f);
        notifyStatus("✕ Closing Profile -> Next Friend");
        dispatchHumanTap(learnedClose[0], learnedClose[1], 5f);
        currentRowSlot++;
        if (currentRowSlot % 3 == 0) {
            giftHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isAutoGiftRunning) return;
                    notifyStatus("📜 Scrolling Friends List Down ↓");
                    performCurvedBezierScroll(w, h);
                    scheduleOpenNextFriend(pace(1450L));
                }
            }, pace(950L));
        } else {
            scheduleOpenNextFriend(pace(1350L));
        }
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
        float startX = w * 0.50f + (random.nextFloat() * 24f - 12f);
        float startY = h * 0.72f;
        float endX = startX + (random.nextFloat() * 20f - 10f);
        float endY = h * 0.29f;

        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.quadTo((startX + endX) / 2f + 24f, (startY + endY) / 2f, endX, endY);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(swipePath, 0L, 390L);
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
