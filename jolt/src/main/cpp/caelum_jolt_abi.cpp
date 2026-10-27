// SPDX-License-Identifier: Apache-2.0
// Native compiler oracle for every transport field in caelum_jolt.h.
// A header-surface test checks that this inventory stays complete.
#include <Jolt/Jolt.h>
#include <Jolt/Physics/Body/BodyID.h>
#include <Jolt/Physics/Body/MotionType.h>
#include <Jolt/Physics/Body/MotionQuality.h>
#include <Jolt/Physics/EActivation.h>
#include <Jolt/Physics/EPhysicsUpdateError.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include "caelum_jolt.h"
#include <cstddef>
#include <cstdint>
#include <type_traits>
namespace {
struct Field { const char *name; uint64_t offset; };
struct Type { const char *name; uint64_t size; uint64_t alignment; const Field *fields; uint32_t count; };
#define FIELD(T, F) {#F, offsetof(T, F)}
#define TYPE(T) {#T, sizeof(T), alignof(T), T##_fields, uint32_t(sizeof(T##_fields) / sizeof(Field))}
static_assert(std::is_standard_layout_v<CJVec3> && std::is_trivially_copyable_v<CJVec3>);
const Field CJVec3_fields[] = {FIELD(CJVec3, x), FIELD(CJVec3, y), FIELD(CJVec3, z)};
static_assert(std::is_standard_layout_v<CJRVec3> && std::is_trivially_copyable_v<CJRVec3>);
const Field CJRVec3_fields[] = {FIELD(CJRVec3, x), FIELD(CJRVec3, y), FIELD(CJRVec3, z)};
static_assert(std::is_standard_layout_v<CJQuat> && std::is_trivially_copyable_v<CJQuat>);
const Field CJQuat_fields[] = {FIELD(CJQuat, x), FIELD(CJQuat, y), FIELD(CJQuat, z), FIELD(CJQuat, w)};
static_assert(std::is_standard_layout_v<CJWorldConfig> && std::is_trivially_copyable_v<CJWorldConfig>);
const Field CJWorldConfig_fields[] = {FIELD(CJWorldConfig, maxBodies), FIELD(CJWorldConfig, bodyMutexCount), FIELD(CJWorldConfig, maxBodyPairs), FIELD(CJWorldConfig, maxContactConstraints), FIELD(CJWorldConfig, objectLayerCount), FIELD(CJWorldConfig, broadPhaseLayerCount), FIELD(CJWorldConfig, gravity)};
static_assert(std::is_standard_layout_v<CJBodyCreateInfo> && std::is_trivially_copyable_v<CJBodyCreateInfo>);
const Field CJBodyCreateInfo_fields[] = {FIELD(CJBodyCreateInfo, position), FIELD(CJBodyCreateInfo, rotation), FIELD(CJBodyCreateInfo, linearVelocity), FIELD(CJBodyCreateInfo, angularVelocity), FIELD(CJBodyCreateInfo, userData), FIELD(CJBodyCreateInfo, motionType), FIELD(CJBodyCreateInfo, motionQuality), FIELD(CJBodyCreateInfo, objectLayer), FIELD(CJBodyCreateInfo, isSensor), FIELD(CJBodyCreateInfo, allowSleeping), FIELD(CJBodyCreateInfo, friction), FIELD(CJBodyCreateInfo, restitution), FIELD(CJBodyCreateInfo, linearDamping), FIELD(CJBodyCreateInfo, angularDamping), FIELD(CJBodyCreateInfo, maxLinearVelocity), FIELD(CJBodyCreateInfo, maxAngularVelocity), FIELD(CJBodyCreateInfo, gravityFactor)};
static_assert(std::is_standard_layout_v<CJBodyTransform> && std::is_trivially_copyable_v<CJBodyTransform>);
const Field CJBodyTransform_fields[] = {FIELD(CJBodyTransform, position), FIELD(CJBodyTransform, rotation), FIELD(CJBodyTransform, linearVelocity), FIELD(CJBodyTransform, angularVelocity), FIELD(CJBodyTransform, userData), FIELD(CJBodyTransform, bodyId), FIELD(CJBodyTransform, motionType), FIELD(CJBodyTransform, objectLayer), FIELD(CJBodyTransform, isActive), FIELD(CJBodyTransform, isSensor)};
static_assert(std::is_standard_layout_v<CJRayCast> && std::is_trivially_copyable_v<CJRayCast>);
const Field CJRayCast_fields[] = {FIELD(CJRayCast, origin), FIELD(CJRayCast, direction), FIELD(CJRayCast, objectLayer), FIELD(CJRayCast, reserved)};
static_assert(std::is_standard_layout_v<CJRayCastResult> && std::is_trivially_copyable_v<CJRayCastResult>);
const Field CJRayCastResult_fields[] = {FIELD(CJRayCastResult, bodyId), FIELD(CJRayCastResult, subShapeId), FIELD(CJRayCastResult, fraction), FIELD(CJRayCastResult, hit), FIELD(CJRayCastResult, reserved), FIELD(CJRayCastResult, position), FIELD(CJRayCastResult, normal)};
static_assert(std::is_standard_layout_v<CJContactValidation> && std::is_trivially_copyable_v<CJContactValidation>);
const Field CJContactValidation_fields[] = {FIELD(CJContactValidation, body1), FIELD(CJContactValidation, body2), FIELD(CJContactValidation, baseOffset), FIELD(CJContactValidation, point1), FIELD(CJContactValidation, point2), FIELD(CJContactValidation, penetrationAxis), FIELD(CJContactValidation, penetrationDepth), FIELD(CJContactValidation, subShapeId1), FIELD(CJContactValidation, subShapeId2)};
static_assert(std::is_standard_layout_v<CJContactEvent> && std::is_trivially_copyable_v<CJContactEvent>);
const Field CJContactEvent_fields[] = {FIELD(CJContactEvent, body1), FIELD(CJContactEvent, body2), FIELD(CJContactEvent, baseOffset), FIELD(CJContactEvent, normal), FIELD(CJContactEvent, penetrationDepth), FIELD(CJContactEvent, subShapeId1), FIELD(CJContactEvent, subShapeId2), FIELD(CJContactEvent, pointCount), FIELD(CJContactEvent, kind), FIELD(CJContactEvent, points1), FIELD(CJContactEvent, points2)};
static_assert(std::is_standard_layout_v<CJContactSettings> && std::is_trivially_copyable_v<CJContactSettings>);
const Field CJContactSettings_fields[] = {FIELD(CJContactSettings, friction), FIELD(CJContactSettings, restitution), FIELD(CJContactSettings, inverseMassScale1), FIELD(CJContactSettings, inverseInertiaScale1), FIELD(CJContactSettings, inverseMassScale2), FIELD(CJContactSettings, inverseInertiaScale2), FIELD(CJContactSettings, isSensor), FIELD(CJContactSettings, reserved), FIELD(CJContactSettings, relativeLinearSurfaceVelocity), FIELD(CJContactSettings, relativeAngularSurfaceVelocity)};
static_assert(std::is_standard_layout_v<CJCallbacks> && std::is_trivially_copyable_v<CJCallbacks>);
const Field CJCallbacks_fields[] = {FIELD(CJCallbacks, context), FIELD(CJCallbacks, validate), FIELD(CJCallbacks, contact), FIELD(CJCallbacks, removed), FIELD(CJCallbacks, activation)};
const Type types[] = {TYPE(CJVec3), TYPE(CJRVec3), TYPE(CJQuat), TYPE(CJWorldConfig), TYPE(CJBodyCreateInfo), TYPE(CJBodyTransform), TYPE(CJRayCast), TYPE(CJRayCastResult), TYPE(CJContactValidation), TYPE(CJContactEvent), TYPE(CJContactSettings), TYPE(CJCallbacks)};
const Type *get(uint32_t type) { return type < sizeof(types) / sizeof(Type) ? &types[type] : nullptr; }
struct Constant { const char *name; int64_t value; };
#define CONSTANT(N) {#N, N}
// The semantic enums come from the pinned C++ declarations, not a second copy
// of bridge numbers. Kotlin tests compare these values to the generated enums.
const Constant constants[] = {
    CONSTANT(CJ_OK), CONSTANT(CJ_INVALID_ARGUMENT), CONSTANT(CJ_NOT_INITIALIZED),
    CONSTANT(CJ_RESOURCE_BUSY), CONSTANT(CJ_OUT_OF_MEMORY), CONSTANT(CJ_SHAPE_ERROR),
    CONSTANT(CJ_INVALID_BODY), CONSTANT(CJ_CAPACITY_EXCEEDED), CONSTANT(CJ_REENTRANT_CALL), CONSTANT(CJ_INTERNAL_ERROR),
    {"CJ_STATIC", int64_t(JPH::EMotionType::Static)},
    {"CJ_KINEMATIC", int64_t(JPH::EMotionType::Kinematic)},
    {"CJ_DYNAMIC", int64_t(JPH::EMotionType::Dynamic)},
    // Bridge activation is a 0/1 choice, explicitly converted in activation().
    CONSTANT(CJ_DONT_ACTIVATE), CONSTANT(CJ_ACTIVATE),
    {"JPH_DONT_ACTIVATE", int64_t(JPH::EActivation::DontActivate)},
    {"JPH_ACTIVATE", int64_t(JPH::EActivation::Activate)},
    {"CJ_DISCRETE", int64_t(JPH::EMotionQuality::Discrete)},
    {"CJ_LINEAR_CAST", int64_t(JPH::EMotionQuality::LinearCast)},
    {"CJ_ACCEPT_ALL_CONTACTS", int64_t(JPH::ValidateResult::AcceptAllContactsForThisBodyPair)},
    {"CJ_ACCEPT_CONTACT", int64_t(JPH::ValidateResult::AcceptContact)},
    {"CJ_REJECT_CONTACT", int64_t(JPH::ValidateResult::RejectContact)},
    {"CJ_REJECT_ALL_CONTACTS", int64_t(JPH::ValidateResult::RejectAllContactsForThisBodyPair)},
    CONSTANT(CJ_CONTACT_ADDED), CONSTANT(CJ_CONTACT_PERSISTED),
    {"BODY_ID_INVALID", JPH::BodyID::cInvalidBodyID},
    {"MANIFOLD_CACHE_FULL", int64_t(JPH::EPhysicsUpdateError::ManifoldCacheFull)},
    {"BODY_PAIR_CACHE_FULL", int64_t(JPH::EPhysicsUpdateError::BodyPairCacheFull)},
    {"CONTACT_CONSTRAINTS_FULL", int64_t(JPH::EPhysicsUpdateError::ContactConstraintsFull)},
};
}
uint32_t cj_abi_type_count() { return sizeof(types) / sizeof(Type); }
const char *cj_abi_type_name(uint32_t type) { const auto *t = get(type); return t ? t->name : ""; }
uint64_t cj_abi_size(uint32_t type) { const auto *t = get(type); return t ? t->size : UINT64_MAX; }
uint64_t cj_abi_alignment(uint32_t type) { const auto *t = get(type); return t ? t->alignment : UINT64_MAX; }
uint32_t cj_abi_field_count(uint32_t type) { const auto *t = get(type); return t ? t->count : 0; }
const char *cj_abi_field_name(uint32_t type, uint32_t field) {
    const auto *t = get(type); return t && field < t->count ? t->fields[field].name : "";
}
uint64_t cj_abi_field_offset(uint32_t type, uint32_t field) {
    const auto *t = get(type); return t && field < t->count ? t->fields[field].offset : UINT64_MAX;
}
uint32_t cj_abi_constant_count() { return sizeof(constants) / sizeof(Constant); }
const char *cj_abi_constant_name(uint32_t index) { return index < cj_abi_constant_count() ? constants[index].name : ""; }
int64_t cj_abi_constant_value(uint32_t index) { return index < cj_abi_constant_count() ? constants[index].value : INT64_MIN; }
