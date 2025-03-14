package com.h3110w0r1d.clipboardsync.utils

import com.tencent.mmkv.MMKV

object MmkvUtils {

	val mmkv = MMKV.defaultMMKV()

	operator fun <T> get(key: String, defValue: T): T {
		return when(defValue){
			is String  -> (mmkv.getString(key, defValue) ?: defValue) as T
			is Int     -> mmkv.getInt(key, defValue) as T
			is Boolean -> mmkv.getBoolean(key, defValue) as T
			is Float   -> mmkv.getFloat(key, defValue) as T
			null -> throw IllegalArgumentException("unsupported mmkv value null")
			else -> throw IllegalArgumentException("unsupported mmkv value type ${defValue!!::class.java.name}")
		}
	}

	operator fun <T> set(key: String, value: T){
		when(value){
			is String  -> mmkv.putString(key, value)
			is Int     -> mmkv.putInt(key, value)
			is Boolean -> mmkv.putBoolean(key, value)
			is Float   -> mmkv.putFloat(key, value)
			null -> throw IllegalArgumentException("unsupported mmkv value null")
			else -> throw IllegalArgumentException("unsupported mmkv value type ${value!!::class.java.name}")
		}
	}

	fun getString(key: String, defValue: String = ""): String = mmkv.getString(key, defValue) ?: defValue
	fun putString(key: String, value: String) = mmkv.putString(key, value)

	fun getInt(key: String, defValue: Int = 0): Int = mmkv.getInt(key, defValue)
	fun putInt(key: String, value: Int) = mmkv.putInt(key, value)

	fun getBoolean(key: String, defValue: Boolean = false): Boolean = mmkv.getBoolean(key, defValue)
	fun putBoolean(key: String, value: Boolean) = mmkv.putBoolean(key, value)

	fun getStringSet(key: String, defValue: Set<String> = emptySet()): Set<String> = mmkv.getStringSet(key, defValue) ?: emptySet()
	fun putStringSet(key: String, value: Set<String>) = mmkv.putStringSet(key, value)

	fun removeKey(key: String){
		mmkv.removeValueForKey(key)
	}
}