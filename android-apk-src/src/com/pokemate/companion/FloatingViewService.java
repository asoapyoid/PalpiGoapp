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
import android.widget.ProgressBar;
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
    private Button miniScanPokemonBtn;
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
    private Button scanPokemonBtn;
    private EditText searchEditText;
    private LinearLayout researchCardsContainer;

    // Settings UI controls inside HUD
    private TextView opacityValueLabel;
    private SeekBar opacitySeekBar;
    private Button toggleMiniThrowBtn;
    private Button toggleMiniGiftBtn;
    private Button toggleMiniScanBtn;
    private Button toggleMiniScanPokemonBtn;
    private Button settingsExcellentWaitBtn;
    private TextView powerCalibrationLabel;
    private TextView angleCalibrationLabel;
    private Button settingsQuickCatchBtn;
    private Button settingsSpinThrowBtn;
    private Button settingsGiftModeBtn;
    private Button settingsDelaySpeedBtn;
    private Button settingsAutoHidePoGoBtn;

    private Button hudHarderBtn;
    private Button hudSofterBtn;
    private Button hudLeftBtn;
    private Button hudRightBtn;

    private FrameLayout activeTrainingOverlay;
    private LinearLayout activePostThrowRatePopup;
    private FrameLayout activePokemonScannerOverlay;
    private WindowManager.LayoutParams pokemonScannerOverlayLp;

    private boolean isExpanded = true;
    private boolean isShowingSettingsInHud = false;
    private boolean isCurrentlyFocusable = false;
    private boolean isTemporarilyHiddenForScreenshot = false;
    private String activeCategoryFilter = "ALL";
    private String activeSearchQuery = "";
    private PokemonMetaAnalyzer.PokemonScanReport lastScannedPokemonReport = null;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        PokemonMetaAnalyzer.syncLivePoGoApiStatsIfNeeded();
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

        statusBannerView.setText("📋 Scanning Field Research Screen (OCR)...");
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
                        lastScannedPokemonReport = null;
                        if (!isExpanded) {
                            setExpandedState(true);
                        }
                        if (isShowingSettingsInHud) {
                            setHudSettingsMode(false);
                        }
                        activeCategoryFilter = "ALL";
                        if (matchedQueryForSearchBar != null && matchedQueryForSearchBar.length() > 0) {
                            searchEditText.setText(matchedQueryForSearchBar);
                            statusBannerView.setText("✓ Scanned & Matched " + matchedCount + " Field Research Task(s)!");
                            Toast.makeText(
                                    FloatingViewService.this,
                                    "Matched " + matchedCount + " active Field Research task(s)!",
                                    Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            statusBannerView.setText("⚠ Open Pokémon GO Field Research list and tap Scan Research");
                            Toast.makeText(
                                    FloatingViewService.this,
                                    "No research tasks detected. Open your Pokémon GO Field Research list and tap Scan Research!",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                });
            }
        }, 95L);
    }

    private void triggerPokemonScreenScan() {
        setOverlayKeyboardFocusable(false);
        if (!ensureAccessibilityReady()) return;
        final PokemonGoAccessibilityService a11y = PokemonGoAccessibilityService.instance;

        statusBannerView.setText("🔍 Scanning Pokémon Stats, IV Bars, Moves & Meta Tier...");
        if (activePokemonScannerOverlay != null) {
            activePokemonScannerOverlay.setVisibility(View.GONE);
        }
        setTemporarilyHiddenForScreenshot(true);

        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                a11y.scanPokemonScreenWithOcr(new PokemonGoAccessibilityService.PokemonScanCallback() {
                    @Override
                    public void onScreenshotCaptured() {
                        setTemporarilyHiddenForScreenshot(false);
                    }

                    @Override
                    public void onPokemonScanned(
                            PokemonMetaAnalyzer.PokemonScanReport report,
                            String rawOcrText,
                            String errorMsg
                    ) {
                        setTemporarilyHiddenForScreenshot(false);

                        if (report != null) {
                            lastScannedPokemonReport = report;
                            renderResearchCards();
                            String ivBadge = (report.appraisal != null && report.appraisal.appraisalCardDetected)
                                    ? (" • " + report.appraisal.starRating + " (" + report.appraisal.ivAtk + "/" + report.appraisal.ivDef + "/" + report.appraisal.ivSta + ")")
                                    : "";
                            String msg = "✓ " + report.displayTitle + " [" + report.effectiveTier + " Tier]" + ivBadge + " → " + report.powerUpTargetText;
                            statusBannerView.setText(msg);
                            openFullPagePokemonScannerOverlay(report, rawOcrText);
                        } else {
                            // Even if OCR couldn't read a nicknamed Pokémon or non-summary screen, open the Full-Page Scanner
                            // so the user can immediately pick/search the Pokémon or tap Re-Scan!
                            PokemonMetaAnalyzer.PokemonScanReport fallback = lastScannedPokemonReport;
                            if (fallback == null) {
                                List<PokemonMetaAnalyzer.PokemonScanReport> defList =
                                        PokemonMetaAnalyzer.searchPokemonByName("Metagross");
                                if (!defList.isEmpty()) fallback = defList.get(0);
                            }
                            statusBannerView.setText("🔍 Opened Full-Page Pokémon Scanner (Tap Re-Scan on Pokémon screen or search name)");
                            openFullPagePokemonScannerOverlay(fallback, rawOcrText);
                        }
                    }
                });
            }
        }, 135L);
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
                boolean showScanPoke = LearnedProfileStore.isMiniShowScanPokemon(FloatingViewService.this);

                if (miniSpinThrowOrStopBtn != null) {
                    miniSpinThrowOrStopBtn.setVisibility(showThrow ? View.VISIBLE : View.GONE);
                }
                if (miniGiftOrStopBtn != null) {
                    miniGiftOrStopBtn.setVisibility(showGift ? View.VISIBLE : View.GONE);
                }
                if (miniScanBtn != null) {
                    miniScanBtn.setVisibility(showScan ? View.VISIBLE : View.GONE);
                }
                if (miniScanPokemonBtn != null) {
                    miniScanPokemonBtn.setVisibility(showScanPoke ? View.VISIBLE : View.GONE);
                }

                if (toggleMiniThrowBtn != null) {
                    styleToggleChip(toggleMiniThrowBtn, showThrow, "🎯 Throw/Stop: " + (showThrow ? "ON" : "OFF"));
                }
                if (toggleMiniGiftBtn != null) {
                    styleToggleChip(toggleMiniGiftBtn, showGift, "▶ Gift/Stop: " + (showGift ? "ON" : "OFF"));
                }
                if (toggleMiniScanBtn != null) {
                    styleToggleChip(toggleMiniScanBtn, showScan, "📋 Scan Research: " + (showScan ? "ON" : "OFF"));
                }
                if (toggleMiniScanPokemonBtn != null) {
                    styleToggleChip(toggleMiniScanPokemonBtn, showScanPoke, "🔍 Scan Pokémon: " + (showScanPoke ? "ON" : "OFF"));
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
                if (angleCalibrationLabel != null) {
                    int angPct = Math.round(LearnedProfileStore.getThrowHorizOffsetFraction(FloatingViewService.this) * 100f);
                    String angTxt = angPct == 0
                            ? "Center (0%)"
                            : (angPct < 0 ? ("⬅ Left " + Math.abs(angPct) + "%") : ("➡ Right +" + angPct + "%"));
                    angleCalibrationLabel.setText("Curve / Aim Angle: " + angTxt);
                }
                refreshHudThrowModifierButtons();

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

    private void styleModifierButtonState(Button btn, boolean isSelected, String text, String idleBgHex, int idleTextColor) {
        if (btn == null) return;
        btn.setText(text);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(9));
        if (isSelected) {
            d.setColor(Color.parseColor("#E6065F46"));
            d.setStroke(dp(1), Color.parseColor("#34D399"));
            btn.setTextColor(Color.parseColor("#A7F3D0"));
        } else {
            d.setColor(Color.parseColor(idleBgHex));
            d.setStroke(dp(1), Color.parseColor("#475569"));
            btn.setTextColor(idleTextColor);
        }
        btn.setBackground(d);
    }

    private void refreshHudThrowModifierButtons() {
        int pSteps = LearnedProfileStore.getLastCandidatePowerDeltaSteps();
        int aSteps = LearnedProfileStore.getLastCandidateAngleDeltaSteps();
        styleModifierButtonState(
                hudHarderBtn,
                pSteps > 0,
                pSteps > 1 ? ("✓ ⬆ Harder ×" + pSteps) : (pSteps > 0 ? "✓ ⬆ Harder" : "⬆ Harder"),
                "#CC1E40AF",
                Color.parseColor("#DBEAFE")
        );
        styleModifierButtonState(
                hudSofterBtn,
                pSteps < 0,
                pSteps < -1 ? ("✓ ⬇ Softer ×" + (-pSteps)) : (pSteps < 0 ? "✓ ⬇ Softer" : "⬇ Softer"),
                "#991E293B",
                Color.parseColor("#CBD5E1")
        );
        styleModifierButtonState(
                hudLeftBtn,
                aSteps < 0,
                aSteps < -1 ? ("✓ ⬅ Left ×" + (-aSteps)) : (aSteps < 0 ? "✓ ⬅ Left" : "⬅ Left"),
                "#AA4C1D95",
                Color.parseColor("#DDD6FE")
        );
        styleModifierButtonState(
                hudRightBtn,
                aSteps > 0,
                aSteps > 1 ? ("✓ ➡ Right ×" + aSteps) : (aSteps > 0 ? "✓ ➡ Right" : "➡ Right"),
                "#AA4C1D95",
                Color.parseColor("#DDD6FE")
        );
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

                        // 1. Calibrate & IMMEDIATELY save the user's drawn throw (with full 1.75-turn spin loop if Spin is ON)!
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
                        final LearnedProfileStore.CalibratedStroke calibrated =
                                LearnedProfileStore.getCurrentActiveThrowForPreview(FloatingViewService.this, screenW, screenH);
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

        final TextView title = new TextView(this);
        title.setText("🎯 1. Select Power & Angle (Can pick both!) → 2. Rate Hit:");
        title.setTextColor(Color.parseColor("#7DD3FC"));
        title.setTextSize(10f);
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

        // Auto-dismiss timer (18s) that automatically resets if the user taps Harder/Softer/Left/Right first!
        final LinearLayout popupRef = activePostThrowRatePopup;
        final Runnable autoDismissRunnable = new Runnable() {
            @Override
            public void run() {
                if (activePostThrowRatePopup == popupRef) {
                    dismissPostThrowRatePopup();
                }
            }
        };

        // STEP 1 ROW: COMBINABLE POWER (⬆ Harder / ⬇ Softer) + ANGLE (⬅ Left / ➡ Right) MODIFIERS
        // Does NOT close popup — lets user select e.g. '⬆ Harder' AND '⬅ Left' at the same time before rating Hit/Miss!
        LinearLayout rowDist = new LinearLayout(this);
        rowDist.setOrientation(LinearLayout.HORIZONTAL);
        rowDist.setPadding(0, 0, 0, dp(5));

        final Object[][] modButtons = new Object[][]{
                {"⬆ Harder", LearnedProfileStore.GRADE_SHORT, "#1E40AF", Color.parseColor("#DBEAFE")},
                {"⬇ Softer", LearnedProfileStore.GRADE_FAR, "#334155", Color.parseColor("#E2E8F0")},
                {"⬅ Left", LearnedProfileStore.GRADE_MORE_LEFT, "#4C1D95", Color.parseColor("#DDD6FE")},
                {"➡ Right", LearnedProfileStore.GRADE_MORE_RIGHT, "#4C1D95", Color.parseColor("#DDD6FE")}
        };

        final Button[] popModBtnRefs = new Button[modButtons.length];
        final Runnable refreshPopupModHighlights = new Runnable() {
            @Override
            public void run() {
                int pSteps = LearnedProfileStore.getLastCandidatePowerDeltaSteps();
                int aSteps = LearnedProfileStore.getLastCandidateAngleDeltaSteps();
                styleModifierButtonState(
                        popModBtnRefs[0],
                        pSteps > 0,
                        pSteps > 1 ? ("✓ ⬆ Harder ×" + pSteps) : (pSteps > 0 ? "✓ ⬆ Harder" : "⬆ Harder"),
                        "#1E40AF",
                        Color.parseColor("#DBEAFE")
                );
                styleModifierButtonState(
                        popModBtnRefs[1],
                        pSteps < 0,
                        pSteps < -1 ? ("✓ ⬇ Softer ×" + (-pSteps)) : (pSteps < 0 ? "✓ ⬇ Softer" : "⬇ Softer"),
                        "#334155",
                        Color.parseColor("#E2E8F0")
                );
                styleModifierButtonState(
                        popModBtnRefs[2],
                        aSteps < 0,
                        aSteps < -1 ? ("✓ ⬅ Left ×" + (-aSteps)) : (aSteps < 0 ? "✓ ⬅ Left" : "⬅ Left"),
                        "#4C1D95",
                        Color.parseColor("#DDD6FE")
                );
                styleModifierButtonState(
                        popModBtnRefs[3],
                        aSteps > 0,
                        aSteps > 1 ? ("✓ ➡ Right ×" + aSteps) : (aSteps > 0 ? "✓ ➡ Right" : "➡ Right"),
                        "#4C1D95",
                        Color.parseColor("#DDD6FE")
                );
                String summary = LearnedProfileStore.getActiveCandidateModifiersSummary();
                if (summary != null && summary.length() > 0) {
                    title.setText("✓ [" + summary + "] Active! Tap more or rate Hit/Miss below:");
                    title.setTextColor(Color.parseColor("#34D399"));
                } else {
                    title.setText("🎯 1. Select Power & Angle (Can pick both!) → 2. Rate Hit:");
                    title.setTextColor(Color.parseColor("#7DD3FC"));
                }
            }
        };

        for (int i = 0; i < modButtons.length; i++) {
            final String lbl = (String) modButtons[i][0];
            final int grade = (Integer) modButtons[i][1];
            final String cHex = (String) modButtons[i][2];
            final int tCol = (Integer) modButtons[i][3];

            final Button btn = createCompactTopButton(lbl, cHex, tCol);
            btn.setTextSize(9.5f);
            btn.setPadding(dp(5), dp(4), dp(5), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < modButtons.length - 1) lp.setMargins(0, 0, dp(4), 0);
            btn.setLayoutParams(lp);
            popModBtnRefs[i] = btn;

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Reset the 18s auto-dismiss timer so the user can combine Power + Left/Right and then tap Step 2!
                    uiHandler.removeCallbacks(autoDismissRunnable);
                    uiHandler.postDelayed(autoDismissRunnable, 18000L);

                    String msg = LearnedProfileStore.rateLastExecutedThrow(FloatingViewService.this, grade);
                    applyUserSettingsLive();
                    refreshPopupModHighlights.run();
                    if (statusBannerView != null) statusBannerView.setText(msg);
                    Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
            rowDist.addView(btn);
        }
        refreshPopupModHighlights.run();
        activePostThrowRatePopup.addView(rowDist);

        // STEP 2 ROW: FINAL THROW OUTCOME (Locks in result + any Power & Left/Right Angle adjustments and closes popup)
        LinearLayout rowFinal = new LinearLayout(this);
        rowFinal.setOrientation(LinearLayout.HORIZONTAL);

        final Object[][] finalButtons = new Object[][]{
                {"🌟 Exc", LearnedProfileStore.GRADE_EXCELLENT, "#10B981", Color.parseColor("#04131D")},
                {"🔥 Great", LearnedProfileStore.GRADE_GREAT, "#F59E0B", Color.parseColor("#04131D")},
                {"👍 Nice", LearnedProfileStore.GRADE_NICE, "#0284C7", Color.WHITE},
                {"⚪ Hit", LearnedProfileStore.GRADE_HIT_NO_BONUS, "#475569", Color.WHITE},
                {"❌ Miss", LearnedProfileStore.GRADE_MISS, "#E11D48", Color.WHITE}
        };

        for (int i = 0; i < finalButtons.length; i++) {
            final String lbl = (String) finalButtons[i][0];
            final int grade = (Integer) finalButtons[i][1];
            final String cHex = (String) finalButtons[i][2];
            final int tCol = (Integer) finalButtons[i][3];

            Button btn = createCompactTopButton(lbl, cHex, tCol);
            btn.setTextSize(10f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < finalButtons.length - 1) lp.setMargins(0, 0, dp(4), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    uiHandler.removeCallbacks(autoDismissRunnable);
                    String msg = LearnedProfileStore.rateLastExecutedThrow(FloatingViewService.this, grade);
                    applyUserSettingsLive();
                    if (statusBannerView != null) statusBannerView.setText(msg);
                    Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
                    dismissPostThrowRatePopup();
                }
            });
            rowFinal.addView(btn);
        }
        activePostThrowRatePopup.addView(rowFinal);

        try {
            windowManager.addView(activePostThrowRatePopup, popLp);
        } catch (Exception ignored) {}

        uiHandler.postDelayed(autoDismissRunnable, 18000L);
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
                "📌 INCOMING GIFT: Tap the 📌 PIN Button (to the right of OPEN)",
                "🎁 INCOMING GIFT: Tap the center 'OPEN' Button to claim the gift",
                "TAP 2: Tap the LEFT '🎁 SEND GIFT' Button on the Profile\n(Or if an Incoming Gift popped up, just tap 📌 PIN or 'OPEN' directly — the app will auto-detect it!)",
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
                DisplayMetrics dm = getResources().getDisplayMetrics();
                final int sw = dm.widthPixels > 0 ? dm.widthPixels : 1080;
                final int sh = dm.heightPixels > 0 ? dm.heightPixels : 2400;

                int rawStep = currentStep[0];
                // SMART AUTO-DETECTION: If currentStep is 3 (waiting for Trainer Profile or Incoming Gift),
                // classify what button the user actually tapped by its screen zone so Pin/Open NEVER overwrite Send Gift!
                if (rawStep == 3) {
                    if (rx >= sw * 0.66f && ry >= sh * 0.56f && ry <= sh * 0.80f) {
                        // User tapped the 📌 PIN button on the right of an Incoming Gift!
                        rawStep = 1;
                    } else if (rx >= sw * 0.35f && rx <= sw * 0.65f && ry >= sh * 0.56f && ry <= sh * 0.78f) {
                        // User tapped the center 'OPEN' button on an Incoming Gift!
                        rawStep = 2;
                    } else if (rx > sw * 0.34f) {
                        Toast.makeText(
                                FloatingViewService.this,
                                "⚠ 'Send Gift' is the LEFT button! Please tap the left '🎁 SEND GIFT' icon (not Battle/Trade).",
                                Toast.LENGTH_SHORT
                        ).show();
                        return true;
                    }
                }

                final int step = rawStep;
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
                            // Wait a patient 1300ms after Pinning, then Unpin!
                            uiHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    a11y.dispatchHumanTap(rx, ry, 0f);
                                }
                            }, 1300L);
                        } else if (step == 2 && a11y != null) {
                            // Skip the Incoming Gift opening animation
                            uiHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    a11y.dispatchHumanTap(sw * 0.50f, sh * 0.84f, 0f);
                                    uiHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            a11y.dispatchHumanTap(sw * 0.50f, sh * 0.84f, 0f);
                                        }
                                    }, 900L);
                                }
                            }, 1650L);
                        }

                        long waitForGameScreenMs = (step == 0) ? 2600L
                                : (step == 1) ? 2300L
                                : (step == 2) ? 3500L
                                : (step == 3) ? 2000L
                                : (step == 4) ? 1750L
                                : (step == 5) ? 2800L
                                : 1100L;

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
                                        a11y.notifyStatus("✓ Smart Gift Workflow Learned (Trained ×" + totalTrained + ")!");
                                    }
                                    Toast.makeText(
                                            FloatingViewService.this,
                                            "✓ Gift Coordinates Saved! Tap '▶ Auto-Gift' to run!",
                                            Toast.LENGTH_LONG
                                    ).show();
                                } else {
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
        miniScanBtn.setText("📋 Res");
        miniScanBtn.setTextColor(Color.WHITE);
        miniScanBtn.setTextSize(10.5f);
        miniScanBtn.setTypeface(Typeface.DEFAULT_BOLD);
        miniScanBtn.setAllCaps(false);
        miniScanBtn.setGravity(Gravity.CENTER);
        miniScanBtn.setMinWidth(0);
        miniScanBtn.setMinHeight(0);
        miniScanBtn.setMinimumWidth(0);
        miniScanBtn.setMinimumHeight(0);
        miniScanBtn.setPadding(dp(9), 0, dp(9), 0);
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

        miniScanPokemonBtn = new Button(this);
        miniScanPokemonBtn.setText("🔍 Poké");
        miniScanPokemonBtn.setTextColor(Color.WHITE);
        miniScanPokemonBtn.setTextSize(10.5f);
        miniScanPokemonBtn.setTypeface(Typeface.DEFAULT_BOLD);
        miniScanPokemonBtn.setAllCaps(false);
        miniScanPokemonBtn.setGravity(Gravity.CENTER);
        miniScanPokemonBtn.setMinWidth(0);
        miniScanPokemonBtn.setMinHeight(0);
        miniScanPokemonBtn.setMinimumWidth(0);
        miniScanPokemonBtn.setMinimumHeight(0);
        miniScanPokemonBtn.setPadding(dp(9), 0, dp(9), 0);
        GradientDrawable mspBg = new GradientDrawable();
        mspBg.setColor(Color.parseColor("#7C3AED"));
        mspBg.setStroke(dp(1), Color.parseColor("#C4B5FD"));
        mspBg.setCornerRadius(dp(18));
        miniScanPokemonBtn.setBackground(mspBg);
        LinearLayout.LayoutParams mspLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
        );
        mspLp.setMargins(dp(4), 0, 0, 0);
        miniScanPokemonBtn.setLayoutParams(mspLp);
        miniScanPokemonBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerPokemonScreenScan();
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
        compactMiniPillBar.addView(miniScanPokemonBtn);
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
        dragTitle.setText("PalpiGO v1.2.2026");
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
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        b1Lp.setMargins(0, 0, dp(4), 0);
        mainThrowOrStopBtn.setLayoutParams(b1Lp);
        mainThrowOrStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleThrowOrStopClicked();
            }
        });

        mainGiftOrStopBtn = createPillButton("▶ Auto-Gift", "#10B981", Color.parseColor("#04131D"));
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mainGiftOrStopBtn.setLayoutParams(b2Lp);
        mainGiftOrStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAutoGiftAction();
            }
        });

        primaryDeckRow.addView(mainThrowOrStopBtn);
        primaryDeckRow.addView(mainGiftOrStopBtn);
        mainHudContentContainer.addView(primaryDeckRow);

        // Dual Scanner Row ('📋 Scan Research' + '🔍 Scan Pokémon')
        LinearLayout scanDeckRow = new LinearLayout(this);
        scanDeckRow.setOrientation(LinearLayout.HORIZONTAL);
        scanDeckRow.setPadding(0, 0, 0, dp(6));

        scanResearchBtn = createPillButton("📋 Scan Research", "#0284C7", Color.WHITE);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        srLp.setMargins(0, 0, dp(4), 0);
        scanResearchBtn.setLayoutParams(srLp);
        scanResearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerOcrResearchScan();
            }
        });

        scanPokemonBtn = createPillButton("🔍 Scan Pokémon", "#7C3AED", Color.WHITE);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        scanPokemonBtn.setLayoutParams(spLp);
        scanPokemonBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerPokemonScreenScan();
            }
        });

        scanDeckRow.addView(scanResearchBtn);
        scanDeckRow.addView(scanPokemonBtn);
        mainHudContentContainer.addView(scanDeckRow);

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

        // 2-Stage Post-Throw Outcome Rating Bar:
        // Row 1 (Step 1 Combinable): Select Power (⬆ Harder / ⬇ Softer) + Angle (⬅ Left / ➡ Right) at the same time!
        // Row 2 (Step 2 Final): Rate Hit / Outcome (🌟 Exc / 🔥 Grt / 👍 Nice / ⚪ Hit / ❌ Miss)
        LinearLayout rateDistRow = new LinearLayout(this);
        rateDistRow.setOrientation(LinearLayout.HORIZONTAL);
        rateDistRow.setGravity(Gravity.CENTER_VERTICAL);
        rateDistRow.setPadding(0, 0, 0, dp(3));

        hudHarderBtn = createCompactTopButton("⬆ Harder", "#CC1E40AF", Color.parseColor("#DBEAFE"));
        hudSofterBtn = createCompactTopButton("⬇ Softer", "#991E293B", Color.parseColor("#CBD5E1"));
        hudLeftBtn = createCompactTopButton("⬅ Left", "#AA4C1D95", Color.parseColor("#DDD6FE"));
        hudRightBtn = createCompactTopButton("➡ Right", "#AA4C1D95", Color.parseColor("#DDD6FE"));

        final Button[] hudModBtns = new Button[]{hudHarderBtn, hudSofterBtn, hudLeftBtn, hudRightBtn};
        final int[] hudModGrades = new int[]{
                LearnedProfileStore.GRADE_SHORT,
                LearnedProfileStore.GRADE_FAR,
                LearnedProfileStore.GRADE_MORE_LEFT,
                LearnedProfileStore.GRADE_MORE_RIGHT
        };

        for (int m = 0; m < hudModBtns.length; m++) {
            final Button mBtn = hudModBtns[m];
            final int mGrade = hudModGrades[m];
            mBtn.setTextSize(9.2f);
            mBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (m < hudModBtns.length - 1) mLp.setMargins(0, 0, dp(3), 0);
            mBtn.setLayoutParams(mLp);
            mBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String feedback = LearnedProfileStore.rateLastExecutedThrow(FloatingViewService.this, mGrade);
                    applyUserSettingsLive();
                    if (statusBannerView != null) statusBannerView.setText(feedback);
                    Toast.makeText(FloatingViewService.this, feedback, Toast.LENGTH_SHORT).show();
                }
            });
            rateDistRow.addView(mBtn);
        }
        mainHudContentContainer.addView(rateDistRow);

        LinearLayout rateThrowRow = new LinearLayout(this);
        rateThrowRow.setOrientation(LinearLayout.HORIZONTAL);
        rateThrowRow.setGravity(Gravity.CENTER_VERTICAL);
        rateThrowRow.setPadding(0, 0, 0, dp(6));

        TextView rateLbl = new TextView(this);
        rateLbl.setText("Hit?");
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
        searchEditText.setHint("🔍 Search task/Pokémon or tap Scan Research / Scan Pokémon...");
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
                {"META", "Meta 🔥"},
                {"SHINY", "Shiny ✨"},
                {"Catch", "Catch"},
                {"Throw", "Throw"},
                {"Raid", "Raid/Battle"},
                {"Explore", "Hatch/Spin"},
                {"Power/Buddy", "Power/Buddy"},
                {"Bonus", "🟩 Bonus"}
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
        miniTogglesRow.setPadding(0, dp(3), 0, dp(4));

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
        toggleMiniGiftBtn.setLayoutParams(mt2Lp);
        toggleMiniGiftBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowGift(FloatingViewService.this);
                LearnedProfileStore.setMiniShowGift(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });

        miniTogglesRow.addView(toggleMiniThrowBtn);
        miniTogglesRow.addView(toggleMiniGiftBtn);
        settingsCol.addView(miniTogglesRow);

        LinearLayout miniTogglesRow2 = new LinearLayout(this);
        miniTogglesRow2.setOrientation(LinearLayout.HORIZONTAL);
        miniTogglesRow2.setPadding(0, 0, 0, dp(10));

        toggleMiniScanBtn = createCompactTopButton("📋 Scan Research: OFF", "#991E293B", Color.WHITE);
        LinearLayout.LayoutParams mt3Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mt3Lp.setMargins(0, 0, dp(4), 0);
        toggleMiniScanBtn.setLayoutParams(mt3Lp);
        toggleMiniScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowScan(FloatingViewService.this);
                LearnedProfileStore.setMiniShowScan(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });

        toggleMiniScanPokemonBtn = createCompactTopButton("🔍 Scan Pokémon: ON", "#CC065F46", Color.WHITE);
        LinearLayout.LayoutParams mt4Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        toggleMiniScanPokemonBtn.setLayoutParams(mt4Lp);
        toggleMiniScanPokemonBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowScanPokemon(FloatingViewService.this);
                LearnedProfileStore.setMiniShowScanPokemon(FloatingViewService.this, next);
                applyUserSettingsLive();
            }
        });

        miniTogglesRow2.addView(toggleMiniScanBtn);
        miniTogglesRow2.addView(toggleMiniScanPokemonBtn);
        settingsCol.addView(miniTogglesRow2);

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

        LinearLayout angleRow = new LinearLayout(this);
        angleRow.setOrientation(LinearLayout.HORIZONTAL);
        angleRow.setGravity(Gravity.CENTER_VERTICAL);
        angleRow.setPadding(0, 0, 0, dp(8));

        angleCalibrationLabel = new TextView(this);
        angleCalibrationLabel.setText("Curve / Aim Angle: Center (0%)");
        angleCalibrationLabel.setTextColor(Color.WHITE);
        angleCalibrationLabel.setTextSize(10.5f);
        angleCalibrationLabel.setTypeface(Typeface.DEFAULT_BOLD);
        applyLegibleTextShadow(angleCalibrationLabel);
        LinearLayout.LayoutParams aclLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        angleRow.addView(angleCalibrationLabel, aclLp);

        Button angLeftBtn = createCompactTopButton("⬅ Left", "#4C1D95", Color.WHITE);
        angLeftBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = LearnedProfileStore.rateLastExecutedThrow(FloatingViewService.this, LearnedProfileStore.GRADE_MORE_LEFT);
                applyUserSettingsLive();
                Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
        angleRow.addView(angLeftBtn);

        Button angRightBtn = createCompactTopButton("➡ Right", "#4C1D95", Color.WHITE);
        LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        arLp.setMargins(dp(4), 0, 0, 0);
        angRightBtn.setLayoutParams(arLp);
        angRightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = LearnedProfileStore.rateLastExecutedThrow(FloatingViewService.this, LearnedProfileStore.GRADE_MORE_RIGHT);
                applyUserSettingsLive();
                Toast.makeText(FloatingViewService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
        angleRow.addView(angRightBtn);
        settingsCol.addView(angleRow);

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

        // 1. If a Pokémon was just scanned via '🔍 Scan Pokémon', show its full Meta & Power-Up Report Card at the top!
        if (lastScannedPokemonReport != null) {
            researchCardsContainer.addView(createPokemonReportCardView(lastScannedPokemonReport, true));
        }

        // 2. If the user typed a Pokémon name in the Search Bar (and it's not a pipe-delimited research scan),
        //    show matching Pokémon Meta & S-F Tier cards as well!
        if (activeSearchQuery != null && activeSearchQuery.trim().length() >= 2
                && !activeSearchQuery.contains("|") && !activeSearchQuery.contains("::")) {
            List<PokemonMetaAnalyzer.PokemonScanReport> pokeMatches =
                    PokemonMetaAnalyzer.searchPokemonByName(activeSearchQuery.trim());
            for (int p = 0; p < pokeMatches.size(); p++) {
                PokemonMetaAnalyzer.PokemonScanReport pm = pokeMatches.get(p);
                if (lastScannedPokemonReport != null
                        && lastScannedPokemonReport.entry.name.equalsIgnoreCase(pm.entry.name)) {
                    continue;
                }
                researchCardsContainer.addView(createPokemonReportCardView(pm, false));
            }
        }

        List<PokeMateDatabase.ResearchItem> items = PokeMateDatabase.getInstance(this)
                .queryResearchItems(activeSearchQuery, activeCategoryFilter);

        if (items.isEmpty() && researchCardsContainer.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No matching Field Research tasks or Pokémon found.");
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
                    + " • " + oddsLabel;
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
            taskTitle.setPadding(0, dp(2), 0, dp(3));
            applyLegibleTextShadow(taskTitle);
            card.addView(taskTitle);

            if (item.pokemonEntries != null && !item.pokemonEntries.isEmpty()) {
                // Merged Pokémon Reward List with Ratings + Per-Pokémon Shiny Symbol (✨ normal / ✨✨ boosted)
                int totalPokes = item.pokemonEntries.size();
                boolean hasLongName = false;
                for (int p = 0; p < totalPokes; p++) {
                    if (item.pokemonEntries.get(p).displayName.length() >= 11) {
                        hasLongName = true;
                        break;
                    }
                }
                int maxPerRow = (totalPokes == 4 || hasLongName) ? 2 : 3;

                LinearLayout currentRow = null;
                int countInRow = 0;
                for (int p = 0; p < totalPokes; p++) {
                    final PokeMateDatabase.RewardPokemonEntry pe = item.pokemonEntries.get(p);
                    if (currentRow == null || countInRow >= maxPerRow) {
                        currentRow = new LinearLayout(this);
                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                        currentRow.setPadding(0, dp(p == 0 ? 1 : 4), 0, 0);
                        card.addView(currentRow);
                        countInRow = 0;
                    }

                    String chipBg = pe.tier.startsWith("S") ? "#065F46"
                            : (pe.tier.startsWith("A") ? "#0F5132" : "#1E293B");
                    int chipTextColor = (pe.shinySymbol != null && pe.shinySymbol.length() >= 2)
                            ? Color.parseColor("#FDE68A")
                            : Color.WHITE;

                    Button inspectPokeBtn = createCompactTopButton(
                            pe.getChipLabel(),
                            chipBg,
                            chipTextColor
                    );
                    LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    ipLp.setMargins(0, 0, dp(5), 0);
                    inspectPokeBtn.setLayoutParams(ipLp);
                    if (pe.report != null) {
                        inspectPokeBtn.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                lastScannedPokemonReport = pe.report;
                                renderResearchCards();
                                openFullPagePokemonScannerOverlay(pe.report, "");
                            }
                        });
                    }
                    currentRow.addView(inspectPokeBtn);
                    countInRow++;
                }

                // Keep the CP Range (and any "+N more" pool indicator) directly below the merged Pokémon list
                TextView cpLine = new TextView(this);
                String cpText = "🎯 " + item.cpRange
                        + (item.extraPoolSuffix != null && item.extraPoolSuffix.length() > 0
                        ? "  •  " + item.extraPoolSuffix
                        : "");
                cpLine.setText(cpText);
                cpLine.setTextColor(Color.parseColor("#6EE7B7"));
                cpLine.setTextSize(11f);
                cpLine.setTypeface(Typeface.DEFAULT_BOLD);
                cpLine.setPadding(0, dp(4), 0, 0);
                applyLegibleTextShadow(cpLine);
                card.addView(cpLine);
            } else {
                // Item / Resource Reward Pool
                TextView rewardLine = new TextView(this);
                rewardLine.setText("→ " + item.reward + "  (" + item.cpRange + ")");
                rewardLine.setTextColor(Color.parseColor("#6EE7B7"));
                rewardLine.setTextSize(11.5f);
                rewardLine.setTypeface(Typeface.DEFAULT_BOLD);
                applyLegibleTextShadow(rewardLine);
                card.addView(rewardLine);
            }

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

    private View createPokemonReportCardView(
            final PokemonMetaAnalyzer.PokemonScanReport report,
            final boolean isPinnedScan
    ) {
        final PokemonMetaAnalyzer.PokemonMetaEntry e = report.entry;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        String tierHex;
        if (report.effectiveTier.startsWith("S")) {
            tierHex = "#F59E0B"; // Gold S+ / S
        } else if (report.effectiveTier.startsWith("A")) {
            tierHex = "#10B981"; // Emerald A+ / A
        } else if (report.effectiveTier.startsWith("B")) {
            tierHex = "#38BDF8"; // Sky B
        } else if (report.effectiveTier.startsWith("C")) {
            tierHex = "#A78BFA"; // Purple C
        } else {
            tierHex = "#94A3B8"; // Slate D / F
        }

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#D80B1528"));
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(2), Color.parseColor(tierHex));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardLp);

        // Top Header Row: Visual Tier Badge Pill + Meta Tag + Dismiss Button (if pinned scan)
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tierBadge = new TextView(this);
        tierBadge.setText(" " + report.effectiveTier + " TIER ");
        tierBadge.setTextColor(Color.parseColor("#04131D"));
        tierBadge.setTextSize(10.5f);
        tierBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tierBadge.setPadding(dp(7), dp(2), dp(7), dp(2));
        GradientDrawable tbBg = new GradientDrawable();
        tbBg.setColor(Color.parseColor(tierHex));
        tbBg.setCornerRadius(dp(6));
        tierBadge.setBackground(tbBg);
        topRow.addView(tierBadge);

        TextView titleView = new TextView(this);
        String cpSuffix = report.scannedCp > 0 ? (" • CP " + report.scannedCp) : "";
        titleView.setText("  " + report.displayTitle + cpSuffix);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(13.5f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        applyLegibleTextShadow(titleView);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        topRow.addView(titleView, tvLp);

        if (isPinnedScan) {
            Button clearScanBtn = createCompactTopButton("✕", "#7F1D1D", Color.WHITE);
            clearScanBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lastScannedPokemonReport = null;
                    renderResearchCards();
                }
            });
            topRow.addView(clearScanBtn);
        }
        card.addView(topRow);

        // Subtitle: Elemental Type + Concise Verdict
        TextView subLine = new TextView(this);
        subLine.setText("🏷 " + e.types + "   •   " + report.verdictHeadline + " (" + report.powerUpTargetText + ")");
        subLine.setTextColor(Color.parseColor(report.effectiveIsMeta ? "#6EE7B7" : "#CBD5E1"));
        subLine.setTextSize(11f);
        subLine.setTypeface(Typeface.DEFAULT_BOLD);
        subLine.setPadding(0, dp(4), 0, dp(4));
        applyLegibleTextShadow(subLine);
        card.addView(subLine);

        // Visual Appraisal IV Bar Strip (if Appraisal card was open on screen)
        if (report.appraisal != null && report.appraisal.appraisalCardDetected) {
            LinearLayout ivStrip = new LinearLayout(this);
            ivStrip.setOrientation(LinearLayout.HORIZONTAL);
            ivStrip.setGravity(Gravity.CENTER_VERTICAL);
            ivStrip.setPadding(dp(8), dp(5), dp(8), dp(5));
            GradientDrawable ivBg = new GradientDrawable();
            ivBg.setColor(Color.parseColor("#071120"));
            ivBg.setCornerRadius(dp(8));
            ivBg.setStroke(dp(1), Color.parseColor(report.appraisal.ivPercent >= 89 ? "#10B981" : "#F59E0B"));
            ivStrip.setBackground(ivBg);
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            ivLp.setMargins(0, 0, 0, dp(5));
            ivStrip.setLayoutParams(ivLp);

            TextView ivText = new TextView(this);
            ivText.setText("⭐ " + report.appraisal.starRating + "  |  ATK " + report.appraisal.ivAtk
                    + " • DEF " + report.appraisal.ivDef + " • HP " + report.appraisal.ivSta);
            ivText.setTextColor(Color.parseColor("#FDE68A"));
            ivText.setTextSize(11f);
            ivText.setTypeface(Typeface.DEFAULT_BOLD);
            ivStrip.addView(ivText);
            card.addView(ivStrip);
        }

        // Visual Moves Box: Scanned Moves (if detected) + Raid (Same-Type) vs PvP (Coverage)
        LinearLayout movesBox = new LinearLayout(this);
        movesBox.setOrientation(LinearLayout.VERTICAL);
        movesBox.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable mbBg = new GradientDrawable();
        mbBg.setColor(Color.parseColor("#C006101E"));
        mbBg.setCornerRadius(dp(9));
        mbBg.setStroke(dp(1), Color.parseColor("#1E293B"));
        movesBox.setBackground(mbBg);

        if (report.hasScannedMoves) {
            TextView scannedMvLine = new TextView(this);
            scannedMvLine.setText("🔎 Equipped: " + report.scannedMovesDisplay + "\n" + report.scannedMovesVerdict);
            scannedMvLine.setTextColor(Color.parseColor(
                    (report.scannedMovesMatchRaid || report.scannedMovesMatchPvp) ? "#34D399" : "#FBBF24"
            ));
            scannedMvLine.setTextSize(10.5f);
            scannedMvLine.setTypeface(Typeface.DEFAULT_BOLD);
            scannedMvLine.setPadding(0, 0, 0, dp(4));
            movesBox.addView(scannedMvLine);
        }

        TextView raidMvLine = new TextView(this);
        raidMvLine.setText("⚔ Raid (Same-Element): " + PokemonMetaAnalyzer.decorateMovesetWithTypes(e.bestMoves));
        raidMvLine.setTextColor(Color.parseColor("#FDE68A"));
        raidMvLine.setTextSize(10.5f);
        raidMvLine.setTypeface(Typeface.DEFAULT_BOLD);
        movesBox.addView(raidMvLine);

        TextView pvpMvLine = new TextView(this);
        pvpMvLine.setText("🛡 PvP (Coverage): " + PokemonMetaAnalyzer.decorateMovesetWithTypes(e.getEffectivePvpMoveset()));
        pvpMvLine.setTextColor(Color.parseColor("#7DD3FC"));
        pvpMvLine.setTextSize(10.5f);
        pvpMvLine.setTypeface(Typeface.DEFAULT_BOLD);
        pvpMvLine.setPadding(0, dp(2), 0, 0);
        movesBox.addView(pvpMvLine);

        card.addView(movesBox);

        Button openFullPageBtn = createCompactTopButton("🔍 Full Visual Breakdown (IV Tuner, Moves & CPs)", "#0284C7", Color.WHITE);
        LinearLayout.LayoutParams fpBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fpBtnLp.setMargins(0, dp(6), 0, 0);
        openFullPageBtn.setLayoutParams(fpBtnLp);
        openFullPageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFullPagePokemonScannerOverlay(report, "");
            }
        });
        card.addView(openFullPageBtn);

        return card;
    }

    private void removePokemonScannerOverlayIfPresent() {
        if (activePokemonScannerOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(activePokemonScannerOverlay);
            } catch (Exception ignored) {}
            activePokemonScannerOverlay = null;
            pokemonScannerOverlayLp = null;
        }
    }

    private void setPokemonOverlayFocusable(boolean focusable) {
        if (activePokemonScannerOverlay == null || pokemonScannerOverlayLp == null || windowManager == null) return;
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!focusable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
        pokemonScannerOverlayLp.flags = flags;
        try {
            windowManager.updateViewLayout(activePokemonScannerOverlay, pokemonScannerOverlayLp);
        } catch (Exception ignored) {}
    }

    /**
     * FULL-PAGE POKÉMON SCANNER & META ANALYZER OVERLAY:
     * Displays:
     * 1. S+ to F Meta Tier Ranking, Meta Relevance Badge, Types, Base Stats & Shadow/Lucky Toggles
     * 2. Visual Team Leader Appraisal IV Bars (ATK / DEF / HP 0–15) + Interactive IV Adjusters
     * 3. Optimal Fast & Charged Moveset with explicit Normal TM (🟢) vs Elite TM Required (🟣) Badges
     *    + Full Move Pool Breakdown (All Normal Fast/Charged TMs vs All Elite Fast/Charged TMs)
     * 4. Recommended Power-Up Level & Verdict + Level 15 / 20 / 25 / 30 / 35 / 40 / 50 CP Comparison Table
     * 5. Type Weaknesses (2.56× Double Weak / 1.6× Weak) & Resistances + Instant Pokémon Search/Re-Scan!
     */
    private void openFullPagePokemonScannerOverlay(
            final PokemonMetaAnalyzer.PokemonScanReport initialReport,
            final String rawOcrDebug
    ) {
        removePokemonScannerOverlayIfPresent();
        if (initialReport == null || initialReport.entry == null) return;

        int overlayType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        pokemonScannerOverlayLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        if (Build.VERSION.SDK_INT >= 28) {
            pokemonScannerOverlayLp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        activePokemonScannerOverlay = new FrameLayout(this);
        activePokemonScannerOverlay.setBackgroundColor(Color.parseColor("#EA040914"));

        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);

        final LinearLayout pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setPadding(dp(14), dp(34), dp(14), dp(28));

        // TOP HEADER BAR: Title + Re-Scan Screen + Close Full Page
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(0, 0, 0, dp(10));

        TextView pageTitle = new TextView(this);
        pageTitle.setText("🧬 POKÉMON SCANNER & TM META PAGE");
        pageTitle.setTextColor(Color.parseColor("#38BDF8"));
        pageTitle.setTextSize(13.5f);
        pageTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerBar.addView(pageTitle, ptLp);

        Button rescanBtn = createCompactTopButton("🔄 Re-Scan Screen", "#10B981", Color.parseColor("#04131D"));
        LinearLayout.LayoutParams rsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rsLp.setMargins(0, 0, dp(6), 0);
        rescanBtn.setLayoutParams(rsLp);
        rescanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerPokemonScreenScan();
            }
        });
        headerBar.addView(rescanBtn);

        Button closePageBtn = createCompactTopButton("✕ Close", "#E11D48", Color.WHITE);
        closePageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && activePokemonScannerOverlay != null) {
                    imm.hideSoftInputFromWindow(activePokemonScannerOverlay.getWindowToken(), 0);
                }
                removePokemonScannerOverlayIfPresent();
            }
        });
        headerBar.addView(closePageBtn);
        pageContent.addView(headerBar);

        // INSTANT POKÉMON SEARCH / SWITCH BAR (in case Pokémon is nicknamed or user wants to look up any species)
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, 0, 0, dp(8));

        final EditText switchPokeInput = new EditText(this);
        switchPokeInput.setHint("🔍 Type any Pokémon name to switch/compare (e.g. Rayquaza, Lucario, Azumarill)...");
        switchPokeInput.setHintTextColor(Color.parseColor("#64748B"));
        switchPokeInput.setTextColor(Color.WHITE);
        switchPokeInput.setTextSize(11.5f);
        switchPokeInput.setSingleLine(true);
        switchPokeInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable spBg = new GradientDrawable();
        spBg.setColor(Color.parseColor("#0B1528"));
        spBg.setCornerRadius(dp(10));
        spBg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        switchPokeInput.setBackground(spBg);
        LinearLayout.LayoutParams spiLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        spiLp.setMargins(0, 0, dp(6), 0);
        switchPokeInput.setLayoutParams(spiLp);

        switchPokeInput.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    setPokemonOverlayFocusable(true);
                    switchPokeInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(switchPokeInput, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
                return false;
            }
        });

        Button goSwitchBtn = createCompactTopButton("Go", "#0284C7", Color.WHITE);
        goSwitchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String q = switchPokeInput.getText() != null ? switchPokeInput.getText().toString().trim() : "";
                if (q.length() >= 2) {
                    setPokemonOverlayFocusable(false);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(switchPokeInput.getWindowToken(), 0);
                    }
                    List<PokemonMetaAnalyzer.PokemonScanReport> matches = PokemonMetaAnalyzer.searchPokemonByName(q);
                    if (!matches.isEmpty()) {
                        PokemonMetaAnalyzer.PokemonScanReport picked = new PokemonMetaAnalyzer.PokemonScanReport(
                                matches.get(0).entry,
                                initialReport.scannedCp,
                                initialReport.scannedHp,
                                initialReport.isShadow || matches.get(0).isShadow,
                                initialReport.isLucky || matches.get(0).isLucky,
                                initialReport.isDynamax,
                                initialReport.appraisal
                        );
                        lastScannedPokemonReport = picked;
                        renderResearchCards();
                        openFullPagePokemonScannerOverlay(picked, rawOcrDebug);
                    } else {
                        Toast.makeText(FloatingViewService.this, "No Pokémon found matching \"" + q + "\"", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        searchRow.addView(switchPokeInput);
        searchRow.addView(goSwitchBtn);
        pageContent.addView(searchRow);

        // Quick-Select Suggestion Chips Row (updates dynamically as user types, or shows top Meta Pokémon)
        final HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(0, 0, 0, dp(10));
        final LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow);
        pageContent.addView(chipScroll);

        final Runnable populateQuickChips = new Runnable() {
            @Override
            public void run() {
                chipRow.removeAllViews();
                String q = switchPokeInput.getText() != null ? switchPokeInput.getText().toString().trim() : "";
                List<PokemonMetaAnalyzer.PokemonScanReport> list;
                if (q.length() >= 2) {
                    list = PokemonMetaAnalyzer.searchPokemonByName(q);
                } else {
                    list = new ArrayList<PokemonMetaAnalyzer.PokemonScanReport>();
                    String[] quickNames = new String[]{
                            "Rayquaza", "Mewtwo", "Metagross", "Tyranitar", "Garchomp",
                            "Lucario", "Origin Forme Palkia", "Origin Forme Dialga",
                            "Dusk Mane Necrozma", "Rhyperior", "Mamoswine", "Landorus (Therian)",
                            "Kartana", "Terrakion", "Annihilape", "Azumarill", "Clodsire", "Feraligatr"
                    };
                    for (int i = 0; i < quickNames.length; i++) {
                        List<PokemonMetaAnalyzer.PokemonScanReport> m = PokemonMetaAnalyzer.searchPokemonByName(quickNames[i]);
                        if (!m.isEmpty()) list.add(m.get(0));
                    }
                }
                for (int i = 0; i < list.size(); i++) {
                    final PokemonMetaAnalyzer.PokemonMetaEntry candEntry = list.get(i).entry;
                    Button chip = createCompactTopButton(
                            candEntry.name + " [" + candEntry.tier + "]",
                            candEntry.name.equalsIgnoreCase(initialReport.entry.name) ? "#065F46" : "#111C30",
                            Color.WHITE
                    );
                    LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    cLp.setMargins(0, 0, dp(5), 0);
                    chip.setLayoutParams(cLp);
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            setPokemonOverlayFocusable(false);
                            PokemonMetaAnalyzer.PokemonScanReport switched = new PokemonMetaAnalyzer.PokemonScanReport(
                                    candEntry,
                                    initialReport.scannedCp,
                                    initialReport.scannedHp,
                                    initialReport.isShadow,
                                    initialReport.isLucky,
                                    initialReport.isDynamax,
                                    initialReport.appraisal
                            );
                            lastScannedPokemonReport = switched;
                            renderResearchCards();
                            openFullPagePokemonScannerOverlay(switched, rawOcrDebug);
                        }
                    });
                    chipRow.addView(chip);
                }
            }
        };
        populateQuickChips.run();

        switchPokeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                populateQuickChips.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        final PokemonMetaAnalyzer.PokemonMetaEntry e = initialReport.entry;
        String tierHex;
        if (initialReport.effectiveTier.startsWith("S")) {
            tierHex = "#F59E0B"; // Gold S+ / S
        } else if (initialReport.effectiveTier.startsWith("A")) {
            tierHex = "#10B981"; // Emerald A+ / A
        } else if (initialReport.effectiveTier.startsWith("B")) {
            tierHex = "#38BDF8"; // Sky B
        } else if (initialReport.effectiveTier.startsWith("C")) {
            tierHex = "#A78BFA"; // Purple C
        } else {
            tierHex = "#94A3B8"; // Slate D / F
        }

        // =========================================================================
        // CARD 1: VISUAL HERO HEADER, TIER BADGE, VERDICT & SHADOW / LUCKY TOGGLES
        // =========================================================================
        LinearLayout heroCard = createFullPageSectionCard(tierHex);

        LinearLayout heroTopRow = new LinearLayout(this);
        heroTopRow.setOrientation(LinearLayout.HORIZONTAL);
        heroTopRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tierBadgePill = new TextView(this);
        tierBadgePill.setText(" " + initialReport.effectiveTier + " TIER ");
        tierBadgePill.setTextColor(Color.parseColor("#04131D"));
        tierBadgePill.setTextSize(12f);
        tierBadgePill.setTypeface(Typeface.DEFAULT_BOLD);
        tierBadgePill.setPadding(dp(9), dp(3), dp(9), dp(3));
        GradientDrawable tbpBg = new GradientDrawable();
        tbpBg.setColor(Color.parseColor(tierHex));
        tbpBg.setCornerRadius(dp(8));
        tierBadgePill.setBackground(tbpBg);
        heroTopRow.addView(tierBadgePill);

        TextView speciesTitle = new TextView(this);
        String cpText = initialReport.scannedCp > 0 ? (" • CP " + initialReport.scannedCp) : "";
        String hpText = initialReport.scannedHp > 0 ? (" • " + initialReport.scannedHp + " HP") : "";
        speciesTitle.setText("  " + initialReport.displayTitle + cpText + hpText);
        speciesTitle.setTextColor(Color.WHITE);
        speciesTitle.setTextSize(17.5f);
        speciesTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams stTitleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        heroTopRow.addView(speciesTitle, stTitleLp);
        heroCard.addView(heroTopRow);

        TextView typeAndStats = new TextView(this);
        typeAndStats.setText("🏷 " + e.types + "   |   ATK " + e.baseAtk + " • DEF " + e.baseDef + " • STA " + e.baseSta);
        typeAndStats.setTextColor(Color.parseColor("#7DD3FC"));
        typeAndStats.setTextSize(11.5f);
        typeAndStats.setTypeface(Typeface.DEFAULT_BOLD);
        typeAndStats.setPadding(0, dp(4), 0, dp(4));
        heroCard.addView(typeAndStats);

        // Compact Power-Up Verdict Banner inside Hero Card
        LinearLayout verdictBanner = new LinearLayout(this);
        verdictBanner.setOrientation(LinearLayout.VERTICAL);
        verdictBanner.setPadding(dp(10), dp(7), dp(10), dp(7));
        GradientDrawable vbBg = new GradientDrawable();
        vbBg.setColor(Color.parseColor("#06101E"));
        vbBg.setCornerRadius(dp(10));
        vbBg.setStroke(dp(1), Color.parseColor(initialReport.effectiveIsMeta ? "#10B981" : "#475569"));
        verdictBanner.setBackground(vbBg);

        TextView vHeadline = new TextView(this);
        vHeadline.setText(initialReport.verdictHeadline + "   •   🎯 " + initialReport.powerUpTargetText);
        vHeadline.setTextColor(Color.parseColor(initialReport.effectiveIsMeta ? "#6EE7B7" : "#FCA5A5"));
        vHeadline.setTextSize(12.5f);
        vHeadline.setTypeface(Typeface.DEFAULT_BOLD);
        verdictBanner.addView(vHeadline);

        if (initialReport.detailedAdvice != null && initialReport.detailedAdvice.length() > 0) {
            TextView vAdvice = new TextView(this);
            vAdvice.setText(initialReport.detailedAdvice);
            vAdvice.setTextColor(Color.parseColor("#CBD5E1"));
            vAdvice.setTextSize(11f);
            vAdvice.setPadding(0, dp(2), 0, 0);
            verdictBanner.addView(vAdvice);
        }
        heroCard.addView(verdictBanner);

        // Interactive Shadow & Lucky Toggles
        LinearLayout modRow = new LinearLayout(this);
        modRow.setOrientation(LinearLayout.HORIZONTAL);
        modRow.setPadding(0, dp(8), 0, 0);

        Button shadowToggleBtn = createCompactTopButton(
                initialReport.isShadow ? "😈 Shadow: ON" : "😈 Shadow: OFF",
                initialReport.isShadow ? "#581C87" : "#162235",
                Color.WHITE
        );
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stLp.setMargins(0, 0, dp(6), 0);
        shadowToggleBtn.setLayoutParams(stLp);
        shadowToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PokemonMetaAnalyzer.PokemonScanReport next = new PokemonMetaAnalyzer.PokemonScanReport(
                        e,
                        initialReport.scannedCp,
                        initialReport.scannedHp,
                        !initialReport.isShadow,
                        initialReport.isLucky,
                        initialReport.isDynamax,
                        initialReport.appraisal,
                        initialReport.scannedFastMove,
                        initialReport.scannedChargedMove1,
                        initialReport.scannedChargedMove2
                );
                lastScannedPokemonReport = next;
                renderResearchCards();
                openFullPagePokemonScannerOverlay(next, rawOcrDebug);
            }
        });

        Button luckyToggleBtn = createCompactTopButton(
                initialReport.isLucky ? "✨ Lucky (-50% Dust): ON" : "✨ Lucky: OFF",
                initialReport.isLucky ? "#854D0E" : "#162235",
                Color.WHITE
        );
        LinearLayout.LayoutParams ltLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        luckyToggleBtn.setLayoutParams(ltLp);
        luckyToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PokemonMetaAnalyzer.PokemonScanReport next = new PokemonMetaAnalyzer.PokemonScanReport(
                        e,
                        initialReport.scannedCp,
                        initialReport.scannedHp,
                        initialReport.isShadow,
                        !initialReport.isLucky,
                        initialReport.isDynamax,
                        initialReport.appraisal,
                        initialReport.scannedFastMove,
                        initialReport.scannedChargedMove1,
                        initialReport.scannedChargedMove2
                );
                lastScannedPokemonReport = next;
                renderResearchCards();
                openFullPagePokemonScannerOverlay(next, rawOcrDebug);
            }
        });

        modRow.addView(shadowToggleBtn);
        modRow.addView(luckyToggleBtn);
        heroCard.addView(modRow);
        pageContent.addView(heroCard);

        // =========================================================================
        // CARD 2: SCANNED MOVESET VERDICT + RAID (SAME-ELEMENT) vs. PVP (COVERAGE)
        // =========================================================================
        LinearLayout movesCard = createFullPageSectionCard("#38BDF8");

        TextView mSectionTitle = new TextView(this);
        mSectionTitle.setText("⚔ MOVESET SCANNER: RAID (SAME-ELEMENT) vs. PVP (COVERAGE)");
        mSectionTitle.setTextColor(Color.parseColor("#38BDF8"));
        mSectionTitle.setTextSize(11.5f);
        mSectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        movesCard.addView(mSectionTitle);

        // If equipped moves were scanned on the Pokémon screen, show a highlighted status box!
        if (initialReport.hasScannedMoves) {
            LinearLayout eqBox = new LinearLayout(this);
            eqBox.setOrientation(LinearLayout.VERTICAL);
            eqBox.setPadding(dp(10), dp(7), dp(10), dp(7));
            GradientDrawable eqBg = new GradientDrawable();
            boolean isOpt = initialReport.scannedMovesMatchRaid || initialReport.scannedMovesMatchPvp;
            eqBg.setColor(Color.parseColor(isOpt ? "#052E16" : "#271705"));
            eqBg.setCornerRadius(dp(9));
            eqBg.setStroke(dp(1), Color.parseColor(isOpt ? "#10B981" : "#F59E0B"));
            eqBox.setBackground(eqBg);
            LinearLayout.LayoutParams eqLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            eqLp.setMargins(0, dp(6), 0, dp(4));
            eqBox.setLayoutParams(eqLp);

            TextView eqTitle = new TextView(this);
            eqTitle.setText("🔎 YOUR SCANNED MOVES: " + initialReport.scannedMovesDisplay);
            eqTitle.setTextColor(Color.WHITE);
            eqTitle.setTextSize(12f);
            eqTitle.setTypeface(Typeface.DEFAULT_BOLD);
            eqBox.addView(eqTitle);

            TextView eqVerdict = new TextView(this);
            eqVerdict.setText(initialReport.scannedMovesVerdict);
            eqVerdict.setTextColor(Color.parseColor(isOpt ? "#6EE7B7" : "#FDE68A"));
            eqVerdict.setTextSize(11.5f);
            eqVerdict.setTypeface(Typeface.DEFAULT_BOLD);
            eqVerdict.setPadding(0, dp(2), 0, 0);
            eqBox.addView(eqVerdict);

            movesCard.addView(eqBox);
        }

        TextView optimalTmBreakdown = new TextView(this);
        optimalTmBreakdown.setText(e.getOptimalMovesTmBreakdown());
        optimalTmBreakdown.setTextColor(Color.parseColor("#FDE68A"));
        optimalTmBreakdown.setTextSize(12f);
        optimalTmBreakdown.setTypeface(Typeface.DEFAULT_BOLD);
        optimalTmBreakdown.setPadding(0, dp(5), 0, dp(6));
        movesCard.addView(optimalTmBreakdown);

        // Collapsible Full Move Pool & Type Weaknesses (keeps default scan view visual & uncluttered)
        final LinearLayout poolBox = new LinearLayout(this);
        poolBox.setOrientation(LinearLayout.VERTICAL);
        poolBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        poolBox.setVisibility(View.GONE);
        GradientDrawable pbBg = new GradientDrawable();
        pbBg.setColor(Color.parseColor("#060D1A"));
        pbBg.setCornerRadius(dp(10));
        pbBg.setStroke(dp(1), Color.parseColor("#1E293B"));
        poolBox.setBackground(pbBg);

        TextView poolBody = new TextView(this);
        poolBody.setText(e.getFullMovePoolSummary()
                + "\n\n⚔ Raid Role: " + e.raidRole
                + "\n🛡 PvP Role: " + e.pvpRole
                + "\n\n" + e.getTypeWeaknessSummary());
        poolBody.setTextColor(Color.parseColor("#E2E8F0"));
        poolBody.setTextSize(11f);
        poolBox.addView(poolBody);

        final Button togglePoolBtn = createCompactTopButton("▼ Show Full Move Pool & Type Weaknesses", "#111C30", Color.parseColor("#7DD3FC"));
        togglePoolBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = poolBox.getVisibility() != View.VISIBLE;
                poolBox.setVisibility(show ? View.VISIBLE : View.GONE);
                togglePoolBtn.setText(show ? "▲ Hide Full Move Pool & Type Weaknesses" : "▼ Show Full Move Pool & Type Weaknesses");
            }
        });
        movesCard.addView(togglePoolBtn);
        movesCard.addView(poolBox);
        pageContent.addView(movesCard);

        // =========================================================================
        // CARD 3: TEAM LEADER APPRAISAL IV BARS & INTERACTIVE IV TUNER
        // =========================================================================
        LinearLayout ivCard = createFullPageSectionCard("#F59E0B");

        final boolean hasScannedIvs = initialReport.appraisal != null && initialReport.appraisal.appraisalCardDetected;
        final int curAtk = hasScannedIvs ? initialReport.appraisal.ivAtk : 15;
        final int curDef = hasScannedIvs ? initialReport.appraisal.ivDef : 15;
        final int curSta = hasScannedIvs ? initialReport.appraisal.ivSta : 15;

        TextView ivHeader = new TextView(this);
        ivHeader.setText(hasScannedIvs
                ? ("⭐ SCANNED APPRAISAL IVs: " + initialReport.appraisal.starRating
                        + "  (" + curAtk + " / " + curDef + " / " + curSta + ")")
                : "📊 APPRAISAL IVs (Open 'Appraise' in-game & tap Re-Scan, or tune below):");
        ivHeader.setTextColor(Color.parseColor("#FBBF24"));
        ivHeader.setTextSize(11.5f);
        ivHeader.setTypeface(Typeface.DEFAULT_BOLD);
        ivHeader.setPadding(0, 0, 0, dp(6));
        ivCard.addView(ivHeader);

        ivCard.addView(createIvStatRow("ATK", curAtk, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, Math.max(0, curAtk - 1), curDef, curSta, rawOcrDebug);
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, Math.min(15, curAtk + 1), curDef, curSta, rawOcrDebug);
            }
        }));

        ivCard.addView(createIvStatRow("DEF", curDef, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, curAtk, Math.max(0, curDef - 1), curSta, rawOcrDebug);
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, curAtk, Math.min(15, curDef + 1), curSta, rawOcrDebug);
            }
        }));

        ivCard.addView(createIvStatRow("HP ", curSta, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, curAtk, curDef, Math.max(0, curSta - 1), rawOcrDebug);
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, curAtk, curDef, Math.min(15, curSta + 1), rawOcrDebug);
            }
        }));

        LinearLayout ivPresetsRow = new LinearLayout(this);
        ivPresetsRow.setOrientation(LinearLayout.HORIZONTAL);
        ivPresetsRow.setPadding(0, dp(6), 0, 0);

        Button pHundoBtn = createCompactTopButton("💯 15/15/15", "#065F46", Color.WHITE);
        LinearLayout.LayoutParams phLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        phLp.setMargins(0, 0, dp(5), 0);
        pHundoBtn.setLayoutParams(phLp);
        pHundoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, 15, 15, 15, rawOcrDebug);
            }
        });

        Button p98Btn = createCompactTopButton("⭐ 15/15/14", "#162235", Color.WHITE);
        LinearLayout.LayoutParams p98Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p98Lp.setMargins(0, 0, dp(5), 0);
        p98Btn.setLayoutParams(p98Lp);
        p98Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, 15, 15, 14, rawOcrDebug);
            }
        });

        Button pPvpBtn = createCompactTopButton("🛡 0/15/15 PvP", "#162235", Color.WHITE);
        LinearLayout.LayoutParams ppvpLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pPvpBtn.setLayoutParams(ppvpLp);
        pPvpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateOverlayWithCustomIvs(initialReport, 0, 15, 15, rawOcrDebug);
            }
        });

        ivPresetsRow.addView(pHundoBtn);
        ivPresetsRow.addView(p98Btn);
        ivPresetsRow.addView(pPvpBtn);
        ivCard.addView(ivPresetsRow);
        pageContent.addView(ivCard);

        // =========================================================================
        // CARD 4: VISUAL 4-BOX CP BENCHMARKS (LV 15, LV 20, LV 40, LV 50)
        // =========================================================================
        LinearLayout cpCard = createFullPageSectionCard("#38BDF8");

        TextView cpHeader = new TextView(this);
        cpHeader.setText("📈 CP BY LEVEL (YOUR " + curAtk + "/" + curDef + "/" + curSta + " IVs  vs.  100% HUNDO)");
        cpHeader.setTextColor(Color.parseColor("#7DD3FC"));
        cpHeader.setTextSize(11f);
        cpHeader.setTypeface(Typeface.DEFAULT_BOLD);
        cpHeader.setPadding(0, 0, 0, dp(6));
        cpCard.addView(cpHeader);

        int your15 = PokemonMetaAnalyzer.computeCp(e.baseAtk, e.baseDef, e.baseSta, curAtk, curDef, curSta, PokemonMetaAnalyzer.CPM_LV15);
        int your20 = PokemonMetaAnalyzer.computeCp(e.baseAtk, e.baseDef, e.baseSta, curAtk, curDef, curSta, PokemonMetaAnalyzer.CPM_LV20);
        int your40 = PokemonMetaAnalyzer.computeCp(e.baseAtk, e.baseDef, e.baseSta, curAtk, curDef, curSta, PokemonMetaAnalyzer.CPM_LV40);
        int your50 = PokemonMetaAnalyzer.computeCp(e.baseAtk, e.baseDef, e.baseSta, curAtk, curDef, curSta, PokemonMetaAnalyzer.CPM_LV50);

        LinearLayout cpGridRow = new LinearLayout(this);
        cpGridRow.setOrientation(LinearLayout.HORIZONTAL);
        cpGridRow.addView(createVisualCpBox("Lv 15 Res", your15, initialReport.cpLv15Hundo));
        cpGridRow.addView(createVisualCpBox("Lv 20 Raid", your20, initialReport.cpLv20Hundo));
        cpGridRow.addView(createVisualCpBox("Lv 40 Max", your40, initialReport.cpLv40Hundo));
        cpGridRow.addView(createVisualCpBox("Lv 50 XL", your50, initialReport.cpLv50Hundo));
        cpCard.addView(cpGridRow);
        pageContent.addView(cpCard);

        pageScroll.addView(pageContent);
        activePokemonScannerOverlay.addView(pageScroll);
        windowManager.addView(activePokemonScannerOverlay, pokemonScannerOverlayLp);
    }

    private View createVisualCpBox(String levelLabel, int yourCp, int hundoCp) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(6), dp(4), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#06101E"));
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.parseColor("#1E293B"));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        box.setLayoutParams(lp);

        TextView lbl = new TextView(this);
        lbl.setText(levelLabel);
        lbl.setTextColor(Color.parseColor("#94A3B8"));
        lbl.setTextSize(9.5f);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(lbl);

        TextView val = new TextView(this);
        val.setText(yourCp + " CP");
        val.setTextColor(Color.WHITE);
        val.setTextSize(12f);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(val);

        TextView hundo = new TextView(this);
        hundo.setText("💯 " + hundoCp);
        hundo.setTextColor(Color.parseColor("#FDE68A"));
        hundo.setTextSize(9.5f);
        box.addView(hundo);

        return box;
    }

    private LinearLayout createFullPageSectionCard(String borderHex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0B1528"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(2), Color.parseColor(borderHex));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    private View createIvStatRow(
            String label,
            int statVal,
            View.OnClickListener onMinus,
            View.OnClickListener onPlus
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView lbl = new TextView(this);
        lbl.setText(label + ": " + statVal + "/15");
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(11.5f);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(lbl, lLp);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(15);
        pb.setProgress(statVal);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(0, dp(10), 1f);
        pbLp.setMargins(dp(6), 0, dp(8), 0);
        row.addView(pb, pbLp);

        Button minusBtn = createCompactTopButton(" - ", "#1E293B", Color.WHITE);
        minusBtn.setOnClickListener(onMinus);
        row.addView(minusBtn);

        Button plusBtn = createCompactTopButton(" + ", "#0284C7", Color.WHITE);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pLp.setMargins(dp(4), 0, 0, 0);
        plusBtn.setLayoutParams(pLp);
        plusBtn.setOnClickListener(onPlus);
        row.addView(plusBtn);

        return row;
    }

    private void updateOverlayWithCustomIvs(
            PokemonMetaAnalyzer.PokemonScanReport current,
            int newAtk,
            int newDef,
            int newSta,
            String rawOcrDebug
    ) {
        PokemonMetaAnalyzer.AppraisalBarVisionResult customAppraisal =
                new PokemonMetaAnalyzer.AppraisalBarVisionResult(
                        true, newAtk, newDef, newSta, current.isShadow
                );
        PokemonMetaAnalyzer.PokemonScanReport updated =
                new PokemonMetaAnalyzer.PokemonScanReport(
                        current.entry,
                        current.scannedCp,
                        current.scannedHp,
                        current.isShadow,
                        current.isLucky,
                        current.isDynamax,
                        customAppraisal,
                        current.scannedFastMove,
                        current.scannedChargedMove1,
                        current.scannedChargedMove2
                );
        lastScannedPokemonReport = updated;
        renderResearchCards();
        openFullPagePokemonScannerOverlay(updated, rawOcrDebug);
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
        removePokemonScannerOverlayIfPresent();
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
