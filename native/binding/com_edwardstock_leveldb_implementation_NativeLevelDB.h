/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_edwardstock_leveldb_implementation_NativeLevelDB */

#ifndef _Included_com_edwardstock_leveldb_implementation_NativeLevelDB
#define _Included_com_edwardstock_leveldb_implementation_NativeLevelDB
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    nopen
 * Signature: (ZIIILjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_nopen
    (JNIEnv *, jobject, jboolean, jint, jint, jint, jstring);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    nclose
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_nclose
    (JNIEnv *, jobject, jlong);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    nput
 * Signature: (JZ[B[B)V
 */
JNIEXPORT void JNICALL Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_nput
    (JNIEnv *, jobject, jlong, jboolean, jbyteArray, jbyteArray);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    ndelete
 * Signature: (JZ[B)V
 */
JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_ndelete
    (JNIEnv *, jobject, jlong, jboolean, jbyteArray);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    nwrite
 * Signature: (JZJ)V
 */
JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_nwrite
    (JNIEnv *, jobject, jlong, jboolean, jlong);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    nget
 * Signature: (J[BJ)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_nget
    (JNIEnv *, jobject, jlong, jbyteArray, jlong);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    ngetProperty
 * Signature: (J[B)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_ngetProperty
    (JNIEnv *, jobject, jlong, jbyteArray);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    ndestroy
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_ndestroy
    (JNIEnv *, jobject, jstring);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    nrepair
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_nrepair
    (JNIEnv *, jobject, jstring);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    niterate
 * Signature: (JZJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_niterate
    (JNIEnv *, jobject, jlong, jboolean, jlong);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    nsnapshot
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_nsnapshot
    (JNIEnv *, jobject, jlong);

/*
 * Class:     com_edwardstock_leveldb_implementation_NativeLevelDB
 * Method:    nreleaseSnapshot
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeLevelDB_00024Companion_nreleaseSnapshot
    (JNIEnv *, jobject, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif
