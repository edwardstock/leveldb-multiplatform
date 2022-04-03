package com.edwardstock.leveldb.example

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LevelDbApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
