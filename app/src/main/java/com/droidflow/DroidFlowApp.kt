package com.droidflow

import android.app.Application

/**
 * Application entry point. Holds the process-wide context used by the
 * application-scoped AgentEngine (the agent loop must keep running while
 * DroidFlow itself is in the background driving other apps).
 */
class DroidFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DroidFlowApp
            private set
    }
}
