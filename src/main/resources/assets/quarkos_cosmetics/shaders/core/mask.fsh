#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color;
    // Alpha under 0.75 is a carved glow texel, its own UV. Orange and cream skip the lightmap.
    // Dark red keeps the shell's light. There is no stand-in UV.
    if (vertexColor.a < 0.75) {
        vec4 glow = texture(Sampler1, texCoord0);
        if (glow.a < 0.1) {
            discard;
        }
        // Orange and cream are the candle. They stay full bright so night does not repaint them.
        // Dark red is the recess and keeps the shell's light.
        if (glow.g >= (64.0 / 255.0) && glow.r > glow.b) {
            color = vec4(glow.rgb, 1.0);
        } else {
            color = vec4(glow.rgb * vertexColor.rgb * ColorModulator.rgb * lightMapColor.rgb, 1.0);
        }
    } else {
        vec4 shell = texture(Sampler0, texCoord0);
        if (shell.a < 0.1) {
            discard;
        }
        // lightMapColor is one block-light step for the whole texel. It is not mixed across the quad.
        color = vec4(shell.rgb * vertexColor.rgb * ColorModulator.rgb * lightMapColor.rgb, 1.0);
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
