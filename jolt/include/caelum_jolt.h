/* SPDX-License-Identifier: Apache-2.0
 * Caelum Jolt bridge ABI 1. This header is both the native ABI and codegen input.
 * Only the CJ* transport structs below have a public layout. Opaque objects must
 * never be allocated, copied or freed by callers. All functions use the C ABI.
 * Raw callers must serialize destruction with every use of an object. World
 * operations serialize internally; callbacks may not reenter any world API.
 */
#ifndef CAELUM_JOLT_H
#define CAELUM_JOLT_H
#include <stdint.h>

#if defined(CJ_CODEGEN)
#define CJ_API
#elif defined(_WIN32)
#define CJ_API __declspec(dllexport)
#else
#define CJ_API __attribute__((visibility("default")))
#endif
#ifdef __cplusplus
extern "C" {
#endif

enum CJStatus {
    CJ_OK = 0,
    CJ_INVALID_ARGUMENT = 1,
    CJ_NOT_INITIALIZED = 2,
    CJ_RESOURCE_BUSY = 3,
    CJ_OUT_OF_MEMORY = 4,
    CJ_SHAPE_ERROR = 5,
    CJ_INVALID_BODY = 6,
    CJ_CAPACITY_EXCEEDED = 7,
    CJ_REENTRANT_CALL = 8,
    CJ_INTERNAL_ERROR = 9
};
enum CJMotionType { CJ_STATIC = 0, CJ_KINEMATIC = 1, CJ_DYNAMIC = 2 };
enum CJActivation { CJ_DONT_ACTIVATE = 0, CJ_ACTIVATE = 1 };
enum CJMotionQuality { CJ_DISCRETE = 0, CJ_LINEAR_CAST = 1 };
enum CJValidateResult {
    CJ_ACCEPT_ALL_CONTACTS = 0, CJ_ACCEPT_CONTACT = 1,
    CJ_REJECT_CONTACT = 2, CJ_REJECT_ALL_CONTACTS = 3
};
enum CJContactKind { CJ_CONTACT_ADDED = 0, CJ_CONTACT_PERSISTED = 1 };

struct CJWorld;
struct CJShape;
struct CJTempAllocator;
struct CJJobSystem;
struct CJBodyLock;

struct CJVec3 { float x; float y; float z; };
struct CJRVec3 { double x; double y; double z; };
struct CJQuat { float x; float y; float z; float w; };

/* Layer data is copied by cj_world_create. Mapping count = objectLayerCount;
 * matrix count = objectLayerCount squared, row major, symmetric 0/1 entries.
 */
struct CJWorldConfig {
    uint32_t maxBodies;
    uint32_t bodyMutexCount;
    uint32_t maxBodyPairs;
    uint32_t maxContactConstraints;
    uint32_t objectLayerCount;
    uint32_t broadPhaseLayerCount;
    struct CJVec3 gravity;
};

/* Contains no owning references. Shape is passed separately to createBody and
 * borrowed for that call. The new body retains its own native shape reference.
 */
struct CJBodyCreateInfo {
    struct CJRVec3 position;
    struct CJQuat rotation;
    struct CJVec3 linearVelocity;
    struct CJVec3 angularVelocity;
    uint64_t userData;
    int32_t motionType;
    int32_t motionQuality;
    uint16_t objectLayer;
    uint8_t isSensor;
    uint8_t allowSleeping;
    float friction;
    float restitution;
    float linearDamping;
    float angularDamping;
    float maxLinearVelocity;
    float maxAngularVelocity;
    float gravityFactor;
};

struct CJBodyTransform {
    struct CJRVec3 position;
    struct CJQuat rotation;
    struct CJVec3 linearVelocity;
    struct CJVec3 angularVelocity;
    uint64_t userData;
    uint32_t bodyId;
    int32_t motionType;
    uint16_t objectLayer;
    uint8_t isActive;
    uint8_t isSensor;
};

struct CJRayCast {
    struct CJRVec3 origin;
    struct CJVec3 direction;
    uint16_t objectLayer;
    uint16_t reserved;
};
struct CJRayCastResult {
    uint32_t bodyId;
    uint32_t subShapeId;
    float fraction;
    uint8_t hit;
    uint8_t reserved[3];
    struct CJRVec3 position;
    struct CJVec3 normal;
};

/* Callback snapshots are borrowed only until the callback returns. */
struct CJContactValidation {
    struct CJBodyTransform body1;
    struct CJBodyTransform body2;
    struct CJRVec3 baseOffset;
    struct CJVec3 point1;
    struct CJVec3 point2;
    struct CJVec3 penetrationAxis;
    float penetrationDepth;
    uint32_t subShapeId1;
    uint32_t subShapeId2;
};
struct CJContactEvent {
    struct CJBodyTransform body1;
    struct CJBodyTransform body2;
    struct CJRVec3 baseOffset;
    struct CJVec3 normal;
    float penetrationDepth;
    uint32_t subShapeId1;
    uint32_t subShapeId2;
    uint32_t pointCount;
    int32_t kind;
    struct CJVec3 points1[64];
    struct CJVec3 points2[64];
};
struct CJContactSettings {
    float friction;
    float restitution;
    float inverseMassScale1;
    float inverseInertiaScale1;
    float inverseMassScale2;
    float inverseInertiaScale2;
    uint8_t isSensor;
    uint8_t reserved[3];
    struct CJVec3 relativeLinearSurfaceVelocity;
    struct CJVec3 relativeAngularSurfaceVelocity;
};

typedef int32_t (*CJValidateCallback)(void *context, const struct CJContactValidation *event);
/* Return 0 to commit settings; nonzero discards all edits (e.g. JVM exception). */
typedef int32_t (*CJContactCallback)(void *context, const struct CJContactEvent *event, struct CJContactSettings *settings);
typedef void (*CJRemovedCallback)(void *context, uint32_t body1, uint32_t subShape1, uint32_t body2, uint32_t subShape2);
typedef void (*CJActivationCallback)(void *context, uint32_t bodyId, uint64_t userData, uint8_t active);
struct CJCallbacks {
    void *context;
    CJValidateCallback validate;
    CJContactCallback contact;
    CJRemovedCallback removed;
    CJActivationCallback activation;
};

/* Bootstrap exports never initialize or depend on the Jolt runtime. */
CJ_API uint32_t cj_abi_version(void);
CJ_API uint32_t cj_precision_bits(void);
CJ_API uint32_t cj_object_layer_bits(void);
CJ_API const char *cj_build_id(void);
CJ_API const char *cj_last_error(void); /* TLS; copy before the next fallible call. */
CJ_API int32_t cj_runtime_acquire(void);
CJ_API int32_t cj_runtime_release(void); /* Last lease refuses live resources. */
CJ_API uint64_t cj_live_resources(void);
/* Cumulative Jolt allocator requests since library load, not live bytes/RSS.
 * Excludes bridge/JVM/system allocations and scratch suballocations. */
CJ_API uint64_t cj_allocation_calls(void);
CJ_API uint64_t cj_allocation_bytes_requested(void);

CJ_API int32_t cj_world_config_init(struct CJWorldConfig *out);
CJ_API int32_t cj_body_create_info_init(struct CJBodyCreateInfo *out);
CJ_API int32_t cj_temp_allocator_create(uint32_t bytes, struct CJTempAllocator **out);
CJ_API int32_t cj_temp_allocator_destroy(struct CJTempAllocator *allocator);
/* workers = 0 selects single threaded; 1..256 selects native thread pool. */
CJ_API int32_t cj_job_system_create(uint32_t workers, struct CJJobSystem **out);
CJ_API int32_t cj_job_system_destroy(struct CJJobSystem *jobs);

CJ_API int32_t cj_world_create(const struct CJWorldConfig *config,
    const uint8_t *objectToBroadPhase, uint32_t mappingCount,
    const uint8_t *collisionMatrix, uint32_t matrixCount, struct CJWorld **out);
CJ_API int32_t cj_world_destroy(struct CJWorld *world);
CJ_API int32_t cj_world_update(struct CJWorld *world, float deltaTime, uint32_t collisionSteps,
    struct CJTempAllocator *allocator, struct CJJobSystem *jobs, uint32_t *updateErrors);
CJ_API int32_t cj_world_optimize_broad_phase(struct CJWorld *world);
CJ_API int32_t cj_world_set_gravity(struct CJWorld *world, const struct CJVec3 *gravity);
CJ_API int32_t cj_world_get_gravity(struct CJWorld *world, struct CJVec3 *out);
/* Copies table. Null removes listeners. Returning guarantees the old callbacks
 * are no longer in flight; context and stubs may then be released. */
CJ_API int32_t cj_world_set_callbacks(struct CJWorld *world, const struct CJCallbacks *callbacks);

/* Each successful create/retain returns exactly one owned reference. */
CJ_API int32_t cj_shape_box(const struct CJVec3 *halfExtent, float convexRadius, struct CJShape **out);
CJ_API int32_t cj_shape_sphere(float radius, struct CJShape **out);
CJ_API int32_t cj_shape_capsule(float halfHeight, float radius, struct CJShape **out);
CJ_API int32_t cj_shape_retain(struct CJShape *shape, struct CJShape **out);
CJ_API int32_t cj_shape_release(struct CJShape *shape);

CJ_API int32_t cj_body_create(struct CJWorld *world, const struct CJBodyCreateInfo *info,
    const struct CJShape *shape, uint8_t add, int32_t activation, uint32_t *outId);
CJ_API int32_t cj_body_add(struct CJWorld *world, uint32_t id, int32_t activation);
CJ_API int32_t cj_body_remove(struct CJWorld *world, uint32_t id);
CJ_API int32_t cj_body_destroy(struct CJWorld *world, uint32_t id);
CJ_API int32_t cj_body_get_transform(struct CJWorld *world, uint32_t id, struct CJBodyTransform *out);
CJ_API int32_t cj_body_get_transforms(struct CJWorld *world, const uint32_t *ids, uint32_t count,
    struct CJBodyTransform *out, uint32_t capacity);
CJ_API int32_t cj_body_set_transform(struct CJWorld *world, uint32_t id, const struct CJRVec3 *position,
    const struct CJQuat *rotation, int32_t activation);
CJ_API int32_t cj_body_set_velocity(struct CJWorld *world, uint32_t id,
    const struct CJVec3 *linear, const struct CJVec3 *angular);
CJ_API int32_t cj_body_add_force(struct CJWorld *world, uint32_t id, const struct CJVec3 *force);
CJ_API int32_t cj_body_add_impulse(struct CJWorld *world, uint32_t id, const struct CJVec3 *impulse);
CJ_API int32_t cj_body_move_kinematic(struct CJWorld *world, uint32_t id,
    const struct CJRVec3 *position, const struct CJQuat *rotation, float deltaTime);
CJ_API int32_t cj_body_set_active(struct CJWorld *world, uint32_t id, uint8_t active);
CJ_API int32_t cj_body_set_user_data(struct CJWorld *world, uint32_t id, uint64_t userData);

/* Lock lives on its creating thread and excludes all other bridge world calls.
 * Only cj_locked_body_* and cj_body_unlock may be called inside the lock scope.
 * Write velocity activates the body after unlocking. Never retain a lock view.
 */
CJ_API int32_t cj_body_lock(struct CJWorld *world, uint32_t id, uint8_t write, struct CJBodyLock **out);
CJ_API int32_t cj_locked_body_get_transform(struct CJBodyLock *lock, struct CJBodyTransform *out);
CJ_API int32_t cj_locked_body_set_velocity(struct CJBodyLock *lock, const struct CJVec3 *linear, const struct CJVec3 *angular);
CJ_API int32_t cj_body_unlock(struct CJBodyLock *lock);
CJ_API int32_t cj_world_cast_ray(struct CJWorld *world, const struct CJRayCast *ray, struct CJRayCastResult *out);

/* Complete ABI inventory. Names and offsets describe this C header, not Jolt's
 * internal C++ classes. UINT64_MAX denotes an invalid type or field index.
 */
CJ_API uint32_t cj_abi_type_count(void);
CJ_API const char *cj_abi_type_name(uint32_t type);
CJ_API uint64_t cj_abi_size(uint32_t type);
CJ_API uint64_t cj_abi_alignment(uint32_t type);
CJ_API uint32_t cj_abi_field_count(uint32_t type);
CJ_API const char *cj_abi_field_name(uint32_t type, uint32_t field);
CJ_API uint64_t cj_abi_field_offset(uint32_t type, uint32_t field);
CJ_API uint32_t cj_abi_constant_count(void);
CJ_API const char *cj_abi_constant_name(uint32_t index);
CJ_API int64_t cj_abi_constant_value(uint32_t index); /* INT64_MIN for invalid index. */
CJ_API uint32_t cj_body_id_invalid(void);
CJ_API int32_t cj_probe_roundtrip(const struct CJBodyCreateInfo *in, struct CJBodyCreateInfo *out);
CJ_API int32_t cj_probe_callback(CJValidateCallback callback, void *context, int32_t *out);

#ifdef __cplusplus
}
#endif
#endif
