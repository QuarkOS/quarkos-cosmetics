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
    // Candle vertices are marked with alpha 0. They sample the glow strip and skip the lightmap,
    // so a dipped frame stays the texture's own red instead of flashing off.
    if (vertexColor.a < 0.5) {
        vec4 glow = texture(Sampler1, texCoord0);
        if (glow.a < 0.1) {
            discard;
        }
        color = vec4(glow.rgb, 1.0);
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
