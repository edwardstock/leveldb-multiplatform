package com.edwardstock.leveldb.exception

import kotlin.reflect.KClass

class LevelDBNoTypeAdapterException(
    clazz: KClass<*>
) : LevelDBException("Cannot find converter for type ${clazz.qualifiedName}") {
}