-keep class com.edwardstock.leveldb.internal.LevelDBNativeProvider { *; }
-keepclassmembers class * {
    native <methods>;
}

# Keep all exception class names (JNI and public API surface rely on stable names)
-keep class com.edwardstock.leveldb.exception.** { *; }
