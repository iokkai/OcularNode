package com.example.util

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

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

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
        addLog("E", tag, "$message\n$stackTrace".trim())
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addLog("W", tag, message)
    }

    private fun addLog(level: String, tag: String, message: String) {
        if (!isEnabled) return
        val time = dateFormat.format(Date())
        val logLine = "[$time] $level/$tag: $message"
        _logs.update { current ->
            val updated = current.toMutableList()
            updated.add(0, logLine)
            if (updated.size > 1000) {
                updated.removeLast()
            }
            updated
        }
    }
    
    fun clear() {
        _logs.value = emptyList()
    }
}
