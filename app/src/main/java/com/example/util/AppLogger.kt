package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: String,
    val tag: String,
    val message: String,
    val isError: Boolean = false
)

object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun d(tag: String, message: String) {
        addLog(tag, message, isError = false)
        android.util.Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        addLog(tag, message, isError = false)
        android.util.Log.i(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message | Exception: ${throwable.localizedMessage ?: throwable.message}" else message
        addLog(tag, fullMsg, isError = true)
        android.util.Log.e(tag, message, throwable)
    }

    private fun addLog(tag: String, message: String, isError: Boolean) {
        synchronized(this) {
            val timeStr = dateFormat.format(Date())
            val entry = LogEntry(timeStr, tag, message, isError)
            val current = _logs.value.toMutableList()
            current.add(0, entry) // Newest on top
            if (current.size > 500) {
                current.removeAt(current.size - 1)
            }
            _logs.value = current
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getAllLogsText(): String {
        return _logs.value.joinToString("\n") { "[${it.timestamp}] [${it.tag}] ${if (it.isError) "❌ " else ""}${it.message}" }
    }
}
