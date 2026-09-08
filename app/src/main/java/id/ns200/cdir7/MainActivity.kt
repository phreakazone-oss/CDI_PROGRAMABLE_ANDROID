package id.ns200.cdir7

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.ns200.cdir7.ui.screens.*
import id.ns200.cdir7.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: CdiViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                viewModel.bleClient.connect()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            CdiR7Theme {
                MainAppScreen(
                    viewModel = viewModel,
                    onRequestPermissions = { checkAndRequestPermissions() }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: CdiViewModel,
    onRequestPermissions: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isSimulation by viewModel.isSimulationMode.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark),
        topBar = {
            MotorsportTopBar(
                isConnected = isConnected,
                isSimulation = isSimulation,
                telemetry = telemetry,
                onConnectClick = {
                    onRequestPermissions()
                    viewModel.toggleConnect()
                },
                onDemoClick = { viewModel.toggleSimulation() }
            )
        },
        bottomBar = {
            MotorsportBottomNav(
                currentTab = currentTab,
                onTabSelect = { viewModel.setTab(it) }
            )
        },
        containerColor = CarbonDark,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                ScreenTab.TACHO -> DashboardScreen(viewModel)
                ScreenTab.MAPS -> MapsScreen(viewModel)
                ScreenTab.SUARA -> SoundScreen(viewModel)
                ScreenTab.STROBO -> StrobeScreen(viewModel)
                ScreenTab.GUIDE -> QuickSetupGuideScreen(viewModel)
                ScreenTab.BLE -> BleHexScreen(viewModel)
            }
        }
    }
}

@Composable
fun MotorsportTopBar(
    isConnected: Boolean,
    isSimulation: Boolean,
    telemetry: Telemetry,
    onConnectClick: () -> Unit,
    onDemoClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle),
        color = SurfacePanel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title Brand
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.armed) RacingLime else RaceRedline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NS200 // ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MotecOrange,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "CDI R7",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "MoTeC i2 / AIM Race Telemetry",
                            fontSize = 9.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Header Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Demo mode toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSimulation) MotecOrange.copy(alpha = 0.2f) else CardBackground)
                            .border(1.dp, if (isSimulation) MotecOrange else BorderSubtle, RoundedCornerShape(6.dp))
                            .clickable { onDemoClick() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("demo_mode_toggle")
                    ) {
                        Text(
                            text = if (isSimulation) "SIM ON" else "DEMO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSimulation) MotecOrange else TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Connect button
                    Button(
                        onClick = onConnectClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) RaceRedline else RacingLime
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("top_connect_button")
                    ) {
                        Text(
                            text = if (isConnected) "DISCONNECT" else "CONNECT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CarbonDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Quick Status Sub-Bar
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "STATUS: ${if (isConnected) (if (isSimulation) "SIMULASI 20Hz" else "BLE ONLINE") else "OFFLINE"}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) RacingLime else RaceRedline,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "BAT: %.1fV • CDI: %dV/%dV".format(telemetry.batteryCv / 100f, telemetry.hvCenter, telemetry.hvSide),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun MotorsportBottomNav(
    currentTab: ScreenTab,
    onTabSelect: (ScreenTab) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle),
        containerColor = SurfacePanel,
        windowInsets = WindowInsets.navigationBars
    ) {
        ScreenTab.entries.forEach { tab ->
            val isSelected = tab == currentTab
            val icon: ImageVector = when (tab) {
                ScreenTab.TACHO -> Icons.Default.Speed
                ScreenTab.MAPS -> Icons.Default.Tune
                ScreenTab.SUARA -> Icons.Default.VolumeUp
                ScreenTab.STROBO -> Icons.Default.FlashOn
                ScreenTab.GUIDE -> Icons.Default.Settings
                ScreenTab.BLE -> Icons.Default.Bluetooth
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelect(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) MotecOrange else TextMuted
                    )
                },
                label = {
                    Text(
                        text = tab.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) MotecOrange else TextSecondary
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MotecOrange.copy(alpha = 0.15f)
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}
