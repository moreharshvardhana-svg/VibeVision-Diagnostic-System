package com.vibevision.app

import android.app.Application
import com.vibevision.app.journal.ErrorJournal

/**
 * Custom Application class for VibeVision.
 * Installs the Error Log Journal and Global Uncaught Exception Hook at process boot.
 */
class VibeVisionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorJournal.initialize(this)
    }
}
