/*
Open Asset Import Library (assimp)
----------------------------------------------------------------------

Copyright (c) 2006-2026, assimp team

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice,
  this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
* Neither the name of the assimp team nor the names of its contributors may
  be used to endorse or promote products derived from this software without
  specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
----------------------------------------------------------------------

Machine-normalized public C ABI declaration set for Assimp v6.0.4,
commit e0b52347c6e52de2827ec957a9ebf00ce3c54f79.

Derived from the C branches of defs.h, types.h, importerdesc.h, cimport.h,
cexport.h, cfileio.h, texture.h, aabb.h, mesh.h, light.h, camera.h,
material.h, anim.h, metadata.h, scene.h, postprocess.h, and version.h.

Normalization removes documentation, C++ members, visibility attributes,
system includes, static-inline helpers, and preprocessor indirection. Array
extents and default single-precision ABI aliases are expanded literally, and
comma-separated field declarations are split so every field is visible to the
Caelum C AST adapter.
aiGetPredefinedLogStream is intentionally declared in the handwritten Kotlin
FFM layer because aggregate-by-value returns need a SegmentAllocator. The two
material static-inline helpers are also provided in Kotlin and are not native
symbols. All other public C exports are declared below.
*/

typedef float ai_real;
typedef signed int ai_int;
typedef unsigned int ai_uint;
typedef signed int ai_int32;
typedef unsigned int ai_uint32;
typedef unsigned long long size_t;

const int AI_MAXLEN = 1024;
const int AI_MAX_NUMBER_OF_COLOR_SETS = 8;
const int AI_MAX_NUMBER_OF_TEXTURECOORDS = 8;
const int AI_FALSE = 0;
const int AI_TRUE = 1;
const int AI_SCENE_FLAGS_INCOMPLETE = 0x1;
const int AI_SCENE_FLAGS_VALIDATED = 0x2;
const int AI_SCENE_FLAGS_VALIDATION_WARNING = 0x4;
const int AI_SCENE_FLAGS_NON_VERBOSE_FORMAT = 0x8;
const int AI_SCENE_FLAGS_TERRAIN = 0x10;
const int AI_SCENE_FLAGS_ALLOW_SHARED = 0x20;
const int ASSIMP_CFLAGS_SHARED = 0x1;
const int ASSIMP_CFLAGS_STLPORT = 0x2;
const int ASSIMP_CFLAGS_DEBUG = 0x4;
const int ASSIMP_CFLAGS_NOBOOST = 0x8;
const int ASSIMP_CFLAGS_SINGLETHREADED = 0x10;
const int ASSIMP_CFLAGS_DOUBLE_SUPPORT = 0x20;

struct aiVector2D {
    ai_real x;
    ai_real y;
};
struct aiVector3D {
    ai_real x;
    ai_real y;
    ai_real z;
};
struct aiColor4D {
    float r;
    float g;
    float b;
    float a;
};
struct aiMatrix3x3 {
    ai_real a1;
    ai_real a2;
    ai_real a3;
    ai_real b1;
    ai_real b2;
    ai_real b3;
    ai_real c1;
    ai_real c2;
    ai_real c3;
};
struct aiMatrix4x4 {
    ai_real a1;
    ai_real a2;
    ai_real a3;
    ai_real a4;
    ai_real b1;
    ai_real b2;
    ai_real b3;
    ai_real b4;
    ai_real c1;
    ai_real c2;
    ai_real c3;
    ai_real c4;
    ai_real d1;
    ai_real d2;
    ai_real d3;
    ai_real d4;
};
struct aiQuaternion {
    ai_real w;
    ai_real x;
    ai_real y;
    ai_real z;
};

struct aiPlane {
    ai_real a;
    ai_real b;
    ai_real c;
    ai_real d;
};
struct aiRay {
    struct aiVector3D pos;
    struct aiVector3D dir;
};
struct aiColor3D {
    float r;
    float g;
    float b;
};
struct aiString {
    ai_uint32 length;
    char data[1024];
};
typedef int aiReturn;
enum {
    aiReturn_SUCCESS = 0x0,
    aiReturn_FAILURE = -0x1,
    aiReturn_OUTOFMEMORY = -0x3,
    _AI_ENFORCE_ENUM_SIZE = 0x7fffffff
};
typedef int aiOrigin;
enum {
    aiOrigin_SET = 0x0,
    aiOrigin_CUR = 0x1,
    aiOrigin_END = 0x2,
    _AI_ORIGIN_ENFORCE_ENUM_SIZE = 0x7fffffff
};
typedef int aiDefaultLogStream;
enum {
    aiDefaultLogStream_FILE = 0x1,
    aiDefaultLogStream_STDOUT = 0x2,
    aiDefaultLogStream_STDERR = 0x4,
    aiDefaultLogStream_DEBUGGER = 0x8,
    _AI_DLS_ENFORCE_ENUM_SIZE = 0x7fffffff
};
struct aiMemoryInfo {
    unsigned int textures;
    unsigned int materials;
    unsigned int meshes;
    unsigned int nodes;
    unsigned int animations;
    unsigned int cameras;
    unsigned int lights;
    unsigned int total;
};
struct aiBuffer {
    const char *data;
    const char *end;
};
typedef int aiImporterFlags;
enum {
    aiImporterFlags_SupportTextFlavour = 0x1,
    aiImporterFlags_SupportBinaryFlavour = 0x2,
    aiImporterFlags_SupportCompressedFlavour = 0x4,
    aiImporterFlags_LimitedSupport = 0x8,
    aiImporterFlags_Experimental = 0x10
};
struct aiImporterDesc {
    const char *mName;
    const char *mAuthor;
    const char *mMaintainer;
    const char *mComments;
    unsigned int mFlags;
    unsigned int mMinMajor;
    unsigned int mMinMinor;
    unsigned int mMaxMajor;
    unsigned int mMaxMinor;
    const char *mFileExtensions;
};
           const struct aiImporterDesc *aiGetImporterDesc(const char *extension);
struct aiScene;
struct aiTexture;
struct aiFileIO;
typedef void (*aiLogStreamCallback)(const char * , char * );
struct aiLogStream {
    aiLogStreamCallback callback;
    char *user;
};
struct aiPropertyStore {
    char sentinel;
};
typedef int aiBool;
           const struct aiScene *aiImportFile(
        const char *pFile,
        unsigned int pFlags);
           const struct aiScene *aiImportFileEx(
        const char *pFile,
        unsigned int pFlags,
        struct aiFileIO *pFS);
           const struct aiScene *aiImportFileExWithProperties(
        const char *pFile,
        unsigned int pFlags,
        struct aiFileIO *pFS,
        const struct aiPropertyStore *pProps);
           const struct aiScene *aiImportFileFromMemory(
        const char *pBuffer,
        unsigned int pLength,
        unsigned int pFlags,
        const char *pHint);
           const struct aiScene *aiImportFileFromMemoryWithProperties(
        const char *pBuffer,
        unsigned int pLength,
        unsigned int pFlags,
        const char *pHint,
        const struct aiPropertyStore *pProps);
           const struct aiScene *aiApplyPostProcessing(
        const struct aiScene *pScene,
        unsigned int pFlags);
void aiAttachLogStream(
        const struct aiLogStream *stream);
           void aiEnableVerboseLogging(aiBool d);
           aiReturn aiDetachLogStream(
        const struct aiLogStream *stream);
           void aiDetachAllLogStreams(void);
           void aiReleaseImport(
        const struct aiScene *pScene);
           const char *aiGetErrorString(void);
           aiBool aiIsExtensionSupported(
        const char *szExtension);
           void aiGetExtensionList(
        struct aiString *szOut);
           void aiGetMemoryRequirements(
        const struct aiScene *pIn,
        struct aiMemoryInfo *in);
           const struct aiTexture *aiGetEmbeddedTexture(const struct aiScene *pIn, const char *filename);
           struct aiPropertyStore *aiCreatePropertyStore(void);
           void aiReleasePropertyStore(struct aiPropertyStore *p);
           void aiSetImportPropertyInteger(
        struct aiPropertyStore *store,
        const char *szName,
        int value);
           void aiSetImportPropertyFloat(
        struct aiPropertyStore *store,
        const char *szName,
        ai_real value);
           void aiSetImportPropertyString(
        struct aiPropertyStore *store,
        const char *szName,
        const struct aiString *st);
           void aiSetImportPropertyMatrix(
        struct aiPropertyStore *store,
        const char *szName,
        const struct aiMatrix4x4 *mat);
           void aiCreateQuaternionFromMatrix(
        struct aiQuaternion *quat,
        const struct aiMatrix3x3 *mat);
           void aiDecomposeMatrix(
        const struct aiMatrix4x4 *mat,
        struct aiVector3D *scaling,
        struct aiQuaternion *rotation,
        struct aiVector3D *position);
           void aiTransposeMatrix4(
        struct aiMatrix4x4 *mat);
           void aiTransposeMatrix3(
        struct aiMatrix3x3 *mat);
           void aiTransformVecByMatrix3(
        struct aiVector3D *vec,
        const struct aiMatrix3x3 *mat);
           void aiTransformVecByMatrix4(
        struct aiVector3D *vec,
        const struct aiMatrix4x4 *mat);
           void aiMultiplyMatrix4(
        struct aiMatrix4x4 *dst,
        const struct aiMatrix4x4 *src);
           void aiMultiplyMatrix3(
        struct aiMatrix3x3 *dst,
        const struct aiMatrix3x3 *src);
           void aiIdentityMatrix3(
        struct aiMatrix3x3 *mat);
           void aiIdentityMatrix4(
        struct aiMatrix4x4 *mat);
           size_t aiGetImportFormatCount(void);
           const struct aiImporterDesc *aiGetImportFormatDescription(size_t pIndex);
           int aiVector2AreEqual(
        const struct aiVector2D *a,
        const struct aiVector2D *b);
           int aiVector2AreEqualEpsilon(
        const struct aiVector2D *a,
        const struct aiVector2D *b,
        const float epsilon);
           void aiVector2Add(
        struct aiVector2D *dst,
        const struct aiVector2D *src);
           void aiVector2Subtract(
        struct aiVector2D *dst,
        const struct aiVector2D *src);
           void aiVector2Scale(
        struct aiVector2D *dst,
        const float s);
           void aiVector2SymMul(
        struct aiVector2D *dst,
        const struct aiVector2D *other);
           void aiVector2DivideByScalar(
        struct aiVector2D *dst,
        const float s);
           void aiVector2DivideByVector(
        struct aiVector2D *dst,
        struct aiVector2D *v);
           ai_real aiVector2Length(
        const struct aiVector2D *v);
           ai_real aiVector2SquareLength(
        const struct aiVector2D *v);
           void aiVector2Negate(
        struct aiVector2D *dst);
           ai_real aiVector2DotProduct(
        const struct aiVector2D *a,
        const struct aiVector2D *b);
           void aiVector2Normalize(
        struct aiVector2D *v);
           int aiVector3AreEqual(
        const struct aiVector3D *a,
        const struct aiVector3D *b);
           int aiVector3AreEqualEpsilon(
        const struct aiVector3D *a,
        const struct aiVector3D *b,
        const float epsilon);
           int aiVector3LessThan(
        const struct aiVector3D *a,
        const struct aiVector3D *b);
           void aiVector3Add(
        struct aiVector3D *dst,
        const struct aiVector3D *src);
           void aiVector3Subtract(
        struct aiVector3D *dst,
        const struct aiVector3D *src);
           void aiVector3Scale(
        struct aiVector3D *dst,
        const float s);
           void aiVector3SymMul(
        struct aiVector3D *dst,
        const struct aiVector3D *other);
           void aiVector3DivideByScalar(
        struct aiVector3D *dst,
        const float s);
           void aiVector3DivideByVector(
        struct aiVector3D *dst,
        struct aiVector3D *v);
           ai_real aiVector3Length(
        const struct aiVector3D *v);
           ai_real aiVector3SquareLength(
        const struct aiVector3D *v);
           void aiVector3Negate(
        struct aiVector3D *dst);
           ai_real aiVector3DotProduct(
        const struct aiVector3D *a,
        const struct aiVector3D *b);
           void aiVector3CrossProduct(
        struct aiVector3D *dst,
        const struct aiVector3D *a,
        const struct aiVector3D *b);
           void aiVector3Normalize(
        struct aiVector3D *v);
           void aiVector3NormalizeSafe(
        struct aiVector3D *v);
           void aiVector3RotateByQuaternion(
        struct aiVector3D *v,
        const struct aiQuaternion *q);
           void aiMatrix3FromMatrix4(
        struct aiMatrix3x3 *dst,
        const struct aiMatrix4x4 *mat);
           void aiMatrix3FromQuaternion(
        struct aiMatrix3x3 *mat,
        const struct aiQuaternion *q);
           int aiMatrix3AreEqual(
        const struct aiMatrix3x3 *a,
        const struct aiMatrix3x3 *b);
           int aiMatrix3AreEqualEpsilon(
        const struct aiMatrix3x3 *a,
        const struct aiMatrix3x3 *b,
        const float epsilon);
           void aiMatrix3Inverse(
        struct aiMatrix3x3 *mat);
           ai_real aiMatrix3Determinant(
        const struct aiMatrix3x3 *mat);
           void aiMatrix3RotationZ(
        struct aiMatrix3x3 *mat,
        const float angle);
           void aiMatrix3FromRotationAroundAxis(
        struct aiMatrix3x3 *mat,
        const struct aiVector3D *axis,
        const float angle);
           void aiMatrix3Translation(
        struct aiMatrix3x3 *mat,
        const struct aiVector2D *translation);
           void aiMatrix3FromTo(
        struct aiMatrix3x3 *mat,
        const struct aiVector3D *from,
        const struct aiVector3D *to);
           void aiMatrix4FromMatrix3(
        struct aiMatrix4x4 *dst,
        const struct aiMatrix3x3 *mat);
           void aiMatrix4FromScalingQuaternionPosition(
        struct aiMatrix4x4 *mat,
        const struct aiVector3D *scaling,
        const struct aiQuaternion *rotation,
        const struct aiVector3D *position);
           void aiMatrix4Add(
        struct aiMatrix4x4 *dst,
        const struct aiMatrix4x4 *src);
           int aiMatrix4AreEqual(
        const struct aiMatrix4x4 *a,
        const struct aiMatrix4x4 *b);
           int aiMatrix4AreEqualEpsilon(
        const struct aiMatrix4x4 *a,
        const struct aiMatrix4x4 *b,
        const float epsilon);
           void aiMatrix4Inverse(
        struct aiMatrix4x4 *mat);
           ai_real aiMatrix4Determinant(
        const struct aiMatrix4x4 *mat);
           int aiMatrix4IsIdentity(
        const struct aiMatrix4x4 *mat);
           void aiMatrix4DecomposeIntoScalingEulerAnglesPosition(
        const struct aiMatrix4x4 *mat,
        struct aiVector3D *scaling,
        struct aiVector3D *rotation,
        struct aiVector3D *position);
           void aiMatrix4DecomposeIntoScalingAxisAnglePosition(
        const struct aiMatrix4x4 *mat,
        struct aiVector3D *scaling,
        struct aiVector3D *axis,
        ai_real *angle,
        struct aiVector3D *position);
           void aiMatrix4DecomposeNoScaling(
        const struct aiMatrix4x4 *mat,
        struct aiQuaternion *rotation,
        struct aiVector3D *position);
           void aiMatrix4FromEulerAngles(
        struct aiMatrix4x4 *mat,
        float x, float y, float z);
           void aiMatrix4RotationX(
        struct aiMatrix4x4 *mat,
        const float angle);
           void aiMatrix4RotationY(
        struct aiMatrix4x4 *mat,
        const float angle);
           void aiMatrix4RotationZ(
        struct aiMatrix4x4 *mat,
        const float angle);
           void aiMatrix4FromRotationAroundAxis(
        struct aiMatrix4x4 *mat,
        const struct aiVector3D *axis,
        const float angle);
           void aiMatrix4Translation(
        struct aiMatrix4x4 *mat,
        const struct aiVector3D *translation);
           void aiMatrix4Scaling(
        struct aiMatrix4x4 *mat,
        const struct aiVector3D *scaling);
           void aiMatrix4FromTo(
        struct aiMatrix4x4 *mat,
        const struct aiVector3D *from,
        const struct aiVector3D *to);
           void aiQuaternionFromEulerAngles(
        struct aiQuaternion *q,
        float x, float y, float z);
           void aiQuaternionFromAxisAngle(
        struct aiQuaternion *q,
        const struct aiVector3D *axis,
        const float angle);
           void aiQuaternionFromNormalizedQuaternion(
        struct aiQuaternion *q,
        const struct aiVector3D *normalized);
           int aiQuaternionAreEqual(
        const struct aiQuaternion *a,
        const struct aiQuaternion *b);
           int aiQuaternionAreEqualEpsilon(
        const struct aiQuaternion *a,
        const struct aiQuaternion *b,
        const float epsilon);
           void aiQuaternionNormalize(
        struct aiQuaternion *q);
           void aiQuaternionConjugate(
        struct aiQuaternion *q);
           void aiQuaternionMultiply(
        struct aiQuaternion *dst,
        const struct aiQuaternion *q);
           void aiQuaternionInterpolate(
        struct aiQuaternion *dst,
        const struct aiQuaternion *start,
        const struct aiQuaternion *end,
        const float factor);
struct aiScene;
struct aiFileIO;
struct aiExportFormatDesc {
    const char *id;
    const char *description;
    const char *fileExtension;
};
           size_t aiGetExportFormatCount(void);
           const struct aiExportFormatDesc *aiGetExportFormatDescription(size_t pIndex);
           void aiReleaseExportFormatDescription(const struct aiExportFormatDesc *desc);
           void aiCopyScene(const struct aiScene *pIn,
        struct aiScene **pOut);
           void aiFreeScene(const struct aiScene *pIn);
           aiReturn aiExportScene(const struct aiScene *pScene,
        const char *pFormatId,
        const char *pFileName,
        unsigned int pPreprocessing);
           aiReturn aiExportSceneEx(const struct aiScene *pScene,
        const char *pFormatId,
        const char *pFileName,
        struct aiFileIO *pIO,
        unsigned int pPreprocessing);
struct aiExportDataBlob {
    size_t size;
    void *data;
    struct aiString name;
    struct aiExportDataBlob *next;
};
           const struct aiExportDataBlob *aiExportSceneToBlob(const struct aiScene *pScene, const char *pFormatId,
        unsigned int pPreprocessing);
           void aiReleaseExportBlob(const struct aiExportDataBlob *pData);
struct aiFileIO;
struct aiFile;
typedef size_t (*aiFileWriteProc) (struct aiFile*, const char*, size_t, size_t);
typedef size_t (*aiFileReadProc) (struct aiFile*, char*, size_t,size_t);
typedef size_t (*aiFileTellProc) (struct aiFile*);
typedef void (*aiFileFlushProc) (struct aiFile*);
typedef aiReturn (*aiFileSeek) (struct aiFile*, size_t, aiOrigin);
typedef struct aiFile* (*aiFileOpenProc) (struct aiFileIO*, const char*, const char*);
typedef void (*aiFileCloseProc) (struct aiFileIO*, struct aiFile*);
typedef char* aiUserData;
struct aiFileIO
{
    aiFileOpenProc OpenProc;
    aiFileCloseProc CloseProc;
    aiUserData UserData;
};
struct aiFile {
    aiFileReadProc ReadProc;
    aiFileWriteProc WriteProc;
    aiFileTellProc TellProc;
    aiFileTellProc FileSizeProc;
    aiFileSeek SeekProc;
    aiFileFlushProc FlushProc;
    aiUserData UserData;
};
struct aiTexel {
    unsigned char b;
    unsigned char g;
    unsigned char r;
    unsigned char a;
} __attribute__((__packed__));
struct aiTexture {
    unsigned int mWidth;
    unsigned int mHeight;
    char achFormatHint[ 9 ];
    struct aiTexel* pcData;
    struct aiString mFilename;
};
struct aiAABB {
    struct aiVector3D mMin;
    struct aiVector3D mMax;
};
struct aiFace {
    unsigned int mNumIndices;
    unsigned int *mIndices;
};
struct aiVertexWeight {
    unsigned int mVertexId;
    ai_real mWeight;
};
struct aiNode;
struct aiBone {
    struct aiString mName;
    unsigned int mNumWeights;
    struct aiNode *mArmature;
    struct aiNode *mNode;
    struct aiVertexWeight *mWeights;
    struct aiMatrix4x4 mOffsetMatrix;
};
typedef int aiPrimitiveType;
enum {
    aiPrimitiveType_POINT = 0x1,
    aiPrimitiveType_LINE = 0x2,
    aiPrimitiveType_TRIANGLE = 0x4,
    aiPrimitiveType_POLYGON = 0x8,
    aiPrimitiveType_NGONEncodingFlag = 0x10,
    _aiPrimitiveType_Force32Bit = 2147483647
};
struct aiAnimMesh {
    struct aiString mName;
    struct aiVector3D *mVertices;
    struct aiVector3D *mNormals;
    struct aiVector3D *mTangents;
    struct aiVector3D *mBitangents;
    struct aiColor4D *mColors[0x8];
    struct aiVector3D *mTextureCoords[0x8];
    unsigned int mNumVertices;
    float mWeight;
};
typedef int aiMorphingMethod;
enum {
    aiMorphingMethod_UNKNOWN = 0x0,
    aiMorphingMethod_VERTEX_BLEND = 0x1,
    aiMorphingMethod_MORPH_NORMALIZED = 0x2,
    aiMorphingMethod_MORPH_RELATIVE = 0x3,
    _aiMorphingMethod_Force32Bit = 2147483647
};
struct aiMesh {
    unsigned int mPrimitiveTypes;
    unsigned int mNumVertices;
    unsigned int mNumFaces;
    struct aiVector3D *mVertices;
    struct aiVector3D *mNormals;
    struct aiVector3D *mTangents;
    struct aiVector3D *mBitangents;
    struct aiColor4D *mColors[0x8];
    struct aiVector3D *mTextureCoords[0x8];
    unsigned int mNumUVComponents[0x8];
    struct aiFace *mFaces;
    unsigned int mNumBones;
    struct aiBone **mBones;
    unsigned int mMaterialIndex;
    struct aiString mName;
    unsigned int mNumAnimMeshes;
    struct aiAnimMesh **mAnimMeshes;
    aiMorphingMethod mMethod;
    struct aiAABB mAABB;
    struct aiString **mTextureCoordsNames;
};
struct aiSkeletonBone {
    int mParent;
    struct aiNode *mArmature;
    struct aiNode *mNode;
    unsigned int mNumnWeights;
    struct aiMesh *mMeshId;
    struct aiVertexWeight *mWeights;
    struct aiMatrix4x4 mOffsetMatrix;
    struct aiMatrix4x4 mLocalMatrix;
};
struct aiSkeleton {
    struct aiString mName;
    unsigned int mNumBones;
    struct aiSkeletonBone **mBones;
};
typedef int aiLightSourceType;
enum {
    aiLightSource_UNDEFINED = 0x0,
    aiLightSource_DIRECTIONAL = 0x1,
    aiLightSource_POINT = 0x2,
    aiLightSource_SPOT = 0x3,
    aiLightSource_AMBIENT = 0x4,
    aiLightSource_AREA = 0x5,
    _aiLightSource_Force32Bit = 2147483647
};
struct aiLight {
    struct aiString mName;
    aiLightSourceType mType;
    struct aiVector3D mPosition;
    struct aiVector3D mDirection;
    struct aiVector3D mUp;
    float mAttenuationConstant;
    float mAttenuationLinear;
    float mAttenuationQuadratic;
    struct aiColor3D mColorDiffuse;
    struct aiColor3D mColorSpecular;
    struct aiColor3D mColorAmbient;
    float mAngleInnerCone;
    float mAngleOuterCone;
    struct aiVector2D mSize;
};
struct aiCamera {
    struct aiString mName;
    struct aiVector3D mPosition;
    struct aiVector3D mUp;
    struct aiVector3D mLookAt;
    float mHorizontalFOV;
    float mClipPlaneNear;
    float mClipPlaneFar;
    float mAspect;
    float mOrthographicWidth;
};
typedef int aiTextureOp;
enum {
    aiTextureOp_Multiply = 0x0,
    aiTextureOp_Add = 0x1,
    aiTextureOp_Subtract = 0x2,
    aiTextureOp_Divide = 0x3,
    aiTextureOp_SmoothAdd = 0x4,
    aiTextureOp_SignedAdd = 0x5,
    _aiTextureOp_Force32Bit = 2147483647
};
typedef int aiTextureMapMode;
enum {
    aiTextureMapMode_Wrap = 0x0,
    aiTextureMapMode_Clamp = 0x1,
    aiTextureMapMode_Decal = 0x3,
    aiTextureMapMode_Mirror = 0x2,
    _aiTextureMapMode_Force32Bit = 2147483647
};
typedef int aiTextureMapping;
enum {
    aiTextureMapping_UV = 0x0,
    aiTextureMapping_SPHERE = 0x1,
    aiTextureMapping_CYLINDER = 0x2,
    aiTextureMapping_BOX = 0x3,
    aiTextureMapping_PLANE = 0x4,
    aiTextureMapping_OTHER = 0x5,
    _aiTextureMapping_Force32Bit = 2147483647
};
typedef int aiTextureType;
enum {
    aiTextureType_NONE = 0,
    aiTextureType_DIFFUSE = 1,
    aiTextureType_SPECULAR = 2,
    aiTextureType_AMBIENT = 3,
    aiTextureType_EMISSIVE = 4,
    aiTextureType_HEIGHT = 5,
    aiTextureType_NORMALS = 6,
    aiTextureType_SHININESS = 7,
    aiTextureType_OPACITY = 8,
    aiTextureType_DISPLACEMENT = 9,
    aiTextureType_LIGHTMAP = 10,
    aiTextureType_REFLECTION = 11,
    aiTextureType_BASE_COLOR = 12,
    aiTextureType_NORMAL_CAMERA = 13,
    aiTextureType_EMISSION_COLOR = 14,
    aiTextureType_METALNESS = 15,
    aiTextureType_DIFFUSE_ROUGHNESS = 16,
    aiTextureType_AMBIENT_OCCLUSION = 17,
    aiTextureType_UNKNOWN = 18,
    aiTextureType_SHEEN = 19,
    aiTextureType_CLEARCOAT = 20,
    aiTextureType_TRANSMISSION = 21,
    aiTextureType_MAYA_BASE = 22,
    aiTextureType_MAYA_SPECULAR = 23,
    aiTextureType_MAYA_SPECULAR_COLOR = 24,
    aiTextureType_MAYA_SPECULAR_ROUGHNESS = 25,
    aiTextureType_ANISOTROPY = 26,
    aiTextureType_GLTF_METALLIC_ROUGHNESS = 27,
    _aiTextureType_Force32Bit = 2147483647
};
           const char *aiTextureTypeToString(aiTextureType in);
typedef int aiShadingMode;
enum {
    aiShadingMode_Flat = 0x1,
    aiShadingMode_Gouraud = 0x2,
    aiShadingMode_Phong = 0x3,
    aiShadingMode_Blinn = 0x4,
    aiShadingMode_Toon = 0x5,
    aiShadingMode_OrenNayar = 0x6,
    aiShadingMode_Minnaert = 0x7,
    aiShadingMode_CookTorrance = 0x8,
    aiShadingMode_NoShading = 0x9,
    aiShadingMode_Unlit = 0x9,
    aiShadingMode_Fresnel = 0xa,
    aiShadingMode_PBR_BRDF = 0xb,
    _aiShadingMode_Force32Bit = 2147483647
};
typedef int aiTextureFlags;
enum {
    aiTextureFlags_Invert = 0x1,
    aiTextureFlags_UseAlpha = 0x2,
    aiTextureFlags_IgnoreAlpha = 0x4,
    _aiTextureFlags_Force32Bit = 2147483647
};
typedef int aiBlendMode;
enum {
    aiBlendMode_Default = 0x0,
    aiBlendMode_Additive = 0x1,
    _aiBlendMode_Force32Bit = 2147483647
};
struct aiUVTransform {
    struct aiVector2D mTranslation;
    struct aiVector2D mScaling;
    ai_real mRotation;
};
typedef int aiPropertyTypeInfo;
enum {
    aiPTI_Float = 0x1,
    aiPTI_Double = 0x2,
    aiPTI_String = 0x3,
    aiPTI_Integer = 0x4,
    aiPTI_Buffer = 0x5,
    _aiPTI_Force32Bit = 2147483647
};
struct aiMaterialProperty {
    struct aiString mKey;
    unsigned int mSemantic;
    unsigned int mIndex;
    unsigned int mDataLength;
    aiPropertyTypeInfo mType;
    char *mData;
};
struct aiMaterial
{
    struct aiMaterialProperty **mProperties;
    unsigned int mNumProperties;
    unsigned int mNumAllocated;
};
           aiReturn aiGetMaterialProperty(
        const struct aiMaterial *pMat,
        const char *pKey,
        unsigned int type,
        unsigned int index,
        const struct aiMaterialProperty **pPropOut);
           aiReturn aiGetMaterialFloatArray(
        const struct aiMaterial *pMat,
        const char *pKey,
        unsigned int type,
        unsigned int index,
        ai_real *pOut,
        unsigned int *pMax);
aiReturn aiGetMaterialIntegerArray(const struct aiMaterial *pMat,
        const char *pKey,
        unsigned int type,
        unsigned int index,
        int *pOut,
        unsigned int *pMax);
aiReturn aiGetMaterialColor(const struct aiMaterial *pMat,
        const char *pKey,
        unsigned int type,
        unsigned int index,
        struct aiColor4D *pOut);
           aiReturn aiGetMaterialUVTransform(const struct aiMaterial *pMat,
        const char *pKey,
        unsigned int type,
        unsigned int index,
        struct aiUVTransform *pOut);
           aiReturn aiGetMaterialString(const struct aiMaterial *pMat,
        const char *pKey,
        unsigned int type,
        unsigned int index,
        struct aiString *pOut);
           unsigned int aiGetMaterialTextureCount(const struct aiMaterial *pMat,
        aiTextureType type);
aiReturn aiGetMaterialTexture(const struct aiMaterial *mat,
        aiTextureType type,
        unsigned int index,
        struct aiString *path,
        aiTextureMapping *mapping ,
        unsigned int *uvindex ,
        ai_real *blend ,
        aiTextureOp *op ,
        aiTextureMapMode *mapmode ,
        unsigned int *flags );
typedef int aiAnimInterpolation;
enum {
    aiAnimInterpolation_Step,
    aiAnimInterpolation_Linear,
    aiAnimInterpolation_Spherical_Linear,
    aiAnimInterpolation_Cubic_Spline,
    _aiAnimInterpolation_Force32Bit = 2147483647
};
struct aiVectorKey {
    double mTime;
    struct aiVector3D mValue;
    aiAnimInterpolation mInterpolation;
};
struct aiQuatKey {
    double mTime;
    struct aiQuaternion mValue;
    aiAnimInterpolation mInterpolation;
};
struct aiMeshKey {
    double mTime;
    unsigned int mValue;
};
struct aiMeshMorphKey {
    double mTime;
    unsigned int *mValues;
    double *mWeights;
    unsigned int mNumValuesAndWeights;
};
typedef int aiAnimBehaviour;
enum {
    aiAnimBehaviour_DEFAULT = 0x0,
    aiAnimBehaviour_CONSTANT = 0x1,
    aiAnimBehaviour_LINEAR = 0x2,
    aiAnimBehaviour_REPEAT = 0x3,
    _aiAnimBehaviour_Force32Bit = 2147483647
};
struct aiNodeAnim {
    struct aiString mNodeName;
    unsigned int mNumPositionKeys;
    struct aiVectorKey *mPositionKeys;
    unsigned int mNumRotationKeys;
    struct aiQuatKey *mRotationKeys;
    unsigned int mNumScalingKeys;
    struct aiVectorKey *mScalingKeys;
    aiAnimBehaviour mPreState;
    aiAnimBehaviour mPostState;
};
struct aiMeshAnim {
    struct aiString mName;
    unsigned int mNumKeys;
    struct aiMeshKey *mKeys;
};
struct aiMeshMorphAnim {
    struct aiString mName;
    unsigned int mNumKeys;
    struct aiMeshMorphKey *mKeys;
};
struct aiAnimation {
    struct aiString mName;
    double mDuration;
    double mTicksPerSecond;
    unsigned int mNumChannels;
    struct aiNodeAnim **mChannels;
    unsigned int mNumMeshChannels;
    struct aiMeshAnim **mMeshChannels;
    unsigned int mNumMorphMeshChannels;
    struct aiMeshMorphAnim **mMorphMeshChannels;
};
typedef int aiMetadataType;
enum {
    AI_BOOL = 0,
    AI_INT32 = 1,
    AI_UINT64 = 2,
    AI_FLOAT = 3,
    AI_DOUBLE = 4,
    AI_AISTRING = 5,
    AI_AIVECTOR3D = 6,
    AI_AIMETADATA = 7,
    AI_INT64 = 8,
    AI_UINT32 = 9,
    AI_META_MAX = 10,
    FORCE_32BIT = 2147483647
};
struct aiMetadataEntry {
    aiMetadataType mType;
    void *mData;
};
struct aiMetadata {
    unsigned int mNumProperties;
    struct aiString *mKeys;
    struct aiMetadataEntry *mValues;
};
struct aiNode {
    struct aiString mName;
    struct aiMatrix4x4 mTransformation;
    struct aiNode* mParent;
    unsigned int mNumChildren;
    struct aiNode** mChildren;
    unsigned int mNumMeshes;
    unsigned int* mMeshes;
    struct aiMetadata* mMetaData;
};
struct aiScene {
    unsigned int mFlags;
    struct aiNode* mRootNode;
    unsigned int mNumMeshes;
    struct aiMesh** mMeshes;
    unsigned int mNumMaterials;
    struct aiMaterial** mMaterials;
    unsigned int mNumAnimations;
    struct aiAnimation** mAnimations;
    unsigned int mNumTextures;
    struct aiTexture** mTextures;
    unsigned int mNumLights;
    struct aiLight** mLights;
    unsigned int mNumCameras;
    struct aiCamera** mCameras;
    struct aiMetadata* mMetaData;
    struct aiString mName;
    unsigned int mNumSkeletons;
    struct aiSkeleton **mSkeletons;
    char* mPrivate;
};
typedef int aiPostProcessSteps;
enum {
    aiProcess_CalcTangentSpace = 0x1,
    aiProcess_JoinIdenticalVertices = 0x2,
    aiProcess_MakeLeftHanded = 0x4,
    aiProcess_Triangulate = 0x8,
    aiProcess_RemoveComponent = 0x10,
    aiProcess_GenNormals = 0x20,
    aiProcess_GenSmoothNormals = 0x40,
    aiProcess_SplitLargeMeshes = 0x80,
    aiProcess_PreTransformVertices = 0x100,
    aiProcess_LimitBoneWeights = 0x200,
    aiProcess_ValidateDataStructure = 0x400,
    aiProcess_ImproveCacheLocality = 0x800,
    aiProcess_RemoveRedundantMaterials = 0x1000,
    aiProcess_FixInfacingNormals = 0x2000,
    aiProcess_PopulateArmatureData = 0x4000,
    aiProcess_SortByPType = 0x8000,
    aiProcess_FindDegenerates = 0x10000,
    aiProcess_FindInvalidData = 0x20000,
    aiProcess_GenUVCoords = 0x40000,
    aiProcess_TransformUVCoords = 0x80000,
    aiProcess_FindInstances = 0x100000,
    aiProcess_OptimizeMeshes = 0x200000,
    aiProcess_OptimizeGraph = 0x400000,
    aiProcess_FlipUVs = 0x800000,
    aiProcess_FlipWindingOrder = 0x1000000,
    aiProcess_SplitByBoneCount = 0x2000000,
    aiProcess_Debone = 0x4000000,
    aiProcess_GlobalScale = 0x8000000,
    aiProcess_EmbedTextures = 0x10000000,
    aiProcess_ForceGenNormals = 0x20000000,
    aiProcess_DropNormals = 0x40000000,
    aiProcess_GenBoundingBoxes = 0x80000000
};
 const char* aiGetLegalString (void);
           unsigned int aiGetVersionPatch(void);
           unsigned int aiGetVersionMinor (void);
           unsigned int aiGetVersionMajor (void);
           unsigned int aiGetVersionRevision (void);
           const char *aiGetBranchName();
           unsigned int aiGetCompileFlags(void);

