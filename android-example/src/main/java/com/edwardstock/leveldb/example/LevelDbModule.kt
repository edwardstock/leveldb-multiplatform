package com.edwardstock.leveldb.example

import android.content.Context
import com.edwardstock.leveldb.AndroidLevelDBInstance
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.ValueAdapter
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
        return AndroidLevelDBInstance.builder(context) {
            instance {
                driver {
                    createIfMissing(true)
                }
                adapters {
                    addAdapter(object : ValueAdapter<TextItem> {
                        override fun decode(value: ByteArray): TextItem {
                            val fields = value
                                .decodeToString()
                                .split(";")
                                .associate {
                                    val (key, v) = it.split("=")
                                    key to v
                                }

                            val id = checkNotNull(fields["id"]?.toIntOrNull())
                            val text = checkNotNull(fields["text"])
                            return TextItem(text, id)
                        }

                        override fun encode(value: TextItem): ByteArray {
                            return "id=${value.id};text=${value.text}".toByteArray()
                        }
                    })
                }
            }
        }
    }
}
