struct DhReflectionPayload {
    float3 color;
    float hitT;
};

[shader("closesthit")]
void main(inout DhReflectionPayload payload, BuiltInTriangleIntersectionAttributes attr) {
    payload.hitT = RayTCurrent();
    float3 hitPos = WorldRayOrigin() + WorldRayDirection() * RayTCurrent();
    float y = hitPos.y;
    // Macro terrain coloration based on altitude
    float3 grass = float3(0.18, 0.28, 0.12);
    float3 rock = float3(0.35, 0.33, 0.30);
    float3 snow = float3(0.85, 0.88, 0.92);
    float3 col = lerp(grass, rock, clamp((y - 70.0) / 50.0, 0.0, 1.0));
    col = lerp(col, snow, clamp((y - 140.0) / 40.0, 0.0, 1.0));
    payload.color = col;
}
