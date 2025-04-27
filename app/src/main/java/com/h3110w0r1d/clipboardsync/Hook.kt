package com.h3110w0r1d.clipboardsync

import android.content.IntentFilter
import android.os.Build
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam


class Hook : IXposedHookLoadPackage {
    companion object {
        const val LOG_TAG = "ClipboardSyncHook"
        const val PACKAGE_NAME = "com.h3110w0r1d.clipboardsync"
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == PACKAGE_NAME) {
            hookSelf(lpparam)
        }
        else if (lpparam.packageName == "android") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                hookCS34(lpparam)
            } else {
                hookCS(lpparam)
            }

            hookAdj(lpparam)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hookAMS30(lpparam)
            } else {
                hookAMS(lpparam)
            }
        }
    }

    private fun hookAMS(lpparam: LoadPackageParam) {
        val clazz = findClass(
            "com.android.server.am.ActivityManagerService",
            lpparam.classLoader
        )
        hookAllMethods(
            clazz,
            "registerReceiver",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    super.beforeHookedMethod(param)
                    val packageName = param?.args?.get(1) as String
                    if (packageName != PACKAGE_NAME) {
                        return
                    }
                    val intentFilter = param.args?.get(3) as IntentFilter
                    intentFilter.addAction("com.h3110w0r1d.clipboardsync.MODULE_ACTIVE")
                }
            })
    }

    private fun hookAMS30(lpparam: LoadPackageParam) {
        val clazz = findClass(
            "com.android.server.am.ActivityManagerService",
            lpparam.classLoader
        )
        hookAllMethods(
            clazz,
            "registerReceiverWithFeature",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    super.beforeHookedMethod(param)
                    val packageName = param?.args?.get(1) as String
                    if (packageName != PACKAGE_NAME) {
                        return
                    }
                    val intentFilter = param.args?.get(5) as IntentFilter
                    intentFilter.addAction("com.h3110w0r1d.clipboardsync.MODULE_ACTIVE")
                }
            })
    }

    private fun hookSelf(lpparam: LoadPackageParam) {
        val clazz = findClass(
            "com.h3110w0r1d.clipboardsync.utils.ModuleUtils",
            lpparam.classLoader
        )
        findAndHookMethod(
            clazz,
            "isModuleEnabled",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    super.beforeHookedMethod(param)
                    param?.result = true
                }
            })
        findAndHookMethod(
            clazz,
            "getModuleVersion",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    super.beforeHookedMethod(param)
                    param?.result = XposedBridge.getXposedVersion()
                }
            })
    }

    private fun hookAdj(lpparam: LoadPackageParam) {
        val clazz = findClass("com.android.server.am.ProcessList", lpparam.classLoader)
        hookAllMethods(
            clazz,
            "newProcessRecordLocked",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    if (param.result.toString().contains("system")) return
                    try {
                        val processName = param.result.get<String>("processName")
                        if (processName != PACKAGE_NAME) return
                        val mState = param.result.get<Any>("mState")
                        callMethod(mState, "setMaxAdj", 0)
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Failed to hook adj", e)
                    }

                }
            })
    }

    private fun hookCS34(lpparam: LoadPackageParam) {
        val clazz = findClass(
            "com.android.server.clipboard.ClipboardService",
            lpparam.classLoader
        )
        findAndHookMethod(
            clazz,
            "clipboardAccessAllowed",
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    if (PACKAGE_NAME == param.args[1]) {
                        param.result = true
                    }
                }
            })
    }

    private fun hookCS(lpparam: LoadPackageParam) {
        val clazz = findClass(
            "com.android.server.clipboard.ClipboardService",
            lpparam.classLoader
        )
        findAndHookMethod(
            clazz,
            "clipboardAccessAllowed",
            Int::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    if (PACKAGE_NAME == param.args[1]) {
                        param.result = true
                    }
                }
            })
    }
}

inline fun <reified T> Any.get(field: String): T? {
    return try {
        val clazz = this.javaClass
        val declaredField = clazz.getDeclaredField(field)
        declaredField.isAccessible = true
        declaredField.get(this) as T
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
