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

static const float3 SKY_ZENITH_VIVID  = float3(0.25, 0.52, 0.95);
static const float3 SKY_HORIZON_VIVID = float3(0.65, 0.82, 0.98);
static const float SUN_DISC_HALF_ANGLE = 0.29146;

[shader("miss")]
void main(inout DhReflectionPayload payload) {
    payload.hitT = -1.0;
    float3 dir = WorldRayDirection();
    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    float3 sky = lerp(SKY_HORIZON_VIVID, SKY_ZENITH_VIVID, t);

    float lum = pc.skyAmbient.w > 0.0 ? pc.skyAmbient.w : 1.0;
    sky *= lum;

    float sunDot = dot(dir, normalize(pc.skyLightDir.xyz));
    if (sunDot > cos(SUN_DISC_HALF_ANGLE) && pc.skyLightDir.y > 0.0) {
        sky += pc.skyLightRadiance.xyz * 0.5;
    }
    payload.color = sky;
}
