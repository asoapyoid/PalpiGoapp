package com.pokemate.companion;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    public static final int CURRENT_VERSION_CODE = 28;
    public static final String CURRENT_VERSION_NAME = "28.0.2026";
    public static final String OTA_MANIFEST_URL = "https://ntfy.sh/pokemate_ota_4e6c31bd/raw?poll=1&since=all";

    private LinearLayout normalControlsContainer;
    private LinearLayout pipCompactHudContainer;
    private TextView pipStatusView;
    private TextView permissionStatusView;
    private TextView updateStatusBanner;
    private LinearLayout mainResearchCardsList;

    // Main App Settings Card Controls
    private LinearLayout settingsCardBody;
    private Button toggleSettingsCardBtn;
    private TextView appOpacityLabel;
    private SeekBar appOpacitySeekBar;
    private Button appMiniThrowBtn;
    private Button appMiniGiftBtn;
    private Button appMiniScanBtn;
    private Button appQuickCatchBtn;
    private Button appSpinThrowBtn;
    private Button appGiftModeBtn;
    private Button appDelaySpeedBtn;
    private Button appAutoHidePoGoBtn;

    private boolean isSettingsExpanded = true;
    private String searchQuery = "";
    private String categoryFilter = "ALL";
    private String latestDirectApkUrl = "https://ais-dev-gewg5eor3v4efc5hr235jz-420113228336.europe-west1.run.app/pokemate-companion.apk";

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollRoot = new ScrollView(this);
        scrollRoot.setFillViewport(true);
        scrollRoot.setBackgroundColor(Color.parseColor("#060B14"));

        LinearLayout masterContainer = new LinearLayout(this);
        masterContainer.setOrientation(LinearLayout.VERTICAL);

        // 1. Compact PiP Mode View
        pipCompactHudContainer = new LinearLayout(this);
        pipCompactHudContainer.setOrientation(LinearLayout.VERTICAL);
        pipCompactHudContainer.setPadding(dp(10), dp(8), dp(10), dp(8));
        pipCompactHudContainer.setBackgroundColor(Color.parseColor("#0A1220"));
        pipCompactHudContainer.setVisibility(View.GONE);

        TextView pipHeader = new TextView(this);
        pipHeader.setText("⚡ PalpiGO PiP HUD");
        pipHeader.setTextColor(Color.parseColor("#34D399"));
        pipHeader.setTextSize(12f);
        pipHeader.setTypeface(Typeface.DEFAULT_BOLD);
        pipCompactHudContainer.addView(pipHeader);

        pipStatusView = new TextView(this);
        pipStatusView.setText("Active over Pokémon GO");
        pipStatusView.setTextColor(Color.WHITE);
        pipStatusView.setTextSize(10.5f);
        pipStatusView.setPadding(0, dp(4), 0, 0);
        pipCompactHudContainer.addView(pipStatusView);
        masterContainer.addView(pipCompactHudContainer);

        // 2. Full App Screen Container
        normalControlsContainer = new LinearLayout(this);
        normalControlsContainer.setOrientation(LinearLayout.VERTICAL);
        normalControlsContainer.setPadding(dp(16), dp(20), dp(16), dp(28));

        // HERO BRAND CARD WITH PIXEL PALPITOAD + SETTINGS BUTTON
        LinearLayout heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0F243C"), Color.parseColor("#091322")}
        );
        heroBg.setCornerRadius(dp(18));
        heroBg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        heroCard.setBackground(heroBg);

        LinearLayout titleBrandRow = new LinearLayout(this);
        titleBrandRow.setOrientation(LinearLayout.HORIZONTAL);
        titleBrandRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView palpitoadIcon = new ImageView(this);
        palpitoadIcon.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(50), dp(50));
        iconLp.setMargins(0, 0, dp(12), 0);
        titleBrandRow.addView(palpitoadIcon, iconLp);

        LinearLayout titleTextCol = new LinearLayout(this);
        titleTextCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ttColLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleTextCol.setLayoutParams(ttColLp);

        TextView title = new TextView(this);
        title.setText("PalpiGO");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleTextCol.addView(title);

        TextView verPill = new TextView(this);
        verPill.setText("v" + CURRENT_VERSION_NAME + " • CUSTOMIZABLE HUD");
        verPill.setTextColor(Color.parseColor("#34D399"));
        verPill.setTextSize(10.5f);
        verPill.setTypeface(Typeface.DEFAULT_BOLD);
        titleTextCol.addView(verPill);

        titleBrandRow.addView(titleTextCol);

        toggleSettingsCardBtn = createSmallActionButton("⚙ Settings ▲", "#0284C7", Color.WHITE);
        toggleSettingsCardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSettingsExpanded = !isSettingsExpanded;
                settingsCardBody.setVisibility(isSettingsExpanded ? View.VISIBLE : View.GONE);
                toggleSettingsCardBtn.setText(isSettingsExpanded ? "⚙ Settings ▲" : "⚙ Settings ▼");
            }
        });
        titleBrandRow.addView(toggleSettingsCardBtn);

        heroCard.addView(titleBrandRow);

        TextView subtitle = new TextView(this);
        subtitle.setText("Use the ⚙ Settings panel below (or inside the floating HUD) to adjust Big Mode Transparency and choose which buttons appear on your Minimized Pill!");
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setTextSize(11.5f);
        subtitle.setPadding(0, dp(8), 0, 0);
        heroCard.addView(subtitle);

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        heroLp.setMargins(0, 0, 0, dp(12));
        normalControlsContainer.addView(heroCard, heroLp);

        // =========================================================================
        // ⚙ SETTINGS & CUSTOMIZATION CARD (Open by default so it's impossible to miss!)
        // =========================================================================
        settingsCardBody = new LinearLayout(this);
        settingsCardBody.setOrientation(LinearLayout.VERTICAL);
        settingsCardBody.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable setBg = new GradientDrawable();
        setBg.setColor(Color.parseColor("#0B1528"));
        setBg.setCornerRadius(dp(16));
        setBg.setStroke(dp(1), Color.parseColor("#38BDF8"));
        settingsCardBody.setBackground(setBg);
        LinearLayout.LayoutParams setCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        setCardLp.setMargins(0, 0, 0, dp(12));
        settingsCardBody.setLayoutParams(setCardLp);

        TextView setHeader = new TextView(this);
        setHeader.setText("⚙ PALPIGO HUD & AUTOMATION SETTINGS");
        setHeader.setTextColor(Color.parseColor("#38BDF8"));
        setHeader.setTextSize(13f);
        setHeader.setTypeface(Typeface.DEFAULT_BOLD);
        setHeader.setPadding(0, 0, 0, dp(8));
        settingsCardBody.addView(setHeader);

        // 1. Transparency Slider
        appOpacityLabel = new TextView(this);
        appOpacityLabel.setTextColor(Color.WHITE);
        appOpacityLabel.setTextSize(12f);
        appOpacityLabel.setTypeface(Typeface.DEFAULT_BOLD);
        settingsCardBody.addView(appOpacityLabel);

        appOpacitySeekBar = new SeekBar(this);
        appOpacitySeekBar.setMax(73); // 25% to 98%
        appOpacitySeekBar.setProgress(LearnedProfileStore.getHudOpacityPercent(this) - 25);
        appOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = 25 + progress;
                LearnedProfileStore.setHudOpacityPercent(MainActivity.this, pct);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        settingsCardBody.addView(appOpacitySeekBar);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(0, dp(4), 0, dp(12));
        final int[] presets = new int[]{35, 55, 72, 92};
        final String[] presetLabels = new String[]{"35% Clear", "55% Glass", "72% Smoked", "92% Solid"};
        for (int i = 0; i < presets.length; i++) {
            final int pVal = presets[i];
            Button pb = createSmallActionButton(presetLabels[i], "#162235", Color.parseColor("#7DD3FC"));
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i < presets.length - 1) pLp.setMargins(0, 0, dp(5), 0);
            pb.setLayoutParams(pLp);
            pb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LearnedProfileStore.setHudOpacityPercent(MainActivity.this, pVal);
                    syncMainSettingsUi();
                    if (FloatingViewService.instance != null) {
                        FloatingViewService.instance.applyUserSettingsLive();
                    }
                }
            });
            presetRow.addView(pb);
        }
        settingsCardBody.addView(presetRow);

        // 2. Minimized Pill Button Toggles
        TextView miniSecTitle = new TextView(this);
        miniSecTitle.setText("Minimized Pill Buttons (Tap to toggle what shows when minimized):");
        miniSecTitle.setTextColor(Color.parseColor("#CBD5E1"));
        miniSecTitle.setTextSize(11.5f);
        miniSecTitle.setTypeface(Typeface.DEFAULT_BOLD);
        miniSecTitle.setPadding(0, 0, 0, dp(6));
        settingsCardBody.addView(miniSecTitle);

        LinearLayout miniTogglesRow = new LinearLayout(this);
        miniTogglesRow.setOrientation(LinearLayout.HORIZONTAL);
        miniTogglesRow.setPadding(0, 0, 0, dp(12));

        appMiniThrowBtn = createSmallActionButton("🎯 Throw: ON", "#065F46", Color.WHITE);
        LinearLayout.LayoutParams mt1Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mt1Lp.setMargins(0, 0, dp(5), 0);
        appMiniThrowBtn.setLayoutParams(mt1Lp);
        appMiniThrowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowThrow(MainActivity.this);
                LearnedProfileStore.setMiniShowThrow(MainActivity.this, next);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });

        appMiniGiftBtn = createSmallActionButton("▶ Gift: OFF", "#162235", Color.WHITE);
        LinearLayout.LayoutParams mt2Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mt2Lp.setMargins(0, 0, dp(5), 0);
        appMiniGiftBtn.setLayoutParams(mt2Lp);
        appMiniGiftBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowGift(MainActivity.this);
                LearnedProfileStore.setMiniShowGift(MainActivity.this, next);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });

        appMiniScanBtn = createSmallActionButton("🔭 Scan: OFF", "#162235", Color.WHITE);
        LinearLayout.LayoutParams mt3Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        appMiniScanBtn.setLayoutParams(mt3Lp);
        appMiniScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowScan(MainActivity.this);
                LearnedProfileStore.setMiniShowScan(MainActivity.this, next);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });

        miniTogglesRow.addView(appMiniThrowBtn);
        miniTogglesRow.addView(appMiniGiftBtn);
        miniTogglesRow.addView(appMiniScanBtn);
        settingsCardBody.addView(miniTogglesRow);

        // 3. Quick Catch, Spin Throw, Gift Mode & Pacing Toggles
        appQuickCatchBtn = createSmallActionButton("⚡ Fast Catch: ON", "#065F46", Color.WHITE);
        LinearLayout.LayoutParams qcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        qcLp.setMargins(0, 0, 0, dp(6));
        appQuickCatchBtn.setLayoutParams(qcLp);
        appQuickCatchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isQuickCatchEnabled(MainActivity.this);
                LearnedProfileStore.setQuickCatchEnabled(MainActivity.this, next);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });
        settingsCardBody.addView(appQuickCatchBtn);

        appSpinThrowBtn = createSmallActionButton("🌀 Spin Throw (Curveball): OFF", "#162235", Color.WHITE);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        stLp.setMargins(0, 0, 0, dp(6));
        appSpinThrowBtn.setLayoutParams(stLp);
        appSpinThrowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isSpinThrowEnabled(MainActivity.this);
                LearnedProfileStore.setSpinThrowEnabled(MainActivity.this, next);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });
        settingsCardBody.addView(appSpinThrowBtn);

        appGiftModeBtn = createSmallActionButton("🎁 Gift Mode: Pin/Unpin + Open + Send Gift", "#162235", Color.parseColor("#38BDF8"));
        LinearLayout.LayoutParams gmLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        gmLp.setMargins(0, 0, 0, dp(6));
        appGiftModeBtn.setLayoutParams(gmLp);
        appGiftModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int nextMode = (LearnedProfileStore.getGiftWorkflowModePref(MainActivity.this) + 1) % 3;
                LearnedProfileStore.setGiftWorkflowModePref(MainActivity.this, nextMode);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });
        settingsCardBody.addView(appGiftModeBtn);

        appDelaySpeedBtn = createSmallActionButton("⏱ Pacing: Standard Fast Speed", "#162235", Color.WHITE);
        LinearLayout.LayoutParams dsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dsLp.setMargins(0, 0, 0, dp(6));
        appDelaySpeedBtn.setLayoutParams(dsLp);
        appDelaySpeedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isSafeHumanDelayEnabled(MainActivity.this);
                LearnedProfileStore.setSafeHumanDelayEnabled(MainActivity.this, next);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });
        settingsCardBody.addView(appDelaySpeedBtn);

        appAutoHidePoGoBtn = createSmallActionButton("👁 Show Overlay: Only in Pokémon GO (Auto-Hide ON)", "#065F46", Color.WHITE);
        LinearLayout.LayoutParams ahLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        ahLp.setMargins(0, 0, 0, dp(8));
        appAutoHidePoGoBtn.setLayoutParams(ahLp);
        appAutoHidePoGoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isAutoHideOutsidePoGoEnabled(MainActivity.this);
                LearnedProfileStore.setAutoHideOutsidePoGoEnabled(MainActivity.this, next);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });
        settingsCardBody.addView(appAutoHidePoGoBtn);

        LinearLayout resetRow = new LinearLayout(this);
        resetRow.setOrientation(LinearLayout.HORIZONTAL);

        Button rThrowsBtn = createSmallActionButton("🗑 Reset Learned Throws", "#7F1D1D", Color.WHITE);
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rtLp.setMargins(0, 0, dp(6), 0);
        rThrowsBtn.setLayoutParams(rtLp);
        rThrowsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.resetLearnedThrow(MainActivity.this);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
                Toast.makeText(MainActivity.this, "Reset learned throws!", Toast.LENGTH_SHORT).show();
            }
        });

        Button rGiftsBtn = createSmallActionButton("🗑 Reset Learned Gifts", "#7F1D1D", Color.WHITE);
        LinearLayout.LayoutParams rgLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rGiftsBtn.setLayoutParams(rgLp);
        rGiftsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LearnedProfileStore.resetLearnedGifts(MainActivity.this);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
                Toast.makeText(MainActivity.this, "Reset learned gifts!", Toast.LENGTH_SHORT).show();
            }
        });

        resetRow.addView(rThrowsBtn);
        resetRow.addView(rGiftsBtn);
        settingsCardBody.addView(resetRow);

        normalControlsContainer.addView(settingsCardBody);
        syncMainSettingsUi();

        // OTA UPDATE CARD
        LinearLayout updateCard = new LinearLayout(this);
        updateCard.setOrientation(LinearLayout.VERTICAL);
        updateCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable updateBg = new GradientDrawable();
        updateBg.setColor(Color.parseColor("#0B1526"));
        updateBg.setCornerRadius(dp(16));
        updateBg.setStroke(dp(1), Color.parseColor("#1E3A5F"));
        updateCard.setBackground(updateBg);
        LinearLayout.LayoutParams uCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        uCardLp.setMargins(0, 0, 0, dp(12));
        updateCard.setLayoutParams(uCardLp);

        updateStatusBanner = new TextView(this);
        updateStatusBanner.setText("Installed: v" + CURRENT_VERSION_NAME + " (Build " + CURRENT_VERSION_CODE + ") • Tap Check Updates anytime");
        updateStatusBanner.setTextColor(Color.parseColor("#7DD3FC"));
        updateStatusBanner.setTextSize(11.5f);
        updateStatusBanner.setTypeface(Typeface.DEFAULT_BOLD);
        updateStatusBanner.setPadding(0, 0, 0, dp(8));
        updateCard.addView(updateStatusBanner);

        LinearLayout updateBtnsRow = new LinearLayout(this);
        updateBtnsRow.setOrientation(LinearLayout.HORIZONTAL);

        Button checkUpdateBtn = createSmallActionButton("🔄 Check Updates", "#0284C7", Color.WHITE);
        LinearLayout.LayoutParams cuLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cuLp.setMargins(0, 0, dp(6), 0);
        checkUpdateBtn.setLayoutParams(cuLp);
        checkUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForAppUpdates();
            }
        });

        Button installUpdateBtn = createSmallActionButton("⬇ Download Latest APK", "#10B981", Color.parseColor("#04131D"));
        LinearLayout.LayoutParams iuLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        installUpdateBtn.setLayoutParams(iuLp);
        installUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Downloading latest APK in browser...", Toast.LENGTH_LONG).show();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(latestDirectApkUrl));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            }
        });

        updateBtnsRow.addView(checkUpdateBtn);
        updateBtnsRow.addView(installUpdateBtn);
        updateCard.addView(updateBtnsRow);
        normalControlsContainer.addView(updateCard);

        // LIVE PERMISSION STATUS BANNER
        permissionStatusView = new TextView(this);
        permissionStatusView.setPadding(dp(14), dp(10), dp(14), dp(10));
        permissionStatusView.setTextSize(11.5f);
        permissionStatusView.setTextColor(Color.parseColor("#E2E8F0"));
        GradientDrawable statusBox = new GradientDrawable();
        statusBox.setColor(Color.parseColor("#0B1322"));
        statusBox.setCornerRadius(dp(14));
        statusBox.setStroke(dp(1), Color.parseColor("#1E293B"));
        permissionStatusView.setBackground(statusBox);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusLp.setMargins(0, 0, 0, dp(12));
        normalControlsContainer.addView(permissionStatusView, statusLp);
        refreshPermissionStatus();

        // PRIMARY LAUNCH BUTTONS CARD
        LinearLayout launchCard = new LinearLayout(this);
        launchCard.setOrientation(LinearLayout.VERTICAL);
        launchCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable launchBg = new GradientDrawable();
        launchBg.setColor(Color.parseColor("#0B1322"));
        launchBg.setCornerRadius(dp(16));
        launchBg.setStroke(dp(1), Color.parseColor("#1E293B"));
        launchCard.setBackground(launchBg);

        Button launchOverlayBtn = createActionButton(
                "⚡ 1. Launch PalpiGO Frosted Overlay HUD",
                "#10B981",
                Color.parseColor("#04131D")
        );
        launchOverlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(
                            MainActivity.this,
                            "Turn ON 'Display over other apps' for PalpiGO, then tap Launch again!",
                            Toast.LENGTH_LONG
                    ).show();
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    );
                    startActivity(intent);
                    return;
                }
                Intent svc = new Intent(MainActivity.this, FloatingViewService.class);
                startService(svc);

                // Try to launch Pokémon GO directly so the overlay immediately appears inside the game!
                String[] pogoPackages = new String[]{
                        "com.nianticlabs.pokemongo",
                        "com.scopely.pokemongo",
                        "com.pokemongo.samsung"
                };
                Intent launchPoGo = null;
                for (int i = 0; i < pogoPackages.length; i++) {
                    launchPoGo = getPackageManager().getLaunchIntentForPackage(pogoPackages[i]);
                    if (launchPoGo != null) break;
                }
                if (launchPoGo != null) {
                    PokemonGoAccessibilityService.isPokemonGoInForeground = true;
                    if (FloatingViewService.instance != null) {
                        FloatingViewService.instance.onForegroundAppChanged(true);
                    }
                    Toast.makeText(
                            MainActivity.this,
                            "🚀 PalpiGO HUD Armed! Launching Pokémon GO (Auto-hides outside game)...",
                            Toast.LENGTH_SHORT
                    ).show();
                    startActivity(launchPoGo);
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            "✓ PalpiGO HUD Armed! Open Pokémon GO — the overlay automatically shows inside Pokémon GO and hides outside it.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });
        launchCard.addView(launchOverlayBtn);

        Button a11yBtn = createActionButton(
                "🤖 2. Enable Accessibility Service (Throws, Gifts & OCR)",
                "#0284C7",
                Color.WHITE
        );
        a11yBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(
                        MainActivity.this,
                        "Tap 'Installed services' -> 'PalpiGO Auto-Gift & Quick Catch' -> Turn ON!",
                        Toast.LENGTH_LONG
                ).show();
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });
        launchCard.addView(a11yBtn);

        LinearLayout permHelperRow = new LinearLayout(this);
        permHelperRow.setOrientation(LinearLayout.HORIZONTAL);
        permHelperRow.setPadding(0, dp(2), 0, 0);

        Button unlockBtn = createSmallActionButton("App Info (Unlock)", "#162235", Color.parseColor("#CBD5E1"));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        uLp.setMargins(0, 0, dp(6), 0);
        unlockBtn.setLayoutParams(uLp);
        unlockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            }
        });

        Button overlayBtn = createSmallActionButton("Overlay Perm", "#162235", Color.parseColor("#CBD5E1"));
        LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        oLp.setMargins(0, 0, dp(6), 0);
        overlayBtn.setLayoutParams(oLp);
        overlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            }
        });

        Button pipBtn = createSmallActionButton("PiP Window", "#162235", Color.parseColor("#34D399"));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pipBtn.setLayoutParams(pLp);
        pipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterFloatingPipHud();
            }
        });

        permHelperRow.addView(unlockBtn);
        permHelperRow.addView(overlayBtn);
        permHelperRow.addView(pipBtn);
        launchCard.addView(permHelperRow);

        normalControlsContainer.addView(launchCard);

        // =========================================================================
        // FIELD RESEARCH BROWSER WITH SEARCH & SEPARATED CARDS
        // =========================================================================
        LinearLayout researchHeaderRow = new LinearLayout(this);
        researchHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        researchHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        researchHeaderRow.setPadding(0, dp(20), 0, dp(8));

        TextView researchHeader = new TextView(this);
        researchHeader.setText("Field Research & Meta Pool");
        researchHeader.setTextColor(Color.WHITE);
        researchHeader.setTextSize(16f);
        researchHeader.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams rhLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        researchHeaderRow.addView(researchHeader, rhLp);

        Button syncBtn = createSmallActionButton("↻ Sync Live Pool", "#0284C7", Color.WHITE);
        syncBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Syncing live Season & Event Research pool...", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final int count = PokeMateDatabase.getInstance(MainActivity.this).syncLiveFromLeekDuck();
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (count > 0) {
                                    Toast.makeText(MainActivity.this, "Synced " + count + " live research tasks!", Toast.LENGTH_SHORT).show();
                                }
                                renderMainResearchCards();
                            }
                        });
                    }
                }).start();
            }
        });
        researchHeaderRow.addView(syncBtn);
        normalControlsContainer.addView(researchHeaderRow);

        // Search Bar
        EditText searchInput = new EditText(this);
        searchInput.setHint("🔍 Search task or reward (e.g. catch 5, throw, rare candy, beldum)...");
        searchInput.setHintTextColor(Color.parseColor("#64748B"));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(13f);
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.parseColor("#0B1322"));
        searchBg.setCornerRadius(dp(12));
        searchBg.setStroke(dp(1), Color.parseColor("#334155"));
        searchInput.setBackground(searchBg);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString() : "";
                renderMainResearchCards();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        normalControlsContainer.addView(searchInput);

        // Category Filter Bar
        HorizontalScrollView catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.setPadding(0, dp(10), 0, dp(12));
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);

        final String[][] cats = new String[][]{
                {"ALL", "All Tasks"},
                {"Event", "🟨 Event (Gold Outline)"},
                {"Bonus", "🟩 Bonus (Green Outline)"},
                {"META", "Meta 🔥"},
                {"Catch", "Catching"},
                {"Throw", "Throwing"},
                {"Raid", "Raids & Battles"},
                {"Explore", "Hatch & Spin"},
                {"Power/Buddy", "Power Up & Buddy"},
                {"SHINY", "Shiny Only ✨"}
        };
        for (int i = 0; i < cats.length; i++) {
            final String key = cats[i][0];
            final String label = cats[i][1];
            Button cBtn = createSmallActionButton(label, "#111C30", Color.WHITE);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cLp.setMargins(0, 0, dp(6), 0);
            cBtn.setLayoutParams(cLp);
            cBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    categoryFilter = key;
                    renderMainResearchCards();
                }
            });
            catRow.addView(cBtn);
        }
        catScroll.addView(catRow);
        normalControlsContainer.addView(catScroll);

        // Separated Research Cards List
        mainResearchCardsList = new LinearLayout(this);
        mainResearchCardsList.setOrientation(LinearLayout.VERTICAL);
        normalControlsContainer.addView(mainResearchCardsList);
        renderMainResearchCards();

        masterContainer.addView(normalControlsContainer);
        scrollRoot.addView(masterContainer);
        setContentView(scrollRoot);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final int synced = PokeMateDatabase.getInstance(MainActivity.this).syncLiveFromLeekDuck();
                if (synced > 0) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            renderMainResearchCards();
                        }
                    });
                }
            }
        }).start();
    }

    private void syncMainSettingsUi() {
        int pct = LearnedProfileStore.getHudOpacityPercent(this);
        if (appOpacityLabel != null) {
            appOpacityLabel.setText("1. Big Mode HUD Opacity: " + pct + "% (" + (100 - pct) + "% Transparent)");
        }
        if (appOpacitySeekBar != null && appOpacitySeekBar.getProgress() != (pct - 25)) {
            appOpacitySeekBar.setProgress(pct - 25);
        }

        boolean showThrow = LearnedProfileStore.isMiniShowThrow(this);
        boolean showGift = LearnedProfileStore.isMiniShowGift(this);
        boolean showScan = LearnedProfileStore.isMiniShowScan(this);

        if (appMiniThrowBtn != null) {
            styleAppToggle(appMiniThrowBtn, showThrow, "🎯 Throw: " + (showThrow ? "ON" : "OFF"));
        }
        if (appMiniGiftBtn != null) {
            styleAppToggle(appMiniGiftBtn, showGift, "▶ Gift: " + (showGift ? "ON" : "OFF"));
        }
        if (appMiniScanBtn != null) {
            styleAppToggle(appMiniScanBtn, showScan, "🔭 Scan: " + (showScan ? "ON" : "OFF"));
        }

        boolean qc = LearnedProfileStore.isQuickCatchEnabled(this);
        if (appQuickCatchBtn != null) {
            styleAppToggle(
                    appQuickCatchBtn,
                    qc,
                    qc ? "⚡ Fast Catch: ON (2-Finger Berry Hold + Escape Run)" : "⚡ Fast Catch: OFF (Single Clean Throw)"
            );
        }

        boolean spinOn = LearnedProfileStore.isSpinThrowEnabled(this);
        if (appSpinThrowBtn != null) {
            styleAppToggle(
                    appSpinThrowBtn,
                    spinOn,
                    spinOn ? "🌀 Throw Style: Spin Throw (Curveball) ON" : "⬆️ Throw Style: Straight Throw (Spin OFF)"
            );
        }

        int gMode = LearnedProfileStore.getGiftWorkflowModePref(this);
        if (appGiftModeBtn != null) {
            if (gMode == PokemonGoAccessibilityService.MODE_PIN_OPEN_AND_SEND) {
                appGiftModeBtn.setText("🎁 Gift Mode: 📌 Pin/Unpin + Open + Send Gift");
            } else if (gMode == PokemonGoAccessibilityService.MODE_PIN_AND_OPEN_ONLY) {
                appGiftModeBtn.setText("🎁 Gift Mode: 📌 Pin/Unpin + Open Incoming Only");
            } else {
                appGiftModeBtn.setText("🎁 Gift Mode: 🎁 Left Send Gift Only (Skip Open)");
            }
        }

        boolean safeDelay = LearnedProfileStore.isSafeHumanDelayEnabled(this);
        if (appDelaySpeedBtn != null) {
            styleAppToggle(
                    appDelaySpeedBtn,
                    safeDelay,
                    safeDelay ? "⏱ Pacing: Safe Human Mode (+30% Delay)" : "⏱ Pacing: Standard Fast Speed"
            );
        }

        boolean autoHidePoGo = LearnedProfileStore.isAutoHideOutsidePoGoEnabled(this);
        if (appAutoHidePoGoBtn != null) {
            styleAppToggle(
                    appAutoHidePoGoBtn,
                    autoHidePoGo,
                    autoHidePoGo
                            ? "👁 Show Overlay: Only in Pokémon GO (Auto-Hide ON)"
                            : "👁 Show Overlay: Everywhere (Always Visible)"
            );
        }
    }

    private void styleAppToggle(Button btn, boolean isOn, String text) {
        btn.setText(text);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        if (isOn) {
            d.setColor(Color.parseColor("#065F46"));
            d.setStroke(dp(1), Color.parseColor("#34D399"));
            btn.setTextColor(Color.parseColor("#A7F3D0"));
        } else {
            d.setColor(Color.parseColor("#162235"));
            d.setStroke(dp(1), Color.parseColor("#334155"));
            btn.setTextColor(Color.parseColor("#94A3B8"));
        }
        btn.setBackground(d);
    }

    private void renderMainResearchCards() {
        if (mainResearchCardsList == null) return;
        mainResearchCardsList.removeAllViews();

        List<PokeMateDatabase.ResearchItem> items = PokeMateDatabase.getInstance(this)
                .queryResearchItems(searchQuery, categoryFilter);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No matching Field Research tasks found for \"" + searchQuery + "\".");
            empty.setTextColor(Color.parseColor("#94A3B8"));
            empty.setTextSize(13f);
            empty.setPadding(dp(8), dp(14), dp(8), dp(14));
            mainResearchCardsList.addView(empty);
            return;
        }

        for (PokeMateDatabase.ResearchItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.parseColor("#0B1322"));
            cardBg.setCornerRadius(dp(14));
            String borderHex = "EVENT".equals(item.outlineType)
                    ? "#F59E0B"
                    : ("BONUS".equals(item.outlineType) ? "#10B981" : (item.isMeta ? "#38BDF8" : "#1E293B"));
            cardBg.setStroke(dp("EVENT".equals(item.outlineType) || "BONUS".equals(item.outlineType) ? 2 : 1), Color.parseColor(borderHex));
            card.setBackground(cardBg);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardLp.setMargins(0, 0, 0, dp(10));
            card.setLayoutParams(cardLp);

            TextView badgeLine = new TextView(this);
            String oddsLabel = item.poolCount == 1
                    ? "100% Guaranteed Reward"
                    : (item.oddsPercentEach + "% chance each (" + item.poolCount + " pool)");
            badgeLine.setText(
                    item.getOutlineBadgeLabel() +
                    (item.isMeta ? "  •  🔥 META" : "") +
                    "  •  " + oddsLabel +
                    (item.shinyPossible ? "  •  ✨ Shiny" : "")
            );
            badgeLine.setTextColor(Color.parseColor("EVENT".equals(item.outlineType) ? "#FBBF24"
                    : ("BONUS".equals(item.outlineType) ? "#34D399" : "#38BDF8")));
            badgeLine.setTextSize(11f);
            badgeLine.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(badgeLine);

            TextView taskTitle = new TextView(this);
            taskTitle.setText(item.taskName);
            taskTitle.setTextColor(Color.WHITE);
            taskTitle.setTextSize(14.5f);
            taskTitle.setTypeface(Typeface.DEFAULT_BOLD);
            taskTitle.setPadding(0, dp(4), 0, dp(4));
            card.addView(taskTitle);

            TextView rewardView = new TextView(this);
            rewardView.setText("Possible Reward(s): " + item.reward);
            rewardView.setTextColor(Color.parseColor("#34D399"));
            rewardView.setTextSize(13f);
            rewardView.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(rewardView);

            if (item.isMeta && item.metaHighlight != null && item.metaHighlight.length() > 0) {
                TextView whoIsMetaView = new TextView(this);
                whoIsMetaView.setText(item.metaHighlight);
                whoIsMetaView.setTextColor(Color.parseColor("#FDE68A"));
                whoIsMetaView.setTextSize(12f);
                whoIsMetaView.setTypeface(Typeface.DEFAULT_BOLD);
                whoIsMetaView.setPadding(0, dp(4), 0, 0);
                card.addView(whoIsMetaView);
            }

            TextView cpView = new TextView(this);
            cpView.setText("CP / Details: " + item.cpRange);
            cpView.setTextColor(Color.parseColor("#94A3B8"));
            cpView.setTextSize(11.5f);
            cpView.setPadding(0, dp(3), 0, 0);
            card.addView(cpView);

            mainResearchCardsList.addView(card);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private Button createActionButton(String text, String bgHex, int textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setAllCaps(false);
        btn.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(bgHex));
        drawable.setCornerRadius(dp(12));
        btn.setBackground(drawable);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(8));
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button createSmallActionButton(String text, String bgHex, int textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setAllCaps(false);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(bgHex));
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(1), Color.parseColor("#334155"));
        btn.setBackground(drawable);
        return btn;
    }

    @Override
    protected void onResume() {
        super.onResume();
        PokemonGoAccessibilityService.isPokemonGoInForeground = false;
        if (FloatingViewService.instance != null) {
            FloatingViewService.instance.onForegroundAppChanged(false);
        }
        refreshPermissionStatus();
        syncMainSettingsUi();
    }

    private void checkForAppUpdates() {
        updateStatusBanner.setText("Checking global OTA channel for newer APK & syncing live research...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int syncedTasks = PokeMateDatabase.getInstance(MainActivity.this).syncLiveFromLeekDuck();
                int remoteVersionCode = -1;
                String remoteVersionName = "";
                String remoteDirectUrl = latestDirectApkUrl;
                String remoteNotes = "";
                String errorMsg = null;

                try {
                    URL url = new URL(OTA_MANIFEST_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Cache-Control", "no-cache");

                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    String lastJsonLine = "";
                    while ((line = r.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("{") && trimmed.contains("\"versionCode\"")) {
                            lastJsonLine = trimmed;
                        }
                    }
                    r.close();

                    if (lastJsonLine.length() > 0) {
                        Matcher vcM = Pattern.compile("\"versionCode\"\\s*:\\s*(\\d+)").matcher(lastJsonLine);
                        if (vcM.find()) remoteVersionCode = Integer.parseInt(vcM.group(1));

                        Matcher vnM = Pattern.compile("\"versionName\"\\s*:\\s*\"([^\"]+)\"").matcher(lastJsonLine);
                        if (vnM.find()) remoteVersionName = vnM.group(1);

                        Matcher urlM = Pattern.compile("\"directApkUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(lastJsonLine);
                        if (urlM.find()) remoteDirectUrl = urlM.group(1);

                        Matcher notesM = Pattern.compile("\"changelog\"\\s*:\\s*\"([^\"]+)\"").matcher(lastJsonLine);
                        if (notesM.find()) remoteNotes = notesM.group(1);
                    } else {
                        errorMsg = "No release manifest found on OTA channel";
                    }
                } catch (Exception e) {
                    errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
                }

                final int finalRemoteCode = remoteVersionCode;
                final String finalRemoteName = remoteVersionName;
                final String finalUrl = remoteDirectUrl;
                final String finalNotes = remoteNotes;
                final String finalError = errorMsg;

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        latestDirectApkUrl = finalUrl;
                        renderMainResearchCards();
                        if (finalError != null || finalRemoteCode < 0) {
                            updateStatusBanner.setText(
                                    "⚠ Update check failed (" + (finalError != null ? finalError : "Unknown") + ").\n" +
                                    "Installed: v" + CURRENT_VERSION_NAME
                            );
                            Toast.makeText(MainActivity.this, "Could not reach OTA server", Toast.LENGTH_LONG).show();
                        } else if (finalRemoteCode > CURRENT_VERSION_CODE) {
                            updateStatusBanner.setText(
                                    "🎉 NEW UPDATE AVAILABLE: v" + finalRemoteName + " (Build " + finalRemoteCode + ")!\n" +
                                    (finalNotes.length() > 0 ? finalNotes + "\n" : "") +
                                    "Tap '⬇ Download Latest APK' right now to install!"
                            );
                            Toast.makeText(MainActivity.this, "Update v" + finalRemoteName + " Ready! Tap Download Latest APK.", Toast.LENGTH_LONG).show();
                        } else {
                            updateStatusBanner.setText(
                                    "✓ Verified Latest Version: v" + CURRENT_VERSION_NAME + " (Remote Build " + finalRemoteCode + ")" +
                                    (syncedTasks > 0 ? " • Synced " + syncedTasks + " live research tasks!" : "")
                            );
                            Toast.makeText(MainActivity.this, "Verified: You are on the latest build (v" + CURRENT_VERSION_NAME + ")!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void refreshPermissionStatus() {
        boolean canOverlay = Settings.canDrawOverlays(this);
        boolean a11yConnected = PokemonGoAccessibilityService.instance != null;
        permissionStatusView.setText(
                "● Overlay Permission: " + (canOverlay ? "GRANTED ✓" : "NOT GRANTED (Tap 'Overlay Perm' below)") +
                "\n● Accessibility Service: " + (a11yConnected ? "ACTIVE ✓ (Ready)" : "OFF (Tap Button 2 below to Enable)")
        );
    }

    private void enterFloatingPipHud() {
        if (Build.VERSION.SDK_INT >= 26) {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build();
            enterPictureInPictureMode(params);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            normalControlsContainer.setVisibility(View.GONE);
            pipCompactHudContainer.setVisibility(View.VISIBLE);
        } else {
            pipCompactHudContainer.setVisibility(View.GONE);
            normalControlsContainer.setVisibility(View.VISIBLE);
        }
    }
}
