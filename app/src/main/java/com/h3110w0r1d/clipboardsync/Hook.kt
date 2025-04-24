package com.h3110w0r1d.clipboardsync

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
            hook_self(lpparam)
        }
        else if (lpparam.packageName == "android") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                hook_cs34(lpparam)
            } else {
                hook_cs(lpparam)
            }
            hook_ams(lpparam)
        }
    }

    private fun hook_self(lpparam: LoadPackageParam) {
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

    private fun hook_ams(lpparam: LoadPackageParam) {
        val clazz = findClass("com.android.server.am.ProcessList", lpparam.classLoader)
        Log.d(LOG_TAG, "hookams: find class$clazz")

        hookAllMethods(
            clazz,
            "newProcessRecordLocked",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    try {
                        changeAppInfoForLockPackage(param.result)
                    }catch (e:Throwable){
                        Log.e(LOG_TAG, "newProcessRecordLocked" + e.stackTraceToString())
                    }

                }
            })
    }

    private fun changeAppInfoForLockPackage(processRecord: Any) {
        if (processRecord.toString().contains("system")) return
        try {
            val processName = processRecord.get<String>("processName")
            if (processName != PACKAGE_NAME) return
            val mState = processRecord.get<Any>("mState")
            callMethod(mState, "setMaxAdj", 0)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to access mState field", e)
        }
    }

    private fun hook_cs34(lpparam: LoadPackageParam) {
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

    private fun hook_cs(lpparam: LoadPackageParam) {
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
        // 获取对象的 Class 对象
        val clazz = this.javaClass
        // 获取字段对象，优先使用 declaredField，兼容 private/protected/public
        val declaredField = clazz.getDeclaredField(field)
        declaredField.isAccessible = true // 设置可访问
        declaredField.get(this) as T // 读取字段值并强制转换为 T
    } catch (e: Exception) {
        // 捕获异常并返回 null
        e.printStackTrace()
        null
    }
}
