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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.widget.ProgressBar;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

public class MainActivity extends Activity {

    public static final int CURRENT_VERSION_CODE = 3;
    public static final String CURRENT_VERSION_NAME = "1.2.2026";
    public static final String OTA_MANIFEST_PRIMARY = "https://ntfy.envs.net/pokemate_ota_4e6c31bd/raw?poll=1&since=all";
    public static final String OTA_APK_CHANNEL_JSON = "https://ntfy.envs.net/pokemate_apk_4e6c31bd/json?poll=1&since=all";
    public static final String OTA_MANIFEST_ADMINFORGE = "https://ntfy.adminforge.de/pokemate_ota_4e6c31bd/raw?poll=1&since=all";
    public static final String OTA_MANIFEST_URL = "https://ntfy.sh/pokemate_ota_4e6c31bd/raw?poll=1&since=all";
    private static final String OTA_PREFS = "pokemate_ota_prefs";
    private static final String KEY_CUSTOM_SERVER_ORIGIN = "custom_server_origin";

    private LinearLayout normalControlsContainer;
    private LinearLayout pipCompactHudContainer;
    private TextView pipStatusView;
    private TextView permissionStatusView;
    private TextView updateStatusBanner;
    private ProgressBar updateProgressBar;
    private LinearLayout updateExtraActionsRow;
    private Button manualInstallCachedApkBtn;
    private LinearLayout customLinkInputRow;
    private EditText customLinkEditText;
    private LinearLayout mainResearchCardsList;

    // Main App Settings Card Controls
    private LinearLayout settingsCardBody;
    private Button toggleSettingsCardBtn;
    private TextView appOpacityLabel;
    private SeekBar appOpacitySeekBar;
    private Button appMiniThrowBtn;
    private Button appMiniGiftBtn;
    private Button appMiniScanBtn;
    private Button appMiniScanPokemonBtn;
    private Button appQuickCatchBtn;
    private Button appSpinThrowBtn;
    private Button appGiftModeBtn;
    private Button appDelaySpeedBtn;
    private Button appAutoHidePoGoBtn;

    private boolean isSettingsExpanded = true;
    private String searchQuery = "";
    private String categoryFilter = "ALL";
    private String latestDirectApkUrl = "https://ntfy.envs.net/file/10kRCDJ78rYd.apk";
    private String latestFallbackApkUrl = "https://ntfy.adminforge.de/file/kjqb9UOrEte4.apk";
    private boolean pendingInstallAfterPermission = false;
    private boolean isDownloadingApk = false;

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
        verPill.setText("v" + getInstalledVersionName() + " • CUSTOMIZABLE HUD");
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

        LinearLayout miniTogglesRow1 = new LinearLayout(this);
        miniTogglesRow1.setOrientation(LinearLayout.HORIZONTAL);
        miniTogglesRow1.setPadding(0, 0, 0, dp(6));

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

        miniTogglesRow1.addView(appMiniThrowBtn);
        miniTogglesRow1.addView(appMiniGiftBtn);
        settingsCardBody.addView(miniTogglesRow1);

        LinearLayout miniTogglesRow2 = new LinearLayout(this);
        miniTogglesRow2.setOrientation(LinearLayout.HORIZONTAL);
        miniTogglesRow2.setPadding(0, 0, 0, dp(12));

        appMiniScanBtn = createSmallActionButton("🔭 Scan Research: OFF", "#162235", Color.WHITE);
        LinearLayout.LayoutParams mt3Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mt3Lp.setMargins(0, 0, dp(5), 0);
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

        appMiniScanPokemonBtn = createSmallActionButton("🧬 Scan Pokémon: OFF", "#162235", Color.WHITE);
        LinearLayout.LayoutParams mt4Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        appMiniScanPokemonBtn.setLayoutParams(mt4Lp);
        appMiniScanPokemonBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !LearnedProfileStore.isMiniShowScanPokemon(MainActivity.this);
                LearnedProfileStore.setMiniShowScanPokemon(MainActivity.this, next);
                syncMainSettingsUi();
                if (FloatingViewService.instance != null) {
                    FloatingViewService.instance.applyUserSettingsLive();
                }
            }
        });

        miniTogglesRow2.addView(appMiniScanBtn);
        miniTogglesRow2.addView(appMiniScanPokemonBtn);
        settingsCardBody.addView(miniTogglesRow2);

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

        // OTA UPDATE & IN-APP INSTALLER CARD
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
        updateStatusBanner.setText("🔍 Auto-Scanning OTA relays for latest version (Installed: v" + CURRENT_VERSION_NAME + " • Build " + CURRENT_VERSION_CODE + ")...");
        updateStatusBanner.setTextColor(Color.parseColor("#7DD3FC"));
        updateStatusBanner.setTextSize(11.5f);
        updateStatusBanner.setTypeface(Typeface.DEFAULT_BOLD);
        updateStatusBanner.setPadding(0, 0, 0, dp(6));
        updateCard.addView(updateStatusBanner);

        updateProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        updateProgressBar.setMax(100);
        updateProgressBar.setProgress(0);
        updateProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        pbLp.setMargins(0, 0, 0, dp(8));
        updateProgressBar.setLayoutParams(pbLp);
        updateCard.addView(updateProgressBar);

        LinearLayout updateBtnsRow = new LinearLayout(this);
        updateBtnsRow.setOrientation(LinearLayout.HORIZONTAL);

        Button checkUpdateBtn = createSmallActionButton("🔄 Auto-Scan Version", "#0284C7", Color.WHITE);
        LinearLayout.LayoutParams cuLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cuLp.setMargins(0, 0, dp(6), 0);
        checkUpdateBtn.setLayoutParams(cuLp);
        checkUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForAppUpdates(false, false);
            }
        });

        Button installUpdateBtn = createSmallActionButton("⚡ Update App Now", "#10B981", Color.parseColor("#04131D"));
        LinearLayout.LayoutParams iuLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f);
        iuLp.setMargins(0, 0, dp(6), 0);
        installUpdateBtn.setLayoutParams(iuLp);
        installUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Auto-scans first to check if a newer version is available, downloads in-app if newer,
                // or confirms the user is already on the latest version!
                checkForAppUpdates(true, false);
            }
        });

        Button linkBtn = createSmallActionButton("🔗 Link", "#1E293B", Color.parseColor("#CBD5E1"));
        linkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (customLinkInputRow != null) {
                    boolean show = customLinkInputRow.getVisibility() != View.VISIBLE;
                    customLinkInputRow.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            }
        });

        updateBtnsRow.addView(checkUpdateBtn);
        updateBtnsRow.addView(installUpdateBtn);
        updateBtnsRow.addView(linkBtn);
        updateCard.addView(updateBtnsRow);

        // Secondary row: Force Re-Install in-app OR Browser Fallback
        updateExtraActionsRow = new LinearLayout(this);
        updateExtraActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        updateExtraActionsRow.setPadding(0, dp(6), 0, 0);

        Button forceReinstallBtn = createSmallActionButton("🔁 Force In-App Download", "#162235", Color.parseColor("#A7F3D0"));
        LinearLayout.LayoutParams frLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        frLp.setMargins(0, 0, dp(6), 0);
        forceReinstallBtn.setLayoutParams(frLp);
        forceReinstallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForAppUpdates(true, true);
            }
        });

        Button browserFallbackBtn = createSmallActionButton("🌐 Open APK in Browser", "#162235", Color.parseColor("#7DD3FC"));
        LinearLayout.LayoutParams bfLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        browserFallbackBtn.setLayoutParams(bfLp);
        browserFallbackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Opening direct APK mirror in browser...", Toast.LENGTH_SHORT).show();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(latestDirectApkUrl));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            }
        });

        manualInstallCachedApkBtn = createSmallActionButton("📦 Install Cached APK", "#065F46", Color.WHITE);
        LinearLayout.LayoutParams miLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f);
        miLp.setMargins(0, 0, dp(6), 0);
        manualInstallCachedApkBtn.setLayoutParams(miLp);
        manualInstallCachedApkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File f = ApkUpdateFileProvider.getDownloadedApkFile(MainActivity.this);
                if (f.exists() && f.length() > 40000) {
                    launchNativePackageInstallerForCachedApk();
                } else {
                    Toast.makeText(MainActivity.this, "Downloading latest APK first...", Toast.LENGTH_SHORT).show();
                    checkForAppUpdates(true, true);
                }
            }
        });

        updateExtraActionsRow.addView(manualInstallCachedApkBtn);
        updateExtraActionsRow.addView(forceReinstallBtn);
        updateExtraActionsRow.addView(browserFallbackBtn);
        updateCard.addView(updateExtraActionsRow);

        // Collapsible Custom Republished Link Row (in case user republishes under a new Cloud Run URL)
        customLinkInputRow = new LinearLayout(this);
        customLinkInputRow.setOrientation(LinearLayout.HORIZONTAL);
        customLinkInputRow.setGravity(Gravity.CENTER_VERTICAL);
        customLinkInputRow.setPadding(0, dp(8), 0, 0);
        customLinkInputRow.setVisibility(View.GONE);

        customLinkEditText = new EditText(this);
        customLinkEditText.setHint("Paste republished app URL (https://...run.app)");
        customLinkEditText.setHintTextColor(Color.parseColor("#64748B"));
        customLinkEditText.setTextColor(Color.WHITE);
        customLinkEditText.setTextSize(11.5f);
        customLinkEditText.setSingleLine(true);
        customLinkEditText.setPadding(dp(10), dp(8), dp(10), dp(8));
        String savedOrigin = getSavedCustomServerOrigin();
        if (savedOrigin.length() > 0) {
            customLinkEditText.setText(savedOrigin);
        }
        GradientDrawable clBg = new GradientDrawable();
        clBg.setColor(Color.parseColor("#060B14"));
        clBg.setCornerRadius(dp(10));
        clBg.setStroke(dp(1), Color.parseColor("#334155"));
        customLinkEditText.setBackground(clBg);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clLp.setMargins(0, 0, dp(6), 0);
        customLinkEditText.setLayoutParams(clLp);

        Button saveLinkBtn = createSmallActionButton("Save & Scan", "#10B981", Color.parseColor("#04131D"));
        saveLinkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String raw = customLinkEditText.getText() != null ? customLinkEditText.getText().toString().trim() : "";
                saveCustomServerOrigin(raw);
                customLinkInputRow.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Saved server link! Scanning for updates...", Toast.LENGTH_SHORT).show();
                checkForAppUpdates(false, false);
            }
        });

        customLinkInputRow.addView(customLinkEditText);
        customLinkInputRow.addView(saveLinkBtn);
        updateCard.addView(customLinkInputRow);

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
                {"Event", "🟨 Event"},
                {"META", "Meta 🔥"},
                {"Catch", "Catching"},
                {"Throw", "Throwing"},
                {"Raid", "Raids & Battles"},
                {"Explore", "Hatch & Spin"},
                {"Power/Buddy", "Power Up & Buddy"},
                {"SHINY", "Shiny Only ✨"},
                {"Bonus", "🟩 Bonus"}
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

        // Automatically scan OTA servers for the latest version & sync live Field Research on startup!
        checkForAppUpdates(false, false);
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
        boolean showScanPokemon = LearnedProfileStore.isMiniShowScanPokemon(this);

        if (appMiniThrowBtn != null) {
            styleAppToggle(appMiniThrowBtn, showThrow, "🎯 Throw: " + (showThrow ? "ON" : "OFF"));
        }
        if (appMiniGiftBtn != null) {
            styleAppToggle(appMiniGiftBtn, showGift, "▶ Gift: " + (showGift ? "ON" : "OFF"));
        }
        if (appMiniScanBtn != null) {
            styleAppToggle(appMiniScanBtn, showScan, "🔭 Scan Research: " + (showScan ? "ON" : "OFF"));
        }
        if (appMiniScanPokemonBtn != null) {
            styleAppToggle(appMiniScanPokemonBtn, showScanPokemon, "🧬 Scan Pokémon: " + (showScanPokemon ? "ON" : "OFF"));
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

        // If user searches for a Pokémon name in the search bar, also render its S–F Tier Meta & Power-Up Card!
        if (searchQuery != null && searchQuery.trim().length() >= 3 && !searchQuery.contains("|") && !searchQuery.contains("::")) {
            List<PokemonMetaAnalyzer.PokemonScanReport> pokeMatches =
                    PokemonMetaAnalyzer.searchPokemonByName(searchQuery.trim());
            for (int p = 0; p < pokeMatches.size(); p++) {
                PokemonMetaAnalyzer.PokemonScanReport queriedReport = pokeMatches.get(p);
                PokemonMetaAnalyzer.PokemonMetaEntry sp = queriedReport.entry;
                String tierHex = queriedReport.effectiveTier.startsWith("S") ? "#F59E0B"
                        : (queriedReport.effectiveTier.startsWith("A") ? "#10B981"
                        : (queriedReport.effectiveTier.startsWith("B") ? "#38BDF8" : "#94A3B8"));

                LinearLayout pCard = new LinearLayout(this);
                pCard.setOrientation(LinearLayout.VERTICAL);
                pCard.setPadding(dp(14), dp(12), dp(14), dp(12));
                GradientDrawable pBg = new GradientDrawable();
                pBg.setColor(Color.parseColor("#0C192E"));
                pBg.setCornerRadius(dp(14));
                pBg.setStroke(dp(2), Color.parseColor(tierHex));
                pCard.setBackground(pBg);
                LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                );
                pLp.setMargins(0, 0, 0, dp(12));
                pCard.setLayoutParams(pLp);

                TextView pTop = new TextView(this);
                pTop.setText("🧬 POKÉMON META & POWER-UP REPORT  •  [" + queriedReport.effectiveTier + " TIER]  •  "
                        + (queriedReport.effectiveIsMeta ? "🔥 META RELEVANT" : "⚪ NON-META"));
                pTop.setTextColor(Color.parseColor(tierHex));
                pTop.setTextSize(11.5f);
                pTop.setTypeface(Typeface.DEFAULT_BOLD);
                pCard.addView(pTop);

                TextView pName = new TextView(this);
                pName.setText(sp.name + "  (" + sp.types + ")");
                pName.setTextColor(Color.WHITE);
                pName.setTextSize(16f);
                pName.setTypeface(Typeface.DEFAULT_BOLD);
                pName.setPadding(0, dp(3), 0, dp(4));
                pCard.addView(pName);

                TextView pStats = new TextView(this);
                pStats.setText("📊 Base Stats: ATK " + sp.baseAtk + " • DEF " + sp.baseDef + " • STA " + sp.baseSta
                        + "\n💯 100% Hundo CPs: Lv15 " + queriedReport.cpLv15Hundo
                        + " • Lv20 " + queriedReport.cpLv20Hundo
                        + " • Lv40 " + queriedReport.cpLv40Hundo
                        + " • Lv50 " + queriedReport.cpLv50Hundo);
                pStats.setTextColor(Color.parseColor("#7DD3FC"));
                pStats.setTextSize(12f);
                pStats.setTypeface(Typeface.DEFAULT_BOLD);
                pCard.addView(pStats);

                TextView pRec = new TextView(this);
                pRec.setText(queriedReport.verdictHeadline
                        + "\n⬆ " + queriedReport.powerUpTargetText
                        + "\n💡 " + queriedReport.detailedAdvice
                        + "\n\n" + sp.getOptimalMovesTmBreakdown()
                        + "\n" + sp.getFullMovePoolSummary()
                        + "\n\n⚔ Raid: " + sp.raidRole
                        + "\n🛡 PvP: " + sp.pvpRole
                        + "\n" + sp.getTypeWeaknessSummary());
                pRec.setTextColor(Color.parseColor("#E2E8F0"));
                pRec.setTextSize(12f);
                pRec.setPadding(0, dp(6), 0, 0);
                pCard.addView(pRec);

                mainResearchCardsList.addView(pCard);
            }
        }

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
                    "  •  " + oddsLabel
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

            if (item.pokemonEntries != null && !item.pokemonEntries.isEmpty()) {
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
                        currentRow.setPadding(0, dp(p == 0 ? 2 : 5), 0, 0);
                        card.addView(currentRow);
                        countInRow = 0;
                    }

                    String chipBg = pe.tier.startsWith("S") ? "#065F46"
                            : (pe.tier.startsWith("A") ? "#0F5132" : "#1E293B");
                    int chipTextColor = (pe.shinySymbol != null && pe.shinySymbol.length() >= 2)
                            ? Color.parseColor("#FDE68A")
                            : Color.WHITE;

                    Button pokeBtn = createSmallActionButton(pe.getChipLabel(), chipBg, chipTextColor);
                    LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    pbLp.setMargins(0, 0, dp(6), 0);
                    pokeBtn.setLayoutParams(pbLp);
                    pokeBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            searchQuery = pe.displayName;
                            renderMainResearchCards();
                        }
                    });
                    currentRow.addView(pokeBtn);
                    countInRow++;
                }

                TextView cpView = new TextView(this);
                String cpText = "🎯 " + item.cpRange
                        + (item.extraPoolSuffix != null && item.extraPoolSuffix.length() > 0
                        ? "  •  " + item.extraPoolSuffix
                        : "");
                cpView.setText(cpText);
                cpView.setTextColor(Color.parseColor("#6EE7B7"));
                cpView.setTextSize(12f);
                cpView.setTypeface(Typeface.DEFAULT_BOLD);
                cpView.setPadding(0, dp(5), 0, 0);
                card.addView(cpView);
            } else {
                TextView rewardView = new TextView(this);
                rewardView.setText("→ " + item.reward + "  (" + item.cpRange + ")");
                rewardView.setTextColor(Color.parseColor("#34D399"));
                rewardView.setTextSize(13f);
                rewardView.setTypeface(Typeface.DEFAULT_BOLD);
                card.addView(rewardView);
            }

            if (item.isMeta && item.metaHighlight != null && item.metaHighlight.length() > 0) {
                TextView whoIsMetaView = new TextView(this);
                whoIsMetaView.setText(item.metaHighlight);
                whoIsMetaView.setTextColor(Color.parseColor("#FDE68A"));
                whoIsMetaView.setTextSize(12f);
                whoIsMetaView.setTypeface(Typeface.DEFAULT_BOLD);
                whoIsMetaView.setPadding(0, dp(4), 0, 0);
                card.addView(whoIsMetaView);
            }

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

        // If user just enabled "Install unknown apps" permission for PalpiGO, immediately resume the APK install!
        if (pendingInstallAfterPermission) {
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) {
                pendingInstallAfterPermission = false;
                File downloadedApk = ApkUpdateFileProvider.getDownloadedApkFile(this);
                if (downloadedApk.exists() && downloadedApk.length() > 40000) {
                    launchNativePackageInstallerForCachedApk();
                    return;
                }
            }
        }
    }

    private String getSavedCustomServerOrigin() {
        SharedPreferences prefs = getSharedPreferences(OTA_PREFS, MODE_PRIVATE);
        return prefs.getString(KEY_CUSTOM_SERVER_ORIGIN, "https://ais-dev-gewg5eor3v4efc5hr235jz-420113228336.europe-west1.run.app");
    }

    private void saveCustomServerOrigin(String rawUrl) {
        if (rawUrl == null) return;
        String cleaned = rawUrl.trim();
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.endsWith("/api/apk-version")) {
            cleaned = cleaned.substring(0, cleaned.length() - "/api/apk-version".length());
        }
        if (cleaned.endsWith("/pokemate-companion.apk")) {
            cleaned = cleaned.substring(0, cleaned.length() - "/pokemate-companion.apk".length());
        }
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            getSharedPreferences(OTA_PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CUSTOM_SERVER_ORIGIN, cleaned)
                    .apply();
        }
    }

    /**
     * Multi-Relay OTA Auto-Scanner & In-App Updater:
     * 1. Scans global relay channels (ntfy.envs.net, ntfy.adminforge.de, ntfy.sh) + any republished server link
     * 2. Discovers the latest APK attachment binary directly from ntfy.envs.net/pokemate_apk_4e6c31bd
     * 3. Auto-verifies if the installed build is the latest version
     * 4. If autoDownloadIfNewer is true and a newer build exists (or forceDownload is true), streams the APK
     *    in-app with a live progress bar, validates the ZIP/APK header, and launches Android PackageInstaller!
     */
    private void checkForAppUpdates(final boolean autoDownloadIfNewer, final boolean forceDownloadEvenIfCurrent) {
        if (isDownloadingApk) {
            Toast.makeText(this, "Update download already in progress...", Toast.LENGTH_SHORT).show();
            return;
        }
        updateStatusBanner.setText("🔍 Auto-Scanning global OTA channels for latest version & syncing research...");
        updateStatusBanner.setTextColor(Color.parseColor("#7DD3FC"));
        if (updateProgressBar != null) {
            updateProgressBar.setIndeterminate(true);
            updateProgressBar.setVisibility(View.VISIBLE);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final int syncedTasks = PokeMateDatabase.getInstance(MainActivity.this).syncLiveFromLeekDuck();
                int remoteVersionCode = -1;
                String remoteVersionName = "";
                String remoteDirectUrl = "";
                String remoteFallbackUrl = "";
                String remoteServerOrigin = "";
                String remoteNotes = "";
                String liveRelayApkAttachmentUrl = "";
                String errorMsg = null;

                // Step A: Query direct APK binary attachment channel on ntfy.envs.net (prefer unexpired)
                HttpURLConnection aConn = null;
                try {
                    URL attachUrl = new URL("https://ntfy.envs.net/pokemate_apk_4e6c31bd/json?poll=1&since=2h");
                    aConn = (HttpURLConnection) attachUrl.openConnection();
                    aConn.setConnectTimeout(5000);
                    aConn.setReadTimeout(5000);
                    aConn.setRequestMethod("GET");
                    aConn.setRequestProperty("Cache-Control", "no-cache");
                    BufferedReader ar = new BufferedReader(new InputStreamReader(aConn.getInputStream(), "UTF-8"));
                    String aLine;
                    long nowSec = System.currentTimeMillis() / 1000L;
                    while ((aLine = ar.readLine()) != null) {
                        if (aLine.contains("\"attachment\"") && aLine.contains(".apk\"")) {
                            Matcher expM = Pattern.compile("\"expires\"\\s*:\\s*(\\d+)").matcher(aLine);
                            long expTime = 0L;
                            if (expM.find()) {
                                expTime = Long.parseLong(expM.group(1));
                            }
                            if (expTime > nowSec || expTime == 0L) {
                                Matcher uM = Pattern.compile("\"url\"\\s*:\\s*\"(https://[^\"]+\\.apk)\"").matcher(aLine);
                                if (uM.find()) {
                                    liveRelayApkAttachmentUrl = uM.group(1);
                                }
                            }
                        }
                    }
                    ar.close();
                } catch (Exception ignored) {
                } finally {
                    if (aConn != null) {
                        aConn.disconnect();
                    }
                }

                // Step B: Query all OTA manifest endpoints (global relays + saved/republished server URLs)
                String savedOrigin = getSavedCustomServerOrigin();
                List<String> endpoints = new ArrayList<String>();
                endpoints.add(OTA_MANIFEST_PRIMARY);
                endpoints.add(OTA_MANIFEST_ADMINFORGE);
                if (savedOrigin != null && savedOrigin.startsWith("http")) {
                    endpoints.add(savedOrigin + "/api/apk-version");
                    endpoints.add(savedOrigin + "/apk-version.json");
                }
                // Published shared app URL & Development app URL
                endpoints.add("https://ais-pre-bh2phvsi6uibgobjg436k4-420113228336.europe-west1.run.app/api/apk-version");
                endpoints.add("https://ais-pre-bh2phvsi6uibgobjg436k4-420113228336.europe-west1.run.app/apk-version.json");
                endpoints.add("https://ais-dev-bh2phvsi6uibgobjg436k4-420113228336.europe-west1.run.app/api/apk-version");
                endpoints.add(OTA_MANIFEST_URL);

                for (int mIdx = 0; mIdx < endpoints.size(); mIdx++) {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(endpoints.get(mIdx));
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(5500);
                        conn.setReadTimeout(5500);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Cache-Control", "no-cache");

                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder fullSb = new StringBuilder();
                        String line;
                        String lastJsonLine = "";
                        while ((line = r.readLine()) != null) {
                            fullSb.append(line).append("\n");
                            String trimmed = line.trim();
                            if (trimmed.startsWith("{") && trimmed.contains("\"versionCode\"")) {
                                lastJsonLine = trimmed;
                            }
                        }
                        r.close();

                        String candidateJson = lastJsonLine.length() > 0 ? lastJsonLine : fullSb.toString().trim();
                        if (candidateJson.contains("\"versionCode\"")) {
                            int foundCode = -1;
                            Matcher vcM = Pattern.compile("\"versionCode\"\\s*:\\s*(\\d+)").matcher(candidateJson);
                            if (vcM.find()) foundCode = Integer.parseInt(vcM.group(1));

                            if (foundCode >= remoteVersionCode && foundCode > 0) {
                                remoteVersionCode = foundCode;
                                Matcher vnM = Pattern.compile("\"versionName\"\\s*:\\s*\"([^\"]+)\"").matcher(candidateJson);
                                if (vnM.find()) remoteVersionName = vnM.group(1);

                                Matcher urlM = Pattern.compile("\"directApkUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(candidateJson);
                                if (urlM.find() && urlM.group(1).startsWith("http")) {
                                    remoteDirectUrl = urlM.group(1);
                                }

                                Matcher fbM = Pattern.compile("\"fallbackApkUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(candidateJson);
                                if (fbM.find() && fbM.group(1).startsWith("http")) {
                                    remoteFallbackUrl = fbM.group(1);
                                }

                                Matcher origM = Pattern.compile("\"serverOrigin\"\\s*:\\s*\"(https?://[^\"]+)\"").matcher(candidateJson);
                                if (origM.find()) {
                                    remoteServerOrigin = origM.group(1);
                                }

                                Matcher notesM = Pattern.compile("\"(?:changelog|releaseNotes)\"\\s*:\\s*\"([^\"]+)\"").matcher(candidateJson);
                                if (notesM.find()) remoteNotes = notesM.group(1);
                                errorMsg = null;
                            }
                        }
                    } catch (Exception e) {
                        if (remoteVersionCode < 0) {
                            errorMsg = e.getClass().getSimpleName();
                        }
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }
                }

                if (remoteServerOrigin.length() > 0) {
                    saveCustomServerOrigin(remoteServerOrigin);
                }

                // Prefer the live attachment URL on ntfy.envs.net if available
                if (liveRelayApkAttachmentUrl.length() > 0) {
                    if (remoteDirectUrl.length() == 0 || remoteDirectUrl.contains("tmpfiles.org")) {
                        remoteFallbackUrl = remoteDirectUrl;
                        remoteDirectUrl = liveRelayApkAttachmentUrl;
                    } else if (remoteFallbackUrl.length() == 0) {
                        remoteFallbackUrl = liveRelayApkAttachmentUrl;
                    }
                }

                final int finalRemoteCode = remoteVersionCode;
                final String finalRemoteName = remoteVersionName.length() > 0 ? remoteVersionName : CURRENT_VERSION_NAME;
                final String finalDirectUrl = remoteDirectUrl.length() > 0 ? remoteDirectUrl : latestDirectApkUrl;
                final String finalFallbackUrl = remoteFallbackUrl;
                final String finalLiveAttachUrl = liveRelayApkAttachmentUrl;
                final String finalNotes = remoteNotes;
                final String finalError = errorMsg;

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (updateProgressBar != null) {
                            updateProgressBar.setIndeterminate(false);
                            updateProgressBar.setVisibility(View.GONE);
                        }
                        latestDirectApkUrl = finalDirectUrl;
                        if (finalFallbackUrl.length() > 0) {
                            latestFallbackApkUrl = finalFallbackUrl;
                        }
                        if (customLinkEditText != null && getSavedCustomServerOrigin().length() > 0) {
                            customLinkEditText.setText(getSavedCustomServerOrigin());
                        }
                        renderMainResearchCards();

                        if (forceDownloadEvenIfCurrent) {
                            downloadAndInstallApkInApp(
                                    finalRemoteName,
                                    finalDirectUrl,
                                    finalLiveAttachUrl,
                                    finalFallbackUrl
                            );
                            return;
                        }

                        String myName = getInstalledVersionName();
                        int myCode = getInstalledVersionCode();

                        if (finalRemoteCode < 0) {
                            updateStatusBanner.setTextColor(Color.parseColor("#FBBF24"));
                            updateStatusBanner.setText(
                                    "⚠ Could not reach OTA manifest (" + (finalError != null ? finalError : "Offline") + ").\n" +
                                    "Installed: v" + myName + " (Build " + myCode + ")"
                            );
                            if (autoDownloadIfNewer) {
                                // Still try direct in-app download from known mirrors!
                                downloadAndInstallApkInApp(
                                        myName,
                                        finalDirectUrl,
                                        finalLiveAttachUrl,
                                        finalFallbackUrl
                                );
                            }
                        } else if (finalRemoteCode > myCode || isRemoteVersionNewer(finalRemoteName, myName)) {
                            updateStatusBanner.setTextColor(Color.parseColor("#34D399"));
                            updateStatusBanner.setText(
                                    "🎉 NEW UPDATE AVAILABLE: v" + finalRemoteName + " (Build " + finalRemoteCode + ")!\n" +
                                    (finalNotes.length() > 0 ? finalNotes + "\n" : "") +
                                    (autoDownloadIfNewer ? "⬇ Starting automatic in-app download..." : "Tap '⚡ Update App Now' to auto-download & install!")
                            );
                            if (autoDownloadIfNewer) {
                                downloadAndInstallApkInApp(
                                        finalRemoteName,
                                        finalDirectUrl,
                                        finalLiveAttachUrl,
                                        finalFallbackUrl
                                );
                            } else {
                                Toast.makeText(MainActivity.this, "Update v" + finalRemoteName + " available! Tap '⚡ Update App Now'.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            updateStatusBanner.setTextColor(Color.parseColor("#34D399"));
                            updateStatusBanner.setText(
                                    "✓ AUTO-SCAN VERIFIED: You are on the LATEST version!\n" +
                                    "Installed: v" + myName + " (Build " + myCode + " = Remote Build " + finalRemoteCode + ")" +
                                    (syncedTasks > 0 ? " • " + syncedTasks + " live tasks synced" : "")
                            );
                            if (autoDownloadIfNewer) {
                                Toast.makeText(
                                        MainActivity.this,
                                        "✓ Auto-Scan Complete: You are already on the latest version (v" + myName + ")!",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * Compares version numbers following the scheme where versions starting with 1.1.2026
     * increment only the second number (e.g. 1.1.2026, 1.2.2026, 1.3.2026, etc.).
     */
    public static boolean isRemoteVersionNewer(String remoteName, String currentName) {
        if (remoteName == null || currentName == null) return false;
        try {
            String cleanRemote = remoteName.trim().replaceAll("^[vV]", "");
            String cleanCurrent = currentName.trim().replaceAll("^[vV]", "");
            if (cleanRemote.equalsIgnoreCase(cleanCurrent)) return false;

            String[] rParts = cleanRemote.split("\\.");
            String[] cParts = cleanCurrent.split("\\.");

            int r0 = Integer.parseInt(rParts[0]);
            int c0 = Integer.parseInt(cParts[0]);
            if (r0 > c0) return true;
            if (r0 < c0) return false;

            // Incrementing only the second number (e.g. 2 > 1 in 1.2.2026 vs 1.1.2026, or 1 > 0 vs 1.0.0)
            if (rParts.length > 1 && cParts.length > 1) {
                int r1 = Integer.parseInt(rParts[1]);
                int c1 = Integer.parseInt(cParts[1]);
                if (r1 > c1) return true;
                if (r1 < c1) return false;
            }

            if (rParts.length > 2 && cParts.length > 2) {
                int r2 = Integer.parseInt(rParts[2]);
                int c2 = Integer.parseInt(cParts[2]);
                if (r2 > c2) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public String getInstalledVersionName() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pInfo != null && pInfo.versionName != null && pInfo.versionName.length() > 0) {
                return pInfo.versionName;
            }
        } catch (Exception ignored) {}
        return CURRENT_VERSION_NAME;
    }

    public int getInstalledVersionCode() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return (int) pInfo.getLongVersionCode();
                } else {
                    return pInfo.versionCode;
                }
            }
        } catch (Exception ignored) {}
        return CURRENT_VERSION_CODE;
    }

    /**
     * Downloads the APK binary directly inside the app across multiple mirrors,
     * validates the ZIP/APK magic bytes ("PK\x03\x04") so it never saves an HTML error page,
     * launches the native Android Package Installer via ApkUpdateFileProvider,
     * and triggers an automatic post-update version scan.
     */
    private void downloadAndInstallApkInApp(
            final String targetVersionName,
            final String primaryUrl,
            final String relayAttachUrl,
            final String secondaryUrl
    ) {
        if (isDownloadingApk) return;
        isDownloadingApk = true;

        if (updateProgressBar != null) {
            updateProgressBar.setIndeterminate(false);
            updateProgressBar.setMax(100);
            updateProgressBar.setProgress(2);
            updateProgressBar.setVisibility(View.VISIBLE);
        }
        updateStatusBanner.setTextColor(Color.parseColor("#38BDF8"));
        updateStatusBanner.setText("⬇ Downloading Update v" + targetVersionName + " in-app (0%)...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> mirrors = new ArrayList<String>();
                if (primaryUrl != null && primaryUrl.startsWith("http")) mirrors.add(primaryUrl);
                if (relayAttachUrl != null && relayAttachUrl.startsWith("http") && !mirrors.contains(relayAttachUrl)) {
                    mirrors.add(relayAttachUrl);
                }
                if (secondaryUrl != null && secondaryUrl.startsWith("http") && !mirrors.contains(secondaryUrl)) {
                    mirrors.add(secondaryUrl);
                }
                String savedOrigin = getSavedCustomServerOrigin();
                if (savedOrigin != null && savedOrigin.startsWith("http")) {
                    String s1 = savedOrigin + "/api/download/pokemate-companion.apk";
                    String s2 = savedOrigin + "/pokemate-companion.apk";
                    if (!mirrors.contains(s1)) mirrors.add(s1);
                    if (!mirrors.contains(s2)) mirrors.add(s2);
                }

                File outApk = ApkUpdateFileProvider.getDownloadedApkFile(MainActivity.this);
                File tempApk = new File(getCacheDir(), "pokemate-temp-download.apk");
                boolean downloadSuccess = false;
                long downloadedBytesTotal = 0L;
                String lastMirrorErr = "No reachable mirror";

                for (int i = 0; i < mirrors.size(); i++) {
                    String candidateUrl = mirrors.get(i);
                    HttpURLConnection conn = null;
                    try {
                        if (tempApk.exists()) tempApk.delete();
                        URL u = new URL(candidateUrl);
                        conn = (HttpURLConnection) u.openConnection();
                        conn.setInstanceFollowRedirects(true);
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(15000);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*");
                        conn.setRequestProperty("Cache-Control", "no-cache");

                        int status = conn.getResponseCode();
                        if (status != 200) {
                            lastMirrorErr = "HTTP " + status;
                            continue;
                        }

                        final long contentLength = conn.getContentLength();
                        InputStream is = conn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(tempApk);
                        byte[] buf = new byte[8192];
                        long totalRead = 0L;
                        int n;
                        int lastReportedPct = -1;

                        while ((n = is.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                            totalRead += n;
                            final int pct = contentLength > 0
                                    ? Math.min(99, (int) ((totalRead * 100L) / contentLength))
                                    : Math.min(95, (int) ((totalRead * 100L) / 112000L));
                            if (pct != lastReportedPct && (pct % 4 == 0 || pct > 90)) {
                                lastReportedPct = pct;
                                final long kbDone = totalRead / 1024L;
                                final long kbTotal = contentLength > 0 ? (contentLength / 1024L) : 110L;
                                uiHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (updateProgressBar != null) {
                                            updateProgressBar.setProgress(pct);
                                        }
                                        updateStatusBanner.setText(
                                                "⬇ Downloading v" + targetVersionName + "... " + pct + "% (" + kbDone + " KB / " + kbTotal + " KB)"
                                        );
                                    }
                                });
                            }
                        }
                        fos.flush();
                        fos.close();
                        is.close();

                        // Verify ZIP/APK Magic Header ('P', 'K', 0x03, 0x04) and size > 40 KB
                        if (tempApk.exists() && tempApk.length() > 40000 && isValidApkZipFile(tempApk)) {
                            if (outApk.exists()) outApk.delete();
                            if (tempApk.renameTo(outApk)) {
                                downloadedBytesTotal = outApk.length();
                                downloadSuccess = true;
                                break;
                            }
                        } else {
                            lastMirrorErr = "Mirror returned HTML instead of APK binary";
                        }
                    } catch (Exception e) {
                        lastMirrorErr = e.getClass().getSimpleName() + ": " + e.getMessage();
                    } finally {
                        if (conn != null) {
                            try { conn.disconnect(); } catch (Exception ignored) {}
                        }
                    }
                }

                final boolean ok = downloadSuccess;
                final long finalBytes = downloadedBytesTotal;
                final String finalErr = lastMirrorErr;

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        isDownloadingApk = false;
                        if (updateProgressBar != null) {
                            updateProgressBar.setProgress(ok ? 100 : 0);
                            updateProgressBar.setVisibility(View.GONE);
                        }

                        if (ok) {
                            updateStatusBanner.setTextColor(Color.parseColor("#34D399"));
                            updateStatusBanner.setText(
                                    "✓ Downloaded v" + targetVersionName + " (" + (finalBytes / 1024L) + " KB)!\n" +
                                    "👉 Android Package Installer opened — tap 'Update' in the system prompt to finish!\n" +
                                    "(If the prompt didn't appear, tap 'Install Cached APK' below)"
                            );
                            launchNativePackageInstallerForCachedApk();
                            // Do not overwrite banner immediately with old version while user is installing!
                            if (manualInstallCachedApkBtn != null) {
                                manualInstallCachedApkBtn.setVisibility(View.VISIBLE);
                            }
                        } else {
                            updateStatusBanner.setTextColor(Color.parseColor("#FBBF24"));
                            updateStatusBanner.setText(
                                    "⚠ In-app mirror stream blocked (" + finalErr + ").\n" +
                                    "Opening direct APK download in browser..."
                            );
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(latestDirectApkUrl));
                            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(browserIntent);
                        }
                    }
                });
            }
        }).start();
    }

    private boolean isValidApkZipFile(File f) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            byte[] header = new byte[4];
            if (fis.read(header) != 4) return false;
            return header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;
        } catch (Exception e) {
            return false;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void launchNativePackageInstallerForCachedApk() {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                pendingInstallAfterPermission = true;
                Toast.makeText(
                        this,
                        "Turn ON 'Allow from this source' once — PalpiGO will immediately pop up the Update installer!",
                        Toast.LENGTH_LONG
                ).show();
                Intent permIntent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())
                );
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(permIntent);
                return;
            }

            Uri apkContentUri = ApkUpdateFileProvider.getContentUriForApk(this);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkContentUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            // Grant URI permission explicitly to all resolving activities (PackageInstaller, Settings, etc.)
            List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resInfoList != null) {
                for (ResolveInfo resolveInfo : resInfoList) {
                    if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.packageName != null) {
                        try {
                            grantUriPermission(resolveInfo.activityInfo.packageName, apkContentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) {}
                    }
                }
            }
            // Explicitly grant read access to known OEM / Google package installers
            String[] knownInstallers = new String[]{
                    "com.google.android.packageinstaller",
                    "com.android.packageinstaller",
                    "com.samsung.android.packageinstaller",
                    "com.miui.packageinstaller"
            };
            for (String pkg : knownInstallers) {
                try {
                    grantUriPermission(pkg, apkContentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
            }

            startActivity(installIntent);
            Toast.makeText(this, "Tap 'Update' in the Android dialog to finish updating!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Opening APK via browser fallback...", Toast.LENGTH_SHORT).show();
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(latestDirectApkUrl));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browserIntent);
        }
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
