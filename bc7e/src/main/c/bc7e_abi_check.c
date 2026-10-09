/*
 * Compile-time check that the ISPC-exported parameter struct keeps the layout the
 * Kotlin bindings (generated from include/bc7e.h) assume. Emits no code.
 */
#include <stdbool.h>
#include <stddef.h>
#include "bc7e_ispc.h"

_Static_assert(sizeof(bool) == 1, "bool must be one byte");
_Static_assert(sizeof(struct $anon0) == 20, "bc7e_opaque_settings size");
_Static_assert(offsetof(struct $anon0, m_use_mode) == 12, "bc7e_opaque_settings.m_use_mode");
_Static_assert(sizeof(struct $anon1) == 28, "bc7e_alpha_settings size");
_Static_assert(offsetof(struct $anon1, m_use_mode4) == 20, "bc7e_alpha_settings.m_use_mode4");
_Static_assert(sizeof(struct bc7e_compress_block_params) == 124, "params size");
_Static_assert(offsetof(struct bc7e_compress_block_params, m_uber_level) == 48, "params.m_uber_level");
_Static_assert(offsetof(struct bc7e_compress_block_params, m_perceptual) == 72, "params.m_perceptual");
_Static_assert(offsetof(struct bc7e_compress_block_params, m_opaque_settings) == 76, "params.m_opaque_settings");
_Static_assert(offsetof(struct bc7e_compress_block_params, m_alpha_settings) == 96, "params.m_alpha_settings");
