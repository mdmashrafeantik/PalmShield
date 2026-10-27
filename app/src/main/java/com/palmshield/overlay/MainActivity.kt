package com.palmshield.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private val DarkColorPalette = darkColorScheme(
    primary = Color(0xFF06B6D4),
    onPrimary = Color(0xFF082F49),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF090D16),
    surface = Color(0xFF111827),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DarkColorPalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PalmShieldLauncherScreen()
                }
            }
        }
    }
}

@Composable
fun PalmShieldLauncherScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermission(context))
    }

    val isServiceRunning by PalmShieldService.isRunningFlow.collectAsState()
    val isGravityStart by PalmShieldService.isGravityStartFlow.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasNotificationPermission = checkNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF083344))
                    .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "PalmShield Icon",
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(28.dp)
                )
            }
            Column {
                Text(
                    text = "PalmShield",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Galaxy Tab A9+ (API 34) Capacitive Stylus Palm Rejection",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Architectural Status Summary Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111827))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "100% Transparent System Overlay Architecture",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color(0xFFE2E8F0)
            )
            Text(
                text = "Intercepts and consumes stray palm touch events inside the shield zone while allowing passive disc stylus strokes outside the zone to pass directly to Samsung Notes, Flexcil, or Xodo.",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 19.sp
            )
            Text(
                text = "FLAGS: FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF38BDF8)
            )
        }

        // Step 1: SYSTEM_ALERT_WINDOW Permission
        PermissionStepCard(
            stepNumber = "01",
            title = "Display Over Other Apps Permission",
            description = "Required to attach TYPE_APPLICATION_OVERLAY to WindowManager with PixelFormat.TRANSLUCENT.",
            isGranted = hasOverlayPermission,
            buttonText = if (hasOverlayPermission) "Overlay Permission Granted" else "Grant Overlay Permission",
            icon = Icons.Default.Layers,
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        )

        // Step 2: POST_NOTIFICATIONS Permission
        PermissionStepCard(
            stepNumber = "02",
            title = "Foreground Service Notification",
            description = "Prevents Samsung One UI 6 (Android 14) from killing the transparent shield while writing.",
            isGranted = hasNotificationPermission,
            buttonText = if (hasNotificationPermission) "Notifications Allowed" else "Request Notification Permission",
            icon = Icons.Default.Notifications,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        // Step 3: Start / Stop Invisible Palm Shield
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111827))
                .border(
                    width = 1.dp,
                    color = if (isServiceRunning) Color(0xFF06B6D4) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "03. Launch Invisible Palm Shield",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isServiceRunning) {
                            "Active on screen — use the top-right floating pill to resize, toggle Corner Mode, or Ghost Lock."
                        } else {
                            "Requires Step 01 overlay permission before starting the foreground service."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (!hasOverlayPermission) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                            return@Button
                        }
                        if (isServiceRunning) {
                            val stopIntent = Intent(context, PalmShieldService::class.java).apply {
                                action = PalmShieldService.ACTION_STOP_SERVICE
                            }
                            context.startService(stopIntent)
                        } else {
                            val startIntent = Intent(context, PalmShieldService::class.java).apply {
                                action = PalmShieldService.ACTION_START_SERVICE
                            }
                            ContextCompat.startForegroundService(context, startIntent)
                        }
                    },
                    enabled = hasOverlayPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) Color(0xFFDC2626) else Color(0xFF06B6D4),
                        contentColor = if (isServiceRunning) Color.White else Color(0xFF082F49)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServiceRunning) "Stop Shield" else "Start Invisible Palm Shield",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (isServiceRunning) {
                    OutlinedButton(
                        onClick = {
                            val toggleGravityIntent = Intent(context, PalmShieldService::class.java).apply {
                                action = PalmShieldService.ACTION_TOGGLE_GRAVITY_HANDEDNESS
                            }
                            context.startService(toggleGravityIntent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch Anchor Side",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGravityStart) "Anchor: BOTTOM|START" else "Anchor: BOTTOM|END",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStepCard(
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111827))
            .border(
                width = 1.dp,
                color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) Color(0xFF064E3B) else Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) Color(0xFF34D399) else Color(0xFF94A3B8)
                    )
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            if (isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }

        Text(
            text = description,
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            lineHeight = 18.sp
        )

        OutlinedButton(
            onClick = onClick,
            enabled = !isGranted,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isGranted) Color(0xFF10B981) else Color(0xFF38BDF8)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buttonText,
                fontSize = 13.sp,
                color = if (isGranted) Color(0xFF10B981) else Color(0xFFE2E8F0)
            )
        }
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
