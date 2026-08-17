package io.github.iokkai.ocularnode.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    var isEnabled: Boolean = true
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val threadLocalDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addLog("D", tag, message)
    }
    
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addLog("I", tag, message)
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        Log.e(tag, message, t)
        val stackTrace = t?.stackTraceToString() ?: ""
        addLog("E", tag, if (stackTrace.isNotEmpty()) "$message\n$stackTrace" else message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addLog("W", tag, message)
    }

    private fun addLog(level: String, tag: String, message: String) {
        if (!isEnabled) return
        val format = threadLocalDateFormat.get() ?: SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val time = format.format(Date())
        val logLine = "[$time] $level/$tag: $message"
        _logs.update { current ->
            val updated = ArrayList<String>(minOf(current.size + 1, 1000))
            updated.add(logLine)
            if (current.size < 1000) {
                updated.addAll(current)
            } else {
                updated.addAll(current.subList(0, 999))
            }
            updated
        }
    }
    
    fun clear() {
        _logs.value = emptyList()
    }
}
