package com.quiet.signal

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.quiet.signal.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsRepository.init(applicationContext)
        model.start(applicationContext)
        setContent {
            QuietSignalApp(model, applicationContext)
        }
    }

    override fun onStart() {
        super.onStart()
        model.start(applicationContext)
    }

    override fun onStop() {
        model.stop()
        super.onStop()
    }
}

class MainViewModel : ViewModel() {
    val snapshot: StateFlow<TailscaleSnapshot> = StateRepository.snapshot
    val settings: StateFlow<UserSettings> = SettingsRepository.settings

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun start(context: Context) {
        SettingsRepository.init(context)
        StateRepository.start(context)
    }

    fun stop() {
        StateRepository.stop()
    }

    fun refresh(context: Context) {
        _refreshing.value = true
        StateRepository.refresh(context)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            _refreshing.value = false
        }, 280)
    }

    fun toggle(context: Context) {
        vibrateIfEnabled(context)
        StateRepository.toggle(context)
        WidgetUpdater.updateAll(context)
    }

    fun setMaterial(context: Context, material: GlassMaterial) {
        SettingsRepository.setMaterial(material)
        WidgetUpdater.updateAll(context)
    }

    fun setSpecularRim(context: Context, enabled: Boolean) {
        SettingsRepository.setSpecularRim(enabled)
        WidgetUpdater.updateAll(context)
    }

    fun setGreenIntensity(context: Context, intensity: GreenIntensity) {
        SettingsRepository.setGreenIntensity(intensity)
        WidgetUpdater.updateAll(context)
    }

    fun setHapticFeedback(enabled: Boolean) {
        SettingsRepository.setHapticFeedback(enabled)
    }

    fun setNetworkRecheck(enabled: Boolean) {
        SettingsRepository.setNetworkRecheck(enabled)
    }

    private fun vibrateIfEnabled(context: Context) {
        if (!settings.value.hapticFeedback) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    v?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(20)
                }
            }
        } catch (_: Exception) { }
    }

    fun openTailscale(context: Context) = TailscaleIntegration.openApp(context)
}

// Deep Obsidian Color Tokens
private val ColorBgDeep = Color(0xFF020304)
private val ColorSilverBright = Color(0xFFF0F4F2)
private val ColorSilverSubtle = Color(0xFF98A29C)
private val ColorMutedDark = Color(0xFF666E69)
private val ColorBorderSubtle = Color(0x14FFFFFF)
private val ColorBorderRim = Color(0x2EFFFFFF)

// Tailscale Colors
private val ColorTsGreen = Color(0xFF86C156)
private val ColorTsGreenGlow = Color(0x7386C156)
private val ColorTsGreenTint = Color(0x2E86C156)
private val ColorAmberPulse = Color(0xFFFFB74D)

@Composable
fun QuietSignalApp(model: MainViewModel, context: Context) {
    val snapshot by model.snapshot.collectAsState()
    val settings by model.settings.collectAsState()
    val refreshing by model.refreshing.collectAsState()
    var currentScreen by remember { mutableStateOf("control") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        when (settings.material) {
                            GlassMaterial.OBSIDIAN -> ColorBgDeep
                            GlassMaterial.GRAPHITE -> Color(0xFF070A0D)
                            GlassMaterial.CLEAR -> Color(0xFF000000)
                        },
                        Color(0xFF000000)
                    )
                )
            )
    ) {
        if (currentScreen == "control") {
            ControlRoomScreen(
                snapshot = snapshot,
                settings = settings,
                refreshing = refreshing,
                onToggle = { model.toggle(context) },
                onRefresh = { model.refresh(context) },
                onOpenSettings = { currentScreen = "settings" },
                onOpenTailscale = { model.openTailscale(context) }
            )
        } else {
            SettingsScreen(
                settings = settings,
                onBack = { currentScreen = "control" },
                onSelectMaterial = { model.setMaterial(context, it) },
                onToggleSpecular = { model.setSpecularRim(context, it) },
                onSelectIntensity = { model.setGreenIntensity(context, it) },
                onToggleHaptics = { model.setHapticFeedback(it) },
                onToggleRecheck = { model.setNetworkRecheck(it) }
            )
        }
    }
}


@Composable
fun ControlRoomScreen(
    snapshot: TailscaleSnapshot,
    settings: UserSettings,
    refreshing: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTailscale: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quiet Signal",
                color = ColorSilverBright,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (refreshing) "Syncing…" else "Sync",
                    color = if (refreshing) ColorTsGreen else ColorSilverSubtle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !refreshing, onClick = onRefresh)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    text = "Settings",
                    color = ColorSilverSubtle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "TAILSCALE",
            color = ColorMutedDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.4.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = snapshot.state.label,
            color = ColorSilverBright,
            fontSize = 38.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = snapshot.detail,
            color = ColorSilverSubtle,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        HeroLiquidOrb(
            state = snapshot.state,
            settings = settings,
            refreshing = refreshing,
            onClick = onToggle
        )

        Spacer(modifier = Modifier.height(40.dp))

        LiquidGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("This device", color = ColorMutedDark, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(snapshot.device, color = ColorSilverBright, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (snapshot.state == TailscaleState.CONNECTED) ColorTsGreenTint else Color(0x1AFFFFFF))
                        .border(1.dp, if (snapshot.state == TailscaleState.CONNECTED) ColorTsGreen.copy(alpha = 0.35f) else ColorBorderSubtle, RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (snapshot.state == TailscaleState.CONNECTED) "Active" else "Standby",
                        color = if (snapshot.state == TailscaleState.CONNECTED) ColorTsGreen else ColorSilverSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LiquidGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("VPN Transport", color = ColorMutedDark, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(snapshot.transport, color = ColorSilverBright, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Text("Best-effort", color = ColorSilverSubtle, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(99.dp))
                .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF))))
                .border(1.dp, ColorBorderRim, RoundedCornerShape(99.dp))
                .clickable(onClick = onOpenTailscale)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Open Official Tailscale  ↗", color = ColorSilverBright, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun HeroLiquidOrb(
    state: TailscaleState,
    settings: UserSettings,
    refreshing: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val isConnected = state == TailscaleState.CONNECTED
    val isConnecting = state == TailscaleState.CONNECTING || state == TailscaleState.DISCONNECTING
    val isBusy = isConnecting || refreshing

    val outerRingColor = when {
        isConnected -> ColorTsGreenGlow
        isBusy -> ColorAmberPulse.copy(alpha = 0.35f)
        else -> Color(0x1AFFFFFF)
    }

    val coreGradient = when {
        isConnected -> Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                if (settings.greenIntensity == GreenIntensity.LUMINOUS) Color(0xFFC7F598) else Color(0xFFBFE0A4),
                Color(0xFF5E8838),
                Color(0xFF223514)
            )
        )
        isBusy -> Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFFFD180),
                Color(0xFFD68A36),
                Color(0xFF4D2A08)
            )
        )
        else -> Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFB8C2BC),
                Color(0xFF4E5652),
                Color(0xFF1D2220)
            )
        )
    }

    Box(
        modifier = Modifier
            .size(150.dp)
            .scale(if (isConnected || isBusy) pulseScale else 1f)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x33161E26), Color(0x8506080C))))
            .border(1.5.dp, if (settings.specularRim) outerRingColor else Color(0x14FFFFFF), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(ColorTsGreenTint)
            )
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(coreGradient)
                .border(1.dp, Color(0x66FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (state) {
                    TailscaleState.CONNECTED -> "ON"
                    TailscaleState.DISCONNECTED -> "OFF"
                    TailscaleState.CONNECTING -> "..."
                    TailscaleState.DISCONNECTING -> "..."
                    else -> "OFF"
                },
                color = if (isConnected) Color(0xFF081006) else Color(0xFF0A0D0B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )
        }
    }
}

@Composable
fun LiquidGlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0x6B161E26), Color(0x990A0D12))))
            .border(1.dp, ColorBorderRim, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onSelectMaterial: (GlassMaterial) -> Unit,
    onToggleSpecular: (Boolean) -> Unit,
    onSelectIntensity: (GreenIntensity) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleRecheck: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Back",
                color = ColorSilverSubtle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Text("Settings", color = ColorSilverBright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "WIDGET APPEARANCE",
            color = ColorMutedDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        LiquidGlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Surface", color = ColorSilverBright, fontSize = 13.sp)
                        Text("Widget surface appearance", color = ColorMutedDark, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillButton("Obsidian", settings.material == GlassMaterial.OBSIDIAN) { onSelectMaterial(GlassMaterial.OBSIDIAN) }
                        PillButton("Graphite", settings.material == GlassMaterial.GRAPHITE) { onSelectMaterial(GlassMaterial.GRAPHITE) }
                        PillButton("Clear", settings.material == GlassMaterial.CLEAR) { onSelectMaterial(GlassMaterial.CLEAR) }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x0FFFFFFF)))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Specular Highlight Rim", color = ColorSilverBright, fontSize = 13.sp)
                        Text("Enhanced prismatic edge reflection", color = ColorMutedDark, fontSize = 11.sp)
                    }
                    LiquidSwitch(checked = settings.specularRim, onCheckedChange = onToggleSpecular)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x0FFFFFFF)))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tailscale Green Intensity", color = ColorSilverBright, fontSize = 13.sp)
                        Text("Restrained authentic vs luminous", color = ColorMutedDark, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillButton("Restrained", settings.greenIntensity == GreenIntensity.RESTRAINED) { onSelectIntensity(GreenIntensity.RESTRAINED) }
                        PillButton("Luminous", settings.greenIntensity == GreenIntensity.LUMINOUS) { onSelectIntensity(GreenIntensity.LUMINOUS) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "INTERACTION & SYNC",
            color = ColorMutedDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        LiquidGlassCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Confirmation", color = ColorSilverBright, fontSize = 13.sp)
                        Text("Subtle tactile click on widget toggle", color = ColorMutedDark, fontSize = 11.sp)
                    }
                    LiquidSwitch(checked = settings.hapticFeedback, onCheckedChange = onToggleHaptics)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x0FFFFFFF)))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Network Recheck", color = ColorSilverBright, fontSize = 13.sp)
                        Text("Fast one-shot verification on tap", color = ColorMutedDark, fontSize = 11.sp)
                    }
                    LiquidSwitch(checked = settings.networkRecheck, onCheckedChange = onToggleRecheck)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ABOUT",
            color = ColorMutedDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        LiquidGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Quiet Signal", color = ColorSilverBright, fontSize = 13.sp)
                    Text("Independent Tailscale companion widget", color = ColorMutedDark, fontSize = 11.sp)
                    Text("Not affiliated with Tailscale Inc.", color = ColorMutedDark, fontSize = 10.sp)
                }
                Text("v" + BuildConfig.VERSION_NAME, color = ColorMutedDark, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PillButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) Color(0x33FFFFFF) else Color(0x0DFFFFFF))
            .border(1.dp, if (selected) ColorSilverBright.copy(alpha = 0.6f) else ColorBorderSubtle, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (selected) ColorSilverBright else ColorSilverSubtle,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun LiquidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = ColorTsGreen,
            checkedTrackColor = ColorTsGreenTint,
            checkedBorderColor = ColorTsGreen.copy(alpha = 0.5f),
            uncheckedThumbColor = ColorSilverSubtle,
            uncheckedTrackColor = Color(0x1AFFFFFF),
            uncheckedBorderColor = ColorBorderSubtle
        )
    )
}

