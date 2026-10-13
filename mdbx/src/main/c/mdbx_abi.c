#include "mdbx.h"
#include <stddef.h>
/* Use the real upstream header for all probes. No normalized declarations here. */
LIBMDBX_API unsigned int caelum_mdbx_version_number(void) {
  return mdbx_version.major * 10000u + mdbx_version.minor * 100u + mdbx_version.patch;
}
LIBMDBX_API size_t caelum_mdbx_val_size(void) { return sizeof(MDBX_val); }
LIBMDBX_API size_t caelum_mdbx_val_alignment(void) { return _Alignof(MDBX_val); }
LIBMDBX_API size_t caelum_mdbx_val_base_offset(void) { return offsetof(MDBX_val, iov_base); }
LIBMDBX_API size_t caelum_mdbx_val_length_offset(void) { return offsetof(MDBX_val, iov_len); }
_Static_assert(sizeof(size_t) == 8, "x86_64 size_t");
_Static_assert(sizeof(intptr_t) == 8, "x86_64 intptr_t");
_Static_assert(sizeof(MDBX_env_flags_t) == 4, "env flags");
_Static_assert(sizeof(MDBX_txn_flags_t) == 4, "txn flags");
_Static_assert(sizeof(MDBX_db_flags_t) == 4, "db flags");
_Static_assert(sizeof(MDBX_put_flags_t) == 4, "put flags");
_Static_assert(sizeof(MDBX_option_t) == 4, "option");
#ifdef _WIN32
_Static_assert(sizeof(mdbx_mode_t) == 2, "Windows mode");
#else
_Static_assert(sizeof(mdbx_mode_t) == 4, "Linux mode");
#endif
/* mdbx_mode_t is uint16_t on Windows and uint32_t on Linux. A fixed-width
 * generated entry point avoids imposing either ABI on the other platform. */
LIBMDBX_API int caelum_mdbx_env_open(MDBX_env *env, const char *path, uint32_t flags, uint32_t mode) {
  return mdbx_env_open(env, path, (MDBX_env_flags_t)flags, (mdbx_mode_t)mode);
}
_Static_assert(sizeof(bool) == 1, "C bool");
_Static_assert(MDBX_RDONLY == 131072 && MDBX_CREATE == 262144 && MDBX_NOOVERWRITE == 16, "flags");
_Static_assert(MDBX_NOTFOUND == -30798 && MDBX_KEYEXIST == -30799, "errors");
_Static_assert(MDBX_opt_max_db == 0, "maxdb option");
_Static_assert(MDBX_THREAD_MISMATCH == -30416, "thread mismatch");

_Static_assert(sizeof(MDBX_val) == 16, "val size");
_Static_assert(_Alignof(MDBX_val) == 8, "val alignment");
_Static_assert(offsetof(MDBX_val, iov_base) == 0, "val base offset");
_Static_assert(offsetof(MDBX_val, iov_len) == 8, "val length offset");
_Static_assert(MDBX_BUSY == -30778, "busy");
_Static_assert(sizeof(MDBX_cursor_op) == 4, "cursor operation enum");
_Static_assert(MDBX_FIRST == 0 && MDBX_NEXT == 8 && MDBX_SET_RANGE == 17, "cursor operations");

LIBMDBX_API int caelum_mdbx_usage(const MDBX_txn *txn, uint64_t *used_bytes,
                                 uint64_t *file_bytes, uint32_t *page_size) {
  MDBX_txn_info info;
  MDBX_stat stat;
  int rc = mdbx_txn_info(txn, &info, false);
  if (rc != MDBX_SUCCESS) return rc;
  rc = mdbx_env_stat_ex(NULL, txn, &stat, sizeof(stat));
  if (rc != MDBX_SUCCESS) return rc;
  *used_bytes = info.txn_space_used;
  *file_bytes = info.txn_space_limit_soft;
  *page_size = stat.ms_psize;
  return MDBX_SUCCESS;
}
