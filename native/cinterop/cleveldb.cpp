#include "cleveldb.h"
#include "ndb_holder.h"
#include "leveldb/cache.h"
#include "leveldb/db.h"
#include "leveldb/write_batch.h"
#include "leveldb/iterator.h"
#include "leveldb/slice.h"

extern "C" {

static thread_local std::string last_leveldb_error;

static cleveldb_status status_from_leveldb(const leveldb::Status& status) {
    if (status.ok()) {
        return LDB_OK;
    } else if (status.IsCorruption()) {
        last_leveldb_error = status.ToString();
        return LDB_CORRUPTED_DATA;
    } else if (status.IsInvalidArgument()) {
        return LDB_INVALID_ARGUMENT;
    } else if (status.IsIOError()) {
        last_leveldb_error = status.ToString();
        return LDB_IO_ERROR;
    } else if (status.IsNotFound()) {
        return LDB_NOT_FOUND;
    } else {
        return LDB_UNKNOWN_ERROR;
    }
}

void leveldb_last_error(char** error, size_t* error_len) {
    // Validate out parameters
    if (!error || !error_len) return;

    const std::string msg = last_leveldb_error;
    last_leveldb_error.clear();

    size_t msg_len = msg.size();

    *error = nullptr;
    *error_len = 0;

    char* buf = static_cast<char*>(malloc(msg_len + 1));
    if (!buf) {
        return;
    }

    if (msg_len > 0) {
        memcpy(buf, msg.data(), msg_len);
    }
    buf[msg_len] = '\0';

    *error = buf;
    *error_len = msg_len;

    // caller is responsible for freeing the returned buffer with leveldb_free_string()
}

cleveldb_status leveldb_open(
    bool create_if_missing,
    bool paranoid_checks,
    size_t cache_size,
    size_t block_size,
    size_t write_buffer_size,
    const char* db_path,
    ndb_holder** out
) {
    //
    leveldb::DB* db;

    // auto* logger = new AndroidLogger();
    leveldb::Cache* cache = nullptr;

    if (cache_size != 0) {
        cache = leveldb::NewLRUCache(cache_size);
    }

    leveldb::Options options;
    options.create_if_missing = create_if_missing;
    options.paranoid_checks = paranoid_checks;
    // options.info_log = logger;

    if (cache != nullptr) {
        options.block_cache = cache;
    }

    if (block_size != 0) {
        options.block_size = block_size;
    }

    if (write_buffer_size != 0) {
        options.write_buffer_size = write_buffer_size;
    }

    leveldb::Status status = leveldb::DB::Open(options, db_path, &db);

    if (status.ok()) {
        // no leak if nclose is called
        *out = new ndb_holder{db, nullptr /* TODO !! */, cache};
        return LDB_OK;
    }

    delete cache;

    return status_from_leveldb(status);
}

void leveldb_close(ndb_holder* holder) {
    if (holder != nullptr) {
        delete holder->db;
        delete holder->cache;
        delete holder->logger;
        delete holder;
    }
}

cleveldb_status leveldb_put(ndb_holder* holder, const uint8_t* key, size_t key_len, const uint8_t* value, size_t value_len, bool sync) {
    if (!holder) return LDB_INVALID_POINTER;

    leveldb::DB* db = holder->db;

    leveldb::WriteOptions writeOptions;
    writeOptions.sync = sync;

    const leveldb::Slice keySlice(reinterpret_cast<const char *>(key), key_len);
    const leveldb::Slice valueSlice(reinterpret_cast<const char *>(value), value_len);

    leveldb::Status status = db->Put(writeOptions, keySlice, valueSlice);
    return status_from_leveldb(status);
}

cleveldb_status leveldb_write(ndb_holder* holder, ndb_batch_holder* batch_holder, bool sync) {
    if (!holder || !batch_holder) return LDB_INVALID_POINTER;
    if (!holder->db) return LDB_INVALID_POINTER;
    if (!batch_holder->batch) return LDB_INVALID_POINTER;

    leveldb::WriteOptions options;
    options.sync = sync;

    const leveldb::Status status = holder->db->Write(options, batch_holder->batch);

    return status_from_leveldb(status);
}

void leveldb_free_buffer(uint8_t* buffer) { free(buffer); }
void leveldb_free_string(char* buffer) { free(buffer); }

cleveldb_status leveldb_get(
    ndb_holder* holder,
    const uint8_t* key,
    size_t key_len,
    ndb_snapshot_holder* snapshot_holder,
    uint8_t** out,
    size_t* out_len
) {
    if (!holder) return LDB_INVALID_POINTER;
    if (!holder->db) return LDB_INVALID_POINTER;

    leveldb::DB* db = holder->db;

    leveldb::ReadOptions readOptions;
    readOptions.snapshot = snapshot_holder ? snapshot_holder->snapshot : nullptr;

    const leveldb::Slice keySlice(reinterpret_cast<const char *>(key), key_len);

    std::string value;
    const leveldb::Status status = db->Get(readOptions, keySlice, &value);

    if (!status.ok()) {
        *out = nullptr;
        *out_len = 0;
        return status_from_leveldb(status);
    }

    *out_len = value.size();
    if (value.empty()) {
        *out = nullptr;
        return LDB_OK;
    }

    // new memory for caller
    auto* buffer = static_cast<uint8_t *>(malloc(value.size()));
    if (!buffer) {
        *out = nullptr;
        *out_len = 0;
        return LDB_MEMORY_ERROR;
    }

    memcpy(buffer, value.data(), value.size());
    *out = buffer;

    return LDB_OK;
}

cleveldb_status leveldb_delete(
    ndb_holder* holder,
    const uint8_t* key,
    size_t key_len,
    bool sync
) {
    if (!holder) return LDB_INVALID_POINTER;
    if (!holder->db) return LDB_INVALID_POINTER;

    leveldb::DB* db = holder->db;

    const leveldb::Slice keySlice(reinterpret_cast<const char *>(key), key_len);

    leveldb::WriteOptions writeOptions;
    writeOptions.sync = sync;

    const leveldb::Status status = db->Delete(writeOptions, keySlice);

    return status_from_leveldb(status);
}

cleveldb_status leveldb_get_property(
    ndb_holder* holder,
    const uint8_t* key,
    size_t key_len,
    uint8_t** out,
    size_t* out_len
) {

    if (!holder) return LDB_INVALID_POINTER;
    if (!holder->db) return LDB_INVALID_POINTER;

    leveldb::DB* db = holder->db;

    leveldb::Slice keySlice(reinterpret_cast<const char *>(key), key_len);

    std::string value;

    const bool ok = db->GetProperty(keySlice, &value);

    if (!ok) return LDB_NOT_FOUND;

    *out_len = value.size();
    if (value.empty()) {
        *out = nullptr;
        return LDB_OK;
    }

    auto* buffer = static_cast<uint8_t *>(malloc(value.size()));
    if (!buffer) {
        *out = nullptr;
        *out_len = 0;
        return LDB_MEMORY_ERROR;
    }

    memcpy(buffer, value.data(), value.size());
    *out = buffer;
    return LDB_OK;
}

// Batch helpers (implementations)
ndb_batch_holder* leveldb_batch_create() {
    auto* h = new ndb_batch_holder();
    h->batch = new leveldb::WriteBatch();
    return h;
}

void leveldb_batch_put(ndb_batch_holder* batch, const uint8_t* key, size_t key_len, const uint8_t* value, size_t value_len) {
    if (!batch || !batch->batch) return;
    const leveldb::Slice keySlice(reinterpret_cast<const char *>(key), key_len);
    const leveldb::Slice valueSlice(reinterpret_cast<const char *>(value), value_len);
    batch->batch->Put(keySlice, valueSlice);
}

void leveldb_batch_delete(ndb_batch_holder* batch, const uint8_t* key, size_t key_len) {
    if (!batch || !batch->batch) return;
    const leveldb::Slice keySlice(reinterpret_cast<const char *>(key), key_len);
    batch->batch->Delete(keySlice);
}

void leveldb_batch_close(ndb_batch_holder* batch) {
    if (!batch) return;
    delete batch->batch;
    delete batch;
}

cleveldb_status leveldb_destroy(const char* path) {
    const leveldb::Status status = leveldb::DestroyDB(path, leveldb::Options());
    return status_from_leveldb(status);
}

cleveldb_status leveldb_repair(const char* path) {
    const leveldb::Status status = leveldb::RepairDB(path, leveldb::Options());
    return status_from_leveldb(status);
}

// Iterator / snapshot helpers (holders only)
ndb_iterator_holder* leveldb_iterate(ndb_holder* holder, bool fill_cache, ndb_snapshot_holder* snapshot_holder) {
    if (!holder) return nullptr;

    leveldb::DB* db = holder->db;

    leveldb::ReadOptions options;
    options.fill_cache = fill_cache;
    options.snapshot = snapshot_holder ? snapshot_holder->snapshot : nullptr;

    leveldb::Iterator* it = db->NewIterator(options);
    auto* h = new ndb_iterator_holder();
    h->it = it;
    return h;
}

void leveldb_iter_close(ndb_iterator_holder* it) {
    if (!it) return;
    if (it->it) {
        if (leveldb::Status status = it->it->status(); !status.ok()) {
            // Could log status here if desired
        }
        delete it->it;
    }
    delete it;
}

int leveldb_iter_valid(ndb_iterator_holder* it) {
    if (!it || !it->it) return 0;
    return it->it->Valid() ? 1 : 0;
}

void leveldb_iter_seek(ndb_iterator_holder* it, const uint8_t* key, size_t key_len) {
    if (!it || !it->it) return;
    const leveldb::Slice keySlice(reinterpret_cast<const char *>(key), key_len);
    it->it->Seek(keySlice);
}

void leveldb_iter_seek_to_first(ndb_iterator_holder* it) {
    if (!it || !it->it) return;
    it->it->SeekToFirst();
}

void leveldb_iter_seek_to_last(ndb_iterator_holder* it) {
    if (!it || !it->it) return;
    it->it->SeekToLast();
}

void leveldb_iter_next(ndb_iterator_holder* it) {
    if (!it || !it->it) return;
    it->it->Next();
}

void leveldb_iter_prev(ndb_iterator_holder* it) {
    if (!it || !it->it) return;
    it->it->Prev();
}

cleveldb_status leveldb_iter_key(ndb_iterator_holder* it, uint8_t** out, size_t* out_len) {
    if (!it || !it->it) return LDB_INVALID_POINTER;
    if (!it->it->Valid()) {
        *out = nullptr;
        *out_len = 0;
        return LDB_NOT_FOUND;
    }

    const leveldb::Slice key = it->it->key();
    *out_len = key.size();
    if (key.empty()) {
        *out = nullptr;
        return LDB_OK;
    }

    auto* buffer = static_cast<uint8_t *>(malloc(key.size()));
    if (!buffer) {
        *out = nullptr;
        *out_len = 0;
        return LDB_MEMORY_ERROR;
    }

    memcpy(buffer, key.data(), key.size());
    *out = buffer;
    return LDB_OK;
}

cleveldb_status leveldb_iter_value(ndb_iterator_holder* it, uint8_t** out, size_t* out_len) {
    if (!it || !it->it) return LDB_INVALID_POINTER;
    if (!it->it->Valid()) {
        *out = nullptr;
        *out_len = 0;
        return LDB_NOT_FOUND;
    }

    const leveldb::Slice value = it->it->value();
    *out_len = value.size();
    if (value.empty()) {
        *out = nullptr;
        return LDB_OK;
    }

    auto* buffer = static_cast<uint8_t *>(malloc(value.size()));
    if (!buffer) {
        *out = nullptr;
        *out_len = 0;
        return LDB_MEMORY_ERROR;
    }

    memcpy(buffer, value.data(), value.size());
    *out = buffer;
    return LDB_OK;
}

ndb_snapshot_holder* leveldb_snapshot(ndb_holder* holder) {
    if (!holder) return nullptr;

    leveldb::DB* db = holder->db;
    auto* snap = new ndb_snapshot_holder();
    // DB::GetSnapshot returns a const Snapshot*; store it in our holder by casting away const
    snap->snapshot = const_cast<leveldb::Snapshot *>(db->GetSnapshot());
    return snap;
}

void leveldb_release_snapshot(ndb_holder* holder, ndb_snapshot_holder* snapshot_holder) {
    if (!snapshot_holder) return;
    if (holder && holder->db && snapshot_holder->snapshot) {
        holder->db->ReleaseSnapshot(snapshot_holder->snapshot);
    }
    delete snapshot_holder;
}

} // extern "C"
