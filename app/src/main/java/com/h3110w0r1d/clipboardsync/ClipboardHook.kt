package com.h3110w0r1d.clipboardsync;

import android.os.Build
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam


class ClipboardHook : IXposedHookLoadPackage {
    var LOG_TAG: String = "=== ClipboardService Hook ==="
    var whitePackageName: String = "com.h3110w0r1d.clipboardsync"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") {
            return
        }
        XposedBridge.log(LOG_TAG + " - Loaded app: " + lpparam.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hook34(lpparam)
        } else {
            hook(lpparam)
        }

//        hookams(lpparam)
    }

    private fun hookams(lpparam: LoadPackageParam) {
        val clazz =
            XposedHelpers.findClass("com.android.server.am.ProcessList", lpparam.classLoader)
        Log.d(LOG_TAG, "hookams: find class$clazz")

        XposedBridge.hookAllMethods(
            clazz,
            "newProcessRecordLocked",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    try {
                        changeAppInfoForLockPackage(param.result, -1)
                    }catch (e:Throwable){
                        Log.e(LOG_TAG, "newProcessRecordLocked" + e.stackTraceToString())
                    }

                }
            })
    }

    private fun changeAppInfoForLockPackage(processRecord: Any, oomAdjValue:Int) {
        if (processRecord.toString().contains("system")) return
        try {
            val processName = processRecord.get<String>("processName")
            if (processName != whitePackageName) return
            Log.d(LOG_TAG, "processName: $processName")
            val mState = processRecord.get<Any>("mState")
            Log.d(LOG_TAG, "mState: $mState")
            XposedHelpers.callMethod(mState, "setMaxAdj", 0)
            XposedHelpers.callMethod(mState, "setSetAdj", 0)
//            processRecord.call<Unit>("setPersistent", true)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to access mState field", e)
        }
    }

    private fun hook34(lpparam: LoadPackageParam) {
        val clazz = XposedHelpers.findClass(
            "com.android.server.clipboard.ClipboardService",
            lpparam.classLoader
        )
        XposedHelpers.findAndHookMethod(
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
                    if (whitePackageName == param.args[1]) {
                        param.result = true
                    }
                }
            })
    }

    private fun hook(lpparam: LoadPackageParam) {
        val clazz = XposedHelpers.findClass(
            "com.android.server.clipboard.ClipboardService",
            lpparam.classLoader
        )
        XposedHelpers.findAndHookMethod(
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
                    if (whitePackageName == param.args[1]) {
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

inline fun <reified T> Any.call(method: String, vararg params: Any?): T? {
    return try {
        // 获取对象的 Class 对象
        val clazz = this.javaClass

        // 获取方法的参数类型数组
        val paramTypes = params.map { it?.javaClass }.toTypedArray()

        // 获取方法对象（匹配方法名和参数类型）
        val declaredMethod = clazz.getDeclaredMethod(method, *paramTypes)
        declaredMethod.isAccessible = true // 设置方法为可访问

        // 调用方法并返回结果
        declaredMethod.invoke(this, *params) as T
    } catch (e: Exception) {
        // 捕获异常并返回 null
        e.printStackTrace()
        null
    }
}