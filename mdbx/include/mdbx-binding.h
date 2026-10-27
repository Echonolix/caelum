/* ABI-normalized D1 subset of libmdbx 0.14.4 mdbx.h (Apache-2.0).
 * 64-bit only: size_t/ intptr_t are 64 bits on all supported targets.
 * Opaque structs remain opaque; uint8_t represents C bool at the ABI boundary.
 * See README.md for source provenance and omitted APIs. */
typedef unsigned char uint8_t;
typedef unsigned int uint32_t;
typedef unsigned long long uint64_t;
typedef long long int64_t;
typedef unsigned long long size_t;
typedef struct MDBX_env MDBX_env;
typedef struct MDBX_txn MDBX_txn;
typedef struct MDBX_cursor MDBX_cursor;
typedef struct MDBX_commit_latency MDBX_commit_latency;
typedef struct MDBX_val {
    void *iov_base;
    size_t iov_len;
} MDBX_val;
#define MDBX_SUCCESS 0
#define MDBX_NOTFOUND -30798
#define MDBX_KEYEXIST -30799
#define MDBX_RDONLY 131072
#define MDBX_CREATE 262144
#define MDBX_NOOVERWRITE 16
#define MDBX_RESERVE 65536
#define MDBX_BUSY -30778
#define MDBX_THREAD_MISMATCH -30416
int mdbx_env_create(MDBX_env **penv);
int mdbx_env_set_option(MDBX_env *env, int option, uint64_t value);
int mdbx_env_set_geometry(MDBX_env *env, int64_t size_lower, int64_t size_now, int64_t size_upper,
                          int64_t growth_step, int64_t shrink_threshold, int64_t pagesize);
int caelum_mdbx_env_open(MDBX_env *env, const char *pathname, unsigned int flags, unsigned int mode);
int mdbx_env_close_ex(MDBX_env *env, uint8_t dont_sync);
int mdbx_txn_begin_ex(MDBX_env *env, MDBX_txn *parent, unsigned int flags, MDBX_txn **txn, void *context);
int mdbx_txn_commit_ex(MDBX_txn *txn, MDBX_commit_latency *latency);
int mdbx_txn_abort_ex(MDBX_txn *txn, MDBX_commit_latency *latency);
int mdbx_dbi_open(MDBX_txn *txn, const char *name, unsigned int flags, unsigned int *dbi);
int mdbx_get(const MDBX_txn *txn, unsigned int dbi, const MDBX_val *key, MDBX_val *data);
int mdbx_put(MDBX_txn *txn, unsigned int dbi, const MDBX_val *key, MDBX_val *data, unsigned int flags);
int mdbx_del(MDBX_txn *txn, unsigned int dbi, const MDBX_val *key, const MDBX_val *data);
const char *mdbx_strerror_r(int errnum, char *buf, size_t buflen);
/* Small native probes expose otherwise-global version data and upstream ABI. */
unsigned int caelum_mdbx_version_number(void);
size_t caelum_mdbx_val_size(void);
size_t caelum_mdbx_val_alignment(void);
size_t caelum_mdbx_val_base_offset(void);
size_t caelum_mdbx_val_length_offset(void);

/* libmdbx 0.14.4 cursor enum ordinals, verified against the pinned header. */
#define MDBX_FIRST 0
#define MDBX_NEXT 8
#define MDBX_SET_RANGE 17
int mdbx_cursor_open(MDBX_txn *txn, unsigned int dbi, MDBX_cursor **cursor);
void mdbx_cursor_close(MDBX_cursor *cursor);
int mdbx_cursor_get(MDBX_cursor *cursor, MDBX_val *key, MDBX_val *data, int op);

/* Read-only upstream snapshot statistics; normalized fixed-width outputs. */
int caelum_mdbx_usage(const MDBX_txn *txn, uint64_t *used_bytes, uint64_t *file_bytes, uint32_t *page_size);
