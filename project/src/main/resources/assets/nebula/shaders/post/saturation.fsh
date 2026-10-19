#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SaturationConfig {
    float Saturation;
};

out vec4 fragColor;

// Meowl Color Saturation: 0 = schwarz-weiß, 1 = normal, 2 = doppelt so kräftig
void main() {
    vec4 c = texture(InSampler, texCoord);
    float luma = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 outColor = clamp(mix(vec3(luma), c.rgb, Saturation), 0.0, 1.0);
    fragColor = vec4(outColor, 1.0);
}
