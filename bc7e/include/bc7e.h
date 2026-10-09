/*
 * Binding header for bc7e.ispc, used only as codegen-c input.
 *
 * It mirrors the bc7e_ispc.h that ISPC generates from src/main/ispc/bc7e.ispc, with
 * two ABI-preserving changes codegen-c needs:
 *  - bool is spelled uint8_t (1 byte, values 0 or 1).
 *  - The anonymous settings structs are named bc7e_opaque_settings and
 *    bc7e_alpha_settings.
 * src/main/c/bc7e_abi_check.c pins the ISPC layout and Bc7eTest pins the generated
 * layout to the same sizes and offsets. Update all three when bc7e.ispc changes.
 */
#include <stdint.h>

struct bc7e_opaque_settings {
    uint32_t m_max_mode13_partitions_to_try;
    uint32_t m_max_mode0_partitions_to_try;
    uint32_t m_max_mode2_partitions_to_try;
    uint8_t m_use_mode[7];
    uint8_t m_unused1;
};

struct bc7e_alpha_settings {
    uint32_t m_max_mode7_partitions_to_try;
    uint32_t m_mode67_error_weight_mul[4];
    uint8_t m_use_mode4;
    uint8_t m_use_mode5;
    uint8_t m_use_mode6;
    uint8_t m_use_mode7;
    uint8_t m_use_mode4_rotation;
    uint8_t m_use_mode5_rotation;
    uint8_t m_unused2;
    uint8_t m_unused3;
};

struct bc7e_compress_block_params {
    uint32_t m_max_partitions_mode[8];
    uint32_t m_weights[4];
    uint32_t m_uber_level;
    uint32_t m_refinement_passes;
    uint32_t m_mode4_rotation_mask;
    uint32_t m_mode4_index_mask;
    uint32_t m_mode5_rotation_mask;
    uint32_t m_uber1_mask;
    uint8_t m_perceptual;
    uint8_t m_pbit_search;
    uint8_t m_mode6_only;
    uint8_t m_unused0;
    struct bc7e_opaque_settings m_opaque_settings;
    struct bc7e_alpha_settings m_alpha_settings;
};

/* Builds the shared lookup tables. Call once before any other function. */
void bc7e_compress_block_init(void);

void bc7e_compress_block_params_init(struct bc7e_compress_block_params *p, uint8_t perceptual);
void bc7e_compress_block_params_init_ultrafast(struct bc7e_compress_block_params *p, uint8_t perceptual);
void bc7e_compress_block_params_init_veryfast(struct bc7e_compress_block_params *p, uint8_t perceptual);
void bc7e_compress_block_params_init_fast(struct bc7e_compress_block_params *p, uint8_t perceptual);
void bc7e_compress_block_params_init_basic(struct bc7e_compress_block_params *p, uint8_t perceptual);
void bc7e_compress_block_params_init_slow(struct bc7e_compress_block_params *p, uint8_t perceptual);
void bc7e_compress_block_params_init_veryslow(struct bc7e_compress_block_params *p, uint8_t perceptual);
void bc7e_compress_block_params_init_slowest(struct bc7e_compress_block_params *p, uint8_t perceptual);

/*
 * Encodes num_blocks 4x4 blocks. pPixelsRGBA holds 16 RGBA8 texels per block in row
 * order; pBlocks receives two uint64_t (one 128-bit BC7 block) per block. Thread-safe
 * after bc7e_compress_block_init.
 */
void bc7e_compress_blocks(uint32_t num_blocks, uint64_t *pBlocks, const uint32_t *pPixelsRGBA,
                          const struct bc7e_compress_block_params *pComp_params);
