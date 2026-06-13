@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lts.control.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lts.control.core.ble.BleManager
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.DeviceState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ConnectionScreen(
    vm: BleViewModel,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val status by vm.status.collectAsState()
    val deviceState by vm.deviceState.collectAsState()
    val connection by vm.connection.collectAsState()

    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var showWifiError by remember { mutableStateOf(false) }
    var wiggleWifi by remember { mutableStateOf(false) }
    var bounceWifi by remember { mutableStateOf(false) }

    var ssids by remember { mutableStateOf(listOf<String>()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanSessionId by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        vm.messages.collectLatest { msg ->
            when (msg) {
                is com.lts.control.core.ble.model.IncomingMessage.WifiScan -> {
                    ssids = msg.ssids.distinct().map { it.trim() }.filter { it.isNotEmpty() }
                    isScanning = false
                    scanSessionId++
                }
                is com.lts.control.core.ble.model.IncomingMessage.WifiConnectResult -> {
                    isConnecting = false
                    if (msg.ok) {
                        bounceWifi = !bounceWifi
                        wifiPassword = ""
                    } else {
                        wiggleWifi = !wiggleWifi
                        showWifiError = true
                        scope.launch {
                            delay(3500)
                            showWifiError = false
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    val isBleConnected = status != null
    val isWifiConnected = isBleConnected && (status?.wifiConnected == true)
    LaunchedEffect(isWifiConnected) {
        if (isWifiConnected) isConnecting = false
    }
    LaunchedEffect(isBleConnected) {
        if (!isBleConnected) {
            wifiSsid = ""
            scanSessionId++
        }
    }

    val canSend = !isConnecting &&
            isBleConnected &&
            deviceState != DeviceState.UPDATING &&
            wifiSsid.isNotBlank() &&
            wifiPassword.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(
                        enabled = isBleConnected && deviceState != DeviceState.UPDATING,
                        onClick = {
                            isScanning = true
                            vm.wifiScan()
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "扫描网络")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 8.dp)
        ) {
            // -------------------------- 状态胶囊 --------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Capsule(
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            Icons.Filled.Bluetooth,
                            contentDescription = null,
                            tint = if (isBleConnected) Color.Cyan else LocalContentColor.current
                        )
                    },
                    title = "蓝牙",
                    subtitle = if (isBleConnected) "已连接" else "未连接"
                )

                val wifiScale by animateFloatAsState(
                    targetValue = when {
                        showWifiError -> 1.08f
                        bounceWifi -> 1.10f
                        else -> 1f
                    },
                    animationSpec = tween(
                        220,
                        easing = if (bounceWifi) LinearOutSlowInEasing else FastOutLinearInEasing
                    ),
                    label = "wifiScale"
                )

                Capsule(
                    modifier = Modifier.weight(1f).scale(wifiScale),
                    icon = {
                        val tint = when {
                            isWifiConnected -> Color(0xFF2E7D32)
                            showWifiError -> Color(0xFFD32F2F)
                            else -> LocalContentColor.current
                        }
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = tint)
                    },
                    title = "无线局域网",
                    subtitle = when {
                        showWifiError -> "错误！"
                        isBleConnected && isWifiConnected -> "已连接"
                        else -> "未连接"
                    }
                )
            }

            // ----------------------------- Wi‑Fi 设置 -----------------------------
            SettingsGroupHeader("无线局域网")
            Text(
                "要连接无线网络，请在此搜索可用网络并输入密码。仅支持 2.4 GHz 网络。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    // SSID 选择
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("网络", Modifier.weight(1f))
                        if (isScanning) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            var expanded by remember(scanSessionId) { mutableStateOf(false) }
                            Text(
                                if (wifiSsid.isBlank()) "选择网络 (${ssids.size})" else wifiSsid,
                                color = Color.Gray,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = ssids.isNotEmpty()) { expanded = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("选择网络 (${ssids.size})") },
                                    onClick = {
                                        wifiSsid = ""
                                        expanded = false
                                    }
                                )
                                ssids.forEach { ssid ->
                                    DropdownMenuItem(
                                        text = { Text(ssid) },
                                        onClick = {
                                            wifiSsid = ssid
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 密码输入
                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Button(
                            enabled = canSend,
                            onClick = {
                                isConnecting = true
                                vm.wifiConnect(ssid = wifiSsid, pass = wifiPassword)
                                wifiPassword = ""
                            }
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("连接中…")
                            } else {
                                Text("发送凭证")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ----------------------------- 蓝牙设置 -----------------------------
            SettingsGroupHeader("蓝牙")
            Text(
                "如果遇到连接问题，请移除已保存的设备并重新建立连接。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Button(
                        onClick = { vm.startScan() },
                        enabled = connection !is BleManager.ConnectionState.Ready
                    ) {
                        Text("连接设备")
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { vm.disconnect() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                    ) {
                        Text("移除已保存的设备")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ----------------------------- 复用组件 ----------------------------- */

@Composable
private fun Capsule(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        modifier
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(25))
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.BottomCenter) { icon() }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable private fun SettingsGroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}
