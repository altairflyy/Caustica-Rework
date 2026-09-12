[[vk::binding(0, 0)]] RaytracingAccelerationStructure topLevelAS;
[[vk::binding(1, 0)]] [format("rgba8")] RWTexture2D<float4> reflectionOutput;
[[vk::binding(2, 0)]] Sampler2D dhColor;
[[vk::binding(3, 0)]] Sampler2D dhDepth;
[[vk::binding(4, 0)]] Sampler2D dhWaterMask;

struct DhReflectionPushConstants {
    float4x4 nativeInvViewProj;
    float4 skyLightDir;
    float4 skyLightRadiance;
    float4 skyAmbient;
    float nativeDepthClear;
    int renderWidth;
    int renderHeight;
    int pad;
};
[[vk::push_constant]] DhReflectionPushConstants pc;

struct DhReflectionPayload {
    float3 color;
    float hitT;
};

RayDesc makeRay(float3 origin, float tmin, float3 dir, float tmax) {
    RayDesc r;
    r.Origin = origin;
    r.TMin = tmin;
    r.Direction = dir;
    r.TMax = tmax;
    return r;
}

[shader("raygeneration")]
void main() {
    uint2 pix = DispatchRaysIndex().xy;
    uint2 size = DispatchRaysDimensions().xy;
    if (pix.x >= size.x || pix.y >= size.y) return;

    float2 uv = (float2(pix) + 0.5) / float2(size);
    float water = dhWaterMask.SampleLevel(uv, 0.0).r;
    if (water < 0.5) {
        reflectionOutput[pix] = float4(0.0);
        return;
    }

    float depth = dhDepth.SampleLevel(uv, 0.0).r;
    if (abs(depth - pc.nativeDepthClear) <= 1.0e-6) {
        reflectionOutput[pix] = float4(0.0);
        return;
    }

    // Reconstruct camera-relative world position
    float4 homogeneous = mul(pc.nativeInvViewProj, float4(uv * 2.0 - 1.0, depth, 1.0));
    if (abs(homogeneous.w) <= 1.0e-8) {
        reflectionOutput[pix] = float4(0.0);
        return;
    }
    float3 cameraRelativeP = homogeneous.xyz / homogeneous.w;

    float3 I = normalize(cameraRelativeP);
    float3 N = float3(0.0, 1.0, 0.0); // flat water normal
    float3 R = reflect(I, N);
    if (R.y < 0.001) {
        R.y = 0.001;
        R = normalize(R);
    }

    DhReflectionPayload payload;
    payload.color = float3(0.0);
    payload.hitT = -1.0;

    // Trace secondary ray against far-field proxy TLAS (instance mask 0x01)
    TraceRay(topLevelAS, RAY_FLAG_CULL_BACK_FACING_TRIANGLES, 0x01, 0, 0, 0,
             makeRay(cameraRelativeP + N * 0.1, 0.1, R, 10000.0), payload);

    reflectionOutput[pix] = float4(payload.color, 1.0);
}
