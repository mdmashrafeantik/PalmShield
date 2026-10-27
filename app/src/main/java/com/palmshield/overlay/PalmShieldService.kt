package com.palmshield.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

enum class GuideLineVisibilityMode {
    DASHED_CYAN,
    FAINT_TINT_5_PERCENT,
    STEALTH_INVISIBLE
}

class PalmShieldService :
    Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    companion object {
        const val ACTION_START_SERVICE = "com.palmshield.overlay.ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.palmshield.overlay.ACTION_STOP_SERVICE"
        const val ACTION_TOGGLE_GRAVITY_HANDEDNESS = "com.palmshield.overlay.ACTION_TOGGLE_GRAVITY_HANDEDNESS"

        private const val NOTIFICATION_CHANNEL_ID = "palm_shield_overlay_channel"
        private const val NOTIFICATION_ID = 4401

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _isGravityStartFlow = MutableStateFlow(false)
        val isGravityStartFlow: StateFlow<Boolean> = _isGravityStartFlow.asStateFlow()
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Reactive UI State driving WindowManager updates
    private var isCornerBoxMode by mutableStateOf(false)
    private var isMinimizedToDot by mutableStateOf(false)
    private var isGhostLocked by mutableStateOf(false)
    private var isBottomStartGravity by mutableStateOf(false)
    private var guideMode by mutableStateOf(GuideLineVisibilityMode.DASHED_CYAN)

    private var savedShieldHeightPx by mutableIntStateOf(640)
    private var savedCornerWidthPx by mutableIntStateOf(780)
    private var absorbedPalmTouches by mutableIntStateOf(0)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val metrics = resources.displayMetrics
        savedShieldHeightPx = (metrics.heightPixels * 0.42f).roundToInt().coerceAtLeast(240)
        savedCornerWidthPx = (metrics.widthPixels * 0.55f).roundToInt().coerceAtLeast(320)

        createNotificationChannel()
        startPalmShieldForeground()
        attachTransparentOverlay()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        _isRunningFlow.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_GRAVITY_HANDEDNESS -> {
                toggleHandednessGravity()
            }
        }
        return START_STICKY
    }

    private fun startPalmShieldForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopServiceIntent = Intent(this, PalmShieldService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopServiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PalmShield Transparent Overlay Active")
            .setContentText("Swallowing stray palm touches on Galaxy Tab A9+.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Shield",
                pendingStopIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PalmShield Active Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the transparent palm rejection shield alive on One UI."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun attachTransparentOverlay() {
        val overlayFlags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            savedShieldHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 0
            y = 0
        }

        val view = object : ComposeView(this) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
                super.dispatchTouchEvent(ev)
                return true
            }
            override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                return true
            }
        }.apply {
            isClickable = true
            isFocusable = false
            setOnTouchListener { _, _ -> true }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@PalmShieldService)
            setViewTreeViewModelStoreOwner(this@PalmShieldService)
            setViewTreeSavedStateRegistryOwner(this@PalmShieldService)

            setContent {
                TransparentPalmShieldOverlayContent()
            }
        }

        composeView = view
        windowManager.addView(view, layoutParams)
    }

    private fun syncWindowLayoutParams() {
        val view = composeView ?: return
        val metrics = resources.displayMetrics
        val dotSizePx = (44f * metrics.density).roundToInt()

        layoutParams.gravity = if (isBottomStartGravity) {
            Gravity.BOTTOM or Gravity.START
        } else {
            Gravity.BOTTOM or Gravity.END
        }

        if (isMinimizedToDot) {
            layoutParams.width = dotSizePx
            layoutParams.height = dotSizePx
        } else if (isCornerBoxMode) {
            layoutParams.width = savedCornerWidthPx.coerceIn(
                (180f * metrics.density).roundToInt(),
                metrics.widthPixels
            )
            layoutParams.height = savedShieldHeightPx.coerceIn(
                (120f * metrics.density).roundToInt(),
                (metrics.heightPixels * 0.85f).roundToInt()
            )
        } else {
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = savedShieldHeightPx.coerceIn(
                (120f * metrics.density).roundToInt(),
                (metrics.heightPixels * 0.85f).roundToInt()
            )
        }

        windowManager.updateViewLayout(view, layoutParams)
    }

    private fun toggleHandednessGravity() {
        isBottomStartGravity = !isBottomStartGravity
        _isGravityStartFlow.value = isBottomStartGravity
        syncWindowLayoutParams()
    }

    @Composable
    private fun TransparentPalmShieldOverlayContent() {
        val density = LocalDensity.current

        if (isMinimizedToDot) {
            // Minimized 44.dp Floating Edge Dot
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF090D16).copy(alpha = 0.82f))
                    .border(1.5.dp, Color(0xFF06B6D4), CircleShape)
                    .clickable {
                        isMinimizedToDot = false
                        syncWindowLayoutParams()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Restore PalmShield",
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(22.dp)
                )
            }
            return
        }

        val backgroundTint = when (guideMode) {
            GuideLineVisibilityMode.FAINT_TINT_5_PERCENT -> Color(0xFF06B6D4).copy(alpha = 0.05f)
            else -> Color.Transparent
        }

        // Main 100% Transparent Touch-Swallowing Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundTint)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            absorbedPalmTouches++
                        }
                    }
                }
        ) {
            // Subtle Top Boundary Guide Line (1.5.dp dashed/solid cyan or hidden in Stealth Mode)
            if (guideMode != GuideLineVisibilityMode.STEALTH_INVISIBLE) {
                val lineAlpha = if (isGhostLocked) 0.20f else 0.85f
                val strokeWidthPx = with(density) { 1.5.dp.toPx() }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter)
                ) {
                    drawLine(
                        color = Color(0xFF06B6D4).copy(alpha = lineAlpha),
                        start = Offset(0f, strokeWidthPx / 2f),
                        end = Offset(size.width, strokeWidthPx / 2f),
                        strokeWidth = strokeWidthPx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                    )

                    // Draw subtle vertical side boundary when in Corner Box Mode
                    if (isCornerBoxMode) {
                        val sideX = if (isBottomStartGravity) size.width else 0f
                        drawLine(
                            color = Color(0xFF06B6D4).copy(alpha = lineAlpha * 0.7f),
                            start = Offset(sideX, 0f),
                            end = Offset(sideX, size.height),
                            strokeWidth = strokeWidthPx,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                        )
                    }
                }
            }

            // Top-Right Compact Floating Control Pill (or Ghost Lock Padlock)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
            ) {
                if (isGhostLocked) {
                    GhostLockedPadlockButton(
                        onLongPressUnlock = {
                            isGhostLocked = false
                        }
                    )
                } else {
                    CompactFloatingControlPill(
                        isCornerMode = isCornerBoxMode,
                        isStartGravity = isBottomStartGravity,
                        guideVisibilityMode = guideMode,
                        onDragResize = { dxPx, dyPx ->
                            val metrics = resources.displayMetrics
                            val minHeight = (120f * metrics.density).roundToInt()
                            val maxHeight = (metrics.heightPixels * 0.85f).roundToInt()
                            // Dragging UP (negative dyPx) increases bottom-anchored shield height
                            savedShieldHeightPx = (savedShieldHeightPx - dyPx.roundToInt())
                                .coerceIn(minHeight, maxHeight)

                            if (isCornerBoxMode) {
                                val minWidth = (180f * metrics.density).roundToInt()
                                val maxWidth = metrics.widthPixels
                                val widthDelta = if (isBottomStartGravity) {
                                    dxPx.roundToInt()
                                } else {
                                    -dxPx.roundToInt()
                                }
                                savedCornerWidthPx = (savedCornerWidthPx + widthDelta)
                                    .coerceIn(minWidth, maxWidth)
                            }
                            syncWindowLayoutParams()
                        },
                        onToggleMode = {
                            isCornerBoxMode = !isCornerBoxMode
                            syncWindowLayoutParams()
                        },
                        onToggleGravity = {
                            toggleHandednessGravity()
                        },
                        onCycleGuideVisibility = {
                            guideMode = when (guideMode) {
                                GuideLineVisibilityMode.DASHED_CYAN ->
                                    GuideLineVisibilityMode.FAINT_TINT_5_PERCENT
                                GuideLineVisibilityMode.FAINT_TINT_5_PERCENT ->
                                    GuideLineVisibilityMode.STEALTH_INVISIBLE
                                GuideLineVisibilityMode.STEALTH_INVISIBLE ->
                                    GuideLineVisibilityMode.DASHED_CYAN
                            }
                        },
                        onEnableGhostLock = {
                            isGhostLocked = true
                        },
                        onMinimizeToDot = {
                            isMinimizedToDot = true
                            syncWindowLayoutParams()
                        },
                        onCloseShield = {
                            stopSelf()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun GhostLockedPadlockButton(
        onLongPressUnlock: () -> Unit
    ) {
        var isHolding by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (isHolding) {
                        Color(0xFF06B6D4).copy(alpha = 0.35f)
                    } else {
                        Color(0xFF090D16).copy(alpha = 0.45f)
                    }
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF06B6D4).copy(alpha = if (isHolding) 0.85f else 0.25f),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isHolding = true
                        val upBeforeTimeout = withTimeoutOrNull(800L) {
                            waitForUpOrCancellation()
                        }
                        isHolding = false
                        if (upBeforeTimeout == null) {
                            // 800ms elapsed while finger remained pressed -> Unlock Ghost Lock
                            onLongPressUnlock()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isHolding) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = "Hold 800ms to Unlock Shield Controls",
                tint = Color(0xFF22D3EE).copy(alpha = if (isHolding) 1f else 0.55f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    @Composable
    private fun CompactFloatingControlPill(
        isCornerMode: Boolean,
        isStartGravity: Boolean,
        guideVisibilityMode: GuideLineVisibilityMode,
        onDragResize: (dxPx: Float, dyPx: Float) -> Unit,
        onToggleMode: () -> Unit,
        onToggleGravity: () -> Unit,
        onCycleGuideVisibility: () -> Unit,
        onEnableGhostLock: () -> Unit,
        onMinimizeToDot: () -> Unit,
        onCloseShield: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF090D16).copy(alpha = 0.78f))
                .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. Drag / Resize Handle
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragResize(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag to Resize Shield",
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(18.dp)
                )
            }

            // 2. Mode Toggle: Full Bottom Width (MATCH_PARENT) vs Corner Box Mode
            IconButton(
                onClick = onToggleMode,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (isCornerMode) Icons.Default.Fullscreen else Icons.Default.AspectRatio,
                    contentDescription = "Toggle Full Width or Corner Box Mode",
                    tint = Color(0xFFE2E8F0),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 3. Handedness Gravity Toggle (BOTTOM|END vs BOTTOM|START)
            IconButton(
                onClick = onToggleGravity,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = if (isStartGravity) "Anchored Bottom-Start" else "Anchored Bottom-End",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 4. Guide Line / Stealth Mode Toggle
            IconButton(
                onClick = onCycleGuideVisibility,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (guideVisibilityMode == GuideLineVisibilityMode.STEALTH_INVISIBLE) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = "Cycle Boundary Guide Line Visibility",
                    tint = when (guideVisibilityMode) {
                        GuideLineVisibilityMode.DASHED_CYAN -> Color(0xFF22D3EE)
                        GuideLineVisibilityMode.FAINT_TINT_5_PERCENT -> Color(0xFF38BDF8)
                        GuideLineVisibilityMode.STEALTH_INVISIBLE -> Color(0xFF64748B)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }

            // 5. Palm Lock ("Ghost Lock")
            IconButton(
                onClick = onEnableGhostLock,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Enable Ghost Lock (800ms Long Press to Unlock)",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(15.dp)
                )
            }

            // 6. Minimize to 44.dp Edge Dot
            IconButton(
                onClick = onMinimizeToDot,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Minimize,
                    contentDescription = "Minimize Shield to Edge Dot",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(16.dp)
                )
            }

            // 7. Stop / Close Shield
            IconButton(
                onClick = onCloseShield,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Shield",
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isRunningFlow.value = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()

        composeView?.let { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        composeView = null
        super.onDestroy()
    }
}
