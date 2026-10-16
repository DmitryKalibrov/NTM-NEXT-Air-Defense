#version 330

#moj_import <minecraft:globals.glsl>

// Burning missile fragment, additive. Vertex colour carries data, not colour:
// r = heat (1 white-hot .. 0 burnt out), g = per-piece seed, b = 1 for the glowing head sprite
// (UV -1..1 both ways), 0 for the trail ribbon (u 0 at the head .. 1 at the tail, v -1..1 across),
// 0.5 for the fireball (UV -1..1, r = progress), 0.75 for a motor plume, a = fade.

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Rough blackbody ramp: dull red, orange, yellow, white.
vec3 blackbody(float t) {
    vec3 c = mix(vec3(0.45, 0.02, 0.0), vec3(1.0, 0.32, 0.02), smoothstep(0.0, 0.35, t));
    c = mix(c, vec3(1.0, 0.72, 0.22), smoothstep(0.35, 0.7, t));
    return mix(c, vec3(1.0, 0.97, 0.88), smoothstep(0.7, 1.0, t));
}

float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += noise(p) * a;
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return s;
}

// The burst: r = progress 0..1, b = 0.5. White-hot centre, a boiling orange body with a ragged edge that
// cools to deep red as it grows, and a warm glow around it that lights the sky for a moment.
vec4 fireball(vec2 p, float t, float seed, float time) {
    float d = length(p);
    float boil = fbm(p * 2.6 + vec2(seed, seed * 0.37 - time * 0.9));
    float edge = 0.62 + 0.28 * (boil - 0.5) - 0.1 * t;
    float body = smoothstep(edge, edge - 0.22, d);
    float cool = t * t;
    float temp = clamp((1.0 - cool) * (0.35 + 0.65 * boil) + exp(-d * d * 10.0) * (1.0 - t) * 1.2 - d * 0.3, 0.0, 1.0);
    vec3 fire = blackbody(temp) * body * (1.0 - cool) * (0.8 + 0.6 * boil);
    float halo = exp(-d * 2.2) * 0.45 * (1.0 - t) * (1.0 - t);
    vec3 col = fire + vec3(1.0, 0.42, 0.08) * halo;
    return vec4(col, 1.0);
}

// Motor exhaust: r = power, b = 0.75; u 0 at the nozzle .. 1 at the end, v -1..1 across. White-hot core
// with shock diamonds by the nozzle, turbulence racing back along it, cooling to orange at the tip.
vec4 plume(vec2 uv, float power, float seed, float time) {
    float u = uv.x, v = uv.y;
    float flick = noise(vec2(u * 6.0 - time * 40.0, seed));
    float width = mix(0.35, 1.0, sqrt(u)) * (0.8 + 0.4 * flick);
    float core = exp(-(v * v) / (width * width) * 4.0);
    float diamonds = 0.75 + 0.25 * cos(u * 38.0) * (1.0 - smoothstep(0.0, 0.45, u));
    float along = pow(1.0 - u, 1.3);
    float temp = clamp((1.0 - u * 0.9) * (0.5 + 0.6 * core), 0.0, 1.0);
    return vec4(blackbody(temp) * core * along * diamonds * 1.5 * power, 1.0);
}

void main() {
    float heat = vertexColor.r;
    float seed = vertexColor.g * 97.0;
    float time = GameTime * 1200.0; // GameTime is the day fraction: 1200 s per day
    float mode = vertexColor.b;
    if (mode > 0.25 && mode < 0.875) {
        vec4 c = (mode < 0.6 ? fireball(texCoord0, heat, seed, time) : plume(texCoord0, heat, seed, time)) * vertexColor.a;
        if (max(c.r, max(c.g, c.b)) < 0.003) discard;
        fragColor = c;
        return;
    }
    float glow, temp;
    if (vertexColor.b > 0.5) {
        float d = length(texCoord0);
        float flicker = 0.75 + 0.5 * noise(vec2(time * 14.0, seed));
        glow = exp(-d * d * 7.0) * flicker + exp(-d * 3.0) * 0.3 * heat;
        temp = heat * (1.05 - d * 0.7);
    } else {
        float u = texCoord0.x, v = texCoord0.y;
        // Flame licks: the width wobbles and the trail tears into blobs that run back along it.
        float n = noise(vec2(u * 9.0 - time * 6.0, seed));
        float tear = noise(vec2(u * 23.0 - time * 11.0, seed + 13.0));
        float width = mix(1.0, 0.3, u) * (0.7 + 0.6 * n);
        float core = exp(-(v * v) / (width * width) * 3.0);
        float along = pow(1.0 - u, 1.5);
        float blobs = smoothstep(0.2, 0.6, tear + (1.0 - u) * 0.7);
        glow = core * along * blobs;
        temp = heat * (1.0 - u * 0.8) * (0.55 + 0.5 * core);
    }
    float a = clamp(glow * vertexColor.a * (0.3 + heat), 0.0, 1.0);
    if (a < 0.003) discard;
    fragColor = vec4(blackbody(clamp(temp, 0.0, 1.0)), a);
}
