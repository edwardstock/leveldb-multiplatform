[![Android Release](https://img.shields.io/maven-central/v/com.edwardstock/leveldb-android?style=flat-square)]\
[![Kotlin Release](https://img.shields.io/maven-central/v/com.edwardstock/leveldb-kt?style=flat-square)]

# LevelDB for Android and Kotlin

This is a Java wrapper for the amazing
[LevelDB](https://github.com/google/leveldb) by Google.

## Usage

Add this to your build.gradle:

```groovy
repositories {
    mavenCentral()
}
```

And then this as a dependency:\n For Android\n

```groovy
dependencies {
    implementation 'com.edwardstock:leveldb-android:1.0.0'
}
```

For Kotlin\n

```groovy
dependencies {
    implementation 'com.edwardstock:leveldb-kt:1.0.0'
}
```

## Example

### Opening, Closing, Putting, Deleting

```kotlin
val levelDb = LevelDB.open("path/to/leveldb", LevelDB.configure().createIfMissing(true))
// or u can just

levelDb.put("leveldb".getBytes(), "Is awesome!")
val result: String? = levelDb.getString("leveldb")
val resultBytes: ByteArray? = levelDb.get("leveldb")

levelDb.put("magic", byteArrayOf(0, 1, 2, 3, 4))
val magic: ByteArray? = levelDB.get("magic")

// !IMPORTANT! you must close it
levelDb.close()
```

For Android almost the same, but with other class

```kotlin
import com.edwardstock.leveldb.*

// context can be used for place db file: context.filesDir.toString() + File.separator + (dbName ?: LevelDB.DEFAULT_DBNAME)
val levelDb = AndroidLevelDB.open(context, LevelDB.configure().createIfMissing(true))

// or also you can use default instance
val levelDb = LevelDB.open(context, LevelDB.configure().createIfMissing(true))
```

### The same, but using try-with-resource

```java
class Main {
    public static myHandler() {

        try (LevelDB levelDb = LevelDB.open("path/to/leveldb", LevelDB.configure().createIfMissing(true))) {
            levelDB.put("leveldb".getBytes(), "Is awesome!".getBytes());
            String value = levelDB.get("leveldb".getBytes());

            leveldb.put("magic".getBytes(), new byte[]{0, 1, 2, 3, 4});
            byte[] magic = levelDB.getBytes("magic".getBytes());
        }
    }
}
```

```kotlin
val levelDb = LevelDB.open("path/to/leveldb") {
    createIfMissing(true)
}

levelDb.use {
    levelDb.put("leveldb", "Is awesome!")
    val result: String? = levelDb.getString("leveldb")
    val resultBytes: ByteArray? = levelDb["leveldb"]

    levelDb.put("magic", byteArrayOf(0, 1, 2, 3, 4))
    val magic: ByteArray? = levelDB["magic"]
}
```

### Open using android context

```kotlin
val context: context

// it writes db file to: `context.filesDir.toString() + File.separator + DEFAULT_DBNAME`
val levelDb = LevelDB.open(context) {
    createIfMissing(true)
}

levelDb.use {
    levelDb.put("leveldb", "Is awesome!")
    val result: String? = levelDb.getString("leveldb")
    val resultBytes: ByteArray? = levelDb["leveldb"]

    levelDb.put("magic", byteArrayOf(0, 1, 2, 3, 4))
    val magic: ByteArray? = levelDB["magic"]
}
```

### WriteBatch (a.k.a. Transactions)

```java
class My {
    public static void myFun() {
        LevelDB levelDB = LevelDB.open("path/to/leveldb"); // createIfMissing == true

        levelDB.put("sql".getBytes(), "is lovely!".getBytes());

        levelDB.writeBatch()
                .put("leveldb".getBytes(), "Is awesome!".getBytes())
                .put("magic".getBytes(), new byte[]{0, 1, 2, 3, 4})
                .del("sql".getBytes())
                .write(); // commit transaction

        levelDB.close(); // closing is a must!
    }

}

```

### Iteration Over Key-Value Pairs

LevelDB is a key-value store, but it has some nice iteration features.

Every key-value pair inside LevelDB is ordered. Until the comparator wrapper API is finished you can iterate over your LevelDB in the key's
lexicographical order.

```java
LevelDB levelDB=LevelDB.open("path/to/leveldb");

        Iterator iterator=levelDB.iterator();

        for(iterator.seekToFirst();iterator.isValid();iterator.next()){
        byte[]key=iterator.key();
        byte[]value=iterator.value();
        }

        iterator.close(); // closing is a must!
```

#### Reverse Iteration

*It is somewhat slower than forward iteration.*

```java
LevelDB levelDB=LevelDB.open("path/to/leveldb");

        Iterator iterator=levelDB.iterator();

        for(iterator.seekToLast();iterator.isValid();iterator.previous()){
        String key=iterator.key();
        String value=iterator.value();
        }

        iterator.close(); // closing is a must!
```

#### Iterate from a Starting Position

```java
LevelDB levelDB=LevelDB.open("path/to/leveldb");

        Iterator iterator=levelDB.iterator();

        for(iterator.seek("leveldb".getBytes());iterator.isValid();iterator.next()){
        String key=iterator.key();
        String value=iterator.value();
        }

        iterator.close(); // closing is a must!
```

This will start from the key `leveldb` if it exists, or from the one that follows (eg. `sql`, i.e. `l` < `s`).

#### Snapshots

Snapshots give you a consistent view of the data in the database at a given time.

Here's a simple example demonstrating their use:

```java
LevelDB levelDB=LevelDB.open("path/to/leveldb");

        levelDB.put("hello".getBytes(),"world".getBytes());

        Snapshot helloWorld=levelDB.obtainSnapshot();

        levelDB.put("hello".getBytes(),"brave-new-world".getBytes());

        levelDB.get("hello".getBytes(),helloWorld); // == "world"

        levelDB.get("hello".getBytes()); // == "brave-new-world"

        levelDB.releaseSnapshot(helloWorld); // release the snapshot

        levelDB.close(); // snapshots will automatically be released after this
```

### Mock LevelDB

The implementation also supplies a mock LevelDB implementation that is an in-memory equivalent of the native LevelDB. It is meant to be used in
testing environments, especially non-Android ones like Robolectric.

There are a few of differences from the native implementation:

+ it is not configurable
+ it does not support properties (as in `LevelDB#getProperty()`)
+ it does not support paths, i.e. always returns `:MOCK:`

Use it like so:

```java
LevelDB.mock();
```

## Building

Until Google (or someone else) fixes the Android Gradle build tools to properly support NDK, this is the way to build this project.

1. Install the [NDK](https://developer.android.com/ndk)
2. Build with Gradle (leveldb::assembleRelease)

Or u can build to local maven repository:

```bash
cd /path/to/project
sh publish_local.sh
```

## License

This wrapper library is licensed under the
[BSD 3-Clause License](http://opensource.org/licenses/BSD-3-Clause), same as the code from Google.

See `LICENSE.txt` for the full Copyright.
