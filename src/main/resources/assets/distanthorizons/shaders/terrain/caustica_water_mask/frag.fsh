#version 330

in vec3 vPos;
in vec4 vertexColor;
in vec3 vertexWorldPos;
in vec3 vBlockPos;
flat in uint vNormalIndex;
flat in uint vTextureTileId;
flat in uint vMaterialId;
flat in uint vPackedLight;
in vec4 vBaseColor;
in vec4 gl_FragCoord;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 surfaceData;

uniform sampler2D uBlockAtlas;

layout (std140) uniform fragUniformBlock
{
    float uClipDistance;
    float uNoiseIntensity;
    int uNoiseSteps;
    int uNoiseDropoff;
    bool uDitherDhRendering;
    bool uNoiseEnabled;
};

float rand(float co) { return fract(sin(co * 91.3458) * 47453.5453); }
float rand(vec2 co) { return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453); }
float rand(vec3 co) { return rand(co.xy + rand(co.z)); }

vec3 quantize(vec3 val, int stepSize)
{
    return floor(val * stepSize) / stepSize;
}

void applyNoise(inout vec4 color, const in float viewDist)
{
    vec3 vertexNormal = normalize(cross(dFdy(vPos.xyz), dFdx(vPos.xyz)));
    vec3 fixedVPos = vPos.xyz + vertexNormal * 0.001;
    float noiseAmplification = uNoiseIntensity;
    float lum = (color.r + color.g + color.b) / 3.0;
    noiseAmplification = (1.0 - pow(lum * 2.0 - 1.0, 2.0)) * noiseAmplification;
    noiseAmplification *= color.a;
    float randomValue = rand(quantize(fixedVPos, uNoiseSteps))
            * 2.0 * noiseAmplification - noiseAmplification;
    vec3 newCol = color.rgb + (1.0 - color.rgb) * randomValue;
    newCol = clamp(newCol, 0.0, 1.0);
    if (uNoiseDropoff != 0) {
        float distF = min(viewDist / uNoiseDropoff, 1.0);
        newCol = mix(newCol, color.rgb, distF);
    }
    color.rgb = newCol;
}

float bayerMatrix4x4(vec2 st)
{
    int x = int(mod(st.x, 4.0));
    int y = int(mod(st.y, 4.0));
    float bayer4x4[16] = float[16](
        0.0,  8.0,  2.0, 10.0,
        12.0,  4.0, 14.0,  6.0,
        3.0, 11.0,  1.0,  9.0,
        15.0,  7.0, 13.0,  5.0
    );
    return bayer4x4[y * 4 + x] / 16.0;
}

vec2 blockFaceUv()
{
    vec3 pos = fract(vBlockPos);
    switch (vNormalIndex)
    {
        case 0u: return vec2(pos.x, 1.0 - pos.z);
        case 1u: return vec2(pos.x, pos.z);
        case 2u: return vec2(1.0 - pos.x, 1.0 - pos.y);
        case 3u: return vec2(pos.x, 1.0 - pos.y);
        case 4u: return vec2(pos.z, 1.0 - pos.y);
        default: return vec2(1.0 - pos.z, 1.0 - pos.y);
    }
}

void main()
{
    fragColor = vertexColor;
    vec4 baseColor = vBaseColor;
    if (vTextureTileId != 0u)
    {
        ivec2 tileOrigin = ivec2(int(vTextureTileId % 256u), int(vTextureTileId / 256u)) * 16;
        ivec2 texelPos = tileOrigin + ivec2(clamp(blockFaceUv() * 16.0, 0.0, 15.0));
        vec4 tile = texelFetch(uBlockAtlas, texelPos, 0);
        vec3 clampedColor = clamp(fragColor.rgb * (tile.rgb * 2.0), 0.0, 1.0);
        fragColor.rgb = mix(fragColor.rgb, clampedColor, tile.a);
        vec3 clampedBase = clamp(baseColor.rgb * (tile.rgb * 2.0), 0.0, 1.0);
        baseColor.rgb = mix(baseColor.rgb, clampedBase, tile.a);
    }

    float viewDist = length(vertexWorldPos);
    if (uDitherDhRendering)
    {
        float worldNoise = bayerMatrix4x4(gl_FragCoord.xy) + 0.001;
        float fadeStep = smoothstep(uClipDistance, uClipDistance * 1.5, viewDist);
        if (fadeStep <= worldNoise) discard;
    }
    else if (viewDist < uClipDistance && uClipDistance > 0.0)
    {
        discard;
    }

    if (uNoiseEnabled && vTextureTileId == 0u)
    {
        applyNoise(fragColor, viewDist);
        applyNoise(baseColor, viewDist);
    }

    // One UNORM8 lane carries the exact axis-aligned face normal, water classification and a
    // 2-bit quantization of each 4-bit light level. Native depth remains the occupancy authority.
    uint skyLevel = (vPackedLight >> 4u) & 15u;
    uint blockLevel = vPackedLight & 15u;
    uint skyQ = (skyLevel + 2u) / 5u;
    uint blockQ = (blockLevel + 2u) / 5u;
    uint packedSurface = (vNormalIndex & 7u)
            | (vMaterialId == 12u ? 8u : 0u)
            | ((skyQ & 3u) << 4u)
            | ((blockQ & 3u) << 6u);
    surfaceData = vec4(clamp(baseColor.rgb, 0.0, 1.0), float(packedSurface) / 255.0);
}
