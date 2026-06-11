#ifndef CLEVELDB_H
#define CLEVELDB_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ndb_holder ndb_holder;
typedef struct ndb_batch_holder ndb_batch_holder;
typedef struct ndb_snapshot_holder ndb_snapshot_holder;
typedef struct ndb_iterator_holder ndb_iterator_holder;

typedef enum cleveldb_status {
    LDB_OK               = 0,
    LDB_IO_ERROR         = 1,
    LDB_CORRUPTED_DATA   = 2,
    LDB_NOT_FOUND        = 3,
    LDB_UNKNOWN_ERROR    = 4,
    LDB_NOT_SUPPORTED    = 5,
    LDB_INVALID_ARGUMENT = 6,
    LDB_MEMORY_ERROR     = 7,
    LDB_INVALID_POINTER  = 100,
} cleveldb_status;

void leveldb_last_error(char** error, size_t* error_len);

cleveldb_status leveldb_open(
    bool create_if_missing,
    bool paranoid_checks,
    size_t cache_size,
    size_t block_size,
    size_t write_buffer_size,
    const char* db_path,
    ndb_holder** out
);

void leveldb_close(ndb_holder* holder);

// Basic KV operations
cleveldb_status leveldb_put(ndb_holder* holder, const uint8_t* key, size_t key_len, const uint8_t* value, size_t value_len, bool sync);
cleveldb_status leveldb_write(ndb_holder* holder, ndb_batch_holder* batch_holder, bool sync);
cleveldb_status leveldb_get(ndb_holder* holder, const uint8_t* key, size_t key_len, ndb_snapshot_holder* snapshot_holder, uint8_t** out, size_t* out_len);
cleveldb_status leveldb_delete(ndb_holder* holder, const uint8_t* key, size_t key_len, bool sync);

// Buffer management for returned values
void leveldb_free_buffer(uint8_t* buffer);
void leveldb_free_string(char* buffer);

// Get property
cleveldb_status leveldb_get_property(ndb_holder* holder, const uint8_t* key, size_t key_len, uint8_t** out, size_t* out_len);

// Batch helpers
ndb_batch_holder* leveldb_batch_create();
void leveldb_batch_put(ndb_batch_holder* batch, const uint8_t* key, size_t key_len, const uint8_t* value, size_t value_len);
void leveldb_batch_delete(ndb_batch_holder* batch, const uint8_t* key, size_t key_len);
void leveldb_batch_close(ndb_batch_holder* batch);

// Destroy and repair database on disk. Return LDB_OK on success or an appropriate error code.
cleveldb_status leveldb_destroy(const char* path);
cleveldb_status leveldb_repair(const char* path);

// Iterator / snapshot helpers
// Returns an allocated iterator holder which must be closed with leveldb_iter_close
ndb_iterator_holder* leveldb_iterate(ndb_holder* holder, bool fill_cache, ndb_snapshot_holder* snapshot_holder);

void leveldb_iter_close(ndb_iterator_holder* it);
int leveldb_iter_valid(ndb_iterator_holder* it);
void leveldb_iter_seek(ndb_iterator_holder* it, const uint8_t* key, size_t key_len);
void leveldb_iter_seek_to_first(ndb_iterator_holder* it);
void leveldb_iter_seek_to_last(ndb_iterator_holder* it);
void leveldb_iter_next(ndb_iterator_holder* it);
void leveldb_iter_prev(ndb_iterator_holder* it);

// Retrieve key/value from current iterator position. Caller gets allocated buffer via *out (malloc) and should call leveldb_free_buffer
cleveldb_status leveldb_iter_key(ndb_iterator_holder* it, uint8_t** out, size_t* out_len);
cleveldb_status leveldb_iter_value(ndb_iterator_holder* it, uint8_t** out, size_t* out_len);

// Snapshot management: returns a newly allocated ndb_snapshot_holder which must be released
ndb_snapshot_holder* leveldb_snapshot(ndb_holder* holder);
void leveldb_release_snapshot(ndb_holder* holder, ndb_snapshot_holder* snapshot_holder);

#ifdef __cplusplus
}
#endif

#endif // CLEVELDB_H
