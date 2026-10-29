// SPDX-License-Identifier: Apache-2.0
#include <Jolt/Jolt.h>
#include <Jolt/RegisterTypes.h>
#include <Jolt/Core/Factory.h>
#include <Jolt/Core/JobSystemSingleThreaded.h>
#include <Jolt/Core/JobSystemThreadPool.h>
#include <Jolt/Core/TempAllocator.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Body/BodyActivationListener.h>
#include <Jolt/Physics/Body/BodyLock.h>
#include <Jolt/Physics/Collision/BroadPhase/BroadPhaseLayerInterfaceTable.h>
#include <Jolt/Physics/Collision/BroadPhase/ObjectVsBroadPhaseLayerFilterTable.h>
#include <Jolt/Physics/Collision/ObjectLayerPairFilterTable.h>
#include <Jolt/Physics/Collision/Shape/BoxShape.h>
#include <Jolt/Physics/Collision/Shape/SphereShape.h>
#include <Jolt/Physics/Collision/Shape/CapsuleShape.h>
#include <Jolt/Physics/Collision/Shape/ConvexHullShape.h>
#include <Jolt/Physics/Collision/CollideShape.h>
#include <Jolt/Physics/Collision/RayCast.h>
#include <Jolt/Physics/Collision/CastResult.h>
#include "caelum_jolt.h"
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <memory>
#include <limits>
#include <mutex>
#include <thread>

static_assert(sizeof(JPH::Real) == 8 && sizeof(JPH::ObjectLayer) == 2, "ABI 1 build policy");
#if !defined(JPH_CROSS_PLATFORM_DETERMINISTIC) || (!defined(__EXCEPTIONS) && !defined(_CPPUNWIND))
#error "ABI 1 requires deterministic FP and C++ exceptions"
#endif
#if defined(JPH_USE_AVX) || defined(JPH_USE_AVX2) || defined(JPH_USE_AVX512) || defined(JPH_USE_FMADD)
#error "ABI 1 requires the baseline CPU configuration"
#endif
#if defined(JPH_USE_DX12) || defined(JPH_USE_VK) || defined(JPH_USE_MTL) || defined(JPH_USE_CPU_COMPUTE) || defined(JPH_DEBUG_RENDERER) || defined(JPH_OBJECT_STREAM)
#error "ABI 1 does not enable compute, debug renderer or object streams"
#endif

namespace {
thread_local char last_error[2048] = {};
thread_local bool in_callback = false;
thread_local CJBodyLock *active_lock = nullptr;
std::mutex runtime_mutex;
uint64_t runtime_leases = 0;
std::atomic<uint64_t> resources {0};
std::atomic<uint64_t> allocation_calls {0};
std::atomic<uint64_t> allocation_bytes {0};
JPH::AllocateFunction default_allocate = nullptr;
JPH::ReallocateFunction default_reallocate = nullptr;
JPH::AlignedAllocateFunction default_aligned_allocate = nullptr;
void track_allocation(size_t bytes) {
    allocation_calls.fetch_add(1, std::memory_order_relaxed);
    allocation_bytes.fetch_add(bytes, std::memory_order_relaxed);
}
void *tracked_allocate(size_t bytes) { track_allocation(bytes); return default_allocate(bytes); }
void *tracked_reallocate(void *block, size_t old_size, size_t new_size) {
    track_allocation(new_size); return default_reallocate(block, old_size, new_size);
}
void *tracked_aligned_allocate(size_t bytes, size_t alignment) {
    track_allocation(bytes); return default_aligned_allocate(bytes, alignment);
}

int32_t fail(int32_t code, const char *message) noexcept {
    std::snprintf(last_error, sizeof(last_error), "%s", message);
    return code;
}
template<class F> int32_t guarded(F &&f) noexcept {
    last_error[0] = '\0';
    try { return f(); }
    catch (const std::bad_alloc &) { return fail(CJ_OUT_OF_MEMORY, "native allocation failed"); }
    catch (const std::exception &e) { return fail(CJ_INTERNAL_ERROR, e.what()); }
    catch (...) { return fail(CJ_INTERNAL_ERROR, "unknown native exception"); }
}
struct CallbackScope {
    bool previous = in_callback;
    CallbackScope() { in_callback = true; }
    ~CallbackScope() { in_callback = previous; }
};
bool finite(const CJVec3 &v) { return std::isfinite(v.x) && std::isfinite(v.y) && std::isfinite(v.z); }
bool finite(const CJRVec3 &v) { return std::isfinite(v.x) && std::isfinite(v.y) && std::isfinite(v.z); }
bool rotation_valid(const CJQuat &q) {
    double norm = double(q.x) * q.x + double(q.y) * q.y + double(q.z) * q.z + double(q.w) * q.w;
    return std::isfinite(norm) && std::abs(norm - 1.0) <= 1.0e-4;
}
JPH::Vec3 vec(const CJVec3 &v) { return {v.x, v.y, v.z}; }
JPH::RVec3 pos(const CJRVec3 &v) { return {v.x, v.y, v.z}; }
JPH::Quat quat(const CJQuat &q) { return {q.x, q.y, q.z, q.w}; }
CJVec3 transport(JPH::Vec3Arg v) { return {v.GetX(), v.GetY(), v.GetZ()}; }
CJRVec3 transport_position(JPH::RVec3Arg v) { return {v.GetX(), v.GetY(), v.GetZ()}; }
CJQuat transport_rotation(JPH::QuatArg q) { return {q.GetX(), q.GetY(), q.GetZ(), q.GetW()}; }
bool activation_valid(int32_t v) { return v == CJ_ACTIVATE || v == CJ_DONT_ACTIVATE; }
JPH::EActivation activation(int32_t v) { return v == CJ_ACTIVATE ? JPH::EActivation::Activate : JPH::EActivation::DontActivate; }
void snapshot(const JPH::Body &body, CJBodyTransform &out) {
    std::memset(&out, 0, sizeof(out));
    out.position = transport_position(body.GetPosition());
    out.rotation = transport_rotation(body.GetRotation());
    if (!body.IsStatic()) {
        out.linearVelocity = transport(body.GetLinearVelocity());
        out.angularVelocity = transport(body.GetAngularVelocity());
    }
    out.userData = body.GetUserData();
    out.bodyId = body.GetID().GetIndexAndSequenceNumber();
    out.motionType = static_cast<int32_t>(body.GetMotionType());
    out.objectLayer = body.GetObjectLayer();
    out.isActive = body.IsActive() ? 1 : 0;
    out.isSensor = body.IsSensor() ? 1 : 0;
}
}

struct CJShape { JPH::RefConst<JPH::Shape> shape; };
struct CJTempAllocator {
    std::mutex mutex;
    std::unique_ptr<JPH::TempAllocator> allocator;
};
struct CJJobSystem {
    std::mutex mutex;
    std::unique_ptr<JPH::JobSystem> jobs;
};

class ContactAdapter final : public JPH::ContactListener {
    CJWorld &world;
    void contact(const JPH::Body &, const JPH::Body &, const JPH::ContactManifold &, JPH::ContactSettings &, int32_t);
public:
    explicit ContactAdapter(CJWorld &w) : world(w) {}
    JPH::ValidateResult OnContactValidate(const JPH::Body &, const JPH::Body &, JPH::RVec3Arg, const JPH::CollideShapeResult &) override;
    void OnContactAdded(const JPH::Body &a, const JPH::Body &b, const JPH::ContactManifold &m, JPH::ContactSettings &s) override { contact(a, b, m, s, CJ_CONTACT_ADDED); }
    void OnContactPersisted(const JPH::Body &a, const JPH::Body &b, const JPH::ContactManifold &m, JPH::ContactSettings &s) override { contact(a, b, m, s, CJ_CONTACT_PERSISTED); }
    void OnContactRemoved(const JPH::SubShapeIDPair &) override;
};
class ActivationAdapter final : public JPH::BodyActivationListener {
    CJWorld &world;
    void notify(const JPH::BodyID &, JPH::uint64, uint8_t);
public:
    explicit ActivationAdapter(CJWorld &w) : world(w) {}
    void OnBodyActivated(const JPH::BodyID &id, JPH::uint64 data) override { notify(id, data, 1); }
    void OnBodyDeactivated(const JPH::BodyID &id, JPH::uint64 data) override { notify(id, data, 0); }
};
struct CJWorld {
    std::mutex mutex;
    uint32_t layer_count = 0;
    CJCallbacks callbacks {};
    std::atomic<bool> invalid_callback {false};
    JPH::BroadPhaseLayerInterfaceTable layer_map;
    JPH::ObjectLayerPairFilterTable pairs;
    std::unique_ptr<JPH::ObjectVsBroadPhaseLayerFilterTable> broad_filter;
    ContactAdapter contacts;
    ActivationAdapter activations;
    // Destroy PhysicsSystem before all filters and listener adapters.
    std::unique_ptr<JPH::PhysicsSystem> system;
    CJWorld(uint32_t layers, uint32_t broad) : layer_count(layers), layer_map(layers, broad),
        pairs(layers), contacts(*this), activations(*this) {}
};

namespace {
template<class F> int32_t with_world(CJWorld *world, F &&f) {
    return guarded([&]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "world calls are forbidden inside callbacks and body lock scopes");
        if (!world) return fail(CJ_INVALID_ARGUMENT, "null world");
        std::lock_guard<std::mutex> lock(world->mutex);
        world->invalid_callback.store(false);
        int32_t result = f();
        if (world->invalid_callback.exchange(false) && result == CJ_OK)
            return fail(CJ_INVALID_ARGUMENT, "callback returned invalid contact settings or validation result");
        return result;
    });
}
JPH::Body *find_body(CJWorld *world, uint32_t id) {
    if (id & JPH::BodyID::cBroadPhaseBit) return nullptr;
    return world->system->GetBodyLockInterfaceNoLock().TryGetBody(JPH::BodyID(id));
}
template<class F> int32_t with_body(CJWorld *world, uint32_t id, F &&f) {
    return with_world(world, [&]() -> int32_t {
        JPH::Body *body = find_body(world, id);
        if (!body) return fail(CJ_INVALID_BODY, "body ID is invalid or destroyed in this world");
        return f(*body, world->system->GetBodyInterfaceNoLock());
    });
}
template<class F> int32_t create_resource(F &&f) {
    return guarded([&]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "resource creation is forbidden inside callbacks and body locks");
        std::lock_guard<std::mutex> lock(runtime_mutex);
        if (!runtime_leases) return fail(CJ_NOT_INITIALIZED, "acquire a Jolt runtime before creating resources");
        int32_t result = f();
        if (result == CJ_OK) ++resources;
        return result;
    });
}
int32_t make_shape(JPH::ShapeSettings &settings, CJShape **out) {
    auto result = settings.Create();
    if (result.HasError()) return fail(CJ_SHAPE_ERROR, result.GetError().c_str());
    auto wrapper = std::make_unique<CJShape>();
    wrapper->shape = result.Get();
    *out = wrapper.release();
    return CJ_OK;
}
}

JPH::ValidateResult ContactAdapter::OnContactValidate(const JPH::Body &a, const JPH::Body &b,
        JPH::RVec3Arg base, const JPH::CollideShapeResult &hit) {
    if (!world.callbacks.validate) return JPH::ValidateResult::AcceptAllContactsForThisBodyPair;
    CJContactValidation event {};
    snapshot(a, event.body1); snapshot(b, event.body2);
    event.baseOffset = transport_position(base);
    event.point1 = transport(hit.mContactPointOn1); event.point2 = transport(hit.mContactPointOn2);
    event.penetrationAxis = transport(hit.mPenetrationAxis); event.penetrationDepth = hit.mPenetrationDepth;
    event.subShapeId1 = hit.mSubShapeID1.GetValue(); event.subShapeId2 = hit.mSubShapeID2.GetValue();
    CallbackScope scope;
    int32_t result = world.callbacks.validate(world.callbacks.context, &event);
    if (result < CJ_ACCEPT_ALL_CONTACTS || result > CJ_REJECT_ALL_CONTACTS) {
        world.invalid_callback.store(true); return JPH::ValidateResult::RejectAllContactsForThisBodyPair;
    }
    return static_cast<JPH::ValidateResult>(result);
}
void ContactAdapter::contact(const JPH::Body &a, const JPH::Body &b, const JPH::ContactManifold &m,
        JPH::ContactSettings &s, int32_t kind) {
    if (!world.callbacks.contact) return;
    CJContactEvent event {};
    snapshot(a, event.body1); snapshot(b, event.body2);
    event.baseOffset = transport_position(m.mBaseOffset); event.normal = transport(m.mWorldSpaceNormal);
    event.penetrationDepth = m.mPenetrationDepth;
    event.subShapeId1 = m.mSubShapeID1.GetValue(); event.subShapeId2 = m.mSubShapeID2.GetValue();
    event.pointCount = static_cast<uint32_t>(m.mRelativeContactPointsOn1.size()); event.kind = kind;
    if (event.pointCount > 64 || m.mRelativeContactPointsOn2.size() != event.pointCount) {
        world.invalid_callback.store(true); return;
    }
    for (uint32_t i = 0; i < event.pointCount; ++i) {
        event.points1[i] = transport(m.mRelativeContactPointsOn1[i]);
        event.points2[i] = transport(m.mRelativeContactPointsOn2[i]);
    }
    CJContactSettings settings {};
    settings.friction = s.mCombinedFriction; settings.restitution = s.mCombinedRestitution;
    settings.inverseMassScale1 = s.mInvMassScale1; settings.inverseInertiaScale1 = s.mInvInertiaScale1;
    settings.inverseMassScale2 = s.mInvMassScale2; settings.inverseInertiaScale2 = s.mInvInertiaScale2;
    settings.isSensor = s.mIsSensor ? 1 : 0;
    settings.relativeLinearSurfaceVelocity = transport(s.mRelativeLinearSurfaceVelocity);
    settings.relativeAngularSurfaceVelocity = transport(s.mRelativeAngularSurfaceVelocity);
    CallbackScope scope;
    if (world.callbacks.contact(world.callbacks.context, &event, &settings) != 0) return;
    const float scalars[] = {settings.friction, settings.restitution, settings.inverseMassScale1,
        settings.inverseMassScale2, settings.inverseInertiaScale1, settings.inverseInertiaScale2};
    for (float value : scalars) if (!std::isfinite(value) || value < 0) { world.invalid_callback.store(true); return; }
    if (settings.isSensor > 1 || !finite(settings.relativeLinearSurfaceVelocity) || !finite(settings.relativeAngularSurfaceVelocity)) {
        world.invalid_callback.store(true); return;
    }
    s.mCombinedFriction = settings.friction; s.mCombinedRestitution = settings.restitution;
    s.mInvMassScale1 = settings.inverseMassScale1; s.mInvInertiaScale1 = settings.inverseInertiaScale1;
    s.mInvMassScale2 = settings.inverseMassScale2; s.mInvInertiaScale2 = settings.inverseInertiaScale2;
    s.mIsSensor = settings.isSensor != 0;
    s.mRelativeLinearSurfaceVelocity = vec(settings.relativeLinearSurfaceVelocity);
    s.mRelativeAngularSurfaceVelocity = vec(settings.relativeAngularSurfaceVelocity);
}
void ContactAdapter::OnContactRemoved(const JPH::SubShapeIDPair &pair) {
    if (!world.callbacks.removed) return;
    CallbackScope scope;
    world.callbacks.removed(world.callbacks.context, pair.GetBody1ID().GetIndexAndSequenceNumber(),
        pair.GetSubShapeID1().GetValue(), pair.GetBody2ID().GetIndexAndSequenceNumber(), pair.GetSubShapeID2().GetValue());
}
void ActivationAdapter::notify(const JPH::BodyID &id, JPH::uint64 data, uint8_t active) {
    if (!world.callbacks.activation) return;
    CallbackScope scope;
    world.callbacks.activation(world.callbacks.context, id.GetIndexAndSequenceNumber(), data, active);
}

uint32_t cj_abi_version() { return 1; }
uint32_t cj_precision_bits() { return sizeof(JPH::Real) * 8; }
uint32_t cj_object_layer_bits() { return sizeof(JPH::ObjectLayer) * 8; }
const char *cj_build_id() {
    return "caelum-jolt/1;jolt=e77f175595e64cb44218cc9d9d56fc365ad0e36a;real=64;layer=16;cpu=baseline;fp=deterministic;exceptions=on;gpu=off";
}
const char *cj_last_error() { return last_error; }
uint64_t cj_live_resources() { return resources.load(); }
uint64_t cj_allocation_calls() { return allocation_calls.load(std::memory_order_relaxed); }
uint64_t cj_allocation_bytes_requested() { return allocation_bytes.load(std::memory_order_relaxed); }
uint32_t cj_body_id_invalid() { return JPH::BodyID::cInvalidBodyID; }

int32_t cj_runtime_acquire() {
    return guarded([]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "runtime operation inside callback or lock");
        std::lock_guard<std::mutex> lock(runtime_mutex);
        if (!runtime_leases) {
            JPH::RegisterDefaultAllocator();
            default_allocate = JPH::Allocate; default_reallocate = JPH::Reallocate;
            default_aligned_allocate = JPH::AlignedAllocate;
            JPH::Allocate = tracked_allocate; JPH::Reallocate = tracked_reallocate;
            JPH::AlignedAllocate = tracked_aligned_allocate;
            auto factory = std::make_unique<JPH::Factory>();
            JPH::Factory::sInstance = factory.get();
            try { JPH::RegisterTypes(); }
            catch (...) { JPH::UnregisterTypes(); JPH::Factory::sInstance = nullptr; throw; }
            factory.release();
        }
        ++runtime_leases;
        return CJ_OK;
    });
}
int32_t cj_runtime_release() {
    return guarded([]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "runtime operation inside callback or lock");
        std::lock_guard<std::mutex> lock(runtime_mutex);
        if (!runtime_leases) return fail(CJ_NOT_INITIALIZED, "no runtime lease to release");
        if (runtime_leases == 1 && resources.load()) return fail(CJ_RESOURCE_BUSY, "native resources still own the runtime");
        if (--runtime_leases == 0) {
            JPH::UnregisterTypes(); delete JPH::Factory::sInstance; JPH::Factory::sInstance = nullptr;
        }
        return CJ_OK;
    });
}
int32_t cj_world_config_init(CJWorldConfig *out) {
    return guarded([&]() -> int32_t {
        if (!out) return fail(CJ_INVALID_ARGUMENT, "null config output");
        std::memset(out, 0, sizeof(*out));
        out->maxBodies = 10240; out->maxBodyPairs = 65536; out->maxContactConstraints = 10240;
        out->objectLayerCount = 2; out->broadPhaseLayerCount = 2; out->gravity.y = -9.81f;
        return CJ_OK;
    });
}
int32_t cj_body_create_info_init(CJBodyCreateInfo *out) {
    return guarded([&]() -> int32_t {
        if (!out) return fail(CJ_INVALID_ARGUMENT, "null body settings output");
        std::memset(out, 0, sizeof(*out));
        const JPH::BodyCreationSettings defaults;
        out->position = transport_position(defaults.mPosition); out->rotation = transport_rotation(defaults.mRotation);
        out->linearVelocity = transport(defaults.mLinearVelocity); out->angularVelocity = transport(defaults.mAngularVelocity);
        out->userData = defaults.mUserData; out->motionType = static_cast<int32_t>(defaults.mMotionType);
        out->motionQuality = static_cast<int32_t>(defaults.mMotionQuality); out->objectLayer = defaults.mObjectLayer;
        out->isSensor = defaults.mIsSensor ? 1 : 0; out->allowSleeping = defaults.mAllowSleeping ? 1 : 0;
        out->friction = defaults.mFriction; out->restitution = defaults.mRestitution;
        out->linearDamping = defaults.mLinearDamping; out->angularDamping = defaults.mAngularDamping;
        out->maxLinearVelocity = defaults.mMaxLinearVelocity; out->maxAngularVelocity = defaults.mMaxAngularVelocity;
        out->gravityFactor = defaults.mGravityFactor;
        return CJ_OK;
    });
}
int32_t cj_temp_allocator_create(uint32_t bytes, CJTempAllocator **out) {
    if (out) *out = nullptr;
    return create_resource([&]() -> int32_t {
        if (!out) return fail(CJ_INVALID_ARGUMENT, "null allocator output");
        auto value = std::make_unique<CJTempAllocator>();
        if (bytes) value->allocator = std::make_unique<JPH::TempAllocatorImplWithMallocFallback>(bytes);
        else value->allocator = std::make_unique<JPH::TempAllocatorMalloc>();
        *out = value.release(); return CJ_OK;
    });
}
int32_t cj_temp_allocator_destroy(CJTempAllocator *value) {
    return guarded([&]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "allocator close inside callback or lock");
        if (!value) return fail(CJ_INVALID_ARGUMENT, "null allocator");
        { std::lock_guard<std::mutex> lock(value->mutex); value->allocator.reset(); }
        delete value; --resources; return CJ_OK;
    });
}
int32_t cj_job_system_create(uint32_t workers, CJJobSystem **out) {
    if (out) *out = nullptr;
    return create_resource([&]() -> int32_t {
        if (!out || workers > 256) return fail(CJ_INVALID_ARGUMENT, "workers must be in 0..256 and output must not be null");
        auto value = std::make_unique<CJJobSystem>();
        if (workers) value->jobs = std::make_unique<JPH::JobSystemThreadPool>(JPH::cMaxPhysicsJobs, JPH::cMaxPhysicsBarriers, static_cast<int>(workers));
        else value->jobs = std::make_unique<JPH::JobSystemSingleThreaded>(JPH::cMaxPhysicsJobs);
        *out = value.release(); return CJ_OK;
    });
}
int32_t cj_job_system_destroy(CJJobSystem *value) {
    return guarded([&]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "job system close inside callback or lock");
        if (!value) return fail(CJ_INVALID_ARGUMENT, "null job system");
        { std::lock_guard<std::mutex> lock(value->mutex); value->jobs.reset(); }
        delete value; --resources; return CJ_OK;
    });
}
int32_t cj_world_create(const CJWorldConfig *config, const uint8_t *mapping, uint32_t mapping_count,
        const uint8_t *matrix, uint32_t matrix_count, CJWorld **out) {
    if (out) *out = nullptr;
    return create_resource([&]() -> int32_t {
        if (!out || !config || !mapping || !matrix) return fail(CJ_INVALID_ARGUMENT, "null world creation argument");
        const auto &c = *config;
        if (!c.maxBodies || c.maxBodies > JPH::PhysicsSystem::cMaxBodiesLimit || !c.maxBodyPairs ||
            c.maxBodyPairs > JPH::PhysicsSystem::cMaxBodyPairsLimit || !c.maxContactConstraints ||
            c.maxContactConstraints > JPH::PhysicsSystem::cMaxContactConstraintsLimit ||
            c.bodyMutexCount > 64 || (c.bodyMutexCount && (c.bodyMutexCount & (c.bodyMutexCount - 1))) ||
            !c.objectLayerCount || c.objectLayerCount > 4096 || !c.broadPhaseLayerCount || c.broadPhaseLayerCount > 256 ||
            mapping_count != c.objectLayerCount || uint64_t(matrix_count) != uint64_t(c.objectLayerCount) * c.objectLayerCount || !finite(c.gravity))
            return fail(CJ_INVALID_ARGUMENT, "invalid world limits, layers, gravity or table lengths");
        for (uint32_t a = 0; a < c.objectLayerCount; ++a) {
            if (mapping[a] >= c.broadPhaseLayerCount) return fail(CJ_INVALID_ARGUMENT, "broadphase mapping outside range");
            for (uint32_t b = 0; b < c.objectLayerCount; ++b)
                if (matrix[a * c.objectLayerCount + b] > 1 || matrix[a * c.objectLayerCount + b] != matrix[b * c.objectLayerCount + a])
                    return fail(CJ_INVALID_ARGUMENT, "collision matrix must be symmetric and contain only 0 or 1");
        }
        auto world = std::make_unique<CJWorld>(c.objectLayerCount, c.broadPhaseLayerCount);
        for (uint32_t a = 0; a < c.objectLayerCount; ++a) {
            world->layer_map.MapObjectToBroadPhaseLayer(static_cast<JPH::ObjectLayer>(a), JPH::BroadPhaseLayer(mapping[a]));
            for (uint32_t b = 0; b <= a; ++b) if (matrix[a * c.objectLayerCount + b])
                world->pairs.EnableCollision(static_cast<JPH::ObjectLayer>(a), static_cast<JPH::ObjectLayer>(b));
        }
        world->broad_filter = std::make_unique<JPH::ObjectVsBroadPhaseLayerFilterTable>(world->layer_map,
            c.broadPhaseLayerCount, world->pairs, c.objectLayerCount);
        world->system = std::make_unique<JPH::PhysicsSystem>();
        world->system->Init(c.maxBodies, c.bodyMutexCount, c.maxBodyPairs, c.maxContactConstraints,
            world->layer_map, *world->broad_filter, world->pairs);
        world->system->SetGravity(vec(c.gravity));
        *out = world.release(); return CJ_OK;
    });
}
int32_t cj_world_destroy(CJWorld *world) {
    return guarded([&]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "world close inside callback or lock");
        if (!world) return fail(CJ_INVALID_ARGUMENT, "null world");
        {
            std::lock_guard<std::mutex> lock(world->mutex);
            world->system->SetContactListener(nullptr); world->system->SetBodyActivationListener(nullptr);
            world->callbacks = {};
            JPH::BodyIDVector ids; world->system->GetBodies(ids);
            auto &bodies = world->system->GetBodyInterfaceNoLock();
            for (auto id : ids) { if (bodies.IsAdded(id)) bodies.RemoveBody(id); bodies.DestroyBody(id); }
            world->system.reset();
        }
        delete world; --resources; return CJ_OK;
    });
}
int32_t cj_world_update(CJWorld *world, float dt, uint32_t steps, CJTempAllocator *allocator, CJJobSystem *jobs, uint32_t *errors) {
    if (errors) *errors = 0;
    return with_world(world, [&]() -> int32_t {
        if (!errors || !allocator || !jobs || !std::isfinite(dt) || dt <= 0 || !steps || steps > 1024)
            return fail(CJ_INVALID_ARGUMENT, "update requires positive finite time, 1..1024 steps, allocator, jobs and result");
        std::scoped_lock lock(allocator->mutex, jobs->mutex);
        *errors = static_cast<uint32_t>(world->system->Update(dt, static_cast<int>(steps), allocator->allocator.get(), jobs->jobs.get()));
        return CJ_OK;
    });
}
int32_t cj_world_optimize_broad_phase(CJWorld *world) {
    return with_world(world, [&]() -> int32_t { world->system->OptimizeBroadPhase(); return CJ_OK; });
}
int32_t cj_world_set_gravity(CJWorld *world, const CJVec3 *gravity) {
    return with_world(world, [&]() -> int32_t {
        if (!gravity || !finite(*gravity)) return fail(CJ_INVALID_ARGUMENT, "gravity must be finite");
        world->system->SetGravity(vec(*gravity)); return CJ_OK;
    });
}
int32_t cj_world_get_gravity(CJWorld *world, CJVec3 *out) {
    return with_world(world, [&]() -> int32_t {
        if (!out) return fail(CJ_INVALID_ARGUMENT, "null gravity output");
        *out = transport(world->system->GetGravity()); return CJ_OK;
    });
}
int32_t cj_world_set_callbacks(CJWorld *world, const CJCallbacks *callbacks) {
    return with_world(world, [&]() -> int32_t {
        world->callbacks = callbacks ? *callbacks : CJCallbacks {};
        world->system->SetContactListener(callbacks ? &world->contacts : nullptr);
        world->system->SetBodyActivationListener(callbacks ? &world->activations : nullptr);
        return CJ_OK;
    });
}
int32_t cj_shape_box(const CJVec3 *extent, float radius, CJShape **out) {
    if (out) *out = nullptr;
    return create_resource([&]() -> int32_t {
        if (!out || !extent || !finite(*extent) || extent->x <= 0 || extent->y <= 0 || extent->z <= 0 || !std::isfinite(radius) || radius < 0)
            return fail(CJ_INVALID_ARGUMENT, "box requires positive finite half extents and nonnegative convex radius");
        JPH::BoxShapeSettings settings(vec(*extent), radius); return make_shape(settings, out);
    });
}
int32_t cj_shape_sphere(float radius, CJShape **out) {
    if (out) *out = nullptr;
    return create_resource([&]() -> int32_t {
        if (!out || !std::isfinite(radius) || radius <= 0) return fail(CJ_INVALID_ARGUMENT, "sphere radius must be positive and finite");
        JPH::SphereShapeSettings settings(radius); return make_shape(settings, out);
    });
}
int32_t cj_shape_capsule(float height, float radius, CJShape **out) {
    if (out) *out = nullptr;
    return create_resource([&]() -> int32_t {
        if (!out || !std::isfinite(height) || height <= 0 || !std::isfinite(radius) || radius <= 0)
            return fail(CJ_INVALID_ARGUMENT, "capsule half height and radius must be positive and finite");
        JPH::CapsuleShapeSettings settings(height, radius); return make_shape(settings, out);
    });
}
int32_t cj_shape_convex_hull(const CJVec3 *points, uint32_t count, float radius, CJShape **out) {
    if (out) *out = nullptr;
    return create_resource([&]() -> int32_t {
        // Jolt's hull builder uses signed int point indices, even though the ABI uses uint32_t.
        if (!out || !points || count < 4 || count > uint32_t(std::numeric_limits<int32_t>::max()) || !std::isfinite(radius) || radius < 0)
            return fail(CJ_INVALID_ARGUMENT, "convex hull requires 4..INT32_MAX finite points and a nonnegative finite convex radius");
        JPH::ConvexHullShapeSettings settings;
        settings.mMaxConvexRadius = radius;
        settings.mPoints.reserve(count);
        for (uint32_t index = 0; index < count; ++index) {
            if (!finite(points[index])) return fail(CJ_INVALID_ARGUMENT, "convex hull points must be finite");
            settings.mPoints.push_back(vec(points[index]));
        }
        auto result = settings.Create();
        if (result.HasError()) return fail(CJ_SHAPE_ERROR, result.GetError().c_str());
        // Upstream also supports flat hulls; this solid-body API requires nonzero volume.
        if (!std::isfinite(result.Get()->GetVolume()) || result.Get()->GetVolume() <= 0)
            return fail(CJ_SHAPE_ERROR, "convex hull must enclose a finite, positive volume");
        return make_shape(settings, out);
    });
}
int32_t cj_shape_retain(CJShape *shape, CJShape **out) {
    if (out) *out = nullptr;
    return create_resource([&]() -> int32_t {
        if (!out || !shape) return fail(CJ_INVALID_ARGUMENT, "null shape or output");
        auto value = std::make_unique<CJShape>(); value->shape = shape->shape;
        *out = value.release(); return CJ_OK;
    });
}
int32_t cj_shape_release(CJShape *shape) {
    return guarded([&]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "shape release inside callback or lock");
        if (!shape) return fail(CJ_INVALID_ARGUMENT, "null shape");
        delete shape; --resources; return CJ_OK;
    });
}

int32_t cj_body_create(CJWorld *world, const CJBodyCreateInfo *info, const CJShape *shape, uint8_t add, int32_t activate, uint32_t *out) {
    if (out) *out = JPH::BodyID::cInvalidBodyID;
    return with_world(world, [&]() -> int32_t {
        if (!info || !shape || !out || add > 1 || !activation_valid(activate)) return fail(CJ_INVALID_ARGUMENT, "invalid body creation argument");
        const auto &i = *info;
        if (!finite(i.position) || !rotation_valid(i.rotation) || !finite(i.linearVelocity) || !finite(i.angularVelocity) ||
            i.motionType < CJ_STATIC || i.motionType > CJ_DYNAMIC || i.motionQuality < CJ_DISCRETE || i.motionQuality > CJ_LINEAR_CAST ||
            i.objectLayer >= world->layer_count || i.isSensor > 1 || i.allowSleeping > 1 || !std::isfinite(i.gravityFactor))
            return fail(CJ_INVALID_ARGUMENT, "invalid body settings, layer or quaternion");
        const float nonnegative[] = {i.friction, i.restitution, i.linearDamping, i.angularDamping, i.maxLinearVelocity, i.maxAngularVelocity};
        for (float value : nonnegative) if (!std::isfinite(value) || value < 0) return fail(CJ_INVALID_ARGUMENT, "body physical parameters must be finite and nonnegative");
        JPH::BodyCreationSettings settings(shape->shape.GetPtr(), pos(i.position), quat(i.rotation), static_cast<JPH::EMotionType>(i.motionType), i.objectLayer);
        settings.mLinearVelocity = vec(i.linearVelocity); settings.mAngularVelocity = vec(i.angularVelocity);
        settings.mUserData = i.userData; settings.mMotionQuality = static_cast<JPH::EMotionQuality>(i.motionQuality);
        settings.mIsSensor = i.isSensor != 0; settings.mAllowSleeping = i.allowSleeping != 0;
        settings.mFriction = i.friction; settings.mRestitution = i.restitution;
        settings.mLinearDamping = i.linearDamping; settings.mAngularDamping = i.angularDamping;
        settings.mMaxLinearVelocity = i.maxLinearVelocity; settings.mMaxAngularVelocity = i.maxAngularVelocity; settings.mGravityFactor = i.gravityFactor;
        auto &bodies = world->system->GetBodyInterfaceNoLock();
        JPH::Body *body = bodies.CreateBody(settings);
        if (!body) return fail(CJ_CAPACITY_EXCEEDED, "world body capacity exceeded");
        const auto id = body->GetID();
        try { if (add) bodies.AddBody(id, activation(activate)); }
        catch (...) { if (bodies.IsAdded(id)) bodies.RemoveBody(id); bodies.DestroyBody(id); throw; }
        *out = id.GetIndexAndSequenceNumber(); return CJ_OK;
    });
}
int32_t cj_body_add(CJWorld *world, uint32_t id, int32_t activate) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (!activation_valid(activate) || body.IsInBroadPhase()) return fail(CJ_INVALID_ARGUMENT, "invalid activation or body already added");
        bodies.AddBody(body.GetID(), activation(activate)); return CJ_OK;
    });
}
int32_t cj_body_remove(CJWorld *world, uint32_t id) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (!body.IsInBroadPhase()) return fail(CJ_INVALID_ARGUMENT, "body is not added");
        bodies.RemoveBody(body.GetID()); return CJ_OK;
    });
}
int32_t cj_body_destroy(CJWorld *world, uint32_t id) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (body.IsInBroadPhase()) return fail(CJ_RESOURCE_BUSY, "remove body before destroying it");
        bodies.DestroyBody(body.GetID()); return CJ_OK;
    });
}
int32_t cj_body_get_transform(CJWorld *world, uint32_t id, CJBodyTransform *out) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &) -> int32_t {
        if (!out) return fail(CJ_INVALID_ARGUMENT, "null transform output");
        snapshot(body, *out); return CJ_OK;
    });
}
int32_t cj_body_get_transforms(CJWorld *world, const uint32_t *ids, uint32_t count, CJBodyTransform *out, uint32_t capacity) {
    return with_world(world, [&]() -> int32_t {
        if (capacity < count || (count && (!ids || !out))) return fail(CJ_INVALID_ARGUMENT, "invalid batch buffer or capacity");
        for (uint32_t i = 0; i < count; ++i) if (!find_body(world, ids[i])) return fail(CJ_INVALID_BODY, "batch contains invalid body ID");
        for (uint32_t i = 0; i < count; ++i) snapshot(*find_body(world, ids[i]), out[i]);
        return CJ_OK;
    });
}
int32_t cj_body_set_transform(CJWorld *world, uint32_t id, const CJRVec3 *position, const CJQuat *rotation, int32_t activate) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (!position || !rotation || !finite(*position) || !rotation_valid(*rotation) || !activation_valid(activate))
            return fail(CJ_INVALID_ARGUMENT, "invalid transform or activation");
        bodies.SetPositionAndRotation(body.GetID(), pos(*position), quat(*rotation), activation(activate)); return CJ_OK;
    });
}
int32_t cj_body_set_velocity(CJWorld *world, uint32_t id, const CJVec3 *linear, const CJVec3 *angular) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (!linear || !angular || !finite(*linear) || !finite(*angular) || body.IsStatic()) return fail(CJ_INVALID_ARGUMENT, "invalid velocities or static body");
        bodies.SetLinearAndAngularVelocity(body.GetID(), vec(*linear), vec(*angular)); return CJ_OK;
    });
}
int32_t cj_body_add_force(CJWorld *world, uint32_t id, const CJVec3 *force) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (!force || !finite(*force) || !body.IsDynamic()) return fail(CJ_INVALID_ARGUMENT, "force requires finite vector and dynamic body");
        bodies.AddForce(body.GetID(), vec(*force)); return CJ_OK;
    });
}
int32_t cj_body_add_impulse(CJWorld *world, uint32_t id, const CJVec3 *impulse) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (!impulse || !finite(*impulse) || !body.IsDynamic()) return fail(CJ_INVALID_ARGUMENT, "impulse requires finite vector and dynamic body");
        bodies.AddImpulse(body.GetID(), vec(*impulse)); return CJ_OK;
    });
}
int32_t cj_body_move_kinematic(CJWorld *world, uint32_t id, const CJRVec3 *position, const CJQuat *rotation, float dt) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (!position || !rotation || !finite(*position) || !rotation_valid(*rotation) || !body.IsKinematic() || !std::isfinite(dt) || dt <= 0)
            return fail(CJ_INVALID_ARGUMENT, "moveKinematic requires kinematic body, valid transform and positive finite time");
        bodies.MoveKinematic(body.GetID(), pos(*position), quat(*rotation), dt); return CJ_OK;
    });
}
int32_t cj_body_set_active(CJWorld *world, uint32_t id, uint8_t active) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        if (active > 1 || body.IsStatic() || !body.IsInBroadPhase()) return fail(CJ_INVALID_ARGUMENT, "activation requires an added nonstatic body and 0/1");
        if (active) bodies.ActivateBody(body.GetID()); else bodies.DeactivateBody(body.GetID()); return CJ_OK;
    });
}
int32_t cj_body_set_user_data(CJWorld *world, uint32_t id, uint64_t data) {
    return with_body(world, id, [&](JPH::Body &body, JPH::BodyInterface &bodies) -> int32_t {
        bodies.SetUserData(body.GetID(), data); return CJ_OK;
    });
}

struct CJBodyLock {
    CJWorld *world;
    std::thread::id thread = std::this_thread::get_id();
    std::unique_lock<std::mutex> world_lock;
    std::unique_ptr<JPH::BodyLockRead> reader;
    std::unique_ptr<JPH::BodyLockWrite> writer;
    bool changed = false;
    CJBodyLock(CJWorld *w) : world(w), world_lock(w->mutex) {}
    const JPH::Body &body() const { return writer ? writer->GetBody() : reader->GetBody(); }
};
int32_t cj_body_lock(CJWorld *world, uint32_t id, uint8_t write, CJBodyLock **out) {
    if (out) *out = nullptr;
    return guarded([&]() -> int32_t {
        if (in_callback || active_lock) return fail(CJ_REENTRANT_CALL, "nested body lock or callback body lock");
        if (!out || !world || write > 1) return fail(CJ_INVALID_ARGUMENT, "invalid body lock argument");
        auto scope = std::make_unique<CJBodyLock>(world);
        if (!find_body(world, id)) return fail(CJ_INVALID_BODY, "invalid body ID");
        if (write) scope->writer = std::make_unique<JPH::BodyLockWrite>(world->system->GetBodyLockInterface(), JPH::BodyID(id));
        else scope->reader = std::make_unique<JPH::BodyLockRead>(world->system->GetBodyLockInterface(), JPH::BodyID(id));
        active_lock = scope.get(); *out = scope.release(); return CJ_OK;
    });
}
int32_t cj_locked_body_get_transform(CJBodyLock *lock, CJBodyTransform *out) {
    return guarded([&]() -> int32_t {
        if (!lock || active_lock != lock || !out) return fail(CJ_INVALID_ARGUMENT, "body lock is not owned by this thread or output is null");
        snapshot(lock->body(), *out); return CJ_OK;
    });
}
int32_t cj_locked_body_set_velocity(CJBodyLock *lock, const CJVec3 *linear, const CJVec3 *angular) {
    return guarded([&]() -> int32_t {
        if (!lock || active_lock != lock || !lock->writer || !linear || !angular || !finite(*linear) || !finite(*angular) || lock->body().IsStatic())
            return fail(CJ_INVALID_ARGUMENT, "velocity write requires this thread's write lock and a nonstatic body");
        lock->writer->GetBody().SetLinearVelocityClamped(vec(*linear));
        lock->writer->GetBody().SetAngularVelocityClamped(vec(*angular));
        lock->changed = true; return CJ_OK;
    });
}
int32_t cj_body_unlock(CJBodyLock *lock) {
    return guarded([&]() -> int32_t {
        if (!lock || active_lock != lock) return fail(CJ_INVALID_ARGUMENT, "body lock is not owned by this thread");
        const auto id = lock->body().GetID(); const bool activate = lock->changed && lock->body().IsInBroadPhase();
        lock->reader.reset(); lock->writer.reset();
        active_lock = nullptr;
        // Destruction releases the world mutex even if activation throws.
        std::unique_ptr<CJBodyLock> owned(lock);
        if (activate) lock->world->system->GetBodyInterfaceNoLock().ActivateBody(id);
        return CJ_OK;
    });
}
int32_t cj_world_cast_ray(CJWorld *world, const CJRayCast *ray, CJRayCastResult *out) {
    return with_world(world, [&]() -> int32_t {
        if (!ray || !out || !finite(ray->origin) || !finite(ray->direction) || ray->objectLayer >= world->layer_count)
            return fail(CJ_INVALID_ARGUMENT, "ray requires finite origin/direction, a valid layer and output");
        std::memset(out, 0, sizeof(*out)); out->bodyId = JPH::BodyID::cInvalidBodyID; out->fraction = 1.0f;
        JPH::RRayCast cast(pos(ray->origin), vec(ray->direction)); JPH::RayCastResult hit;
        const JPH::DefaultBroadPhaseLayerFilter broad(*world->broad_filter, ray->objectLayer);
        const JPH::DefaultObjectLayerFilter objects(world->pairs, ray->objectLayer);
        if (world->system->GetNarrowPhaseQueryNoLock().CastRay(cast, hit, broad, objects)) {
            out->hit = 1; out->fraction = hit.mFraction; out->bodyId = hit.mBodyID.GetIndexAndSequenceNumber();
            out->subShapeId = hit.mSubShapeID2.GetValue(); out->position = transport_position(cast.GetPointOnRay(hit.mFraction));
            const auto *body = find_body(world, out->bodyId);
            if (!body) return fail(CJ_INTERNAL_ERROR, "ray hit disappeared while world was locked");
            out->normal = transport(body->GetWorldSpaceSurfaceNormal(hit.mSubShapeID2, cast.GetPointOnRay(hit.mFraction)));
        }
        return CJ_OK;
    });
}
int32_t cj_probe_roundtrip(const CJBodyCreateInfo *in, CJBodyCreateInfo *out) {
    return guarded([&]() -> int32_t {
        if (!in || !out) return fail(CJ_INVALID_ARGUMENT, "null roundtrip buffer");
        std::memmove(out, in, sizeof(*out));
        out->position = transport_position(pos(out->position));
        out->rotation = transport_rotation(quat(out->rotation));
        out->linearVelocity = transport(vec(out->linearVelocity));
        out->angularVelocity = transport(vec(out->angularVelocity));
        return CJ_OK;
    });
}
int32_t cj_probe_callback(CJValidateCallback callback, void *context, int32_t *out) {
    return guarded([&]() -> int32_t {
        if (!callback || !out) return fail(CJ_INVALID_ARGUMENT, "null probe callback or output");
        CJContactValidation event {}; event.body1.bodyId = 0x12345678; event.body2.userData = UINT64_C(0x123456789abcdef0);
        event.baseOffset = {1.25, -2.5, 3.75}; CallbackScope scope;
        *out = callback(context, &event); return CJ_OK;
    });
}
