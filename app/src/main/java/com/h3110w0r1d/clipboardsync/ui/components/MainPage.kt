package com.h3110w0r1d.clipboardsync.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.h3110w0r1d.clipboardsync.R
import com.h3110w0r1d.clipboardsync.service.SyncStatus
import com.h3110w0r1d.clipboardsync.utils.ModuleUtils.getModuleVersion
import com.h3110w0r1d.clipboardsync.utils.ModuleUtils.isModuleEnabled
import com.h3110w0r1d.clipboardsync.viewmodel.ClipboardViewModel

fun getAppIconBitmap(context: Context): Bitmap? {
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainPage(viewModel: ClipboardViewModel) {

	val context = LocalContext.current
	val showSettingsDialog = remember { mutableStateOf(false) }
	val showAboutDialog = remember { mutableStateOf(false) }

	val clipboardContent by viewModel.clipboardContent.collectAsState()
	// 启动并绑定服务
	LaunchedEffect(Unit) {
		viewModel.bindService(context)
	}

	// 销毁时解除绑定（但不会停止 Service）
	DisposableEffect(Unit) {
		onDispose {
			viewModel.unbindService(context)
		}
	}
	Scaffold(
		topBar = {
			TopAppBar(
				navigationIcon = {
					getAppIconBitmap(context)?.let {
						Image(
							bitmap = it.asImageBitmap(),
							contentDescription = null,
							modifier = Modifier
								.height(32.dp)
								.width(48.dp)
						)
					}
				},
				title = { Text(stringResource(R.string.app_name)) },
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surfaceContainer,
					titleContentColor = MaterialTheme.colorScheme.onSurface
				),
				actions = {
					IconButton(onClick = { showSettingsDialog.value = true }) {
						Icon(
							imageVector = Icons.Filled.Settings,
							contentDescription = stringResource(R.string.setting),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
					IconButton(onClick = { showAboutDialog.value = true }) {
						Icon(
							imageVector = Icons.Filled.Info,
							contentDescription = stringResource(R.string.about),
							tint = MaterialTheme.colorScheme.onSurface
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
			ModuleCard(
				onOpenLSP = { viewModel.openLsposedManager(context) }
			)
			// 状态卡片
			StatusCard(
				viewModel,
				onToggleSync = {
					viewModel.toggleSync()
				}
			)

			// 剪贴板内容卡片
			ClipboardContentCard(
				content = clipboardContent,
				onSync = { viewModel.syncClipboardContent() }
			)
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
fun ModuleCard(
	onOpenLSP: () -> Unit
) {
	var moduleStatus = stringResource(R.string.module_inactivated)
	var cardBackground = MaterialTheme.colorScheme.error
	var textColor = MaterialTheme.colorScheme.onError
	var iconVector = Icons.Filled.AddCircle
	var deg = 45f
	if (isModuleEnabled()) {
		moduleStatus = stringResource(R.string.module_activated)
		cardBackground = MaterialTheme.colorScheme.primary
		textColor = MaterialTheme.colorScheme.onPrimary
		iconVector = Icons.Filled.CheckCircle
		deg = 0f
	}

	Card(
		colors = cardColors(containerColor = cardBackground),
		modifier = Modifier.fillMaxWidth(),
		onClick = onOpenLSP
	) {
		Row (
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon (
				imageVector = iconVector,
				contentDescription = null,
				modifier = Modifier
					.padding(24.dp)
					.size(24.dp)
					.rotate(deg),
				tint = textColor
			)
			Column {
				Text(
					text = moduleStatus,
					fontSize = 16.sp,
					fontWeight = FontWeight.Medium,
					color = textColor
				)
				if (isModuleEnabled())
				Text(
					text = "Xposed API Version: " + getModuleVersion(),
					fontSize = 12.sp,
					color = textColor
				)
			}
		}
	}
}

@Composable
fun StatusCard(
	viewModel: ClipboardViewModel,
	onToggleSync: () -> Unit
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

	Card(
		modifier = Modifier.fillMaxWidth(),
		onClick = onToggleSync,
		colors = cardColors(containerColor = when (syncStatus) {
			is SyncStatus.Connected -> MaterialTheme.colorScheme.primary
			is SyncStatus.Connecting -> MaterialTheme.colorScheme.primaryContainer
			is SyncStatus.Error -> MaterialTheme.colorScheme.error
			else -> MaterialTheme.colorScheme.surfaceContainerHigh
		}),
	) {
		Row (
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon (
				imageVector = when (syncStatus) {
					is SyncStatus.Connected -> Icons.Filled.CheckCircle
					is SyncStatus.Connecting -> Icons.Filled.MoreVert
					is SyncStatus.Error -> Icons.Filled.AddCircle
					else -> Icons.Filled.PlayArrow
				},
				contentDescription = null,
				modifier = Modifier
					.padding(24.dp)
					.size(24.dp)
					.rotate(when (syncStatus) {
						is SyncStatus.Connecting -> 90f
						is SyncStatus.Error -> 45f
						else -> 0f
					}),
				tint = when(syncStatus) {
					is SyncStatus.Connected -> MaterialTheme.colorScheme.onPrimary
					is SyncStatus.Connecting -> MaterialTheme.colorScheme.onPrimaryContainer
					is SyncStatus.Error -> MaterialTheme.colorScheme.onError
					else -> MaterialTheme.colorScheme.onSurface
				}
			)
			Column {
				Text(
					text = when (syncStatus) {
						is SyncStatus.Connected -> stringResource(R.string.connected)
						is SyncStatus.Connecting -> stringResource(R.string.connecting)
						is SyncStatus.Error -> stringResource(R.string.connection_failure)
						else -> stringResource(R.string.disconnect)
					},
					fontWeight = FontWeight.Medium,
					color = when(syncStatus) {
						is SyncStatus.Connected -> MaterialTheme.colorScheme.onPrimary
						is SyncStatus.Connecting -> MaterialTheme.colorScheme.onPrimaryContainer
						is SyncStatus.Error -> MaterialTheme.colorScheme.onError
						else -> MaterialTheme.colorScheme.onSurface
					}
				)
				Text(
					text = "${stringResource(R.string.device_id)}: $deviceId",
					fontSize = 12.sp,
					color = when(syncStatus) {
						is SyncStatus.Connected -> MaterialTheme.colorScheme.onPrimary
						is SyncStatus.Connecting -> MaterialTheme.colorScheme.onPrimaryContainer
						is SyncStatus.Error -> MaterialTheme.colorScheme.onError
						else -> MaterialTheme.colorScheme.onSurface
					}
				)
			}
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
		elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
		colors = cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
fun SettingsDialog(
	viewModel: ClipboardViewModel,
	onDismiss: () -> Unit
) {
	val uiState = viewModel.mqttSettingUIState

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
					value = uiState.serverAddress.value,
					onValueChange = { uiState.serverAddress.value = it },
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
						value = uiState.port.value,
						onValueChange = { uiState.port.value = it },
						label = { Text(stringResource(R.string.port)) },
						keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
						modifier = Modifier
							.fillMaxWidth(0.5f)
							.padding(vertical = 8.dp)
					)
					Checkbox(
						checked = uiState.enableSSL.value,
						onCheckedChange = { uiState.enableSSL.value = it }
					)
					Text(
						text = stringResource(R.string.enable_ssl),
						modifier = Modifier
							.clickable { uiState.enableSSL.value = !uiState.enableSSL.value }
					)
				}
				OutlinedTextField(
					value = uiState.username.value,
					onValueChange = { uiState.username.value = it },
					label = { Text(stringResource(R.string.username)) },
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = 8.dp)
				)

				OutlinedTextField(
					value = uiState.password.value,
					onValueChange = { uiState.password.value = it },
					label = { Text(stringResource(R.string.password)) },
					visualTransformation = PasswordVisualTransformation(),
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = 8.dp)
				)

				OutlinedTextField(
					value = uiState.secretKey.value,
					onValueChange = { uiState.secretKey.value = it },
					label = { Text(stringResource(R.string.secretKey)) },
					visualTransformation = PasswordVisualTransformation(),
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = 8.dp)
				)

				OutlinedTextField(
					value = uiState.topic.value,
					onValueChange = { uiState.topic.value = it },
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
						viewModel.saveSetting()
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
				fun packageVersion(): String {
					val manager = context.packageManager;
					var version = "1.0"
					try {
						val info = manager.getPackageInfo(context.packageName, 0);
						version = info.versionName ?: "1.0"
					} catch (_: PackageManager.NameNotFoundException) { }
					return version;
				}
				// 应用 Logo
				getAppIconBitmap(context)?.let {
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

				FlowRow(
					horizontalArrangement = Arrangement.Center
				) {
					Text(
						text = "@h3110w0r1d-y",
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
							.padding(4.dp)
					)
					Text(
						text = "@2891954521",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.primary,
						modifier = Modifier
							.clickable {
								// 打开 GitHub 链接
								val intent = Intent(
									Intent.ACTION_VIEW,
									"https://github.com/2891954521".toUri()
								)
								context.startActivity(intent)
							}
							.padding(4.dp)
					)
				}
			}
		}
	}
}
