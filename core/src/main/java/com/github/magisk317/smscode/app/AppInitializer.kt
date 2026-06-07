package com.github.magisk317.smscode.app

import android.app.Application

interface AppInitializer {
    fun init(application: Application)
}
