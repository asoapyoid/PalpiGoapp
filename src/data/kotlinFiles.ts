export interface KotlinSourceFile {
  id: string;
  filename: string;
  path: string;
  language: 'kotlin' | 'xml' | 'gradle';
  category: 'Core Services' | 'Automation & AI' | 'Data & Room DB' | 'UI & Manifest';
  description: string;
  linesOfCode: number;
  code: string;
}

export const KOTLIN_SOURCE_FILES: KotlinSourceFile[] = [
  {
    id: 'manifest',
    filename: 'AndroidManifest.xml',
    path: 'app/src/main/AndroidManifest.xml',
    language: 'xml',
    category: 'UI & Manifest',
    description: 'Declares SYSTEM_ALERT_WINDOW, BIND_ACCESSIBILITY_SERVICE, FOREGROUND_SERVICE, and registers FloatingViewService, PokemonGoAccessibilityService, and ResearchWidgetProvider.',
    linesOfCode: 92,
    code: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.pokemate.companion">

    <!-- Required Overlay & Automation Permissions -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Package Visibility for Pokémon GO (Scopely Explore & Legacy Package IDs, Android 11+) -->
    <queries>
        <package android:name="com.scopely.pokemongo" />
        <package android:name="com.nianticlabs.pokemongo" />
        <package android:name="com.pokemongo.samsung" />
    </queries>

    <application
        android:name=".PokeMateApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="PokéMate Companion"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.PokeMate.Material3.Dark"
        tools:targetApi="34">

        <!-- 1. Initial Setup, Permissions & Companion Dashboard -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 2. Floating Overlay Foreground Service (Bubble + Control Panel) -->
        <service
            android:name=".service.FloatingViewService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="floating_companion_overlay_and_research_hud" />
        </service>

        <!-- 3. Accessibility Service for Screen Parsing & Gesture Dispatch -->
        <service
            android:name=".service.PokemonGoAccessibilityService"
            android:enabled="true"
            android:exported="false"
            android:label="PokéMate Auto-Gift &amp; Screen Parser"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <!-- 4. Bonus: Home Screen Field Research Widget -->
        <receiver
            android:name=".widget.ResearchWidgetProvider"
            android:exported="true"
            android:label="Field Research Highlights">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/research_widget_info" />
        </receiver>

        <!-- FileProvider for CSV Export of Friend Gift History -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="\${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>
</manifest>`
  },
  {
    id: 'accessibility_config',
    filename: 'accessibility_service_config.xml',
    path: 'app/src/main/res/xml/accessibility_service_config.xml',
    language: 'xml',
    category: 'UI & Manifest',
    description: 'Configures the AccessibilityService to observe Scopely Explore Pokémon GO windows, retrieve view hierarchies, and dispatch human-like touch gestures.',
    linesOfCode: 19,
    code: `<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshot="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="120"
    android:packageNames="com.scopely.pokemongo,com.nianticlabs.pokemongo,com.pokemongo.samsung"
    android:settingsActivity="com.pokemate.companion.ui.MainActivity" />`
  },
  {
    id: 'floating_view_service',
    filename: 'FloatingViewService.kt',
    path: 'app/src/main/java/com/pokemate/companion/service/FloatingViewService.kt',
    language: 'kotlin',
    category: 'Core Services',
    description: 'Manages the persistent foreground notification and TYPE_APPLICATION_OVERLAY draggable floating bubble + expandable Material 3 control panel.',
    linesOfCode: 248,
    code: `package com.pokemate.companion.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pokemate.companion.automation.AutomationStatus
import com.pokemate.companion.automation.GiftAutomationEngine
import com.pokemate.companion.data.ResearchRepository
import com.pokemate.companion.ui.MainActivity
import com.pokemate.companion.ui.overlay.FloatingOverlayComposable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * FloatingViewService.kt
 *
 * Manages the persistent WindowManager overlay (TYPE_APPLICATION_OVERLAY)
 * and keeps automation alive in the background via a Foreground Service notification.
 * Supports smooth drag physics, edge-snapping, haptic feedback, and collapsible states
 * (compact draggable bubble vs expanded Material 3 control HUD).
 */
class FloatingViewService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val CHANNEL_ID = "pokemate_overlay_channel"
        const val NOTIFICATION_ID = 4092
        const val ACTION_START_GIFT = "com.pokemate.action.START_GIFT"
        const val ACTION_STOP_AUTOMATION = "com.pokemate.action.STOP_AUTOMATION"
        const val ACTION_TOGGLE_PANEL = "com.pokemate.action.TOGGLE_PANEL"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayView: ComposeView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _activeTab = MutableStateFlow("CONTROLS") // CONTROLS, RESEARCH, IV_CHECK, SETTINGS
    val activeTab: StateFlow<String> = _activeTab

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildPersistentNotification("Idle • Ready over Pokémon GO"))

        initFloatingWindow()
        observeAutomationStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GIFT -> {
                triggerHapticFeedback()
                GiftAutomationEngine.getInstance(applicationContext).startGiftSession()
            }
            ACTION_STOP_AUTOMATION -> {
                triggerHapticFeedback(isWarning = true)
                GiftAutomationEngine.getInstance(applicationContext).stopSession("Stopped by user")
            }
            ACTION_TOGGLE_PANEL -> {
                toggleExpandedState(!_isExpanded.value)
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Start in non-focusable mode so touch events outside the bubble pass through to Pokémon GO
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingViewService)
            setViewTreeViewModelStoreOwner(this@FloatingViewService)
            setViewTreeSavedStateRegistryOwner(this@FloatingViewService)

            setContent {
                val expanded by isExpanded.collectAsState()
                val currentTab by activeTab.collectAsState()
                val engine = remember { GiftAutomationEngine.getInstance(applicationContext) }
                val status by engine.statusFlow.collectAsState()
                val researchRepo = remember { ResearchRepository.getInstance(applicationContext) }
                val researchTasks by researchRepo.activeResearchFlow.collectAsState(initial = emptyList())

                FloatingOverlayComposable(
                    isExpanded = expanded,
                    currentTab = currentTab,
                    status = status,
                    researchTasks = researchTasks,
                    onBubbleDrag = { dx, dy -> updateBubblePosition(dx, dy) },
                    onBubbleDragEnd = { snapBubbleToNearestEdge() },
                    onToggleExpand = {
                        triggerHapticFeedback()
                        toggleExpandedState(!expanded)
                    },
                    onSelectTab = { tab ->
                        triggerHapticFeedback()
                        _activeTab.value = tab
                    },
                    onStartAutoGift = {
                        triggerHapticFeedback()
                        engine.startGiftSession()
                    },
                    onStopAutomation = {
                        triggerHapticFeedback(isWarning = true)
                        engine.stopSession("User pressed Stop")
                    },
                    onTriggerIvCapture = {
                        triggerHapticFeedback()
                        PokemonGoAccessibilityService.instance?.captureAppraisalScreenshot()
                    }
                )
            }
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun updateBubblePosition(deltaX: Float, deltaY: Float) {
        layoutParams.x = (layoutParams.x + deltaX.toInt()).coerceAtLeast(0)
        layoutParams.y = (layoutParams.y + deltaY.toInt()).coerceAtLeast(48)
        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun snapBubbleToNearestEdge() {
        if (_isExpanded.value) return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val targetX = if (layoutParams.x + 80 < screenWidth / 2) 16 else screenWidth - 160
        layoutParams.x = targetX
        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun toggleExpandedState(expand: Boolean) {
        _isExpanded.value = expand
        // Allow keyboard input when expanded on Research filter search
        layoutParams.flags = if (expand) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun observeAutomationStatus() {
        serviceScope.launch {
            GiftAutomationEngine.getInstance(applicationContext).statusFlow.collect { status ->
                val summary = if (status.isRunning) {
                    "Sending Gifts: \${status.giftsSent}/\${status.maxGiftsSession} • Friends: \${status.friendsProcessed}"
                } else {
                    "Standby • Sent: \${status.giftsSent} • Errors: \${status.errorsEncountered}"
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildPersistentNotification(summary))
            }
        }
    }

    private fun triggerHapticFeedback(isWarning: Boolean = false) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (isWarning) {
                VibrationEffect.createWaveForm(longArrayOf(0, 45, 60, 45), -1)
            } else {
                VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PokéMate Floating Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the PokéMate Floating Control Panel active over Pokémon GO"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingViewService::class.java).apply { action = ACTION_STOP_AUTOMATION },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("PokéMate Overlay Active")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Automation", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        overlayView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}`
  },
  {
    id: 'accessibility_service',
    filename: 'PokemonGoAccessibilityService.kt',
    path: 'app/src/main/java/com/pokemate/companion/service/PokemonGoAccessibilityService.kt',
    language: 'kotlin',
    category: 'Core Services',
    description: 'AccessibilityService implementation with recursive DFS node traversal, screen state detection, buddy/egg distance parsing, and Bezier human-like gesture dispatching.',
    linesOfCode: 286,
    code: `package com.pokemate.companion.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.pokemate.companion.automation.GiftAutomationEngine
import com.pokemate.companion.automation.ParsedScreenState
import com.pokemate.companion.automation.UiElementTarget
import kotlinx.coroutines.*
import java.util.Random
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * PokemonGoAccessibilityService.kt
 *
 * Reads the active window hierarchy when Pokémon GO is in the foreground
 * and dispatches non-intrusive Accessibility gestures (taps & curved swipes)
 * with Gaussian spatial jitter and randomized durations to mimic natural human input.
 *
 * NOTE: Uses ONLY standard Android Accessibility APIs — zero memory modification
 * or packet injection.
 */
class PokemonGoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PokeMateA11y"
        const val POKEMON_GO_PKG = "com.scopely.pokemongo"
        val SUPPORTED_PACKAGES = setOf(
            "com.scopely.pokemongo",
            "com.nianticlabs.pokemongo",
            "com.pokemongo.samsung"
        )

        @Volatile
        var instance: PokemonGoAccessibilityService? = null
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val random = Random()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "PokéMate AccessibilityService connected and monitoring $POKEMON_GO_PKG")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkgName = event?.packageName?.toString() ?: return
        if (!pkgName.contains("pokemongo")) return

        val rootNode = rootInActiveWindow ?: return
        try {
            val screenState = parsePokemonGoScreen(rootNode)
            GiftAutomationEngine.getInstance(applicationContext).onScreenStateUpdated(screenState)
        } finally {
            // Always recycle or let GC handle in API 33+
        }
    }

    /**
     * Traverses the AccessibilityNodeInfo tree using Depth-First Search (DFS)
     * to identify Friends List headers, Trainer rows, "Send Gift" action buttons,
     * daily limit banners, and Egg/Buddy distances.
     */
    fun parsePokemonGoScreen(root: AccessibilityNodeInfo): ParsedScreenState {
        val visibleTrainers = mutableListOf<UiElementTarget>()
        val detectedFieldResearchSlots = mutableListOf<String>()
        var isFriendsListHeaderVisible = false
        var isFieldResearchMenuVisible = false
        var sendGiftButton: UiElementTarget? = null
        var confirmSendButton: UiElementTarget? = null
        var dailyLimitReached = false
        var bagNearlyFullWarning = false
        var parsedBuddyDistanceKm: Float? = null

        val regexKm = Regex("""(\\d+\\.?\\d*)\\s*/\\s*(\\d+\\.?\\d*)\\s*km""", RegexOption.IGNORE_CASE)
        val researchTaskPrefixRegex = Regex(
            """^(Catch|Make|Win|Battle|Spin|Send|Earn|Power up|Evolve|Hatch|Defeat|Use|Take|Purify|Trade)\\s+.+""",
            RegexOption.IGNORE_CASE
        )

        fun traverseDfs(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 28) return

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val combined = "$text $desc".lowercase()

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // 1. Detect "Friends" tab header or "Field Research" menu header
            if (combined.contains("friends") && bounds.top < 450) {
                isFriendsListHeaderVisible = true
            }
            if ((combined.contains("field") || combined.contains("research")) && bounds.top < 520) {
                isFieldResearchMenuVisible = true
            }

            // 1B. Detect Active Field Research Tasks in the player's Research Menu (up to 4 slots)
            val candidateLine = text.ifEmpty { desc }.lines().firstOrNull()?.trim().orEmpty()
            if (candidateLine.length in 8..75 && researchTaskPrefixRegex.matches(candidateLine)) {
                if (!detectedFieldResearchSlots.contains(candidateLine) && detectedFieldResearchSlots.size < 4) {
                    detectedFieldResearchSlots.add(candidateLine)
                }
            }

            // 2. Detect Daily Limit or Full Inventory warnings
            if (combined.contains("daily limit") || combined.contains("cannot send more gifts")) {
                dailyLimitReached = true
            }
            if (combined.contains("item bag is full") || combined.contains("bag is nearly full")) {
                bagNearlyFullWarning = true
            }

            // 3. Detect "Send Gift" button on a Trainer Profile
            if (combined.contains("send gift") && !combined.contains("sent")) {
                sendGiftButton = UiElementTarget(
                    label = "Send Gift",
                    bounds = Rect(bounds),
                    isEnabled = node.isEnabled
                )
            }

            // 4. Detect "Send" confirmation button inside Postcard selector
            if (text.equals("Send", ignoreCase = true) || desc.equals("Send", ignoreCase = true)) {
                confirmSendButton = UiElementTarget(
                    label = "Confirm Send",
                    bounds = Rect(bounds),
                    isEnabled = node.isEnabled
                )
            }

            // 5. Detect Trainer Rows in Friends List (clickable cards in middle viewport)
            if (isFriendsListHeaderVisible && node.isClickable && bounds.top in 380..1850 && bounds.height() in 110..340) {
                val trainerName = text.ifEmpty { desc }.split("\\n").firstOrNull()?.trim().orEmpty()
                if (trainerName.length in 3..22 && !trainerName.equals("Friends", ignoreCase = true)) {
                    val alreadySentToday = combined.contains("gift sent") || combined.contains("arrow_sent")
                    visibleTrainers.add(
                        UiElementTarget(
                            label = trainerName,
                            bounds = Rect(bounds),
                            isEnabled = !alreadySentToday,
                            metadata = if (alreadySentToday) "SENT_TODAY" else "GIFTABLE"
                        )
                    )
                }
            }

            // 6. Utility: Parse Buddy or Egg Incubator Distance (e.g., "3.4 / 5.0 km")
            regexKm.find(combined)?.let { matchResult ->
                parsedBuddyDistanceKm = matchResult.groupValues[1].toFloatOrNull()
            }

            for (i in 0 until node.childCount) {
                traverseDfs(node.getChild(i), depth + 1)
            }
        }

        traverseDfs(root, 0)

        return ParsedScreenState(
            packageName = POKEMON_GO_PKG,
            isFriendsListScreen = isFriendsListHeaderVisible,
            isFieldResearchMenu = isFieldResearchMenuVisible || detectedFieldResearchSlots.isNotEmpty(),
            detectedResearchTasksOnScreen = detectedFieldResearchSlots,
            visibleTrainerTargets = visibleTrainers,
            sendGiftButton = sendGiftButton,
            confirmSendButton = confirmSendButton,
            dailyLimitReached = dailyLimitReached,
            bagNearlyFull = bagNearlyFullWarning,
            buddyDistanceKm = parsedBuddyDistanceKm,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Dispatches a human-like tap inside the target Rect with 2D Gaussian coordinate jitter
     * and randomized press duration (48ms - 115ms) so no two taps ever hit the exact same pixel.
     */
    suspend fun performHumanTap(targetRect: Rect): Boolean = suspendCoroutine { cont ->
        val centerX = targetRect.exactCenterX()
        val centerY = targetRect.exactCenterY()

        // Apply Gaussian spatial jitter within safe inner 40% of button bounds
        val maxOffsetX = (targetRect.width() * 0.2f).coerceAtLeast(4f)
        val maxOffsetY = (targetRect.height() * 0.2f).coerceAtLeast(4f)
        val jitterX = (random.nextGaussian() * (maxOffsetX / 2f)).toFloat().coerceIn(-maxOffsetX, maxOffsetX)
        val jitterY = (random.nextGaussian() * (maxOffsetY / 2f)).toFloat().coerceIn(-maxOffsetY, maxOffsetY)

        val tapX = (centerX + jitterX).coerceAtLeast(10f)
        val tapY = (centerY + jitterY).coerceAtLeast(10f)
        val tapDurationMs = (55L + (abs(random.nextGaussian()) * 32).toLong()).coerceIn(45L, 125L)

        val clickPath = Path().apply {
            moveTo(tapX, tapY)
            // Micro finger drift (1-2px) during tap
            lineTo(tapX + random.nextFloat() * 1.5f, tapY + random.nextFloat() * 1.5f)
        }

        val stroke = GestureDescription.StrokeDescription(clickPath, 0L, tapDurationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                cont.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                cont.resume(false)
            }
        }, null)

        if (!dispatched) cont.resume(false)
    }

    /**
     * Performs a human-like curved vertical swipe using a quadratic Bezier path
     * to scroll down the Friends List naturally.
     */
    suspend fun performHumanScrollDown(): Boolean = suspendCoroutine { cont ->
        val metrics = resources.displayMetrics
        val startX = metrics.widthPixels * (0.48f + (random.nextFloat() * 0.08f))
        val startY = metrics.heightPixels * (0.74f + (random.nextFloat() * 0.04f))
        val endX = startX + (random.nextGaussian() * 28f).toFloat()
        val endY = metrics.heightPixels * (0.28f + (random.nextFloat() * 0.05f))

        // Control point for subtle thumb arc
        val controlX = (startX + endX) / 2f + 34f
        val controlY = (startY + endY) / 2f

        val swipePath = Path().apply {
            moveTo(startX, startY)
            quadTo(controlX, controlY, endX, endY)
        }

        val durationMs = (340L + (abs(random.nextGaussian()) * 95).toLong()).coerceIn(280L, 560L)
        val stroke = GestureDescription.StrokeDescription(swipePath, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = cont.resume(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = cont.resume(false)
        }, null)

        if (!dispatched) cont.resume(false)
    }

    /**
     * Captures the current screen for the IV Check Helper (Android 11+ API)
     */
    fun captureAppraisalScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        Log.i(TAG, "Appraisal screenshot captured for IV parsing")
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Failed to capture appraisal screenshot: $errorCode")
                    }
                }
            )
        }
    }

    override fun onInterrupt() {
        GiftAutomationEngine.getInstance(applicationContext).stopSession("Accessibility Service interrupted")
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }
}`
  },
  {
    id: 'gift_automation_engine',
    filename: 'GiftAutomationEngine.kt',
    path: 'app/src/main/java/com/pokemate/companion/automation/GiftAutomationEngine.kt',
    language: 'kotlin',
    category: 'Automation & AI',
    description: 'State machine and coroutine loop executing the gift sequence with Gaussian delays (800-1200ms), safe mode, friend filtering, and Room DB logging.',
    linesOfCode: 242,
    code: `package com.pokemate.companion.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import com.pokemate.companion.data.AppDatabase
import com.pokemate.companion.data.AutomationLogEntity
import com.pokemate.companion.service.PokemonGoAccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Random
import kotlin.math.abs

enum class GiftSequenceStep {
    IDLE,
    SCANNING_FRIENDS_LIST,
    OPENING_TRAINER_PROFILE,
    WAITING_FOR_PROFILE_LOAD,
    CLICKING_SEND_GIFT,
    SELECTING_FIRST_POSTCARD,
    CONFIRMING_GIFT_SEND,
    RETURNING_TO_FRIENDS_LIST,
    SCROLLING_FOR_MORE_FRIENDS
}

data class AutomationConfig(
    val maxGiftsPerSession: Int = 20,
    val targetFriendGroup: String = "ALL", // ALL, ULTRA_BEST_PUSH, LUCKY_CANDIDATES
    val skipFriendsWithFullStorage: Boolean = true,
    val safeModeEnabled: Boolean = false, // Normal: 800-1200ms, Safe Mode: 1450-2200ms
    val alertOnBagFull: Boolean = true
)

data class AutomationStatus(
    val isRunning: Boolean = false,
    val currentStep: GiftSequenceStep = GiftSequenceStep.IDLE,
    val currentTrainerName: String = "—",
    val friendsProcessed: Int = 0,
    val giftsSent: Int = 0,
    val errorsEncountered: Int = 0,
    val maxGiftsSession: Int = 20,
    val lastDelayMs: Long = 0L,
    val statusMessage: String = "Ready to start Auto-Gift session"
)

/**
 * GiftAutomationEngine.kt
 *
 * Orchestrates the automated gift-sending coroutine pipeline:
 * Click first eligible friend -> Wait for profile load -> Click "Send Gift" ->
 * Select first postcard -> Confirm -> Back -> Next friend -> Scroll at bottom.
 */
class GiftAutomationEngine private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: GiftAutomationEngine? = null

        fun getInstance(context: Context): GiftAutomationEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GiftAutomationEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = AppDatabase.getInstance(context)
    private val random = Random()

    private var automationJob: Job? = null
    private var latestScreenState: ParsedScreenState? = null
    private val processedTrainersInSession = mutableSetOf<String>()

    var config = AutomationConfig()

    private val _statusFlow = MutableStateFlow(AutomationStatus())
    val statusFlow: StateFlow<AutomationStatus> = _statusFlow

    fun onScreenStateUpdated(state: ParsedScreenState) {
        latestScreenState = state
        if (config.alertOnBagFull && state.bagNearlyFull && _statusFlow.value.isRunning) {
            stopSession("Paused: Item Bag is nearly full!")
        }
    }

    fun startGiftSession(customConfig: AutomationConfig = config) {
        if (_statusFlow.value.isRunning) return
        config = customConfig
        processedTrainersInSession.clear()

        _statusFlow.value = AutomationStatus(
            isRunning = true,
            currentStep = GiftSequenceStep.SCANNING_FRIENDS_LIST,
            maxGiftsSession = config.maxGiftsPerSession,
            statusMessage = "Scanning Pokémon GO Friends List..."
        )

        automationJob = engineScope.launch {
            logAction("SESSION_START", "System", 0L, true, "Started Auto-Gift (max=\${config.maxGiftsPerSession})")

            while (isActive && _statusFlow.value.giftsSent < config.maxGiftsPerSession) {
                val a11y = PokemonGoAccessibilityService.instance
                if (a11y == null) {
                    stopSession("Error: Accessibility Service not connected")
                    break
                }

                val screen = latestScreenState
                if (screen?.dailyLimitReached == true) {
                    stopSession("Completed: Daily Gift limit reached in Pokémon GO")
                    break
                }

                // Find next unprocessed trainer on screen
                val nextTrainer = screen?.visibleTrainerTargets?.firstOrNull { target ->
                    target.isEnabled && !processedTrainersInSession.contains(target.label)
                }

                if (nextTrainer == null) {
                    // Reached bottom of currently visible list -> Scroll down
                    updateStep(GiftSequenceStep.SCROLLING_FOR_MORE_FRIENDS, "Scrolling friend list...")
                    val scrolled = a11y.performHumanScrollDown()
                    val scrollDelay = nextGaussianDelayMs()
                    delay(scrollDelay)

                    if (!scrolled) {
                        incrementError("Scroll gesture cancelled")
                    }
                    continue
                }

                // Execute 6-step gift sending sequence for this trainer
                executeSingleFriendGiftSequence(a11y, nextTrainer)
            }

            if (_statusFlow.value.giftsSent >= config.maxGiftsPerSession) {
                stopSession("Session target reached (\${config.maxGiftsPerSession} gifts sent)")
            }
        }
    }

    private suspend fun executeSingleFriendGiftSequence(
        a11y: PokemonGoAccessibilityService,
        trainerTarget: UiElementTarget
    ) {
        val trainerName = trainerTarget.label
        processedTrainersInSession.add(trainerName)

        // Step 1: Click Friend row
        updateStep(GiftSequenceStep.OPENING_TRAINER_PROFILE, "Opening profile: $trainerName", trainerName)
        a11y.performHumanTap(trainerTarget.bounds)
        val delay1 = waitGaussianDelay()

        // Step 2: Wait for Trainer Profile to load & locate "Send Gift" button
        updateStep(GiftSequenceStep.WAITING_FOR_PROFILE_LOAD, "Verifying gift eligibility...", trainerName)
        val sendGiftRect = latestScreenState?.sendGiftButton?.bounds
            ?: Rect(140, 1480, 520, 1660) // Fallback coordinate for 1080x2400 profile button

        if (config.skipFriendsWithFullStorage && latestScreenState?.sendGiftButton?.isEnabled == false) {
            // Friend still holds an unopened gift from us
            logAction("SKIP_UNOPENED", trainerName, delay1, true, "Friend has unopened gift")
            a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            incrementFriendProcessed(giftSent = false)
            waitGaussianDelay()
            return
        }

        // Step 3: Tap "Send Gift" button
        updateStep(GiftSequenceStep.CLICKING_SEND_GIFT, "Tapping Send Gift for $trainerName", trainerName)
        a11y.performHumanTap(sendGiftRect)
        waitGaussianDelay()

        // Step 4: Select first Postcard in gift carousel
        updateStep(GiftSequenceStep.SELECTING_FIRST_POSTCARD, "Selecting first postcard...", trainerName)
        val firstPostcardRect = Rect(180, 920, 900, 1340)
        a11y.performHumanTap(firstPostcardRect)
        waitGaussianDelay()

        // Step 5: Tap "Send" confirmation button
        updateStep(GiftSequenceStep.CONFIRMING_GIFT_SEND, "Confirming gift dispatch...", trainerName)
        val confirmRect = latestScreenState?.confirmSendButton?.bounds ?: Rect(340, 1740, 740, 1900)
        val confirmed = a11y.performHumanTap(confirmRect)
        val sendDelay = waitGaussianDelay()

        if (confirmed) {
            incrementFriendProcessed(giftSent = true)
            db.friendDao().markGiftSent(trainerName, System.currentTimeMillis())
            logAction("GIFT_SENT", trainerName, sendDelay, true, "Gift sent with Gaussian delay \${sendDelay}ms")
        } else {
            incrementError("Failed confirming gift for $trainerName")
        }

        // Step 6: Navigate back to Friends List
        updateStep(GiftSequenceStep.RETURNING_TO_FRIENDS_LIST, "Returning to Friends List...", trainerName)
        a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        waitGaussianDelay()
    }

    /**
     * Generates a Gaussian-distributed human delay:
     * Standard Mode: Mean = 1000ms, SD = 95ms, Clamped to [800ms, 1200ms]
     * Safe Mode:     Mean = 1800ms, SD = 180ms, Clamped to [1450ms, 2300ms]
     */
    private suspend fun waitGaussianDelay(): Long {
        val delayMs = nextGaussianDelayMs()
        _statusFlow.value = _statusFlow.value.copy(lastDelayMs = delayMs)
        delay(delayMs)
        return delayMs
    }

    fun nextGaussianDelayMs(): Long {
        val (mean, stdDev, minMs, maxMs) = if (config.safeModeEnabled) {
            listOf(1800.0, 180.0, 1450L, 2300L)
        } else {
            listOf(1000.0, 95.0, 800L, 1200L)
        }
        val sample = ((mean as Double) + random.nextGaussian() * (stdDev as Double)).toLong()
        return sample.coerceIn(minMs as Long, maxMs as Long)
    }

    fun stopSession(reason: String) {
        automationJob?.cancel()
        _statusFlow.value = _statusFlow.value.copy(
            isRunning = false,
            currentStep = GiftSequenceStep.IDLE,
            statusMessage = reason
        )
        engineScope.launch {
            logAction("SESSION_STOP", "System", 0L, true, reason)
        }
    }

    private fun updateStep(step: GiftSequenceStep, message: String, trainer: String = _statusFlow.value.currentTrainerName) {
        _statusFlow.value = _statusFlow.value.copy(
            currentStep = step,
            currentTrainerName = trainer,
            statusMessage = message
        )
    }

    private fun incrementFriendProcessed(giftSent: Boolean) {
        val cur = _statusFlow.value
        _statusFlow.value = cur.copy(
            friendsProcessed = cur.friendsProcessed + 1,
            giftsSent = if (giftSent) cur.giftsSent + 1 else cur.giftsSent
        )
    }

    private suspend fun incrementError(reason: String) {
        val cur = _statusFlow.value
        _statusFlow.value = cur.copy(errorsEncountered = cur.errorsEncountered + 1)
        logAction("ERROR", cur.currentTrainerName, cur.lastDelayMs, false, reason)
    }

    private suspend fun logAction(type: String, trainer: String, delayMs: Long, success: Boolean, details: String) {
        db.automationLogDao().insertLog(
            AutomationLogEntity(
                timestamp = System.currentTimeMillis(),
                actionType = type,
                trainerName = trainer,
                delayMs = delayMs,
                success = success,
                details = details
            )
        )
    }
}`
  },
  {
    id: 'room_database',
    filename: 'AppDatabase.kt',
    path: 'app/src/main/java/com/pokemate/companion/data/AppDatabase.kt',
    language: 'kotlin',
    category: 'Data & Room DB',
    description: 'Room SQLite database schema defining the friends, research_tasks, and automation_logs tables, DAOs, and CSV Export utility.',
    linesOfCode: 196,
    code: `package com.pokemate.companion.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 1. friends table: name, friendship_level, last_gift_sent, giftable_status
 */
@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val name: String,
    @ColumnInfo(name = "friendship_level") val friendshipLevel: Int, // 1=Good, 2=Great, 3=Ultra, 4=Best
    @ColumnInfo(name = "days_to_next_tier") val daysToNextTier: Int,
    @ColumnInfo(name = "last_gift_sent") val lastGiftSent: Long?,
    @ColumnInfo(name = "giftable_status") val giftableStatus: String, // GIFTABLE, SENT_TODAY, UNOPENED_GIFT
    @ColumnInfo(name = "friend_group") val friendGroup: String = "GENERAL"
)

/**
 * 2. research_tasks table: task_name, reward, date_added, is_active
 */
@Entity(tableName = "research_tasks")
data class ResearchTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_name") val taskName: String,
    @ColumnInfo(name = "reward") val reward: String,
    @ColumnInfo(name = "cp_range") val cpRange: String,
    @ColumnInfo(name = "shiny_possible") val shinyPossible: Boolean,
    @ColumnInfo(name = "is_high_value") val isHighValue: Boolean,
    @ColumnInfo(name = "quick_to_complete") val quickToComplete: Boolean,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true
)

/**
 * 3. automation_logs table: timestamp, action_type, success/failure
 */
@Entity(tableName = "automation_logs")
data class AutomationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "trainer_name") val trainerName: String,
    @ColumnInfo(name = "delay_ms") val delayMs: Long,
    @ColumnInfo(name = "success") val success: Boolean,
    @ColumnInfo(name = "details") val details: String
)

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY days_to_next_tier ASC, name ASC")
    fun observeAllFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE days_to_next_tier <= 3 AND friendship_level IN (2, 3)")
    fun observeCloseToUltraOrBest(): Flow<List<FriendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFriends(friends: List<FriendEntity>)

    @Query("UPDATE friends SET last_gift_sent = :timestamp, giftable_status = 'SENT_TODAY' WHERE name = :trainerName")
    suspend fun markGiftSent(trainerName: String, timestamp: Long)

    @Query("SELECT * FROM friends")
    suspend fun getAllFriendsSnapshot(): List<FriendEntity>
}

@Dao
interface ResearchTaskDao {
    @Query("SELECT * FROM research_tasks WHERE is_active = 1 ORDER BY is_high_value DESC, shiny_possible DESC")
    fun observeActiveTasks(): Flow<List<ResearchTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<ResearchTaskEntity>)

    @Query("UPDATE research_tasks SET is_high_value = :starred WHERE id = :taskId")
    suspend fun setStarred(taskId: Long, starred: Boolean)
}

@Dao
interface AutomationLogDao {
    @Query("SELECT * FROM automation_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeRecentLogs(): Flow<List<AutomationLogEntity>>

    @Insert
    suspend fun insertLog(log: AutomationLogEntity)

    @Query("DELETE FROM automation_logs")
    suspend fun clearLogs()
}

@Database(
    entities = [FriendEntity::class, ResearchTaskEntity::class, AutomationLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun friendDao(): FriendDao
    abstract fun researchTaskDao(): ResearchTaskDao
    abstract fun automationLogDao(): AutomationLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pokemate_companion.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }

    /**
     * Bonus Feature: Exports the Room friends table & gift history to a shareable CSV file.
     */
    suspend fun exportFriendsToCsv(context: Context): File {
        val friends = friendDao().getAllFriendsSnapshot()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val csvFile = File(context.cacheDir, "pokemate_friends_gift_export.csv")

        csvFile.bufferedWriter().use { writer ->
            writer.write("trainer_name,friendship_level,days_to_next_tier,giftable_status,friend_group,last_gift_sent\\n")
            for (f in friends) {
                val lastSentFormatted = f.lastGiftSent?.let { sdf.format(Date(it)) } ?: "NEVER"
                writer.write("\"\${f.name}\",\${f.friendshipLevel},\${f.daysToNextTier},\${f.giftableStatus},\${f.friendGroup},\"\$lastSentFormatted\"\\n")
            }
        }
        return csvFile
    }
}`
  },
  {
    id: 'research_repository',
    filename: 'ResearchRepository.kt',
    path: 'app/src/main/java/com/pokemate/companion/data/ResearchRepository.kt',
    language: 'kotlin',
    category: 'Data & Room DB',
    description: 'Scrapes live seasonal & event Field Research directly from https://leekduck.com/research/ (with ScrapedDuck JSON mirror fallback), caches into Room, and fires keyword notifications.',
    linesOfCode: 198,
    code: `package com.pokemate.companion.data

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.pokemate.companion.service.FloatingViewService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

enum class ResearchFilter {
    ALL,
    SHINY_POSSIBLE,
    GOOD_REWARDS,
    QUICK_TO_COMPLETE
}

/**
 * ResearchRepository.kt
 *
 * Scrapes live Field Research tasks & encounter rewards directly from
 * https://leekduck.com/research/ (parsing .task-category, .task-item, .shiny-badge,
 * and Min/Max CP values) with automatic fallback to the ScrapedDuck JSON mirror.
 * Caches results into Room's research_tasks table and fires high-priority notifications
 * when user-defined keywords appear.
 */
class ResearchRepository private constructor(private val context: Context) {

    companion object {
        const val LEEKDUCK_RESEARCH_URL = "https://leekduck.com/research/"
        const val POGO_API_NET_EXCLUSIVES = "https://pogoapi.net/api/v1/research_task_exclusive_pokemon.json"
        const val POKEMON_GO_API_RAIDBOSS = "https://pokemon-go-api.github.io/pokemon-go-api/api/raidboss.json"
        const val SCRAPED_DUCK_FALLBACK_JSON = "https://raw.githubusercontent.com/bigfoott/ScrapedDuck/data/research.min.json"

        @Volatile
        private var INSTANCE: ResearchRepository? = null

        fun getInstance(context: Context): ResearchRepository {
            return INSTANCE ?: synchronized(this) {
                ResearchRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val dao = AppDatabase.getInstance(context).researchTaskDao()
    val activeResearchFlow: Flow<List<ResearchTaskEntity>> = dao.observeActiveTasks()

    private val alertKeywords = mutableSetOf("Rare Candy", "Silver Pinap", "Sweet Apple", "Applin", "Beldum", "Gible", "Snorlax")

    /**
     * Pulls the latest Field Research rotation directly from https://leekduck.com/research/
     */
    suspend fun refreshSeasonalResearch(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val html = fetchUrlText(LEEKDUCK_RESEARCH_URL)
            val parsedFromHtml = parseLeekDuckResearchHtml(html)
            if (parsedFromHtml.isNotEmpty()) {
                dao.insertTasks(parsedFromHtml)
                parsedFromHtml.forEach { checkAndTriggerKeywordAlert(it) }
                return@withContext Result.success(parsedFromHtml.size)
            }
            throw IllegalStateException("0 tasks parsed from LeekDuck HTML, trying JSON mirror")
        } catch (primaryError: Exception) {
            // Fallback to ScrapedDuck JSON mirror of LeekDuck
            try {
                val payload = fetchUrlText(SCRAPED_DUCK_FALLBACK_JSON)
                val jsonArray = JSONArray(payload)
                val tasks = mutableListOf<ResearchTaskEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val taskText = obj.optString("text", "Unknown Task").replace(Regex("<[^>]+>"), "").trim()
                    val rewardsArray = obj.optJSONArray("rewards") ?: continue
                    if (rewardsArray.length() == 0) continue

                    val firstReward = rewardsArray.getJSONObject(0)
                    val rewardName = firstReward.optString("name", "Mystery Encounter")
                    val canBeShiny = firstReward.optBoolean("canBeShiny", false)
                    val maxCp = firstReward.optJSONObject("combatPower")?.optInt("max", -1) ?: -1
                    val minCp = firstReward.optJSONObject("combatPower")?.optInt("min", -1) ?: -1
                    val cpRange = if (minCp > 0 && maxCp > 0) "$minCp - $maxCp CP (100% IV)" else "Item Reward"

                    val entity = buildTaskEntity(taskText, rewardName, cpRange, canBeShiny)
                    tasks.add(entity)
                    checkAndTriggerKeywordAlert(entity)
                }

                dao.insertTasks(tasks)
                Result.success(tasks.size)
            } catch (fallbackError: Exception) {
                Result.failure(fallbackError)
            }
        }
    }

    /**
     * Parses https://leekduck.com/research/ HTML DOM structure (.task-item, .reward-list)
     */
    private fun parseLeekDuckResearchHtml(html: String): List<ResearchTaskEntity> {
        val results = mutableListOf<ResearchTaskEntity>()
        val taskTextRegex = Regex("""<span class="task-text">\s*<span>([\s\S]*?)</span>\s*</span>""")
        val rewardLabelRegex = Regex("""<span class="reward-label">([\s\S]*?)</span>""")
        val maxCpRegex = Regex("""<span class="max-cp">\s*<div>Max CP</div>\s*(\d+)""")
        val minCpRegex = Regex("""<span class="min-cp">\s*<div>Min CP</div>\s*(\d+)""")

        val taskChunks = html.split("""<li class="task-item">""")
        for (i in 1 until taskChunks.size) {
            val chunk = taskChunks[i]
            val rawTask = taskTextRegex.find(chunk)?.groupValues?.get(1) ?: continue
            val taskName = rawTask.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").trim()

            val rewardNames = rewardLabelRegex.findAll(chunk)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotEmpty() }
                .toList()

            if (rewardNames.isEmpty()) continue

            val rewardSummary = rewardNames.take(3).joinToString(" / ")
            val canBeShiny = chunk.contains("""class="shiny-badge"""")
            val maxCp = maxCpRegex.find(chunk)?.groupValues?.get(1)?.toIntOrNull()
            val minCp = minCpRegex.find(chunk)?.groupValues?.get(1)?.toIntOrNull()
            val cpRange = if (minCp != null && maxCp != null) {
                "$minCp - $maxCp CP (100% IV)"
            } else {
                "Item / Resource Reward"
            }

            results.add(buildTaskEntity(taskName, rewardSummary, cpRange, canBeShiny))
        }
        return results
    }

    private fun buildTaskEntity(
        taskText: String,
        rewardName: String,
        cpRange: String,
        canBeShiny: Boolean
    ): ResearchTaskEntity {
        val isHighValue = rewardName.contains("Candy", true) ||
            rewardName.contains("Pinap", true) ||
            rewardName.contains("Apple", true) ||
            rewardName.contains("Applin", true) ||
            rewardName in listOf("Beldum", "Gible", "Dratini", "Chansey", "Snorlax", "Aerodactyl", "Mawile")

        val isQuick = taskText.contains("Catch 3", true) ||
            taskText.contains("Catch 5", true) ||
            taskText.contains("Spin 3", true) ||
            taskText.contains("Send 3", true)

        return ResearchTaskEntity(
            taskName = taskText,
            reward = rewardName,
            cpRange = cpRange,
            shinyPossible = canBeShiny,
            isHighValue = isHighValue,
            quickToComplete = isQuick,
            dateAdded = System.currentTimeMillis(),
            isActive = true
        )
    }

    private fun fetchUrlText(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000
            readTimeout = 9000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) PokéMateCompanion/1.0")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun checkAndTriggerKeywordAlert(task: ResearchTaskEntity) {
        val matched = alertKeywords.firstOrNull { kw ->
            task.taskName.contains(kw, ignoreCase = true) || task.reward.contains(kw, ignoreCase = true)
        } ?: return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, FloatingViewService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle("High-Value Research Detected: $matched")
            .setContentText("\${task.taskName} → \${task.reward} (\${task.cpRange})")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(task.taskName.hashCode(), notification)
    }

    /**
     * Takes the active Field Research task strings read from the player's screen by
     * PokemonGoAccessibilityService and returns the exact reward possibilities & odds
     * from the cached LeekDuck database.
     */
    fun matchScreenResearchPossibilities(
        screenTaskStrings: List<String>,
        cachedTasks: List<ResearchTaskEntity>
    ): List<Pair<String, List<Pair<String, Double>>>> {
        return screenTaskStrings.map { screenTask ->
            val normalizedScreen = screenTask.lowercase().trim()
            val matchedEntity = cachedTasks.firstOrNull { entity ->
                val normDb = entity.taskName.lowercase().trim()
                normDb == normalizedScreen || normDb.contains(normalizedScreen) || normalizedScreen.contains(normDb)
            }
            if (matchedEntity == null) {
                screenTask to emptyList()
            } else {
                val options = matchedEntity.reward.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                val probabilityEach = if (options.isNotEmpty()) 100.0 / options.size else 0.0
                screenTask to options.map { rewardName -> rewardName to probabilityEach }
            }
        }
    }
}`
  },
  {
    id: 'build_gradle',
    filename: 'build.gradle.kts (Module: app)',
    path: 'app/build.gradle.kts',
    language: 'gradle',
    category: 'UI & Manifest',
    description: 'Android Gradle build script configuring Jetpack Compose, Material 3, Room SQLite compiler, Coroutines, and debug/release APK generation.',
    linesOfCode: 74,
    code: `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

android {
    namespace = "com.pokemate.companion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pokemate.companion"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.2026"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Room SQLite Persistence
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:\$roomVersion")
    implementation("androidx.room:room-ktx:\$roomVersion")
    ksp("androidx.room:room-compiler:\$roomVersion")

    // Coroutines for Gaussian Delay Loop & LeekDuck Scraper
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}`
  },
  {
    id: 'github_workflow_apk',
    filename: 'build-apk.yml (Cloud APK Builder)',
    path: '.github/workflows/build-apk.yml',
    language: 'xml',
    category: 'UI & Manifest',
    description: 'GitHub Actions workflow that compiles the Kotlin source into an installable pokemate-debug.apk in the cloud without needing Android Studio on your PC.',
    linesOfCode: 44,
    code: `name: Build PokéMate Android APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build-apk:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.6'

      - name: Build Debug APK
        run: gradle assembleDebug --no-daemon

      - name: Upload Installable Phone APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: pokemate-companion-debug-apk
          path: app/build/outputs/apk/debug/*.apk`
  },
  {
    id: 'main_activity',
    filename: 'MainActivity.kt',
    path: 'app/src/main/java/com/pokemate/companion/ui/MainActivity.kt',
    language: 'kotlin',
    category: 'UI & Manifest',
    description: 'Material Design 3 AMOLED-capable main activity handling permission checks, service launch, CSV export, multi-account switching, and Raid coordinate parsing.',
    linesOfCode: 194,
    code: `package com.pokemate.companion.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.pokemate.companion.data.AppDatabase
import com.pokemate.companion.service.FloatingViewService
import kotlinx.coroutines.launch

/**
 * MainActivity.kt
 *
 * Material Design 3 setup screen for verifying SYSTEM_ALERT_WINDOW and
 * BIND_ACCESSIBILITY_SERVICE permissions, toggling AMOLED True Black battery-saver theme,
 * launching the FloatingViewService bubble, and exporting Room friend history to CSV.
 */
class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var useAmoledBlack by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionStatus()

        setContent {
            val colorScheme = if (useAmoledBlack) {
                darkColorScheme(
                    background = Color(0xFF000000),
                    surface = Color(0xFF0A0E14),
                    primary = Color(0xFF10B981),
                    secondary = Color(0xFF38BDF8),
                    error = Color(0xFFEF4444)
                )
            } else {
                darkColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
                    CompanionDashboardScreen(
                        hasOverlay = hasOverlayPermission,
                        hasAccessibility = hasAccessibilityPermission,
                        useAmoledBlack = useAmoledBlack,
                        onToggleAmoled = { useAmoledBlack = it },
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestAccessibilityPermission = { openAccessibilitySettings() },
                        onLaunchFloatingOverlay = { startFloatingOverlayService() },
                        onExportFriendsCsv = { exportAndShareFriendsCsv() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun refreshPermissionStatus() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasAccessibilityPermission = isAccessibilityServiceEnabled()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startFloatingOverlayService() {
        if (!hasOverlayPermission) {
            requestOverlayPermission()
            return
        }
        val serviceIntent = Intent(this, FloatingViewService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun exportAndShareFriendsCsv() {
        lifecycleScope.launch {
            val csvFile = AppDatabase.getInstance(this@MainActivity).exportFriendsToCsv(this@MainActivity)
            val uri = FileProvider.getUriForFile(
                this@MainActivity,
                "\${packageName}.fileprovider",
                csvFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export Friend Gift CSV"))
        }
    }
}`
  }
];
