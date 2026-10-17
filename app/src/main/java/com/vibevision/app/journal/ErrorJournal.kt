package com.vibevision.app.journal

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Severity levels for the VibeVision Error Journal.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRITICAL
}

/**
 * An individual log entry captured by the Error Log Journal.
 */
data class JournalEntry(
    val id: Long,
    val timestamp: Long,
    val formattedTime: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val threadName: String = "unknown"
) {
    fun toFormattedLog(): String {
        val sb = StringBuilder()
        sb.append("[$formattedTime] [${level.name}] [$tag] (thread: $threadName): $message\n")
        if (!stackTrace.isNullOrBlank()) {
            sb.append("Stack Trace:\n").append(stackTrace).append("\n")
        }
        return sb.toString()
    }
}

/**
 * Central Error Catching and Logging System for VibeVision.
 *
 * Implements:
 * - Persistent On-Device Error Log Journal file storage (`error_journal.log`).
 * - Automatic log file rotation and size capping (prevents unbounded disk growth).
 * - Global Uncaught Exception Handler to catch and record fatal app crashes before termination.
 * - Reactive StateFlow observation for live HUD telemetry and error badges.
 * - In-app viewing, clipboard export, and sharing capabilities.
 * - Thread-safe logging from background analysis, camera, and OpenGL surface threads.
 */
object ErrorJournal {

    private const val TAG = "VibeErrorJournal"
    private const val JOURNAL_FILE_NAME = "error_journal.log"
    private const val JOURNAL_OLD_FILE_NAME = "error_journal.old.log"
    private const val MAX_FILE_SIZE_BYTES = 512 * 1024L // 512 KB
    private const val MAX_IN_MEMORY_ENTRIES = 200

    private val idCounter = AtomicLong(0)
    private val fileLock = Any()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var logFile: File? = null
    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var isInitialized = false

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    private val _warnCount = MutableStateFlow(0)
    val warnCount: StateFlow<Int> = _warnCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _lastErrorTime = MutableStateFlow<String?>(null)
    val lastErrorTime: StateFlow<String?> = _lastErrorTime.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Initializes the error journal system, sets up log persistence,
     * loads recent historical entries, and attaches the global uncaught exception hook.
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, JOURNAL_FILE_NAME)
        this.logFile = file

        loadHistoricalEntries(file)
        installGlobalExceptionHandler()

        isInitialized = true

        i(TAG, "VibeVision Error Journal initialized. Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
    }

    private fun installGlobalExceptionHandler() {
        defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logCritical(
                    tag = "FATAL_CRASH",
                    message = "Uncaught fatal exception on thread '${thread.name}': ${throwable.localizedMessage ?: throwable.javaClass.simpleName}",
                    throwable = throwable,
                    flushImmediately = true
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to record uncaught exception in journal", t)
            } finally {
                defaultUncaughtHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Reads recent entries from the persistent log file on startup.
     */
    private fun loadHistoricalEntries(file: File) {
        scope.launch {
            if (!file.exists()) return@launch

            try {
                val lines = synchronized(fileLock) {
                    file.readLines()
                }

                val loaded = mutableListOf<JournalEntry>()
                var errors = 0
                var warns = 0
                var currentEntrySb: StringBuilder? = null
                var currentId = 0L

                // Quick parse of historical file entries
                for (line in lines) {
                    if (line.startsWith("[") && line.contains("] [")) {
                        // Header line: [2026-10-17 08:55:00.123] [LEVEL] [TAG] (thread: X): Message
                        try {
                            val timeEnd = line.indexOf("]")
                            val timeStr = line.substring(1, timeEnd)

                            val levelStart = line.indexOf("[", timeEnd + 1)
                            val levelEnd = line.indexOf("]", levelStart + 1)
                            val levelStr = line.substring(levelStart + 1, levelEnd)
                            val level = LogLevel.valueOf(levelStr)

                            val tagStart = line.indexOf("[", levelEnd + 1)
                            val tagEnd = line.indexOf("]", tagStart + 1)
                            val tag = line.substring(tagStart + 1, tagEnd)

                            val msgIdx = line.indexOf("): ", tagEnd)
                            val message = if (msgIdx != -1) line.substring(msgIdx + 3) else ""

                            val threadIdx = line.indexOf("(thread: ", tagEnd)
                            val threadName = if (threadIdx != -1 && msgIdx != -1) line.substring(threadIdx + 9, msgIdx) else "main"

                            val entry = JournalEntry(
                                id = ++currentId,
                                timestamp = System.currentTimeMillis(),
                                formattedTime = timeStr,
                                level = level,
                                tag = tag,
                                message = message,
                                threadName = threadName
                            )
                            loaded.add(entry)

                            if (level == LogLevel.ERROR || level == LogLevel.CRITICAL) errors++
                            if (level == LogLevel.WARN) warns++
                        } catch (e: Exception) {
                            // Ignore corrupted log line
                        }
                    }
                }

                idCounter.set(currentId)
                _entries.value = loaded.takeLast(MAX_IN_MEMORY_ENTRIES)
                _errorCount.value = errors
                _warnCount.value = warns
                _totalCount.value = loaded.size

            } catch (e: Exception) {
                Log.e(TAG, "Error loading journal history", e)
            }
        }
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)
    fun logCritical(tag: String, message: String, throwable: Throwable? = null, flushImmediately: Boolean = false) =
        log(LogLevel.CRITICAL, tag, message, throwable, flushImmediately)

    /**
     * Core logging dispatcher.
     */
    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        flushImmediately: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val timeString = synchronized(dateFormat) { dateFormat.format(Date(now)) }
        val threadName = Thread.currentThread().name

        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        val entry = JournalEntry(
            id = idCounter.incrementAndGet(),
            timestamp = now,
            formattedTime = timeString,
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace,
            threadName = threadName
        )

        // 1. Mirror to standard Android Logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
            LogLevel.CRITICAL -> Log.e("CRITICAL_$tag", message, throwable)
        }

        // 2. Update reactive StateFlow in-memory representation
        val currentList = _entries.value
        val updatedList = if (currentList.size >= MAX_IN_MEMORY_ENTRIES) {
            currentList.drop(1) + entry
        } else {
            currentList + entry
        }
        _entries.value = updatedList
        _totalCount.value = _totalCount.value + 1

        if (level == LogLevel.ERROR || level == LogLevel.CRITICAL) {
            _errorCount.value = _errorCount.value + 1
            _lastErrorTime.value = timeString
        } else if (level == LogLevel.WARN) {
            _warnCount.value = _warnCount.value + 1
        }

        // 3. Write to persistent journal file
        val writeAction = {
            writeToFile(entry)
        }

        if (flushImmediately) {
            writeAction()
        } else {
            scope.launch {
                writeAction()
            }
        }
    }

    private fun writeToFile(entry: JournalEntry) {
        val file = logFile ?: return
        synchronized(fileLock) {
            try {
                // Check for file rotation if exceeding max size
                if (file.exists() && file.length() >= MAX_FILE_SIZE_BYTES) {
                    val oldFile = File(file.parentFile, JOURNAL_OLD_FILE_NAME)
                    if (oldFile.exists()) oldFile.delete()
                    file.renameTo(oldFile)
                }

                FileOutputStream(file, true).bufferedWriter().use { writer ->
                    writer.write(entry.toFormattedLog())
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append entry to journal file", e)
            }
        }
    }

    /**
     * Purges all historical and in-memory error journal entries.
     */
    fun clearJournal() {
        synchronized(fileLock) {
            try {
                logFile?.delete()
                val oldFile = logFile?.let { File(it.parentFile, JOURNAL_OLD_FILE_NAME) }
                oldFile?.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting journal files", e)
            }
        }
        _entries.value = emptyList()
        _errorCount.value = 0
        _warnCount.value = 0
        _totalCount.value = 0
        _lastErrorTime.value = null

        i(TAG, "Error Journal reset by operator.")
    }

    /**
     * Returns the full content of the journal file for display or export.
     */
    fun getJournalContent(): String {
        val file = logFile ?: return "Journal not initialized."
        return synchronized(fileLock) {
            if (!file.exists()) {
                "No journal entries recorded."
            } else {
                try {
                    file.readText()
                } catch (e: Exception) {
                    "Error reading journal: ${e.message}"
                }
            }
        }
    }

    /**
     * Creates a temporary shareable intent with the error journal text.
     */
    fun createShareIntent(context: Context): Intent {
        val content = getJournalContent()
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "VibeVision Error Journal Log")
            putExtra(Intent.EXTRA_TEXT, content)
        }
    }

    /**
     * Generates a synthetic test error with full stack trace to verify
     * the error catching and journal logging pipeline in the field.
     */
    fun simulateTestError() {
        try {
            throw IllegalStateException("Field Diagnostic Simulated Exception: Optical Camera stream pipeline glitch test")
        } catch (e: Exception) {
            e(
                tag = "SIMULATED_TEST",
                message = "Simulated field exception triggered to verify error journal persistence.",
                throwable = e
            )
        }
    }
}
