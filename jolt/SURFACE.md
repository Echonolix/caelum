# caelum-jolt ABI 1 surface

The authoritative declarations are in [caelum_jolt.h](include/caelum_jolt.h).
Every symbol below is implemented and generated. This is implementation coverage,
not a claim that native acceptance has run. P0–P5 define the initial release; P6
is future expansion. `JoltContractsTest` checks exports, generated functions,
transport fields and the native layout-oracle inventory against this header.

All upstream links below use official commit
`e77f175595e64cb44218cc9d9d56fc365ad0e36a`. Caelum helpers have no upstream symbol.

## Transport data and pointer contract

| C type → generated Kotlin type | Meaning / upstream | Ownership and extent |
| --- | --- | --- |
| `CJVec3` → `JoltVec3` | [Vec3][math], three floats | caller storage; directions, velocities, offsets |
| `CJRVec3` → `JoltRVec3` | [Real/RVec3][real], three doubles | caller storage; world coordinates |
| `CJQuat` → `JoltQuat` | [Quat][quat], four floats | caller storage; normalized rotation, identity default |
| `CJWorldConfig` → `JoltPhysicsSystemConfig` | [PhysicsSystem::Init][system] arguments, gravity | copied during create; native initializer supplies Caelum capacities |
| `CJBodyCreateInfo` → `JoltBodyCreateInfo` | subset of [BodyCreationSettings][settings] | copied during create; initializer reads pinned C++ defaults; shape is a separate borrowed argument |
| `CJBodyTransform` → `JoltBodyTransform` | [Body][body] snapshot, full uint32 ID, uint64 user data | caller output; position/rotation/velocities/motion/layer/active/sensor |
| `CJRayCast` → `JoltRayCast` | [RRayCast][ray], query object layer | synchronous input; direction contains length; reserved must be zero |
| `CJRayCastResult` → `JoltRayCastResult` | [RayCastResult][cast-result] plus point/normal | caller output; initialized on hit/miss, invalid body + fraction 1 on miss |
| `CJContactValidation` → `JoltContactValidation` | [ContactListener::OnContactValidate][contact] | callback-only input; copied body snapshots, base offset, points/axis/depth/subshape IDs |
| `CJContactEvent` → `JoltContactEvent` | [ContactManifold][contact] snapshot | callback-only input; two arrays of 64 relative points, first `pointCount` are valid |
| `CJContactSettings` → `JoltContactSettings` | [ContactSettings][contact] | callback-only read/write; nonzero contact return discards all edits |
| `CJCallbacks` → `JoltCallbacks` | Caelum virtual-function adapter | table copied; context and four stubs retained until replacement/unregister/world destruction returns |

These are C transport layouts, not Jolt C++/SIMD layouts. Each field is declared
once in the header; `sizeof`, `alignof` and every `offsetof` are exposed by the
compiler oracle. Callback snapshots must not be mutated except settings. Managed
views expose copied values and enforce thread/scope bounds. Raw views have no
automatic lifetime protection.

Opaque `CJWorld`, `CJShape`, `CJTempAllocator`, `CJJobSystem`, `CJBodyLock` map to
generated `Jolt*Handle` types and managed owners/scopes. Their generated empty
struct descriptors are only pointer type markers. Never allocate their layouts.

Every non-null input pointer must address aligned, live storage for the whole
call. Every out pointer requires writable storage. Fixed-struct arguments have
extent one; counts/capacities below are element counts, not byte counts. Inputs
are not retained except the callback function/context addresses. All create/retain
out-handles are nulled first; ownership transfers on success only. Body creation
sets invalid ID before attempting creation. Null destroy/release arguments are
invalid; idempotent close is a managed-wrapper feature. All struct arguments use
pointers, never aggregate-by-value. Header status/enum parameters use int32;
bools use uint8 0/1; no bare C long or C++ bool crosses the ABI.

## Runtime, resources and worlds

| C export | Upstream / Kotlin operation | Contract / phase |
| --- | --- | --- |
| `cj_abi_version` | Caelum / `JoltRuntime.load` | bootstrap scalar, P1 |
| `cj_precision_bits` | `sizeof(JPH::Real)` / load | bootstrap scalar, P1 |
| `cj_object_layer_bits` | `sizeof(JPH::ObjectLayer)` / load | bootstrap scalar, P1 |
| `cj_build_id` | Caelum / load | static borrowed string, P1 |
| `cj_last_error` | Caelum / `checkJolt` | TLS string copied before next fallible call, P1 |
| `cj_runtime_acquire` | [RegisterDefaultAllocator][memory], [Factory/RegisterTypes][register] / `JoltRuntime.acquire` | one lease; last release owns global teardown, P1 |
| `cj_runtime_release` | UnregisterTypes, Factory deletion / runtime close | last lease refuses live owned resources, P1 |
| `cj_live_resources` | Caelum / `liveNativeResources` | counts bridge world/shape/allocator/job owners, not JPH refs/bodies/locks, P3/P5 |
| `cj_allocation_calls` | JPH Allocate/Reallocate/AlignedAllocate / `allocationStats` | cumulative requests, P5 |
| `cj_allocation_bytes_requested` | same / `allocationStats` | requested bytes, not live memory; excludes bridge/system/JVM and scratch suballocations, P5 |
| `cj_world_config_init` | Caelum defaults / config `allocate` | writes every byte including padding, P2 |
| `cj_body_create_info_init` | BodyCreationSettings defaults / create-info `allocate` | writes every byte, P2 |
| `cj_temp_allocator_create` | [TempAllocatorImplWithMallocFallback / TempAllocatorMalloc][temp] / runtime factory | owned, zero size chooses malloc; scratch overflow uses fallback, P3 |
| `cj_temp_allocator_destroy` | same / allocator close | must serialize raw destruction with every use, P3 |
| `cj_job_system_create` | [JobSystemSingleThreaded][single], [JobSystemThreadPool][pool] / runtime factory | workers 0 or 1–256, owned, P3/P4 |
| `cj_job_system_destroy` | same / job close | joins native workers, P3/P4 |
| `cj_world_create` | PhysicsSystem::Init / `createWorld` | copies config, uint8 mapping[count=layers], symmetric 0/1 matrix[count=layers²]; owns filters, P3 |
| `cj_world_destroy` | PhysicsSystem + BodyInterface teardown / world close | disconnect callbacks, remove/destroy bodies, system before filters; P3/P4 |
| `cj_world_update` | PhysicsSystem::Update / `update` | allocator/jobs borrowed for whole call; out uint32 flags preserved, P3/P4 |
| `cj_world_optimize_broad_phase` | PhysicsSystem::OptimizeBroadPhase / same | serialized world call, P3 |
| `cj_world_set_gravity` | PhysicsSystem::SetGravity / gravity setter | reads one finite Vec3, P3 |
| `cj_world_get_gravity` | PhysicsSystem::GetGravity / gravity getter | writes one Vec3, P3 |
| `cj_world_set_callbacks` | PhysicsSystem::SetContactListener/SetBodyActivationListener / `setListeners`, `clearListeners` | nullable table; old callbacks quiescent on return, P4 |

Managed world operations are serialized; native world operations also serialize.
Allocator/job locks remain held throughout update, so shared managed resources
serialize use across worlds. Raw callers must prevent concurrent destruction,
including waiters; raw pointers cannot verify ownership or use-after-free.
Managed borrowed allocator/jobs remain caller-owned; closing one early makes a
later update fail its managed state check.

## Shapes, bodies and query

| C export | Upstream / Kotlin operation | Contract / phase |
| --- | --- | --- |
| `cj_shape_box` | [BoxShapeSettings::Create][box] / `createBox` | positive finite half extents; nonnegative convex radius; owned ref, P3 |
| `cj_shape_sphere` | [SphereShapeSettings::Create][sphere] / `createSphere` | positive finite radius; owned ref, P3 |
| `cj_shape_capsule` | [CapsuleShapeSettings::Create][capsule] / `createCapsule` | positive finite half-height/radius; owned ref, P3 |
| `cj_shape_convex_hull` | [ConvexHullShapeSettings::Create][hull] / `createConvexHull` | copied Vec3 points[count], 4..INT32_MAX; finite points, finite nonnegative maximum convex radius; positive-volume hull, owned ref, P6 expansion |
| `cj_shape_retain` | [RefConst][ref] / shape `retain` | new independently owned wrapper/ref, P3 |
| `cj_shape_release` | RefConst destructor / shape close | releases exactly one wrapper/ref, P3 |
| `cj_body_create` | [BodyInterface::CreateBody/AddBody][bodies] / `create`, `createAndAdd` | settings+shape borrowed; body retains shape, world owns body; out uint32 ID, P3 |
| `cj_body_add` | BodyInterface::AddBody / `add` | ID must exist and not be added, P3 |
| `cj_body_remove` | BodyInterface::RemoveBody / `remove` | ID must be added; does not destroy, P3 |
| `cj_body_destroy` | BodyInterface::DestroyBody / `destroy` | remove first; stale IDs rejected when sequence differs, P3 |
| `cj_body_get_transform` | Body getters / `readTransform`, `transform` | writes one transport snapshot, P3 |
| `cj_body_get_transforms` | Body getters / `readTransforms` | uint32 ids[count], transform out[capacity≥count]; null buffers allowed only at count 0; validates all IDs before writing, P4 |
| `cj_body_set_transform` | BodyInterface::SetPositionAndRotation / `setTransform` | finite position, normalized quaternion, activation 0/1, P3 |
| `cj_body_set_velocity` | BodyInterface::SetLinearAndAngularVelocity / `setVelocity` | nonstatic body, two finite Vec3 inputs, P3 |
| `cj_body_add_force` | BodyInterface::AddForce / `addForce` | dynamic body, finite Vec3 input, P3 |
| `cj_body_add_impulse` | BodyInterface::AddImpulse / `addImpulse` | dynamic body, finite Vec3 input, P3 |
| `cj_body_move_kinematic` | BodyInterface::MoveKinematic / `moveKinematic` | kinematic body, normalized transform, positive finite deltaTime, P3 |
| `cj_body_set_active` | BodyInterface::ActivateBody/DeactivateBody / `setActive` | added nonstatic body, P3 |
| `cj_body_set_user_data` | BodyInterface::SetUserData / `setUserData` | preserves all uint64 bits, P3 |
| `cj_body_lock` | [BodyLockRead/Write][lock] / `withBodyRead`, `withBodyWrite` | same-thread scoped handle, holds world lock; no nested locks/world calls, P3 |
| `cj_locked_body_get_transform` | Body getters / scoped `readTransform`, `snapshot` | only while this thread owns the lock, P3 |
| `cj_locked_body_set_velocity` | Body::SetLinear/AngularVelocityClamped / scoped `setVelocity` | write lock, nonstatic body; activates after unlock, P3 |
| `cj_body_unlock` | BodyLock destruction / managed finally | consumes lock, even if post-unlock activation reports a failure, P3 |
| `cj_world_cast_ray` | [NarrowPhaseQuery::CastRay][query], Body::GetWorldSpaceSurfaceNormal / `query.castRay` | closest hit with world's broad/object layer filters; copies result, P4 |

Body IDs preserve index and sequence bits, including invalid `0xffffffff`.
They carry no world identity: a valid ID from another world can name an unrelated
body with the same bits. Sequence counters can wrap. Neither cross-world misuse
nor permanent entity identity can be inferred from a uint32 alone.

Convex hull creation accepts interior/duplicate model vertices; Jolt reduces them
to its convex surface (at most 256 hull vertices) using its pinned default hull
tolerance. At that vertex limit upstream accepts an approximation, which may
exceed the default tolerance. It may lower the requested convex radius to fit without enlarging the
outer hull. Coincident/collinear or zero-volume coplanar inputs fail with
`CJ_SHAPE_ERROR`; malformed counts, nonfinite points/radius and negative radius
fail with `CJ_INVALID_ARGUMENT`. Inputs are copied synchronously and never retained.
The managed `NArray<JoltVec3>` overload checks the requested count against its
capacity; the packed `FloatArray` overload requires complete xyz triples.
Model coordinates remain relative to the supplied shape origin. Body snapshots
use `Body::GetPosition`, preserving this origin when the hull's center of mass is
offset. Concave detail is filled by the convex hull; no decomposition is performed.

Body-create fields currently supported: position, rotation, linear/angular
velocity, userData, motionType, motionQuality, objectLayer, isSensor,
allowSleeping, friction, restitution, damping, velocity limits, gravityFactor.
The bridge restricts friction/restitution to nonnegative values even though
upstream permits negative inputs with valid combined material values. Advanced
mass overrides, allowed DOFs, motion-type changes, custom materials and
additional shape settings remain P6. Default motion is upstream Dynamic;
the default layer is 0, so applications using the supplied two-layer table must
explicitly choose layer 1 for moving bodies.

## Callbacks and diagnostics

`CJValidateCallback` returns the four explicit validation codes; JVM failure
returns reject-all (3). `CJContactCallback` returns 0 to commit a complete valid
settings snapshot; nonzero discards edits. `CJRemovedCallback` receives only
IDs, and `CJActivationCallback` receives ID, uint64 userData and uint8 active.
Neither callback has an upstream object pointer. The managed registration owns
an `Arena.ofShared`, listener references and bound method handles. It never uses
the process-wide `NFunction` stub cache. All JVM Throwables are contained; the
first cause and total failure count are reported after native return. Update
flags accompany a callback exception. World mutations that already happened
are not rolled back by listener failures.

Callbacks may run concurrently. World/resource operations and close are
forbidden from callbacks, including on other worlds, to avoid lock inversion.
`world.defer` is permitted with copied values. It drains at most 1024 commands
after an update with no error flags; update flags or a callback failure leave the queue for explicit
`flushDeferred`. Recursive update/drain and close from a draining command are
rejected. Deferred commands execute in queue order; a throwing command is
consumed and later commands remain queued. Closing a world discards the queue.

| C export | Kotlin use / purpose | Phase |
| --- | --- | --- |
| `cj_abi_type_count` | native tests enumerate all 12 transport types | P2 |
| `cj_abi_type_name` | static borrowed C type name | P2 |
| `cj_abi_size` | sizeof; invalid index returns UINT64_MAX | P2 |
| `cj_abi_alignment` | alignof; invalid index returns UINT64_MAX | P2 |
| `cj_abi_field_count` | exact declared field count | P2 |
| `cj_abi_field_name` | static borrowed field name | P2 |
| `cj_abi_field_offset` | offsetof; invalid index returns UINT64_MAX | P2 |
| `cj_abi_constant_count` | enum/status/flag inventory | P2 |
| `cj_abi_constant_name` | static borrowed constant name | P2 |
| `cj_abi_constant_value` | pinned upstream values plus Caelum codes; invalid returns INT64_MIN | P2 |
| `cj_body_id_invalid` | pinned BodyID::cInvalidBodyID | P2 |
| `cj_probe_roundtrip` | one input/output create-info, copies through JPH math types; buffers may alias | P1/P2 |
| `cj_probe_callback` | non-null validation stub/context, int32 output; borrowed context used only for this call | P1/P4 |

Caelum activation uses false/true = 0/1, explicitly translated to upstream
DontActivate/Activate (whose enum numbers are 1/0). The oracle reports both
domains. Other mapped motion/quality/validation codes are checked directly
against the official enums. Error status codes and contact-kind values are
Caelum's own. EPhysicsUpdateError bits are preserved, including unknown bits.

## Deferred and excluded surface

P6 remaining: Mesh/HeightField/Compound/decorated shapes, all-hits/shape/overlap
queries and collectors, advanced body settings/batch writes, constraints,
Character/CharacterVirtual, vehicles, ragdolls/skeletons, SoftBody,
StateRecorder/serialization and DebugRenderer. Every future batch needs its own
ownership, field/symbol inventory and scenario gates. Custom schedulers,
allocators and query filters are also deferred.

Excluded from this release: JNI ABI/runtime, jolt-jni Java compatibility,
fork-specific APIs without official counterparts, V-HACD, Hair/GPU compute,
Android/32-bit, extra ISA variants and renderer integration. No corresponding
bridge symbol is generated or claimed supported.

[math]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Math/Vec3.h
[real]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Math/Real.h
[quat]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Math/Quat.h
[system]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/PhysicsSystem.h
[settings]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Body/BodyCreationSettings.h
[body]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Body/Body.h
[bodies]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Body/BodyInterface.h
[ray]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Collision/RayCast.h
[cast-result]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Collision/CastResult.h
[query]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Collision/NarrowPhaseQuery.h
[contact]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Collision/ContactListener.h
[memory]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Core/Memory.h
[register]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/RegisterTypes.h
[temp]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Core/TempAllocator.h
[single]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Core/JobSystemSingleThreaded.h
[pool]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Core/JobSystemThreadPool.h
[box]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Collision/Shape/BoxShape.h
[sphere]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Collision/Shape/SphereShape.h
[capsule]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Collision/Shape/CapsuleShape.h
[hull]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Collision/Shape/ConvexHullShape.h
[ref]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Core/Reference.h
[lock]: https://github.com/jrouwe/JoltPhysics/blob/e77f175595e64cb44218cc9d9d56fc365ad0e36a/Jolt/Physics/Body/BodyLock.h
