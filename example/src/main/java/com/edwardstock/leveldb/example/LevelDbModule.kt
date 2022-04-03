package com.edwardstock.leveldb.example

import android.content.Context
import com.edwardstock.leveldb.AndroidLevelDBInstance
import com.edwardstock.leveldb.implementation.LevelDBInstance
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object LevelDbModule {
    @Provides
    fun provideLevelDB(@ApplicationContext context: Context): LevelDBInstance {
        return AndroidLevelDBInstance(context) {
            createIfMissing = true
        }
    }
}
