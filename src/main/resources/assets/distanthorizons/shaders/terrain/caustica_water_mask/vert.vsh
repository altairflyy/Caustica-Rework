#version 330

in uvec3 vPosition;
in uint meta; // contains light and micro-offset data
in vec4 vColor;
in int irisMaterial;
in int irisNormal;
in uint textureTile; // block texture tile id, 0 = flat color

// order matters, this must match the fragment shader's inputs
out vec3 vPos;
out vec4 vertexColor;
out vec3 vertexWorldPos;
out vec3 vBlockPos;
flat out uint vNormalIndex;
flat out uint vTextureTileId;
flat out uint vMaterialId;
flat out uint vPackedLight;
out vec4 vBaseColor;

layout (std140) uniform vertUniqueUniformBlock
{
    vec3 uModelOffset;
};

layout (std140) uniform vertSharedUniformBlock
{
    bool uIsWhiteWorld;
    float uWorldYOffset;
    float uMircoOffset;
    float uEarthRadius;
    vec3 uCameraPos;
    mat4 uCombinedMatrix;
};

uniform sampler2D uLightMap;

void main()
{
    vPos = vPosition;
    vBlockPos = vec3(vPosition.xyz);
    vNormalIndex = uint(irisNormal);
    vTextureTileId = textureTile;
    vMaterialId = uint(irisMaterial);
    vPackedLight = meta & 0xFFu;

    vertexWorldPos = vPosition.xyz + (uModelOffset - uCameraPos);
    float vertexYPos = vPosition.y + uWorldYOffset;

    uint mirco = (meta & 0xFF00u) >> 8u;
    float mx = (mirco & 1u) != 0u ? uMircoOffset : 0.0;
    mx = (mirco & 2u) != 0u ? -mx : mx;
    float mz = (mirco & 16u) != 0u ? uMircoOffset : 0.0;
    mz = (mirco & 32u) != 0u ? -mz : mz;
    vertexWorldPos.x += mx;
    vertexWorldPos.z += mz;

    if (uEarthRadius < -1.0f || uEarthRadius > 1.0f)
    {
        float localRadius = uEarthRadius + vertexYPos;
        float phi = length(vertexWorldPos.xz) / localRadius;
        vertexWorldPos.y += (cos(phi) - 1.0) * localRadius;
        vertexWorldPos.xz = vertexWorldPos.xz * sin(phi) / phi;
    }

    uint lights = vPackedLight;
    float skyLight = (float(lights / 16u) + 0.5) / 16.0;
    float blockLight = (mod(float(lights), 16.0) + 0.5) / 16.0;
    vBaseColor = uIsWhiteWorld ? vec4(1.0) : vColor;
    vertexColor = vec4(texture(uLightMap, vec2(skyLight, blockLight)).xyz, 1.0) * vBaseColor;
    gl_Position = uCombinedMatrix * vec4(vertexWorldPos, 1.0);
}
