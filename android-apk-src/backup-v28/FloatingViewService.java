package com.pokemate.companion;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class FloatingViewService extends Service implements PokemonGoAccessibilityService.StatusListener {

    public static volatile FloatingViewService instance;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout rootWrapper;

    // Minimized Pill (Customizable buttons + expand handle)
    private LinearLayout compactMiniPillBar;
    private GradientDrawable miniPillBg;
    private Button miniSpinThrowOrStopBtn;
    private Button miniGiftOrStopBtn;
    private Button miniScanBtn;
    private Button miniExpandHandleBtn;

    // Expanded Frosted-Glass HUD Panel
    private LinearLayout expandedCard;
    private GradientDrawable expandedPanelBg;
    private Button settingsTabToggleBtn;
    private TextView statusBannerView;

    // 2 Views inside Expanded Panel: Main HUD Container vs Settings Container
    private LinearLayout mainHudContentContainer;
    private ScrollView settingsContentScroll;

    // Main HUD controls
    private Button mainThrowOrStopBtn;
    private Button mainGiftOrStopBtn;
    private Button mainQuickCatchToggleBtn;
    private Button mainSpinThrowToggleBtn;
    private Button mainDistanceProfileBtn;
    private Button teachThrowBtn;
    private Button teachGiftBtn;
    private Button scanResearchBtn;
    private EditText searchEditText;
    private LinearLayout researchCardsContainer;

    // Settings UI controls inside HUD
    private TextView opacityValueLabel;
    private SeekBar opacitySeekBar;
    private Button toggleMiniThrowBtn;
    private Button toggleMiniGiftBtn;
    private Button toggleMiniScanBtn;
    private Button settingsExcellentWaitBtn;
    private TextView powerCalibrationLabel;
    private Button settingsQuickCatchBtn;
    private Button settingsSpinThrowBtn;
    private Button settingsGiftModeBtn;
    private Button settingsDelaySpeedBtn;
    private Button settingsAutoHidePoGoBtn;

    private FrameLayout activeTrainingOverlay;
    private LinearLayout activePostThrowRatePopup;

    private boolean isExpanded = true;
    private boolean isShowingSettingsInHud = false;
    private boolean isCurrentlyFocusable = false;
    private boolean isTemporarilyHiddenForScreenshot = false;
    private String activeCategoryFilter = "ALL";
    private String activeSearchQuery = "";

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        PokemonGoAccessibilityService.giftWorkflowMode = LearnedProfileStore.getGiftWorkflowModePref(this);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        int overlayType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams.x = 18;
        layoutParams.y = 145;

        buildOverlayHierarchy();
        windowManager.addView(rootWrapper, layoutParams);

        PokemonGoAccessibilityService.statusListener = this;
        applyUserSettingsLive();
        updateOverlayVisibilityState();
        updateStatusBannerOnce(PokemonGoAccessibilityService.currentStepText, PokemonGoAccessibilityService.isAutoGiftRunning);
    }

    public void onForegroundAppChanged(final boolean inPokemonGo) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!inPokemonGo && LearnedProfileStore.isAutoHideOutsidePoGoEnabled(FloatingViewService.this)) {
                    setOverlayKeyboardFocusable(false);
                    removeTrainingOverlayIfPresent();
                    dismissPostThrowRatePopup();
                }
                updateOverlayVisibilityState();
            }
        });
    }

    public void setTemporarilyHiddenForScreenshot(boolean hidden) {
        this.isTemporarilyHiddenForScreenshot = hidden;
        updateOverlayVisibilityState();
    }

    public void updateOverlayVisibilityState() {
        if (rootWrapper == null) return;
        if (isTemporarilyHiddenForScreenshot) {
            rootWrapper.setVisibility(View.INVISIBLE);
            return;
        }
        boolean autoHide = LearnedProfileStore.isAutoHideOutsidePoGoEnabled(this);
        if (autoHide && !PokemonGoAccessibilityService.isPokemonGoInForeground) {
            rootWrapper.setVisibility(View.GONE);
        } else {
            rootWrapper.setVisibility(View.VISIBLE);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void applyLegibleTextShadow(TextView tv) {
        tv.setShadowLayer(4.5f, 0f, 1.5f, Color.parseColor("#EE000000"));
    }

    private void setOverlayKeyboardFocusable(boolean focusable) {
        if (layoutParams == null || windowManager == null || rootWrapper == null) return;
        if (this.isCurrentlyFocusable == focusable) return;
        this.isCurrentlyFocusable = focusable;

        if (focusable) {
            layoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && searchEditText != null) {
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
        }
        windowManager.updateViewLayout(rootWrapper, layoutParams);
    }

    private boolean ensureAccessibilityReady() {
        if (PokemonGoAccessibilityService.instance != null) return true;
        Toast.makeText(
                FloatingViewService.this,
                "Enable 'PalpiGO Auto-Gift & Quick Catch' in Accessibility Settings first!",
                Toast.LENGTH_LONG
        ).show();
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return false;
    }

    public void onThrowSequenceFinished(final boolean showRatePopup) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                setTemporarilyHiddenForScreenshot(false);
                refreshThrowAndGiftButtons();
                if (showRatePopup) {
                    schedulePostThrowRatePopup();
                }
            }
        });
    }

    private void handleThrowOrStopClicked() {
        setOverlayKeyboardFocusable(false);
        dismissPostThrowRatePopup();
        if (!ensureAccessibilityReady()) return;
        final PokemonGoAccessibilityService a11y = PokemonGoAccessibilityService.instance;

        if (PokemonGoAccessibilityService.isThrowRunning) {
            a11y.stopActiveThrow();
            refreshThrowAndGiftButtons();
            return;
        }

        PokemonGoAccessibilityService.isThrowRunning = true;
        refreshThrowAndGiftButtons();

        setTemporarilyHiddenForScreenshot(true);
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                PokemonGoAccessibilityService.isThrowRunning = false;
                a11y.performAutoExcellentSpinThrow(null);
            }
        }, 45L);
    }

    private void toggleAutoGiftAction() {
        setOverlayKeyboardFocusable(false);
        if (!ensureAccessibilityReady()) return;
        PokemonGoAccessibilityService a11y = PokemonGoAccessibilityService.instance;
        if (PokemonGoAccessibilityService.isAutoGiftRunning) {
            a11y.stopActiveGiftLoop();
            Toast.makeText(FloatingViewService.this, "Auto-Gift Stopped", Toast.LENGTH_SHORT).show();
        } else {
            if (isExpanded) {
                setExpandedState(false);
            }
            a11y.startActiveGiftLoop();
        }
        refreshThrowAndGiftButtons();
    }

    private void triggerOcrResearchScan() {
        setOverlayKeyboardFocusable(false);
        if (!ensureAccessibilityReady()) return;
        final PokemonGoAccessibilityService a11y = PokemonGoAccessibilityService.instance;

        statusBannerView.setText("🔭 Scanning Pokémon GO Research Screen (OCR)...");
        setTemporarilyHiddenForScreenshot(true);

        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                a11y.scanGameScreenWithOcr(new PokemonGoAccessibilityService.OcrScanCallback() {
                    @Override
                    public void onScreenshotCaptured() {
                        setTemporarilyHiddenForScreenshot(false);
                    }

                    @Override
                    public void onScanResult(String matchedQueryForSearchBar, int matchedCount, String errorOrRaw) {
                        setTemporarilyHiddenForScreenshot(false);
                        if (!isExpanded) {
                            setExpandedState(true);
                        }
                        if (isShowingSettingsInHud) {
                            setHudSettingsMode(false);
                        }
                        activeCategoryFilter = "ALL";
                        if (matchedQueryForSearchBar != null && matchedQueryForSearchBar.length() > 0) {
                            searchEditText.setText(matchedQueryForSearchBar);
                            statusBannerView.setText("✓ Scanned & Pasted " + matchedCount + " Research Task(s)!");
                            Toast.makeText(
                                    FloatingViewService.this,
                                    "Pasted " + matchedCount + " active research task(s) into search bar!",
                                    Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            statusBannerView.setText("⚠ Open Pokémon GO Field Research list and tap Scan");
                            Toast.makeText(
                                    FloatingViewService.this,
                                    "No research text detected. Open your Pokémon GO Field Research list and tap Scan!",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                });
            }
        }, 95L);
    }

    public void applyUserSettingsLive() {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                int opacityPct = LearnedProfileStore.getHudOpacityPercent(FloatingViewService.this);
                int alphaByte = Math.max(55, Math.min(250, Math.round((opacityPct / 100f) * 255f)));
                if (expandedPanelBg != null) {
                    expandedPanelBg.setColor(Color.argb(alphaByte, 8, 17, 30));
                }
                if (miniPillBg != null) {
                    int miniAlpha = Math.max(150, Math.min(250, alphaByte + 35));
                    miniPillBg.setColor(Color.argb(miniAlpha, 8, 17, 30));
                }
                if (opacityValueLabel != null) {
                    opacityValueLabel.setText("Big Mode Opacity: " + opacityPct + "% (" + (100 - opacityPct) + "% Transparent)");
                }
                if (opacitySeekBar != null && opacitySeekBar.getProgress() != (opacityPct - 25)) {
                    opacitySeekBar.setProgress(opacityPct - 25);
                }

                boolean showThrow = LearnedProfileStore.isMiniShowThrow(FloatingViewService.this);
                boolean showGift = LearnedProfileStore.isMiniShowGift(FloatingViewService.this);
                boolean showScan = LearnedProfileStore.isMiniShowScan(FloatingViewService.this);

                if (miniSpinThrowOrStopBtn != null) {
                    miniSpinThrowOrStopBtn.setVisibility(showThrow ? View.VISIBLE : View.GONE);
                }
                if (miniGiftOrStopBtn != null) {
                    miniGiftOrStopBtn.setVisibility(showGift ? View.VISIBLE : View.GONE);
                }
                if (miniScanBtn != null) {
                    miniScanBtn.setVisibility(showScan ? View.VISIBLE : View.GONE);
                }

                if (toggleMiniThrowBtn != null) {
                    styleToggleChip(toggleMiniThrowBtn, showThrow, "🎯 Throw/Stop: " + (showThrow ? "ON" : "OFF"));
                }
                if (toggleMiniGiftBtn != null) {
                    styleToggleChip(toggleMiniGiftBtn, showGift, "▶ Gift/Stop: " + (showGift ? "ON" : "OFF"));
                }
                if (toggleMiniScanBtn != null) {
                    styleToggleChip(toggleMiniScanBtn, showScan, "🔭 Scan: " + (showScan ? "ON" : "OFF"));
                }

                boolean waitExc = LearnedProfileStore.isWaitForExcellentEnabled(FloatingViewService.this);
                if (settingsExcellentWaitBtn != null) {
                    styleToggleChip(
                            settingsExcellentWaitBtn,
                            waitExc,
                            waitExc
                                    ? "🎯 Smallest Circle Lock (Excellent Timing): ON"
                                    : "🎯 Smallest Circle Lock: OFF (Instant Throw)"
                    );
                }

                int pwrPct = Math.round(LearnedProfileStore.getThrowPowerMultiplier(FloatingViewService.this) * 100f);
                if (powerCalibrationLabel != null) {
                    powerCalibrationLabel.setText("Throw Distance / Power: " + pwrPct + "%");
                }

                boolean qc = LearnedProfileStore.isQuickCatchEnabled(FloatingViewService.this);
                if (mainQuickCatchToggleBtn != null) {
                    styleToggleChip(
                            mainQuickCatchToggleBtn,
                            qc,
                            qc ? "⚡ Fast Catch: ON" : "⚡ Fast Catch: OFF"
                    );
                }
                if (settingsQuickCatchBtn != null) {
                    styleToggleChip(
                            settingsQuickCatchBtn,
                            qc,
                            qc ? "⚡ Fast Catch: ON (2-Finger Berry Hold + Run)" : "⚡ Fast Catch: OFF (Single Clean Throw)"
                    );
                }

                boolean spinOn = LearnedProfileStore.isSpinThrowEnabled(FloatingViewService.this);
                if (mainSpinThrowToggleBtn != null) {
                    styleToggleChip(
                            mainSpinThrowToggleBtn,
                            spinOn,
                            spinOn ? "🌀 Spin: ON" : "🌀 Spin: OFF"
                    );
                }
                if (settingsSpinThrowBtn != null) {
                    styleToggleChip(
                            settingsSpinThrowBtn,
                            spinOn,
                            spinOn ? "🌀 Throw Style: Spin (Curveball) ON" : "⬆️ Throw Style: Straight Throw (Spin OFF)"
                    );
                }
                if (mainDistanceProfileBtn != null) {
                    String distTag = LearnedProfileStore.getActiveDistanceProfileShortTag(FloatingViewService.this);
                    styleToggleChip(mainDistanceProfileBtn, true, "📏 " + distTag);
                }

                int gMode = LearnedProfileStore.getGiftWorkflowModePref(FloatingViewService.this);
                PokemonGoAccessibilityService.giftWorkflowMode = gMode;
                if (settingsGiftModeBtn != null) {
                    if (gMode == PokemonGoAccessibilityService.MODE_PIN_OPEN_AND_SEND) {
                        settingsGiftModeBtn.setText("🎁 Gift Mode: 📌 Pin/Unpin + Open + Send Gift");
                    } else if (gMode == PokemonGoAccessibilityService.MODE_PIN_AND_OPEN_ONLY) {
                        settingsGiftModeBtn.setText("🎁 Gift Mode: 📌 Pin/Unpin + Open Incoming Only");
                    } else {
                        settingsGiftModeBtn.setText("🎁 Gift Mode: 🎁 Left Send Gift Only (Skip Open)");
                    }
                }

                boolean safeDelay = LearnedProfileStore.isSafeHumanDelayEnabled(FloatingViewService.this);
                if (settingsDelaySpeedBtn != null) {
                    styleToggleChip(
                            settingsDelaySpeedBtn,
                            safeDelay,
                            safeDelay ? "⏱ Pacing: Safe Human Mode (+30% Delay)" : "⏱ Pacing: Standard Fast Speed"
                    );
                }

                boolean autoHidePoGo = LearnedProfileStore.isAutoHideOutsidePoGoEnabled(FloatingViewService.this);
                if (settingsAutoHidePoGoBtn != null) {
                    styleToggleChip(
                            settingsAutoHidePoGoBtn,
                            autoHidePoGo,
                            autoHidePoGo
                                    ? "👁 Show Overlay: Only in Pokémon GO (Auto-Hide ON)"
                                    : "👁 Show Overlay: Everywhere (Always Visible)"
                    );
                }

                updateOverlayVisibilityState();
                refreshTeachButtonLabels();
                refreshThrowAndGiftButtons();
            }
        });
    }

    private void styleToggleChip(Button btn, boolean isOn, String text) {
        btn.setText(text);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        if (isOn) {
            d.setColor(Color.parseColor("#CC065F46"));
            d.setStroke(dp(1), Color.parseColor("#34D399"));
            btn.setTextColor(Color.parseColor("#A7F3D0"));
        } else {
            d.setColor(Color.parseColor("#991E293B"));
            d.setStroke(dp(1), Color.parseColor("#475569"));
            btn.setTextColor(Color.parseColor("#94A3B8"));
        }
        btn.setBackground(d);
    }

    private void refreshThrowAndGiftButtons() {
        boolean throwing = PokemonGoAccessibilityService.isThrowRunning;
        boolean gifting = PokemonGoAccessibilityService.isAutoGiftRunning;
        boolean qc = LearnedProfileStore.isQuickCatchEnabled(this);
        boolean spinOn = LearnedProfileStore.isSpinThrowEnabled(this);
        String distShort = LearnedProfileStore.getActiveDistanceProfileShortTag(this);
        String miniModeLabel = (spinOn ? "🌀 Spin" : "🎯 Throw") + (qc ? " ⚡" : "");
        String mainModeLabel = (spinOn ? "🌀 Spin (" + distShort + ")" : "🎯 Throw (" + distShort + ")") + (qc ? " ⚡" : "");

        if (miniSpinThrowOrStopBtn != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            if (throwing) {
                miniSpinThrowOrStopBtn.setText("■ Stop");
                miniSpinThrowOrStopBtn.setTextColor(Color.WHITE);
                bg.setColor(Color.parseColor("#E11D48"));
                bg.setStroke(dp(1), Color.parseColor("#FDA4AF"));
            } else {
                miniSpinThrowOrStopBtn.setText(miniModeLabel);
                miniSpinThrowOrStopBtn.setTextColor(Color.parseColor("#04131D"));
                bg.setColor(Color.parseColor("#10B981"));
                bg.setStroke(dp(1), Color.parseColor("#6EE7B7"));
            }
            miniSpinThrowOrStopBtn.setBackground(bg);
        }

        if (miniGiftOrStopBtn != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            if (gifting) {
                miniGiftOrStopBtn.setText("■ Stop");
                miniGiftOrStopBtn.setTextColor(Color.WHITE);
                bg.setColor(Color.parseColor("#E11D48"));
            } else {
                miniGiftOrStopBtn.setText("▶ Gift");
                miniGiftOrStopBtn.setTextColor(Color.parseColor("#04131D"));
                bg.setColor(Color.parseColor("#38BDF8"));
            }
            miniGiftOrStopBtn.setBackground(bg);
        }

        if (mainThrowOrStopBtn != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            if (throwing) {
                mainThrowOrStopBtn.setText("■ Stop Throw");
                mainThrowOrStopBtn.setTextColor(Color.WHITE);
                bg.setColor(Color.parseColor("#E11D48"));
                bg.setStroke(dp(1), Color.parseColor("#FDA4AF"));
            } else {
                mainThrowOrStopBtn.setText(mainModeLabel);
                mainThrowOrStopBtn.setTextColor(Color.parseColor("#04131D"));
                bg.setColor(Color.parseColor("#F59E0B"));
                bg.setStroke(dp(1), Color.parseColor("#FDE68A"));
            }
            mainThrowOrStopBtn.setBackground(bg);
        }

        if (mainGiftOrStopBtn != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            if (gifting) {
                mainGiftOrStopBtn.setText("■ Stop Gift");
                mainGiftOrStopBtn.setTextColor(Color.WHITE);
                bg.setColor(Color.parseColor("#E11D48"));
                bg.setStroke(dp(1), Color.parseColor("#FDA4AF"));
            } else {
                mainGiftOrStopBtn.setText("▶ Auto-Gift");
                mainGiftOrStopBtn.setTextColor(Color.parseColor("#04131D"));
                bg.setColor(Color.parseColor("#10B981"));
                bg.setStroke(dp(1), Color.parseColor("#6EE7B7"));
            }
            mainGiftOrStopBtn.setBackground(bg);
        }
    }

    // =========================================================================
    // INTERACTIVE '🎓 TEACH THROW' WITH LIVE SHRINKING TARGET CIRCLE & BALL PHYSICS
    // =========================================================================

    private void openTeachThrowOverlay() {
        setOverlayKeyboardFocusable(false);
        if (!ensureAccessibilityReady()) return;
        removeTrainingOverlayIfPresent();
        rootWrapper.setVisibility(View.GONE);

        final DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = dm.widthPixels > 0 ? dm.widthPixels : 1080;
        int h = dm.heightPixels > 0 ? dm.heightPixels : 2400;
        float refX = LearnedProfileStore.getReferenceRingX(this, w * 0.50f);
        float refY = LearnedProfileStore.getReferenceRingY(this, h * 0.40f);
        float refRad = LearnedProfileStore.getReferenceRingRadius(this, w * 0.175f);
        PokemonGoAccessibilityService.TargetRingState ringState =
                new PokemonGoAccessibilityService.TargetRingState(true, refX, refY, refRad * 0.35f, refRad, 0.35f, null);
        showInteractiveTeachThrowCanvas(ringState);
    }

    private void showInteractiveTeachThrowCanvas(final PokemonGoAccessibilityService.TargetRingState detectedRing) {
        removeTrainingOverlayIfPresent();
        dismissPostThrowRatePopup();

        int overlayType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams trainLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        if (Build.VERSION.SDK_INT >= 28) {
            trainLp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        activeTrainingOverlay = new FrameLayout(this);
        activeTrainingOverlay.setBackgroundColor(Color.TRANSPARENT);

        final DisplayMetrics dm = getResources().getDisplayMetrics();
        final int screenW = dm.widthPixels > 0 ? dm.widthPixels : 1080;
        final int screenH = dm.heightPixels > 0 ? dm.heightPixels : 2400;

        final float[] ringStateHolder = new float[]{
                detectedRing.centerX,
                detectedRing.centerY,
                Math.max(dp(44), detectedRing.outerWhiteRadius)
        };

        float defaultBallX = screenW * 0.50f;
        float defaultBallY = screenH * 0.872f;
        if (LearnedProfileStore.hasLearnedThrow(this)) {
            List<LearnedProfileStore.PointSample> prevPts = LearnedProfileStore.loadLearnedThrowPoints(this);
            if (prevPts != null && !prevPts.isEmpty()) {
                LearnedProfileStore.PointSample p0 = prevPts.get(0);
                if (p0.y >= screenH * 0.62f && p0.y <= screenH * 0.96f) {
                    defaultBallX = p0.x;
                    defaultBallY = p0.y;
                }
            }
        }
        final float homeBallX = defaultBallX;
        final float homeBallY = defaultBallY;

        final List<LearnedProfileStore.PointSample> recordedPts = new ArrayList<LearnedProfileStore.PointSample>();
        final Path visualPath = new Path();
        final Path activePreviewPath = new Path();
        final List<LearnedProfileStore.PointSample> activePreviewPts = new ArrayList<LearnedProfileStore.PointSample>();
        final long[] activePreviewDurMs = new long[]{185L};
        final boolean[] isTeachingBerrySpot = new boolean[]{false};
        final boolean[] isSettingCircleCenter = new boolean[]{false};

        final Runnable rebuildPreviewTrajectory = new Runnable() {
            @Override
            public void run() {
                activePreviewPts.clear();
                activePreviewPath.reset();
                LearnedProfileStore.CalibratedStroke cs = LearnedProfileStore.getCurrentActiveThrowForPreview(
                        FloatingViewService.this,
                        screenW,
                        screenH
                );
                if (cs != null && cs.points != null && cs.points.size() >= 2) {
                    activePreviewPts.addAll(cs.points);
                    activePreviewDurMs[0] = Math.max(55L, cs.durationMs);
                    activePreviewPath.moveTo(cs.points.get(0).x, cs.points.get(0).y);
                    for (int i = 1; i < cs.points.size(); i++) {
                        activePreviewPath.lineTo(cs.points.get(i).x, cs.points.get(i).y);
                    }
                }
            }
        };
        rebuildPreviewTrajectory.run();

        final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRingPaint.setColor(Color.WHITE);
        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(dp(3));

        final Paint shrinkingRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shrinkingRingPaint.setStyle(Paint.Style.STROKE);
        shrinkingRingPaint.setStrokeWidth(dp(4));

        final Paint excellentZonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        excellentZonePaint.setColor(Color.parseColor("#8810B981"));
        excellentZonePaint.setStyle(Paint.Style.STROKE);
        excellentZonePaint.setStrokeWidth(dp(2));

        final Paint previewTrailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        previewTrailPaint.setColor(Color.parseColor("#CC38BDF8"));
        previewTrailPaint.setStyle(Paint.Style.STROKE);
        previewTrailPaint.setStrokeWidth(dp(4));
        previewTrailPaint.setStrokeCap(Paint.Cap.ROUND);
        previewTrailPaint.setStrokeJoin(Paint.Join.ROUND);

        final Paint releaseMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        releaseMarkerPaint.setColor(Color.parseColor("#FBBF24"));
        releaseMarkerPaint.setStyle(Paint.Style.STROKE);
        releaseMarkerPaint.setStrokeWidth(dp(2));

        final Paint ghostBallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ghostBallPaint.setColor(Color.parseColor("#9934D399"));
        ghostBallPaint.setStyle(Paint.Style.FILL);

        final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trailPaint.setColor(Color.parseColor("#34D399"));
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(dp(5));
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStrokeJoin(Paint.Join.ROUND);

        final Paint ballRedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballRedPaint.setColor(Color.parseColor("#EF4444"));
        ballRedPaint.setStyle(Paint.Style.FILL);

        final Paint ballWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballWhitePaint.setColor(Color.WHITE);
        ballWhitePaint.setStyle(Paint.Style.FILL);

        final Paint ballStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballStrokePaint.setColor(Color.BLACK);
        ballStrokePaint.setStyle(Paint.Style.STROKE);
        ballStrokePaint.setStrokeWidth(dp(3));

        final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(12));
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);

        final LinearLayout topBanner = new LinearLayout(this);
        final TextView titleTv = new TextView(this);
        final TextView descTv = new TextView(this);

        final long animStartMs = System.currentTimeMillis();

        final View drawingCanvas = new View(this) {
            private float ballX = homeBallX;
            private float ballY = homeBallY;
            private float spinAngleDeg = 0f;
            private final int[] winLoc = new int[2];

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                // Compensate for any status bar / display cutout window offset so raw screen coordinates
                // align 100% pixel-for-pixel with the real Pokémon GO game screen!
                getLocationOnScreen(winLoc);
                canvas.save();
                canvas.translate(-winLoc[0], -winLoc[1]);

                float ringCx = ringStateHolder[0];
                float ringCy = ringStateHolder[1];
                float outerRad = ringStateHolder[2];

                // 1. Animate the shrinking Target Circle at (ringCx, ringCy) in raw screen space over the live game!
                long elapsed = (System.currentTimeMillis() - animStartMs) % 1680L;
                float currentRatio = 1.0f - (0.86f * (elapsed / 1680f));
                float innerRad = outerRad * currentRatio;

                canvas.drawCircle(ringCx, ringCy, outerRad, outerRingPaint);
                canvas.drawCircle(ringCx, ringCy, outerRad * 0.28f, excellentZonePaint);

                boolean isExcellentNow = currentRatio <= 0.28f;
                if (isExcellentNow) {
                    shrinkingRingPaint.setColor(Color.parseColor("#10B981"));
                    canvas.drawText("★ EXCELLENT WINDOW NOW! ★", ringCx, ringCy - outerRad - dp(10), textPaint);
                } else if (currentRatio <= 0.65f) {
                    shrinkingRingPaint.setColor(Color.parseColor("#F59E0B"));
                } else {
                    shrinkingRingPaint.setColor(Color.parseColor("#38BDF8"));
                }
                canvas.drawCircle(ringCx, ringCy, innerRad, shrinkingRingPaint);

                // 2. Draw Live Saved Trajectory Preview + Animated Ghost Ball + Release Point Marker
                if (recordedPts.isEmpty() && activePreviewPts.size() >= 2) {
                    canvas.drawPath(activePreviewPath, previewTrailPaint);
                    LearnedProfileStore.PointSample releasePt = activePreviewPts.get(activePreviewPts.size() - 1);
                    int rm = dp(12);
                    canvas.drawCircle(releasePt.x, releasePt.y, rm, releaseMarkerPaint);
                    canvas.drawLine(releasePt.x - rm - dp(4), releasePt.y, releasePt.x + rm + dp(4), releasePt.y, releaseMarkerPaint);
                    canvas.drawLine(releasePt.x, releasePt.y - rm - dp(4), releasePt.x, releasePt.y + rm + dp(4), releaseMarkerPaint);
                    canvas.drawText(
                            "⊕ RELEASE (" + activePreviewDurMs[0] + "ms)",
                            releasePt.x,
                            releasePt.y - rm - dp(6),
                            textPaint
                    );

                    // Animate Ghost PokéBall along the active trajectory
                    long cycleMs = Math.max(120L, activePreviewDurMs[0]) + 450L;
                    long cyclePos = (System.currentTimeMillis() - animStartMs) % cycleMs;
                    if (cyclePos <= activePreviewDurMs[0]) {
                        float frac = (float) cyclePos / (float) Math.max(1L, activePreviewDurMs[0]);
                        int idx = Math.min(activePreviewPts.size() - 1, Math.round(frac * (activePreviewPts.size() - 1)));
                        LearnedProfileStore.PointSample gp = activePreviewPts.get(idx);
                        canvas.drawCircle(gp.x, gp.y, dp(14), ghostBallPaint);
                    }
                }

                // 3. Draw user's newly drawn throw trail in raw screen coordinates
                canvas.drawPath(visualPath, trailPaint);

                // 4. Draw Interactive PokéBall
                int r = dp(26);
                canvas.save();
                canvas.translate(ballX, ballY);
                canvas.rotate(spinAngleDeg);
                canvas.drawCircle(0, 0, r, ballWhitePaint);
                canvas.drawArc(-r, -r, r, r, 180, 180, true, ballRedPaint);
                canvas.drawCircle(0, 0, r, ballStrokePaint);
                canvas.drawLine(-r, 0, r, 0, ballStrokePaint);
                canvas.drawCircle(0, 0, dp(8), ballWhitePaint);
                canvas.drawCircle(0, 0, dp(8), ballStrokePaint);
                canvas.restore();

                canvas.restore();

                if (activeTrainingOverlay != null && activeTrainingOverlay.getVisibility() == View.VISIBLE && getVisibility() == View.VISIBLE) {
                    postInvalidateDelayed(16L);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                float rx = event.getRawX();
                float ry = event.getRawY();

                if (isTeachingBerrySpot[0]) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        LearnedProfileStore.saveBerryButtonPoint(FloatingViewService.this, rx, ry);
                        isTeachingBerrySpot[0] = false;
                        descTv.setText("✓ Saved Berry Button at (" + Math.round(rx) + "," + Math.round(ry) + ")! Draw throw or nudge below:");
                        Toast.makeText(FloatingViewService.this, "✓ Saved Berry Button spot!", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                if (isSettingCircleCenter[0]) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                        ringStateHolder[0] = rx;
                        ringStateHolder[1] = ry;
                        invalidate();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        ringStateHolder[0] = rx;
                        ringStateHolder[1] = ry;
                        isSettingCircleCenter[0] = false;
                        descTv.setText("✓ Circle center locked at (" + Math.round(rx) + "," + Math.round(ry) + ")! Draw throw or nudge below:");
                        invalidate();
                    }
                    return true;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        recordedPts.clear();
                        visualPath.reset();
                        ballX = rx;
                        ballY = ry;
                        recordedPts.add(new LearnedProfileStore.PointSample(rx, ry, 0L));
                        visualPath.moveTo(rx, ry);
                        invalidate();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int histSize = event.getHistorySize();
                        for (int h = 0; h < histSize; h++) {
                            float hx = event.getHistoricalX(h) + winLoc[0];
                            float hy = event.getHistoricalY(h) + winLoc[1];
                            long ht = Math.max(1L, event.getHistoricalEventTime(h) - event.getDownTime());
                            recordedPts.add(new LearnedProfileStore.PointSample(hx, hy, ht));
                            visualPath.lineTo(hx, hy);
                        }
                        long moveElapsed = Math.max(1L, event.getEventTime() - event.getDownTime());
                        ballX = rx;
                        ballY = ry;
                        spinAngleDeg = (spinAngleDeg + 28f) % 360f;
                        recordedPts.add(new LearnedProfileStore.PointSample(rx, ry, moveElapsed));
                        visualPath.lineTo(rx, ry);
                        invalidate();
                        return true;
                    case MotionEvent.ACTION_UP:
                        final long rawDur = Math.max(45L, event.getEventTime() - event.getDownTime());
                        recordedPts.add(new LearnedProfileStore.PointSample(rx, ry, rawDur));
                        ballX = homeBallX;
                        ballY = homeBallY;
                        invalidate();

                        if (!LearnedProfileStore.isValidRealThrowAttempt(recordedPts, screenW, screenH)) {
                            recordedPts.clear();
                            visualPath.reset();
                            invalidate();
                            Toast.makeText(
                                    FloatingViewService.this,
                                    "⚠ Start on the PokéBall at the bottom and flick UP toward the Pokémon!",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return true;
                        }

                        // 1. Calibrate & IMMEDIATELY save the user's exact drawn throw 1:1 right on ACTION_UP!
                        final LearnedProfileStore.CalibratedStroke calibrated =
                                LearnedProfileStore.calibrateUserTaughtStroke(recordedPts, rawDur, screenW, screenH);
                        String savedMsg = LearnedProfileStore.recordGradedThrowWithRingAndRadius(
                                FloatingViewService.this,
                                recordedPts,
                                rawDur,
                                ringStateHolder[0],
                                ringStateHolder[1],
                                ringStateHolder[2],
                                screenW,
                                screenH,
                                LearnedProfileStore.GRADE_EXCELLENT,
                                true
                        );
                        LearnedProfileStore.setLastExecutedThrowCandidateWithRadius(
                                calibrated.points,
                                calibrated.durationMs,
                                ringStateHolder[0],
                                ringStateHolder[1],
                                ringStateHolder[2],
                                screenW,
                                screenH
                        );
                        applyUserSettingsLive();
                        Toast.makeText(FloatingViewService.this, "✓ Copied 1:1! " + savedMsg, Toast.LENGTH_SHORT).show();

                        // 2. Close the teaching overlay, hide HUD during throw/Fast Catch, and immediately execute the exact taught throw!
                        removeTrainingOverlayIfPresent();
                        setTemporarilyHiddenForScreenshot(true);

                        uiHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                PokemonGoAccessibilityService a11y = PokemonGoAccessibilityService.instance;
                                boolean useQuickCatch = LearnedProfileStore.isQuickCatchEnabled(FloatingViewService.this);
                                if (a11y != null) {
                                    a11y.replayStrokePoints(calibrated.points, calibrated.durationMs, 0f, useQuickCatch, true);
                                } else {
                                    onThrowSequenceFinished(false);
                                }
                            }
                        }, 55L);
                        return true;
                }
                return false;
            }
        };
        activeTrainingOverlay.addView(drawingCanvas, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Top Instruction & Live Trajectory Studio Card
        topBanner.setOrientation(LinearLayout.VERTICAL);
        topBanner.setPadding(dp(11), dp(9), dp(11), dp(9));
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(Color.parseColor("#E808111E"));
        bannerBg.setCornerRadius(dp(16));
        bannerBg.setStroke(dp(1), Color.parseColor("#10B981"));
        topBanner.setBackground(bannerBg);

        titleTv.setText("🎓 1:1 THROW STUDIO (" + LearnedProfileStore.getActiveDistanceProfileLabel(this) + ")");
        titleTv.setTextColor(Color.parseColor("#34D399"));
        titleTv.setTextSize(11.5f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        applyLegibleTextShadow(titleTv);
        topBanner.addView(titleTv);

        descTv.setText("Draw your throw below (saves 1:1 & throws immediately), OR nudge the live cyan curve:");
        descTv.setTextColor(Color.WHITE);
        descTv.setTextSize(10f);
        descTv.setPadding(0, dp(2), 0, dp(5));
        applyLegibleTextShadow(descTv);
        topBanner.addView(descTv);

        // Row 1: Live Visual Trajectory Nudge Bar (Higher / Lower / Left / Right / Faster / Test)
        LinearLayout nudgeRow = new LinearLayout(this);
        nudgeRow.setOrientation(LinearLayout.HORIZONTAL);
        nudgeRow.setPadding(0, 0, 0, dp(5));

        final Object[][] nudges = new Object[][]{
                {"⬆ Higher", 0.06f, 0f, -8L, "#065F46"},
                {"⬇ Lower", -0.06f, 0f, 8L, "#1E3A8A"},
                {"⬅ Left", 0f, -0.035f, 0L, "#334155"},
                {"➡ Right", 0f, 0.035f, 0L, "#334155"},
                {"⚡ Faster", 0f, 0f, -15L, "#78350F"}
        };

        for (int n = 0; n < nudges.length; n++) {
            final String nLbl = (String) nudges[n][0];
            final float vDelta = (Float) nudges[n][1];
            final float hDelta = (Float) nudges[n][2];
            final long sDelta = (Long) nudges[n][3];
            final String bgHex = (String) nudges[n][4];

            Button nBtn = createCompactTopButton(nLbl, bgHex, Color.WHITE);
            nBtn.setTextSize(9.5f);
            nBtn.setPadding(dp(6), dp(4), dp(6), dp(4));
            LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nLp.setMargins(0, 0, dp(3), 0);
            nBtn.setLayoutParams(nLp);
            nBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String msg = LearnedProfileStore.nudgeActiveThrow(
                            FloatingViewService.this,
                            vDelta,
                            hDelta,
                            sDelta,
                            screenW,
                            screenH
                    );
                    rebuildPreviewTrajectory.run();
                    drawingCanvas.invalidate();
                    applyUserSettingsLive();
                    descTv.setText(msg);
                }
            });
            nudgeRow.addView(nBtn);
        }

        Button testThrowBtn = createCompactTopButton("▶ Test", "#10B981", Color.parseColor("#04131D"));
        testThrowBtn.setTextSize(9.5f);
        testThrowBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        testThrowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeTrainingOverlayIfPresent();
                handleThrowOrStopClicked();
            }
        });
        nudgeRow.addView(testThrowBtn);
        topBanner.addView(nudgeRow);

        // Row 2: Distance Switcher, Move Ring, Berry Spot, Reset, Close
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        final Button distCycleBtn = createCompactTopButton(
                "📏 " + LearnedProfileStore.getActiveDistanceProfileShortTag(this),
                "#4C1D95",
                Color.parseColor("#DDD6FE")
        );
        distCycleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int nextD = LearnedProfileStore.cycleActiveDistanceProfile(FloatingViewService.this);
                distCycleBtn.setText("📏 " + LearnedProfileStore.getActiveDistanceProfileShortTag(FloatingViewService.this));
                titleTv.setText("🎓 1:1 THROW STUDIO (" + LearnedProfileStore.getCircleBucketLabel(nextD) + ")");
                rebuildPreviewTrajectory.run();
                drawingCanvas.invalidate();
                applyUserSettingsLive();
            }
        });
        btnRow.addView(distCycleBtn);

        Button setCircleBtn = createCompactTopButton("📍 Ring", "#0D9488", Color.WHITE);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        scLp.setMargins(dp(4), 0, 0, 0);
        setCircleBtn.setLayoutParams(scLp);
        setCircleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSettingCircleCenter[0] = true;
                descTv.setText("👉 Tap or drag directly on the center of the Pokémon's circle to align the guide ring!");
            }
        });
        btnRow.addView(setCircleBtn);

        Button setBerryBtn = createCompactTopButton("🍓 Berry", "#0284C7", Color.WHITE);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bLp.setMargins(dp(4), 0, 0, 0);
        setBerryBtn.setLayoutParams(bLp);
        setBerryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isTeachingBerrySpot[0] = true;
                descTv.setText("👉 Tap once right on the BERRY BUTTON (bottom-left) for Quick Catch!");
            }
        });
        btnRow.addView(setBerryBtn);

        Button resetBtn = createCompactTopButton("🗑 Reset", "#7F1D1D", Color.WHITE);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rLp.setMargins(dp(4), 0, 0, 0);
        resetBtn.setLayoutParams(rLp);
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.resetLearnedThrow(FloatingViewService.this);
                rebuildPreviewTrajectory.run();
                drawingCanvas.invalidate();
                applyUserSettingsLive();
                Toast.makeText(FloatingViewService.this, "Reset learned throws to defaults!", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(resetBtn);

        Button cancelBtn = createCompactTopButton("✕", "#1E293B", Color.WHITE);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cLp.setMargins(dp(4), 0, 0, 0);
        cancelBtn.setLayoutParams(cLp);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeTrainingOverlayIfPresent();
                updateOverlayVisibilityState();
            }
        });
        btnRow.addView(cancelBtn);
        topBanner.addView(btnRow);

        FrameLayout.LayoutParams bannerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        );
        bannerLp.setMargins(dp(10), dp(34), dp(10), 0);
        activeTrainingOverlay.addView(topBanner, bannerLp);

        windowManager.addView(activeTrainingOverlay, trainLp);
    }

    // =========================================================================
    // AUTOMATIC POST-THROW QUICK-RATE POPUP (POPS UP AFTER EVERY AUTO-THROW!)
    // =========================================================================

    public void schedulePostThrowRatePopup() {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showPostThrowRatePopup();
            }
        }, 780L);
    }

    public void dismissPostThrowRatePopup() {
        if (activePostThrowRatePopup != null && windowManager != null) {
            try {
                windowManager.removeView(activePostThrowRatePopup);
            } catch (Exception ignored) {}
            activePostThrowRatePopup = null;
        }
    }

    private void showPostThrowRatePopup() {
        dismissPostThrowRatePopup();
        if (activeTrainingOverlay != null) return;
        if (LearnedProfileStore.isAutoHideOutsidePoGoEnabled(this)
                && !PokemonGoAccessibilityService.isPokemonGoInForeground) {
            return;
        }

        int overlayType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams popLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        popLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        popLp.y = dp(46);

        activePostThrowRatePopup = new LinearLayout(this);
        activePostThrowRatePopup.setOrientation(LinearLayout.VERTICAL);
        activePostThrowRatePopup.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#EB08111E"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        activePostThrowRatePopup.setBackground(bg);

        LinearLayout topHeader = new LinearLayout(this);
        topHeader.setOrientation(LinearLayout.HORIZONTAL);
        topHeader.setGravity(Gravity.CENTER_VERTICAL);
        topHeader.setPadding(0, 0, 0, dp(5));

        TextView title = new TextView(this);
        title.setText("🎯 Rate Throw (Trains AI for This Circle Size):");
        title.setTextColor(Color.parseColor("#7DD3FC"));
        title.setTextSize(11f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        applyLegibleTextShadow(title);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        topHeader.addView(title, tLp);

        Button closePopBtn = createCompactTopButton("✕", "#1E293B", Color.parseColor("#94A3B8"));
        closePopBtn.setPadding(dp(7), dp(2), dp(7), dp(2));
        closePopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissPostThrowRatePopup();
            }
        });
        topHeader.addView(closePopBtn);
        activePostThrowRatePopup.addView(topHeader);

        LinearLayout rowHits = new LinearLayout(this);
        rowHits.setOrientation(LinearLayout.HORIZONTAL);
        rowHits.setPadding(0, 0, 0, dp(4));

        final Object[][] hitButtons = new Object[][]{
                {"🌟 Exc", LearnedProfileStore.GRADE_EXCELLENT, "#10B981", Color.parseColor("#04131D")},
                {"🔥 Great", LearnedProfileStore.GRADE_GREAT, "#F59E0B", Color.parseColor("#04131D")},
                {"👍 Nice", LearnedProfileStore.GRADE_NICE, "#0284C7", Color.WHITE},
                {"⚪ Hit (Bad)", LearnedProfileStore.GRADE_HIT_NO_BONUS, "#475569", Color.WHITE}
        };

        for (int i = 0; i < hitButtons.length; i++) {
            final String lbl = (String) hitButtons[i][0];
            final int grade = (Integer) hitButtons[i][1];
            final String cHex = (String) hitButtons[i][2];
            final int tCol = (Integer) hitButtons[i][3];

            Button btn = createCompactTopButton(lbl, cHex, tCol);
            btn.setTextSize(10f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < hitButtons.length - 1) lp.setMargins(0, 0, dp(4), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String msg = LearnedProfileStore.rateLastExecutedThrow(FloatingViewService.this, grade);
                    applyUserSettingsLive();
                    if (statusBannerView != null) statusBannerView.setText(msg);
                    Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
                    dismissPostThrowRatePopup();
                }
            });
            rowHits.addView(btn);
        }
        activePostThrowRatePopup.addView(rowHits);

        LinearLayout rowMiss = new LinearLayout(this);
        rowMiss.setOrientation(LinearLayout.HORIZONTAL);

        final Object[][] missButtons = new Object[][]{
                {"⬆ Short (Harder!)", LearnedProfileStore.GRADE_SHORT, "#1E40AF", Color.WHITE},
                {"⬇ Far (Softer)", LearnedProfileStore.GRADE_FAR, "#334155", Color.WHITE},
                {"❌ Missed", LearnedProfileStore.GRADE_MISS, "#E11D48", Color.WHITE}
        };

        for (int i = 0; i < missButtons.length; i++) {
            final String lbl = (String) missButtons[i][0];
            final int grade = (Integer) missButtons[i][1];
            final String cHex = (String) missButtons[i][2];
            final int tCol = (Integer) missButtons[i][3];

            Button btn = createCompactTopButton(lbl, cHex, tCol);
            btn.setTextSize(10f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < missButtons.length - 1) lp.setMargins(0, 0, dp(4), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String msg = LearnedProfileStore.rateLastExecutedThrow(FloatingViewService.this, grade);
                    applyUserSettingsLive();
                    if (statusBannerView != null) statusBannerView.setText(msg);
                    Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
                    dismissPostThrowRatePopup();
                }
            });
            rowMiss.addView(btn);
        }
        activePostThrowRatePopup.addView(rowMiss);

        try {
            windowManager.addView(activePostThrowRatePopup, popLp);
        } catch (Exception ignored) {}

        // Auto-dismiss after 9 seconds if untouched
        final LinearLayout popupRef = activePostThrowRatePopup;
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activePostThrowRatePopup == popupRef) {
                    dismissPostThrowRatePopup();
                }
            }
        }, 9000L);
    }

    // =========================================================================
    // INTERACTIVE '🎓 TEACH GIFT' 7-STEP GUIDED TRAINER OVERLAY
    // =========================================================================

    private void openTeachGiftOverlay() {
        setOverlayKeyboardFocusable(false);
        if (!ensureAccessibilityReady()) return;
        removeTrainingOverlayIfPresent();

        rootWrapper.setVisibility(View.INVISIBLE);

        int overlayType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams trainLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        activeTrainingOverlay = new FrameLayout(this);
        activeTrainingOverlay.setBackgroundColor(Color.parseColor("#280284C7"));

        final String[] stepInstructions = new String[]{
                "TAP 1: Tap the 1st FRIEND ROW at the very TOP of your Friends List",
                "OPTIONAL PIN: Tap the 📌 PIN Button (to the right of OPEN)",
                "OPTIONAL OPEN: Tap the center 'OPEN' Button on the Incoming Gift",
                "TAP 2: On the Trainer Profile, tap the LEFT '🎁 SEND GIFT' Button\n(If an Incoming Gift popped up instead, tap '📌 Has Incoming Gift' below!)",
                "TAP 3: Tap a GIFT POSTCARD from your bag to select it",
                "TAP 4: Tap the green 'SEND' Button at the bottom",
                "TAP 5: Tap the bottom '( ✕ )' CLOSE Button to return to Friends List"
        };

        final int[] currentStep = new int[]{0};
        final TextView stepInstructionTv = new TextView(this);

        View tapCatcherView = new View(this) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
                final float rx = event.getRawX();
                final float ry = event.getRawY();
                final int step = currentStep[0];

                LearnedProfileStore.saveLearnedGiftStepPoint(FloatingViewService.this, step, rx, ry);
                activeTrainingOverlay.setVisibility(View.INVISIBLE);

                uiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final PokemonGoAccessibilityService a11y = PokemonGoAccessibilityService.instance;
                        if (a11y != null) {
                            a11y.dispatchHumanTap(rx, ry, 0f);
                        }

                        if (step == 1 && a11y != null) {
                            uiHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    a11y.dispatchHumanTap(rx, ry, 0f);
                                }
                            }, 850L);
                        } else if (step == 2 && a11y != null) {
                            uiHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    a11y.dispatchHumanTap(rx, ry, 0f);
                                    uiHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            a11y.dispatchHumanTap(rx, ry, 0f);
                                        }
                                    }, 650L);
                                }
                            }, 1300L);
                        }

                        long waitForGameScreenMs = (step == 0) ? 2250L
                                : (step == 1) ? 1650L
                                : (step == 2) ? 2800L
                                : (step == 3) ? 1750L
                                : (step == 4) ? 1450L
                                : (step == 5) ? 2150L
                                : 900L;

                        uiHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (activeTrainingOverlay == null) return;
                                if (step >= 6) {
                                    int totalTrained = LearnedProfileStore.incrementGiftTeachCount(FloatingViewService.this);
                                    removeTrainingOverlayIfPresent();
                                    refreshTeachButtonLabels();
                                    rootWrapper.setVisibility(View.VISIBLE);
                                    if (a11y != null) {
                                        a11y.notifyStatus("✓ Gift Workflow Learned (Trained ×" + totalTrained + ")!");
                                    }
                                    Toast.makeText(
                                            FloatingViewService.this,
                                            "✓ Gift Coordinates Saved! Tap '▶ Auto-Gift' to run!",
                                            Toast.LENGTH_LONG
                                    ).show();
                                } else {
                                    // CRITICAL FIX: After tapping Friend Row (step 0), jump directly to Step 3 ('🎁 SEND GIFT')
                                    // so the user's Send Gift tap is NEVER mistakenly saved into the Pin/Open slot!
                                    if (step == 0) {
                                        currentStep[0] = 3;
                                    } else {
                                        currentStep[0] = step + 1;
                                    }
                                    stepInstructionTv.setText(stepInstructions[currentStep[0]]);
                                    activeTrainingOverlay.setVisibility(View.VISIBLE);
                                }
                            }
                        }, waitForGameScreenMs);
                    }
                }, 90L);

                return true;
            }
        };
        activeTrainingOverlay.addView(tapCatcherView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topBanner = new LinearLayout(this);
        topBanner.setOrientation(LinearLayout.VERTICAL);
        topBanner.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(Color.parseColor("#E608111E"));
        bannerBg.setCornerRadius(dp(16));
        bannerBg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        topBanner.setBackground(bannerBg);

        int giftCount = LearnedProfileStore.getLearnedGiftCount(this);
        TextView titleTv = new TextView(this);
        titleTv.setText("🎓 TEACH GIFT MODE (Trained ×" + giftCount + ")");
        titleTv.setTextColor(Color.parseColor("#38BDF8"));
        titleTv.setTextSize(13f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        applyLegibleTextShadow(titleTv);
        topBanner.addView(titleTv);

        stepInstructionTv.setText(stepInstructions[0]);
        stepInstructionTv.setTextColor(Color.WHITE);
        stepInstructionTv.setTextSize(11.5f);
        stepInstructionTv.setTypeface(Typeface.DEFAULT_BOLD);
        stepInstructionTv.setPadding(0, dp(5), 0, dp(8));
        applyLegibleTextShadow(stepInstructionTv);
        topBanner.addView(stepInstructionTv);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button teachIncomingBtn = createCompactTopButton("📌 Has Incoming Gift", "#0284C7", Color.WHITE);
        teachIncomingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentStep[0] = 1;
                stepInstructionTv.setText(stepInstructions[1]);
            }
        });
        btnRow.addView(teachIncomingBtn);

        Button jumpSendBtn = createCompactTopButton("🎁 On Profile (Send)", "#065F46", Color.WHITE);
        LinearLayout.LayoutParams jsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        jsLp.setMargins(dp(5), 0, 0, 0);
        jumpSendBtn.setLayoutParams(jsLp);
        jumpSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentStep[0] = 3;
                stepInstructionTv.setText(stepInstructions[3]);
            }
        });
        btnRow.addView(jumpSendBtn);

        Button resetBtn = createCompactTopButton("🗑 Reset", "#7F1D1D", Color.WHITE);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rLp.setMargins(dp(6), 0, 0, 0);
        resetBtn.setLayoutParams(rLp);
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.resetLearnedGifts(FloatingViewService.this);
                refreshTeachButtonLabels();
                Toast.makeText(FloatingViewService.this, "Reset learned gift coordinates!", Toast.LENGTH_SHORT).show();
                removeTrainingOverlayIfPresent();
                rootWrapper.setVisibility(View.VISIBLE);
            }
        });
        btnRow.addView(resetBtn);

        Button doneBtn = createCompactTopButton("✕ Close", "#1E293B", Color.WHITE);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dLp.setMargins(dp(6), 0, 0, 0);
        doneBtn.setLayoutParams(dLp);
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentStep[0] > 0) {
                    LearnedProfileStore.incrementGiftTeachCount(FloatingViewService.this);
                }
                refreshTeachButtonLabels();
                removeTrainingOverlayIfPresent();
                rootWrapper.setVisibility(View.VISIBLE);
            }
        });
        btnRow.addView(doneBtn);

        topBanner.addView(btnRow);

        FrameLayout.LayoutParams bannerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        );
        bannerLp.setMargins(dp(12), dp(40), dp(12), 0);
        activeTrainingOverlay.addView(topBanner, bannerLp);

        windowManager.addView(activeTrainingOverlay, trainLp);
    }

    private void removeTrainingOverlayIfPresent() {
        if (activeTrainingOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(activeTrainingOverlay);
            } catch (Exception ignored) {}
            activeTrainingOverlay = null;
        }
    }

    private void refreshTeachButtonLabels() {
        if (teachThrowBtn != null) {
            teachThrowBtn.setText(LearnedProfileStore.getThrowStatsBadge(this));
        }
        if (teachGiftBtn != null) {
            int gc = LearnedProfileStore.getLearnedGiftCount(this);
            teachGiftBtn.setText(gc > 0 ? ("🎓 Teach Gift (×" + gc + ")") : "🎓 Teach Gift");
        }
        refreshThrowAndGiftButtons();
    }

    private void setHudSettingsMode(boolean showSettings) {
        this.isShowingSettingsInHud = showSettings;
        if (showSettings) {
            setOverlayKeyboardFocusable(false);
            mainHudContentContainer.setVisibility(View.GONE);
            settingsContentScroll.setVisibility(View.VISIBLE);
            if (settingsTabToggleBtn != null) {
                settingsTabToggleBtn.setText("← HUD");
                settingsTabToggleBtn.setTextColor(Color.parseColor("#34D399"));
            }
            applyUserSettingsLive();
        } else {
            settingsContentScroll.setVisibility(View.GONE);
            mainHudContentContainer.setVisibility(View.VISIBLE);
            if (settingsTabToggleBtn != null) {
                settingsTabToggleBtn.setText("⚙ Settings");
                settingsTabToggleBtn.setTextColor(Color.parseColor("#38BDF8"));
            }
        }
    }

    private void buildOverlayHierarchy() {
        rootWrapper = new LinearLayout(this);
        rootWrapper.setOrientation(LinearLayout.VERTICAL);

        View.OnTouchListener dragAndTapListener = new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        layoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(rootWrapper, layoutParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dx = Math.abs(event.getRawX() - initialTouchX);
                        float dy = Math.abs(event.getRawY() - initialTouchY);
                        if (dx < dp(10) && dy < dp(10) && (System.currentTimeMillis() - downTime) < 320) {
                            if (!isExpanded) {
                                setExpandedState(true);
                            }
                        } else if (!isExpanded) {
                            DisplayMetrics dm = getResources().getDisplayMetrics();
                            int screenW = dm.widthPixels > 0 ? dm.widthPixels : 1080;
                            layoutParams.x = (layoutParams.x < screenW / 2) ? dp(8) : (screenW - dp(165));
                            windowManager.updateViewLayout(rootWrapper, layoutParams);
                        }
                        return true;
                }
                return false;
            }
        };

        // =========================================================================
        // 1. MINIMIZED PILL (Customizable Buttons via Settings!)
        // =========================================================================
        compactMiniPillBar = new LinearLayout(this);
        compactMiniPillBar.setOrientation(LinearLayout.HORIZONTAL);
        compactMiniPillBar.setGravity(Gravity.CENTER_VERTICAL);
        compactMiniPillBar.setPadding(dp(5), dp(4), dp(5), dp(4));

        miniPillBg = new GradientDrawable();
        miniPillBg.setColor(Color.parseColor("#CC08111E"));
        miniPillBg.setCornerRadius(dp(24));
        miniPillBg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        compactMiniPillBar.setBackground(miniPillBg);
        compactMiniPillBar.setVisibility(View.GONE);

        miniSpinThrowOrStopBtn = new Button(this);
        miniSpinThrowOrStopBtn.setText("🎯 Throw");
        miniSpinThrowOrStopBtn.setTextSize(12f);
        miniSpinThrowOrStopBtn.setTypeface(Typeface.DEFAULT_BOLD);
        miniSpinThrowOrStopBtn.setAllCaps(false);
        miniSpinThrowOrStopBtn.setGravity(Gravity.CENTER);
        miniSpinThrowOrStopBtn.setMinWidth(0);
        miniSpinThrowOrStopBtn.setMinHeight(0);
        miniSpinThrowOrStopBtn.setMinimumWidth(0);
        miniSpinThrowOrStopBtn.setMinimumHeight(0);
        miniSpinThrowOrStopBtn.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams mtLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
        );
        miniSpinThrowOrStopBtn.setLayoutParams(mtLp);
        miniSpinThrowOrStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleThrowOrStopClicked();
            }
        });

        miniGiftOrStopBtn = new Button(this);
        miniGiftOrStopBtn.setText("▶ Gift");
        miniGiftOrStopBtn.setTextSize(11.5f);
        miniGiftOrStopBtn.setTypeface(Typeface.DEFAULT_BOLD);
        miniGiftOrStopBtn.setAllCaps(false);
        miniGiftOrStopBtn.setGravity(Gravity.CENTER);
        miniGiftOrStopBtn.setMinWidth(0);
        miniGiftOrStopBtn.setMinHeight(0);
        miniGiftOrStopBtn.setMinimumWidth(0);
        miniGiftOrStopBtn.setMinimumHeight(0);
        miniGiftOrStopBtn.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams mgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
        );
        mgLp.setMargins(dp(4), 0, 0, 0);
        miniGiftOrStopBtn.setLayoutParams(mgLp);
        miniGiftOrStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAutoGiftAction();
            }
        });

        miniScanBtn = new Button(this);
        miniScanBtn.setText("🔭");
        miniScanBtn.setTextColor(Color.WHITE);
        miniScanBtn.setTextSize(11.5f);
        miniScanBtn.setAllCaps(false);
        miniScanBtn.setGravity(Gravity.CENTER);
        miniScanBtn.setMinWidth(0);
        miniScanBtn.setMinHeight(0);
        miniScanBtn.setMinimumWidth(0);
        miniScanBtn.setMinimumHeight(0);
        miniScanBtn.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable msBg = new GradientDrawable();
        msBg.setColor(Color.parseColor("#0284C7"));
        msBg.setCornerRadius(dp(18));
        miniScanBtn.setBackground(msBg);
        LinearLayout.LayoutParams msLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
        );
        msLp.setMargins(dp(4), 0, 0, 0);
        miniScanBtn.setLayoutParams(msLp);
        miniScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerOcrResearchScan();
            }
        });

        // Compact 26dp x 26dp circular button — noticeably smaller than the 36dp-tall Throw button!
        miniExpandHandleBtn = new Button(this);
        miniExpandHandleBtn.setText("⤢");
        miniExpandHandleBtn.setTextColor(Color.parseColor("#E2E8F0"));
        miniExpandHandleBtn.setTextSize(10.5f);
        miniExpandHandleBtn.setTypeface(Typeface.DEFAULT_BOLD);
        miniExpandHandleBtn.setAllCaps(false);
        miniExpandHandleBtn.setGravity(Gravity.CENTER);
        miniExpandHandleBtn.setMinWidth(0);
        miniExpandHandleBtn.setMinHeight(0);
        miniExpandHandleBtn.setMinimumWidth(0);
        miniExpandHandleBtn.setMinimumHeight(0);
        miniExpandHandleBtn.setPadding(0, 0, 0, 0);
        GradientDrawable meBg = new GradientDrawable();
        meBg.setColor(Color.parseColor("#CC1E293B"));
        meBg.setCornerRadius(dp(13));
        meBg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        miniExpandHandleBtn.setBackground(meBg);
        LinearLayout.LayoutParams meLp = new LinearLayout.LayoutParams(dp(26), dp(26));
        meLp.setMargins(dp(4), 0, dp(1), 0);
        miniExpandHandleBtn.setLayoutParams(meLp);
        miniExpandHandleBtn.setOnTouchListener(dragAndTapListener);

        compactMiniPillBar.addView(miniSpinThrowOrStopBtn);
        compactMiniPillBar.addView(miniGiftOrStopBtn);
        compactMiniPillBar.addView(miniScanBtn);
        compactMiniPillBar.addView(miniExpandHandleBtn);
        rootWrapper.addView(compactMiniPillBar);

        // =========================================================================
        // 2. EXPANDED FROSTED-GLASS HUD PANEL (v16.0)
        // =========================================================================
        expandedCard = new LinearLayout(this);
        expandedCard.setOrientation(LinearLayout.VERTICAL);
        expandedCard.setPadding(dp(12), dp(10), dp(12), dp(12));

        expandedPanelBg = new GradientDrawable();
        expandedPanelBg.setColor(Color.parseColor("#AD08111E"));
        expandedPanelBg.setCornerRadius(dp(20));
        expandedPanelBg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        expandedCard.setBackground(expandedPanelBg);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int panelWidth = Math.min(dp(346), (int) (dm.widthPixels * 0.93f));

        // Top Glass Header Bar: Palpitoad Logo + PalpiGO v16.0 + ⚙ Settings + ─ Min + ✕
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(dp(2), dp(2), dp(2), dp(7));

        ImageView hudLogo = new ImageView(this);
        hudLogo.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        logoLp.setMargins(0, 0, dp(7), 0);
        hudLogo.setOnTouchListener(dragAndTapListener);
        headerBar.addView(hudLogo, logoLp);

        TextView dragTitle = new TextView(this);
        dragTitle.setText("PalpiGO v28.0");
        dragTitle.setTextColor(Color.WHITE);
        dragTitle.setTextSize(13f);
        dragTitle.setTypeface(Typeface.DEFAULT_BOLD);
        applyLegibleTextShadow(dragTitle);
        dragTitle.setOnTouchListener(dragAndTapListener);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerBar.addView(dragTitle, titleLp);

        settingsTabToggleBtn = createCompactTopButton("⚙ Settings", "#991E293B", Color.parseColor("#38BDF8"));
        settingsTabToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setHudSettingsMode(!isShowingSettingsInHud);
            }
        });
        headerBar.addView(settingsTabToggleBtn);

        Button minBtn = createCompactTopButton("─ Min", "#991E293B", Color.parseColor("#34D399"));
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        minLp.setMargins(dp(4), 0, 0, 0);
        minBtn.setLayoutParams(minLp);
        minBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setOverlayKeyboardFocusable(false);
                setExpandedState(false);
            }
        });
        headerBar.addView(minBtn);

        Button closeBtn = createCompactTopButton("✕", "#991E293B", Color.parseColor("#F87171"));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(dp(4), 0, 0, 0);
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PokemonGoAccessibilityService a11y = PokemonGoAccessibilityService.instance;
                if (a11y != null) {
                    a11y.stopActiveThrow();
                    a11y.stopActiveGiftLoop();
                }
                stopSelf();
            }
        });
        headerBar.addView(closeBtn);
        expandedCard.addView(headerBar);

        // Sleek Translucent Status Pill
        statusBannerView = new TextView(this);
        statusBannerView.setTextColor(Color.parseColor("#6EE7B7"));
        statusBannerView.setTextSize(10.5f);
        statusBannerView.setTypeface(Typeface.DEFAULT_BOLD);
        statusBannerView.setPadding(dp(10), dp(5), dp(10), dp(5));
        applyLegibleTextShadow(statusBannerView);
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(Color.parseColor("#880F172A"));
        statusBg.setCornerRadius(dp(10));
        statusBg.setStroke(dp(1), Color.parseColor("#4438BDF8"));
        statusBannerView.setBackground(statusBg);
        expandedCard.addView(statusBannerView);

        // =========================================================================
        // VIEW A: MAIN HUD CONTENT CONTAINER
        // =========================================================================
        mainHudContentContainer = new LinearLayout(this);
        mainHudContentContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout primaryDeckRow = new LinearLayout(this);
        primaryDeckRow.setOrientation(LinearLayout.HORIZONTAL);
        primaryDeckRow.setPadding(0, dp(7), 0, dp(5));

        mainThrowOrStopBtn = createPillButton("🎯 Throw Ball", "#F59E0B", Color.parseColor("#04131D"));
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f);
        b1Lp.setMargins(0, 0, dp(4), 0);
        mainThrowOrStopBtn.setLayoutParams(b1Lp);
        mainThrowOrStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleThrowOrStopClicked();
            }
        });

        mainGiftOrStopBtn = createPillButton("▶ Auto-Gift", "#10B981", Color.parseColor("#04131D"));
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f);
        b2Lp.setMargins(0, 0, dp(4), 0);
        mainGiftOrStopBtn.setLayoutParams(b2Lp);
        mainGiftOrStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAutoGiftAction();
            }
        });

        scanResearchBtn = createPillButton("🔭 Scan", "#0284C7", Color.WHITE);
        LinearLayout.LayoutParams b3Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scanResearchBtn.setLayoutParams(b3Lp);
        scanResearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerOcrResearchScan();
            }
        });

        primaryDeckRow.addView(mainThrowOrStopBtn);
        primaryDeckRow.addView(mainGiftOrStopBtn);
        primaryDeckRow.addView(scanResearchBtn);
        mainHudContentContainer.addView(primaryDeckRow);

        // AI Trainer Row ('🎓 Teach Throw' + '🎓 Teach Gift')
        LinearLayout teachRow = new LinearLayout(this);
        teachRow.setOrientation(LinearLayout.HORIZONTAL);
        teachRow.setPadding(0, 0, 0, dp(6));

        teachThrowBtn = createCompactTopButton("🎓 Teach Throw", "#AA4C1D95", Color.parseColor("#DDD6FE"));
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ttLp.setMargins(0, 0, dp(4), 0);
        teachThrowBtn.setLayoutParams(ttLp);
        teachThrowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTeachThrowOverlay();
            }
        });

        teachGiftBtn = createCompactTopButton("🎓 Teach Gift", "#AA1E3A8A", Color.parseColor("#BAE6FD"));
        LinearLayout.LayoutParams tgLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        teachGiftBtn.setLayoutParams(tgLp);
        teachGiftBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTeachGiftOverlay();
            }
        });

        teachRow.addView(teachThrowBtn);
        teachRow.addView(teachGiftBtn);
        mainHudContentContainer.addView(teachRow);

        // 1-Tap Fast Catch & Spin Throw Quick-Toggle Row (Always Visible on Main HUD!)
        LinearLayout quickTogglesRow = new LinearLayout(this);
        quickTogglesRow.setOrientation(LinearLayout.HORIZONTAL);
        quickTogglesRow.setPadding(0, 0, 0, dp(6));

        mainQuickCatchToggleBtn = createCompactTopButton("⚡ Fast Catch: ON", "#CC065F46", Color.parseColor("#A7F3D0"));
        LinearLayout.LayoutParams mqcLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mqcLp.setMargins(0, 0, dp(4), 0);
        mainQuickCatchToggleBtn.setLayoutParams(mqcLp);
        mainQuickCatchToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isQuickCatchEnabled(FloatingViewService.this);
                LearnedProfileStore.setQuickCatchEnabled(FloatingViewService.this, next);
                applyUserSettingsLive();
                String msg = next ? "⚡ Fast Catch: ON (2-Finger Berry Hold + Escape)" : "⚡ Fast Catch: OFF (Single Throw)";
                if (statusBannerView != null) statusBannerView.setText(msg);
                Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        mainSpinThrowToggleBtn = createCompactTopButton("🌀 Spin: OFF", "#991E293B", Color.parseColor("#94A3B8"));
        LinearLayout.LayoutParams mstLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.95f);
        mstLp.setMargins(0, 0, dp(4), 0);
        mainSpinThrowToggleBtn.setLayoutParams(mstLp);
        mainSpinThrowToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isSpinThrowEnabled(FloatingViewService.this);
                LearnedProfileStore.setSpinThrowEnabled(FloatingViewService.this, next);
                applyUserSettingsLive();
                String msg = next ? "🌀 Spin (Curveball) Throw: ON" : "⬆️ Straight Throw Mode: ON (Spin OFF)";
                if (statusBannerView != null) statusBannerView.setText(msg);
                Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        mainDistanceProfileBtn = createCompactTopButton("📏 Med", "#CC065F46", Color.parseColor("#A7F3D0"));
        LinearLayout.LayoutParams mdpLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.75f);
        mainDistanceProfileBtn.setLayoutParams(mdpLp);
        mainDistanceProfileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.cycleActiveDistanceProfile(FloatingViewService.this);
                applyUserSettingsLive();
                String msg = "📏 Active Throw Distance: " + LearnedProfileStore.getActiveDistanceProfileLabel(FloatingViewService.this);
                if (statusBannerView != null) statusBannerView.setText(msg);
                Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        quickTogglesRow.addView(mainQuickCatchToggleBtn);
        quickTogglesRow.addView(mainSpinThrowToggleBtn);
        quickTogglesRow.addView(mainDistanceProfileBtn);
        mainHudContentContainer.addView(quickTogglesRow);

        // 1-Tap Post-Throw Outcome Rating Bar (Nice / Great / Excellent / Short / Far)
        LinearLayout rateThrowRow = new LinearLayout(this);
        rateThrowRow.setOrientation(LinearLayout.HORIZONTAL);
        rateThrowRow.setGravity(Gravity.CENTER_VERTICAL);
        rateThrowRow.setPadding(0, 0, 0, dp(6));

        TextView rateLbl = new TextView(this);
        rateLbl.setText("Rate Throw:");
        rateLbl.setTextColor(Color.parseColor("#94A3B8"));
        rateLbl.setTextSize(9.5f);
        rateLbl.setTypeface(Typeface.DEFAULT_BOLD);
        rateLbl.setPadding(0, 0, dp(4), 0);
        applyLegibleTextShadow(rateLbl);
        rateThrowRow.addView(rateLbl);

        final Object[][] gradeChips = new Object[][]{
                {"🌟 Exc", LearnedProfileStore.GRADE_EXCELLENT, "#CC065F46", Color.parseColor("#6EE7B7")},
                {"🔥 Grt", LearnedProfileStore.GRADE_GREAT, "#CC78350F", Color.parseColor("#FDE68A")},
                {"👍 Nice", LearnedProfileStore.GRADE_NICE, "#CC0C4A6E", Color.parseColor("#BAE6FD")},
                {"⚪ Hit", LearnedProfileStore.GRADE_HIT_NO_BONUS, "#CC334155", Color.parseColor("#E2E8F0")},
                {"⬆ Short", LearnedProfileStore.GRADE_SHORT, "#CC1E40AF", Color.parseColor("#DBEAFE")},
                {"⬇ Far", LearnedProfileStore.GRADE_FAR, "#991E293B", Color.parseColor("#CBD5E1")},
                {"❌ Miss", LearnedProfileStore.GRADE_MISS, "#CC9F1239", Color.parseColor("#FECDD3")}
        };

        for (int g = 0; g < gradeChips.length; g++) {
            final String gText = (String) gradeChips[g][0];
            final int gCode = (Integer) gradeChips[g][1];
            final String gBg = (String) gradeChips[g][2];
            final int gColor = (Integer) gradeChips[g][3];

            Button gBtn = createCompactTopButton(gText, gBg, gColor);
            gBtn.setTextSize(9.5f);
            gBtn.setPadding(dp(6), dp(4), dp(6), dp(4));
            LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (g < gradeChips.length - 1) gLp.setMargins(0, 0, dp(3), 0);
            gBtn.setLayoutParams(gLp);
            gBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String feedback = LearnedProfileStore.rateLastExecutedThrow(FloatingViewService.this, gCode);
                    applyUserSettingsLive();
                    if (statusBannerView != null) {
                        statusBannerView.setText(feedback);
                    }
                    Toast.makeText(FloatingViewService.this, feedback, Toast.LENGTH_SHORT).show();
                }
            });
            rateThrowRow.addView(gBtn);
        }
        mainHudContentContainer.addView(rateThrowRow);

        // Search Bar
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, 0, 0, dp(6));

        searchEditText = new EditText(this);
        searchEditText.setHint("🔍 Search or tap '🔭 Scan' for active tasks...");
        searchEditText.setHintTextColor(Color.parseColor("#94A3B8"));
        searchEditText.setTextColor(Color.WHITE);
        searchEditText.setTextSize(11f);
        searchEditText.setSingleLine(true);
        searchEditText.setPadding(dp(10), dp(6), dp(10), dp(6));
        applyLegibleTextShadow(searchEditText);

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.parseColor("#990F172A"));
        searchBg.setCornerRadius(dp(10));
        searchBg.setStroke(dp(1), Color.parseColor("#475569"));
        searchEditText.setBackground(searchBg);

        searchEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setOverlayKeyboardFocusable(true);
                searchEditText.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                activeSearchQuery = s != null ? s.toString() : "";
                renderResearchCards();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        searchLp.setMargins(0, 0, dp(5), 0);
        searchRow.addView(searchEditText, searchLp);

        Button clearOrDoneBtn = createCompactTopButton("Clear", "#991E293B", Color.parseColor("#CBD5E1"));
        clearOrDoneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activeSearchQuery.length() > 0) {
                    searchEditText.setText("");
                }
                setOverlayKeyboardFocusable(false);
            }
        });
        searchRow.addView(clearOrDoneBtn);
        mainHudContentContainer.addView(searchRow);

        // Category Filter Chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, 0, 0, dp(6));

        final String[][] categories = new String[][]{
                {"ALL", "All Tasks"},
                {"Event", "🟨 Event"},
                {"Bonus", "🟩 Bonus"},
                {"META", "Meta 🔥"},
                {"Catch", "Catch"},
                {"Throw", "Throw"},
                {"Raid", "Raid/Battle"},
                {"Explore", "Hatch/Spin"},
                {"Power/Buddy", "Power/Buddy"},
                {"SHINY", "Shiny ✨"}
        };

        for (int i = 0; i < categories.length; i++) {
            final String catKey = categories[i][0];
            final String catLabel = categories[i][1];
            Button chip = createCompactTopButton(catLabel, "#991E293B", Color.WHITE);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            chipLp.setMargins(0, 0, dp(5), 0);
            chip.setLayoutParams(chipLp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activeCategoryFilter = catKey;
                    setOverlayKeyboardFocusable(false);
                    renderResearchCards();
                }
            });
            chipRow.addView(chip);
        }
        chipScroll.addView(chipRow);
        mainHudContentContainer.addView(chipScroll);

        ScrollView cardsScroll = new ScrollView(this);
        researchCardsContainer = new LinearLayout(this);
        researchCardsContainer.setOrientation(LinearLayout.VERTICAL);
        cardsScroll.addView(researchCardsContainer);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                panelWidth,
                dp(195)
        );
        mainHudContentContainer.addView(cardsScroll, scrollLp);
        expandedCard.addView(mainHudContentContainer);

        // =========================================================================
        // VIEW B: IN-HUD '⚙ SETTINGS' PANEL
        // =========================================================================
        settingsContentScroll = new ScrollView(this);
        settingsContentScroll.setVisibility(View.GONE);

        LinearLayout settingsCol = new LinearLayout(this);
        settingsCol.setOrientation(LinearLayout.VERTICAL);
        settingsCol.setPadding(0, dp(8), 0, dp(4));

        // SECTION 1: HUD Transparency / Opacity Slider
        settingsCol.addView(createSettingsSectionHeader("1. BIG MODE TRANSPARENCY / OPACITY"));

        opacityValueLabel = new TextView(this);
        opacityValueLabel.setTextColor(Color.WHITE);
        opacityValueLabel.setTextSize(11f);
        opacityValueLabel.setTypeface(Typeface.DEFAULT_BOLD);
        applyLegibleTextShadow(opacityValueLabel);
        settingsCol.addView(opacityValueLabel);

        opacitySeekBar = new SeekBar(this);
        opacitySeekBar.setMax(73); // 25% to 98%
        opacitySeekBar.setProgress(LearnedProfileStore.getHudOpacityPercent(this) - 25);
        opacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = 25 + progress;
                LearnedProfileStore.setHudOpacityPercent(FloatingViewService.this, pct);
                applyUserSettingsLive();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        settingsCol.addView(opacitySeekBar);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(0, dp(3), 0, dp(10));
        final int[] presets = new int[]{35, 55, 72, 92};
        final String[] presetNames = new String[]{"35% Clear", "55% Glass", "72% Smoked", "92% Solid"};
        for (int i = 0; i < presets.length; i++) {
            final int pVal = presets[i];
            Button pb = createCompactTopButton(presetNames[i], "#991E293B", Color.parseColor("#38BDF8"));
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < presets.length - 1) pLp.setMargins(0, 0, dp(4), 0);
            pb.setLayoutParams(pLp);
            pb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LearnedProfileStore.setHudOpacityPercent(FloatingViewService.this, pVal);
                    applyUserSettingsLive();
                }
            });
            presetRow.addView(pb);
        }
        settingsCol.addView(presetRow);

        // SECTION 2: Minimized Pill Button Toggles
        settingsCol.addView(createSettingsSectionHeader("2. MINIMIZED PILL BUTTONS (TAP TO TOGGLE)"));

        LinearLayout miniTogglesRow = new LinearLayout(this);
        miniTogglesRow.setOrientation(LinearLayout.HORIZONTAL);
        miniTogglesRow.setPadding(0, dp(3), 0, dp(10));

        toggleMiniThrowBtn = createCompactTopButton("🎯 Throw/Stop: ON", "#CC065F46", Color.WHITE);
        LinearLayout.LayoutParams mt1Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mt1Lp.setMargins(0, 0, dp(4), 0);
        toggleMiniThrowBtn.setLayoutParams(mt1Lp);
        toggleMiniThrowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowThrow(FloatingViewService.this);
                LearnedProfileStore.setMiniShowThrow(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });

        toggleMiniGiftBtn = createCompactTopButton("▶ Gift/Stop: OFF", "#991E293B", Color.WHITE);
        LinearLayout.LayoutParams mt2Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mt2Lp.setMargins(0, 0, dp(4), 0);
        toggleMiniGiftBtn.setLayoutParams(mt2Lp);
        toggleMiniGiftBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowGift(FloatingViewService.this);
                LearnedProfileStore.setMiniShowGift(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });

        toggleMiniScanBtn = createCompactTopButton("🔭 Scan: OFF", "#991E293B", Color.WHITE);
        LinearLayout.LayoutParams mt3Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.85f);
        toggleMiniScanBtn.setLayoutParams(mt3Lp);
        toggleMiniScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowScan(FloatingViewService.this);
                LearnedProfileStore.setMiniShowScan(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });

        miniTogglesRow.addView(toggleMiniThrowBtn);
        miniTogglesRow.addView(toggleMiniGiftBtn);
        miniTogglesRow.addView(toggleMiniScanBtn);
        settingsCol.addView(miniTogglesRow);

        // SECTION 3: Excellent Circle Lock & Power Tuning
        settingsCol.addView(createSettingsSectionHeader("3. EXCELLENT CIRCLE TIMING & POWER"));

        settingsExcellentWaitBtn = createCompactTopButton(
                "🎯 Smallest Circle Lock (Excellent Timing): ON",
                "#CC065F46",
                Color.WHITE
        );
        LinearLayout.LayoutParams sewLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sewLp.setMargins(0, dp(2), 0, dp(5));
        settingsExcellentWaitBtn.setLayoutParams(sewLp);
        settingsExcellentWaitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isWaitForExcellentEnabled(FloatingViewService.this);
                LearnedProfileStore.setWaitForExcellentEnabled(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });
        settingsCol.addView(settingsExcellentWaitBtn);

        LinearLayout powerRow = new LinearLayout(this);
        powerRow.setOrientation(LinearLayout.HORIZONTAL);
        powerRow.setGravity(Gravity.CENTER_VERTICAL);
        powerRow.setPadding(0, 0, 0, dp(8));

        powerCalibrationLabel = new TextView(this);
        powerCalibrationLabel.setText("Throw Distance / Power: 100%");
        powerCalibrationLabel.setTextColor(Color.WHITE);
        powerCalibrationLabel.setTextSize(10.5f);
        powerCalibrationLabel.setTypeface(Typeface.DEFAULT_BOLD);
        applyLegibleTextShadow(powerCalibrationLabel);
        LinearLayout.LayoutParams pclLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        powerRow.addView(powerCalibrationLabel, pclLp);

        Button pwrMinusBtn = createCompactTopButton("⬇ -5%", "#991E293B", Color.WHITE);
        pwrMinusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.adjustThrowPowerMultiplier(FloatingViewService.this, -0.05f);
                applyUserSettingsLive();
            }
        });
        powerRow.addView(pwrMinusBtn);

        Button pwrPlusBtn = createCompactTopButton("⬆ +5%", "#0284C7", Color.WHITE);
        LinearLayout.LayoutParams ppLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        ppLp.setMargins(dp(4), 0, 0, 0);
        pwrPlusBtn.setLayoutParams(ppLp);
        pwrPlusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.adjustThrowPowerMultiplier(FloatingViewService.this, 0.05f);
                applyUserSettingsLive();
            }
        });
        powerRow.addView(pwrPlusBtn);
        settingsCol.addView(powerRow);

        // SECTION 4: Quick Catch, Spin Throw & Gift Mode
        settingsQuickCatchBtn = createCompactTopButton(
                "⚡ Fast Catch: ON (2-Finger Berry Hold + Run)",
                "#CC065F46",
                Color.WHITE
        );
        LinearLayout.LayoutParams sqcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sqcLp.setMargins(0, 0, 0, dp(5));
        settingsQuickCatchBtn.setLayoutParams(sqcLp);
        settingsQuickCatchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isQuickCatchEnabled(FloatingViewService.this);
                LearnedProfileStore.setQuickCatchEnabled(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });
        settingsCol.addView(settingsQuickCatchBtn);

        settingsSpinThrowBtn = createCompactTopButton(
                "⬆️ Throw Style: Straight Throw (Spin OFF)",
                "#991E293B",
                Color.WHITE
        );
        LinearLayout.LayoutParams sstLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sstLp.setMargins(0, 0, 0, dp(5));
        settingsSpinThrowBtn.setLayoutParams(sstLp);
        settingsSpinThrowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isSpinThrowEnabled(FloatingViewService.this);
                LearnedProfileStore.setSpinThrowEnabled(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });
        settingsCol.addView(settingsSpinThrowBtn);

        settingsGiftModeBtn = createCompactTopButton(
                "🎁 Gift Mode: 📌 Pin/Unpin + Open + Send Gift",
                "#991E293B",
                Color.parseColor("#38BDF8")
        );
        LinearLayout.LayoutParams sgmLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sgmLp.setMargins(0, 0, 0, dp(5));
        settingsGiftModeBtn.setLayoutParams(sgmLp);
        settingsGiftModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int nextMode = (LearnedProfileStore.getGiftWorkflowModePref(FloatingViewService.this) + 1) % 3;
                LearnedProfileStore.setGiftWorkflowModePref(FloatingViewService.this, nextMode);
                applyUserSettingsLive();
            }
        });
        settingsCol.addView(settingsGiftModeBtn);

        settingsDelaySpeedBtn = createCompactTopButton(
                "⏱ Pacing: Standard Fast Speed",
                "#991E293B",
                Color.WHITE
        );
        LinearLayout.LayoutParams sdsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sdsLp.setMargins(0, 0, 0, dp(5));
        settingsDelaySpeedBtn.setLayoutParams(sdsLp);
        settingsDelaySpeedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isSafeHumanDelayEnabled(FloatingViewService.this);
                LearnedProfileStore.setSafeHumanDelayEnabled(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });
        settingsCol.addView(settingsDelaySpeedBtn);

        settingsAutoHidePoGoBtn = createCompactTopButton(
                "👁 Show Overlay: Only in Pokémon GO (Auto-Hide ON)",
                "#CC065F46",
                Color.WHITE
        );
        LinearLayout.LayoutParams sahLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sahLp.setMargins(0, 0, 0, dp(8));
        settingsAutoHidePoGoBtn.setLayoutParams(sahLp);
        settingsAutoHidePoGoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isAutoHideOutsidePoGoEnabled(FloatingViewService.this);
                LearnedProfileStore.setAutoHideOutsidePoGoEnabled(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });
        settingsCol.addView(settingsAutoHidePoGoBtn);

        LinearLayout resetAiRow = new LinearLayout(this);
        resetAiRow.setOrientation(LinearLayout.HORIZONTAL);

        Button resetThrowsBtn = createCompactTopButton("🗑 Reset Learned Throws", "#7F1D1D", Color.WHITE);
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rtLp.setMargins(0, 0, dp(4), 0);
        resetThrowsBtn.setLayoutParams(rtLp);
        resetThrowsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.resetLearnedThrow(FloatingViewService.this);
                applyUserSettingsLive();
                Toast.makeText(FloatingViewService.this, "Cleared learned throws!", Toast.LENGTH_SHORT).show();
            }
        });

        Button resetGiftsBtn = createCompactTopButton("🗑 Reset Learned Gifts", "#7F1D1D", Color.WHITE);
        LinearLayout.LayoutParams rgLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        resetGiftsBtn.setLayoutParams(rgLp);
        resetGiftsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.resetLearnedGifts(FloatingViewService.this);
                applyUserSettingsLive();
                Toast.makeText(FloatingViewService.this, "Cleared learned gift taps!", Toast.LENGTH_SHORT).show();
            }
        });

        resetAiRow.addView(resetThrowsBtn);
        resetAiRow.addView(resetGiftsBtn);
        settingsCol.addView(resetAiRow);

        settingsContentScroll.addView(settingsCol);
        LinearLayout.LayoutParams setScrollLp = new LinearLayout.LayoutParams(
                panelWidth,
                dp(275)
        );
        expandedCard.addView(settingsContentScroll, setScrollLp);

        rootWrapper.addView(expandedCard);
        renderResearchCards();
    }

    private TextView createSettingsSectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#38BDF8"));
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(2), 0, dp(2));
        applyLegibleTextShadow(tv);
        return tv;
    }

    private void setExpandedState(boolean expanded) {
        this.isExpanded = expanded;
        if (expanded) {
            if (PokemonGoAccessibilityService.isAutoGiftRunning) {
                PokemonGoAccessibilityService a11y = PokemonGoAccessibilityService.instance;
                if (a11y != null) {
                    a11y.stopActiveGiftLoop();
                } else {
                    PokemonGoAccessibilityService.isAutoGiftRunning = false;
                }
                Toast.makeText(this, "■ Auto-Gift Stopped", Toast.LENGTH_SHORT).show();
            }
            compactMiniPillBar.setVisibility(View.GONE);
            expandedCard.setVisibility(View.VISIBLE);
            refreshThrowAndGiftButtons();
        } else {
            expandedCard.setVisibility(View.GONE);
            compactMiniPillBar.setVisibility(View.VISIBLE);
        }
    }

    private void renderResearchCards() {
        if (researchCardsContainer == null) return;
        researchCardsContainer.removeAllViews();

        List<PokeMateDatabase.ResearchItem> items = PokeMateDatabase.getInstance(this)
                .queryResearchItems(activeSearchQuery, activeCategoryFilter);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No matching Field Research tasks found.");
            empty.setTextColor(Color.parseColor("#CBD5E1"));
            empty.setTextSize(11.5f);
            applyLegibleTextShadow(empty);
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            researchCardsContainer.addView(empty);
            return;
        }

        for (PokeMateDatabase.ResearchItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(11), dp(8), dp(11), dp(8));

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.parseColor("#A80F172A"));
            cardBg.setCornerRadius(dp(12));
            String borderHex = "EVENT".equals(item.outlineType)
                    ? "#F59E0B"
                    : ("BONUS".equals(item.outlineType) ? "#10B981" : (item.isMeta ? "#38BDF8" : "#334155"));
            cardBg.setStroke(dp("EVENT".equals(item.outlineType) || "BONUS".equals(item.outlineType) ? 2 : 1), Color.parseColor(borderHex));
            card.setBackground(cardBg);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardLp.setMargins(0, 0, 0, dp(6));
            card.setLayoutParams(cardLp);

            TextView metaLine = new TextView(this);
            String oddsLabel = item.poolCount == 1
                    ? "100% Guaranteed"
                    : (item.oddsPercentEach + "% each (" + item.poolCount + " pool)");
            String tags = item.getOutlineBadgeLabel()
                    + (item.isMeta ? " • 🔥 META" : "")
                    + " • " + oddsLabel
                    + (item.shinyPossible ? " • ✨ Shiny" : "");
            metaLine.setText(tags);
            metaLine.setTextColor(Color.parseColor("EVENT".equals(item.outlineType) ? "#FBBF24"
                    : ("BONUS".equals(item.outlineType) ? "#34D399" : "#38BDF8")));
            metaLine.setTextSize(10f);
            metaLine.setTypeface(Typeface.DEFAULT_BOLD);
            applyLegibleTextShadow(metaLine);
            card.addView(metaLine);

            TextView taskTitle = new TextView(this);
            taskTitle.setText(item.taskName);
            taskTitle.setTextColor(Color.WHITE);
            taskTitle.setTextSize(12.5f);
            taskTitle.setTypeface(Typeface.DEFAULT_BOLD);
            taskTitle.setPadding(0, dp(2), 0, dp(2));
            applyLegibleTextShadow(taskTitle);
            card.addView(taskTitle);

            TextView rewardLine = new TextView(this);
            rewardLine.setText("→ " + item.reward + "  (" + item.cpRange + ")");
            rewardLine.setTextColor(Color.parseColor("#6EE7B7"));
            rewardLine.setTextSize(11.5f);
            rewardLine.setTypeface(Typeface.DEFAULT_BOLD);
            applyLegibleTextShadow(rewardLine);
            card.addView(rewardLine);

            if (item.isMeta && item.metaHighlight != null && item.metaHighlight.length() > 0) {
                TextView whoIsMetaView = new TextView(this);
                whoIsMetaView.setText(item.metaHighlight);
                whoIsMetaView.setTextColor(Color.parseColor("#FDE68A"));
                whoIsMetaView.setTextSize(10.5f);
                whoIsMetaView.setTypeface(Typeface.DEFAULT_BOLD);
                whoIsMetaView.setPadding(0, dp(4), 0, 0);
                applyLegibleTextShadow(whoIsMetaView);
                card.addView(whoIsMetaView);
            }

            researchCardsContainer.addView(card);
        }
    }

    private Button createPillButton(String text, String bgHex, int textColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(11f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(10), dp(7), dp(10), dp(7));
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(bgHex));
        d.setCornerRadius(dp(12));
        b.setBackground(d);
        return b;
    }

    private Button createCompactTopButton(String text, String bgHex, int textColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(10f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(5), dp(8), dp(5));
        applyLegibleTextShadow(b);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(bgHex));
        d.setCornerRadius(dp(9));
        d.setStroke(dp(1), Color.parseColor("#475569"));
        b.setBackground(d);
        return b;
    }

    @Override
    public void onStatusChanged(String statusText, boolean isRunning) {
        updateStatusBannerOnce(statusText, isRunning);
    }

    private void updateStatusBannerOnce(String statusText, boolean running) {
        refreshThrowAndGiftButtons();
        if (statusBannerView != null) {
            boolean a11yOk = PokemonGoAccessibilityService.instance != null;
            String a11yBadge = a11yOk ? "● A11Y ACTIVE" : "○ A11Y OFF";
            statusBannerView.setText(a11yBadge + "   •   " + statusText);
        }
    }

    @Override
    public void onDestroy() {
        removeTrainingOverlayIfPresent();
        if (instance == this) {
            instance = null;
        }
        if (PokemonGoAccessibilityService.statusListener == this) {
            PokemonGoAccessibilityService.statusListener = null;
        }
        if (rootWrapper != null && windowManager != null) {
            windowManager.removeView(rootWrapper);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
