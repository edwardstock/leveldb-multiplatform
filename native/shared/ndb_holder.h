//
// Created by Eduard Maximovich on 22.10.2025.
//

#ifndef LEVELDB_JNI_NDBHOLDER_H
#define LEVELDB_JNI_NDBHOLDER_H
#include "leveldb/cache.h"
#include "leveldb/db.h"
#include "leveldb/env.h"
#include "leveldb/write_batch.h"

// Hold references to heap-allocated native objects so that they can be closed

struct ndb_holder {
    leveldb::DB* db = nullptr;
    leveldb::Logger* logger = nullptr;
    leveldb::Cache* cache = nullptr;

    ndb_holder() = default;
    ndb_holder(leveldb::DB* d, leveldb::Logger* l, leveldb::Cache* c)
        : db(d), logger(l), cache(c) {}
};

struct ndb_batch_holder {
    leveldb::WriteBatch* batch = nullptr;
    ndb_batch_holder() = default;
    explicit ndb_batch_holder(leveldb::WriteBatch* b) : batch(b) {}
};

struct ndb_snapshot_holder {
    leveldb::Snapshot* snapshot = nullptr;
    ndb_snapshot_holder() = default;
    explicit ndb_snapshot_holder(leveldb::Snapshot* s) : snapshot(s) {}
};

struct ndb_iterator_holder {
    leveldb::Iterator* it = nullptr;
    ndb_iterator_holder() = default;
    explicit ndb_iterator_holder(leveldb::Iterator* i) : it(i) {}
};

#endif //LEVELDB_JNI_NDBHOLDER_H
