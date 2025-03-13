package com.h3110w0r1d.clipboardsync

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.h3110w0r1d.clipboardsync.ui.theme.ClipboardSyncTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
        requestIgnoreBatteryOptimizations()

        enableEdgeToEdge()
        setContent {
            ClipboardSyncTheme {
                ClipboardSyncView()
            }
        }
    }

    @SuppressLint("ServiceCast")
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = this.getSystemService(POWER_SERVICE) as PowerManager
        val packageName = this.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val packageName = this.packageName
        if (!isIgnoringBatteryOptimizations()) {
            val intent = Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            this.startActivity(intent)
        }
    }

    // 创建一个 ActivityResultLauncher 来请求通知权限
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 权限已授予，可以发送通知
                println("Notification permission granted")
            } else {
                // 权限被拒绝
                println("Notification permission denied")
            }
        }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 检查是否已获得权限
            if (ContextCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 权限尚未授予，请求权限
                requestPermissionLauncher.launch(POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardSyncView(
    viewModel: ClipboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val showSettingsDialog = remember { mutableStateOf(false) }
    val showAboutDialog = remember { mutableStateOf(false) }

    val historyItems by viewModel.historyItems.collectAsState()
    val clipboardContent by viewModel.clipboardContent.collectAsState()
    // 启动并绑定服务
    LaunchedEffect(Unit) {
        viewModel.initializeMMKV(context)
        viewModel.bindService(context)
    }

    // 销毁时解除绑定（但不会停止 Service）
    DisposableEffect(Unit) {
        onDispose {
            viewModel.unbindService(context)
            // 若需停止 Service，需显式调用 stopService()
            // context.stopService(Intent(context, MyService::class.java))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showSettingsDialog.value = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.setting),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showAboutDialog.value = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.about),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            StatusCard(
                viewModel,
                onToggleSync = { isEnabled ->
                    viewModel.toggleSync(isEnabled)
                }
            )

            // 剪贴板内容卡片
            ClipboardContentCard(
                content = clipboardContent,
                onSync = { viewModel.syncClipboardContent() }
            )

            // 同步历史记录
            HistoryCard(historyItems = historyItems)
        }

        if (showSettingsDialog.value) {
            SettingsDialog(viewModel, onDismiss = { showSettingsDialog.value = false })
        }

        if (showAboutDialog.value) {
            InfoDialog(onDismiss = { showAboutDialog.value = false })
        }
    }
}

@Composable
fun StatusCard(
    viewModel: ClipboardViewModel,
    onToggleSync: (Boolean) -> Unit
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isBound by viewModel.isBound
    val deviceId by viewModel.deviceId.collectAsState()

    LaunchedEffect(isBound) {
        if (isBound) {
            viewModel.serviceBinder?.getStatusFlow()?.collect { status ->
                viewModel.updateSyncStatus(status)
            }
        }
    }

    val isEnabled = syncStatus is SyncStatus.Connected

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (syncStatus) {
                                    is SyncStatus.Connected -> Color.Green
                                    is SyncStatus.Connecting -> Color.Yellow
                                    is SyncStatus.Error -> Color.Red
                                    else -> Color.Gray
                                }
                            )
                    )
                    Text(
                        text = when (syncStatus) {
                            is SyncStatus.Connected -> stringResource(R.string.connected)
                            is SyncStatus.Connecting -> stringResource(R.string.connecting)
                            is SyncStatus.Error -> stringResource(R.string.connection_failure)
                            else -> stringResource(R.string.disconnect)
                        },
                        fontWeight = FontWeight.Medium
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleSync
                )
            }

            Text(
                text = "${stringResource(R.string.device_id)}: $deviceId",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ClipboardContentCard(
    content: String,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.clipboard_content),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = content,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    Button(onClick = {
                        onSync()
                    }) {
                        Text(stringResource(R.string.sync))
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryCard(
    historyItems: List<HistoryItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "同步历史",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            historyItems.forEachIndexed { index, item ->
                HistoryItemRow(item)
                if (index < historyItems.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(item: HistoryItem) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = item.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "来自：${item.deviceId}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = item.time,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: ClipboardViewModel,
    onDismiss: () -> Unit
) {
    val settingsState by viewModel.settingsState.collectAsState()
    // 本地UI状态
    var serverAddress by remember { mutableStateOf(settingsState.serverAddress) }
    var port by remember { mutableStateOf(settingsState.port) }
    var enableSSL by remember { mutableStateOf(settingsState.enableSSL) }
    var username by remember { mutableStateOf(settingsState.username) }
    var password by remember { mutableStateOf(settingsState.password) }
    var topic by remember { mutableStateOf(settingsState.topic) }

    // 监听设置状态变化
    LaunchedEffect(settingsState) {
        serverAddress = settingsState.serverAddress
        port = settingsState.port
        enableSSL = settingsState.enableSSL
        username = settingsState.username
        password = settingsState.password
        topic = settingsState.topic
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.mqtt_settings),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 设置表单
                OutlinedTextField(
                    value = serverAddress,
                    onValueChange = { serverAddress = it },
                    label = { Text(stringResource(R.string.server_ip_host)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text(stringResource(R.string.port)) },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .padding(vertical = 8.dp)
                    )
                    Checkbox(
                        checked = enableSSL,
                        onCheckedChange = { enableSSL = it }
                    )
                    Text(
                        text = stringResource(R.string.enable_ssl),
                        modifier = Modifier
                            .clickable { enableSSL = !enableSSL }
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.topic)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // 按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        // 更新设置
                        val newSettings = MqttSettingsState(
                            serverAddress = serverAddress,
                            port = port,
                            enableSSL = enableSSL,
                            username = username,
                            password = password,
                            topic = topic,
                        )
                        viewModel.updateSettings(newSettings)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
fun InfoDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                fun getAppIconBitmap(): Bitmap? {
                    return ResourcesCompat.getDrawable(
                        context.resources,
                        R.mipmap.ic_launcher,
                        context.theme
                    )?.let { drawable ->
                        createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight).apply {
                            val canvas = Canvas(this)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                        }
                    }
                }
                fun packageVersion(): String {
                    val manager = context.getPackageManager();
                    var version = "1.0"
                    try {
                        val info = manager.getPackageInfo(context.getPackageName(), 0);
                        version = info.versionName ?: "1.0"
                    } catch (e: PackageManager.NameNotFoundException) {
                        e.printStackTrace();
                    }
                    return version;
                }
                // 应用 Logo
                getAppIconBitmap()?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = null)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 应用名称
                Text(
                    text = stringResource(R.string.app_name), // 替换你的应用名称资源
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))
                // 版本信息
                Text(
                    text = "Version ${packageVersion()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 开发者信息
                Text(
                    text = "Developed by",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // GitHub 链接
                Text(
                    text = "@h3110w0r1d-y", // 替换你的 GitHub 地址
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            // 打开 GitHub 链接
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/h3110w0r1d-y".toUri()
                            )
                            context.startActivity(intent)
                        }
                        .padding(8.dp)
                )
            }
        }
    }
}

// 数据类
data class HistoryItem(
    val content: String,
    val deviceId: String,
    val time: String
)

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ClipboardSyncTheme {
        ClipboardSyncView()
    }
}
