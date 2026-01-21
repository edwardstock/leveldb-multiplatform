package com.edwardstock.leveldb.example

import android.app.Application
import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.loadNative
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LevelDbApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LevelDB.loadNative()
    }
}
