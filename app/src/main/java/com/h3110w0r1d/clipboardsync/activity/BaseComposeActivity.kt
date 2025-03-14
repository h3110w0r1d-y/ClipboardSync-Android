package com.h3110w0r1d.clipboardsync.activity

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.h3110w0r1d.clipboardsync.ui.theme.ClipboardSyncTheme

abstract class BaseComposeActivity: ComponentActivity() {

	private val activityResult: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		activityResultCallback?.onActivityResult(result)
	}

	private var activityResultCallback: ActivityResultCallback<ActivityResult>? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()

		super.onCreate(savedInstanceState)

		setContent { BaseContent { Content() } }
	}

	@Composable
	open fun BaseContent(content: @Composable () -> Unit){
		ClipboardSyncTheme {
			content()
		}
	}

	@Composable
	open fun Content(){

	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if(requestCode == 233) {
			var shouldShowRequest = true
			val deniedPermissions: MutableList<String> = ArrayList(permissions.size)
			for(i in permissions.indices) {
				if(grantResults[i] == PackageManager.PERMISSION_GRANTED) {
					onPermissionGranted(permissions[i])
				} else {
					if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						if(!shouldShowRequestPermissionRationale(permissions[i])) {
							shouldShowRequest = false
						}
					}
					deniedPermissions.add(permissions[i])
				}
			}

			if(deniedPermissions.isNotEmpty()) {
				val finalShouldShowRequest = shouldShowRequest
				AlertDialog.Builder(this).setTitle("提示").setMessage("请授予软件运行需要的全部权限")
					.setPositiveButton("设置") { dialog: DialogInterface?, which: Int ->
						if(finalShouldShowRequest) {
							ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray<String>(), 233)
						} else {
							startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
						}
					}.setCancelable(false).show()
			}
		}
	}

	/**
	 * 检查软件运行需要的权限
	 */
	fun checkPermissions(permissions: Array<String>) {
		val deniedPermissions: MutableList<String> = ArrayList(permissions.size)
		for(permission in permissions) {
			if(ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
				onPermissionGranted(permission)
			} else {
				deniedPermissions.add(permission)
			}
		}
		if(deniedPermissions.isNotEmpty()) ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray<String>(), 233)
	}

	/**
	 * 权限被授予时的回调，包括已经授予和新授予的权限
	 */
	protected open fun onPermissionGranted(permission: String) {}

	fun startActivityForResult(intent: Intent, callback: ActivityResultCallback<ActivityResult>) {
		activityResultCallback = callback
		activityResult.launch(intent)
	}
}