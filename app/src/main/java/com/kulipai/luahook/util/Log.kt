package com.kulipai.luahook.util

import android.util.Log

private val TAG = "LuaXposed"

fun Throwable.log(text: String = "Throwable"): Throwable {
    Log.e(TAG, text, this)
    return this
}

fun String.d(text: String = "Debug"): String {
    Log.d(TAG, this)
    return this
}

fun String.e(text: String = "Error"): String {
    Log.e(TAG, this)
    return this
}


