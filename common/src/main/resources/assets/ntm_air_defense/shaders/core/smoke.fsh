#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Rocket smoke. Not vanilla's particle fog: that swallows everything at render distance, while Distant
// Horizons draws terrain kilometres further, so far smoke just broke off. Here the smoke only fades into
// the sky colour with distance like real haze, and still disappears in short fog (water, lava, blindness).
// No alpha cut-off either: the puffs' soft edges must fade to nothing, not end in a line.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float HAZE = 3000.0;        // blocks for the haze to take ~63% of the colour
const float SHORT_FOG = 128.0;    // environmental fog ending closer than this is water/lava/blindness

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    float haze = 1.0 - exp(-sphericalVertexDistance / HAZE);
    float fog = haze;
    if (FogEnvironmentalEnd < SHORT_FOG) {
        fog = max(fog, linear_fog_value(sphericalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd));
    }
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fog * FogColor.a), color.a);
}
