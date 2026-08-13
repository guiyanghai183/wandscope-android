package com.guiyanghai.wandscope

import android.app.Application

class WandScopeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RunCompletionTracker.createChannel(this)
    }
}
