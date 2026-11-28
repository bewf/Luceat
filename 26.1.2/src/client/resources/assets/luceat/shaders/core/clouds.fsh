#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(std140) uniform LuceatCloud {
    vec4 CloudSun;
    vec4 CloudMotion;
    vec4 CloudMisc;
};

in vec3 rel;
flat in vec4 cloudTint;
flat in vec2 camera;
flat in float slabBase;
flat in int keepFaces;

#ifndef LUCEAT_CUT
#define LUCEAT_CUT 0.0
#endif
#ifndef LUCEAT_DEPTH
#define LUCEAT_DEPTH 40.0
#endif
#ifndef LUCEAT_LIFT
#define LUCEAT_LIFT 0.0
#endif
#ifndef LUCEAT_ALPHA
#define LUCEAT_ALPHA 1.0
#endif
#ifndef LUCEAT_DARK
#define LUCEAT_DARK 1.0
#endif
#ifndef LUCEAT_FORM
#define LUCEAT_FORM 0
#endif
#ifndef LUCEAT_FLAT
#define LUCEAT_FLAT 0.0
#endif
#ifndef LUCEAT_ERODE
#define LUCEAT_ERODE 0.6
#endif
#ifndef LUCEAT_STEPS
#define LUCEAT_STEPS 20
#endif
#ifndef LUCEAT_TAPS
#define LUCEAT_TAPS 3
#endif
#ifndef LUCEAT_DETAIL
#define LUCEAT_DETAIL 1
#endif
#ifndef LUCEAT_ZZERO
#define LUCEAT_ZZERO 0
#endif

const int STEPS = LUCEAT_STEPS;
const float DEPTH = LUCEAT_DEPTH;
const float SLAB = LUCEAT_DEPTH * 1.3;
const float SIGMA = 0.12;
const float EDGE = mix(0.3, 0.55, LUCEAT_FLAT);

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// The lattice repeats every `period` cells in x and z so the layer wraps at 720 blocks without a seam.
float noise2(vec2 p, vec2 period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(mod(i, period));
    float b = hash21(mod(i + vec2(1.0, 0.0), period));
    float c = hash21(mod(i + vec2(0.0, 1.0), period));
    float d = hash21(mod(i + vec2(1.0, 1.0), period));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float noise2(vec2 p, float period) {
    return noise2(p, vec2(period));
}

float noise3(vec3 p, float period) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec2 m = vec2(period);
    vec2 a = mod(i.xz, m);
    vec2 b = mod(i.xz + vec2(1.0, 0.0), m);
    vec2 c = mod(i.xz + vec2(0.0, 1.0), m);
    vec2 d = mod(i.xz + vec2(1.0, 1.0), m);
    float n000 = hash31(vec3(a.x, i.y, a.y));
    float n100 = hash31(vec3(b.x, i.y, b.y));
    float n010 = hash31(vec3(a.x, i.y + 1.0, a.y));
    float n110 = hash31(vec3(b.x, i.y + 1.0, b.y));
    float n001 = hash31(vec3(c.x, i.y, c.y));
    float n101 = hash31(vec3(d.x, i.y, d.y));
    float n011 = hash31(vec3(c.x, i.y + 1.0, c.y));
    float n111 = hash31(vec3(d.x, i.y + 1.0, d.y));
    return mix(mix(mix(n000, n100, f.x), mix(n001, n101, f.x), f.z), mix(mix(n010, n110, f.x), mix(n011, n111, f.x), f.z), f.y);
}

float coverage(vec2 xz) {
#if LUCEAT_FORM == 1
    return 0.65 * noise2(xz / 36.0, 20.0) + 0.35 * noise2(xz / 12.0 + 5.0, 60.0);
#elif LUCEAT_FORM == 2
    float broad = 0.6 * noise2(xz / 120.0, 6.0) + 0.4 * noise2(xz / 48.0 + 17.0, 15.0);
    vec2 bend = 7.0 * vec2(noise2(xz / 40.0 + 3.0, 18.0), noise2(xz / 40.0 + 9.0, 18.0));
    vec2 q = xz + bend;
    float puffs = 0.55 * noise2(vec2(q.x / 24.0, q.y / 9.0), vec2(30.0, 80.0)) + 0.45 * noise2(vec2(q.x / 10.0, q.y / 6.0) + 5.0, vec2(72.0, 120.0));
    return broad * (0.25 + 0.95 * puffs);
#elif LUCEAT_FORM == 3
    return 0.7 * noise2(xz / 180.0, 4.0) + 0.3 * noise2(xz / 60.0 + 9.0, 12.0);
#elif LUCEAT_FORM == 4
    float broad = noise2(vec2(xz.x / 120.0, xz.y / 48.0), vec2(6.0, 15.0));
    float mid = noise2(vec2(xz.x / 60.0, xz.y / 24.0) + 4.0, vec2(12.0, 30.0));
    float fibre = noise2(vec2(xz.x / 72.0, xz.y / 12.0) + 9.0, vec2(10.0, 60.0));
    return 0.5 * broad + 0.28 * mid + 0.22 * fibre;
#else
    vec2 warp = 14.0 * vec2(noise2(xz / 90.0, 8.0) - 0.5, noise2(xz / 90.0 + 11.0, 8.0) - 0.5);
    vec2 q = xz + warp;
    return 0.58 * noise2(q / 120.0, 6.0) + 0.3 * noise2(q / 48.0 + 17.0, 15.0) + 0.12 * noise2(q / 18.0 + 3.0, 40.0);
#endif
}

float cutoff() {
    float base = LUCEAT_CUT > 0.0 ? LUCEAT_CUT : 0.40;
    return mix(base, -0.25, LUCEAT_FLAT);
}

struct Column {
    float cover;
    float amount;
    float base;
    float top;
};

Column column(vec2 xz) {
    Column k;
    k.cover = coverage(xz);
    k.amount = smoothstep(cutoff(), cutoff() + EDGE, k.cover);
    float character = noise2(xz / 240.0 + 7.0, 3.0);
    float drift = noise2(xz / 360.0 + 21.0, 2.0);
    float grow = mix(mix(0.35, 1.0, smoothstep(0.2, 0.8, character)), 1.0, LUCEAT_FLAT);
    k.base = DEPTH * (0.1 + (drift - 0.5) * 0.3 * (1.0 - 0.7 * LUCEAT_FLAT));
    k.top = k.base + DEPTH * grow * (0.25 + 0.75 * pow(k.amount, 0.75));
    return k;
}

float density(vec2 xz, float height, Column k, bool detail) {
    float amount = k.amount;
#if LUCEAT_FORM == 3
    float relative = (height - k.base) / max(k.top - k.base, 1.0);
    amount = smoothstep(cutoff() - 0.24 * smoothstep(0.62, 0.9, relative), cutoff() - 0.24 * smoothstep(0.62, 0.9, relative) + EDGE, k.cover);
#endif
    if (amount <= 0.0 || height < k.base || height > k.top) {
        return 0.0;
    }
    float h = (height - k.base) / max(k.top - k.base, 1.0);
    float profile = smoothstep(0.0, 0.14, h) * (1.0 - smoothstep(0.5, 1.0, h));
#if LUCEAT_FORM == 3
    profile = max(profile, smoothstep(0.5, 0.7, h) * (1.0 - smoothstep(0.86, 1.0, h)) * 0.85);
#endif
    float d = profile * amount;
    if (d <= 0.0) {
        return 0.0;
    }
    vec3 q = vec3(xz.x, height * 1.35, xz.y);
    float billow = 0.6 * noise3(q / 30.0, 24.0) + 0.4 * noise3(q / 16.0 + 3.0, 45.0);
    d -= (1.0 - billow) * LUCEAT_ERODE * (0.45 + 0.55 * h) * (1.0 - LUCEAT_FLAT * 0.5);
    if (detail && LUCEAT_DETAIL == 1) {
        float fine = noise3(q / 9.0 + 7.0, 80.0);
        d -= (1.0 - fine) * 0.3 * LUCEAT_ERODE;
    }
    return clamp(d * 1.9, 0.0, 1.0);
}

float wake(inout vec3 p, float time) {
    float speed = CloudMotion.w;
    float amount = smoothstep(12.0, 40.0, speed);
    if (amount <= 0.0) {
        return 0.0;
    }
    vec3 axis = CloudMotion.xyz / max(speed, 0.001);
    float along = dot(p, -axis);
    float trail = 34.0 + speed * 1.6;
    float behind = smoothstep(-8.0, 4.0, along) * (1.0 - smoothstep(0.3 * trail, trail, along));
    vec3 side = p + axis * along;
    float radius = 6.0 + max(along, 0.0) * 0.14;
    float core = exp(-dot(side, side) / (radius * radius)) * behind * amount;
    float away = max(length(side), 0.001);
    float angle = core * 1.3 * sin(along * 0.07 - time * 2.2);
    vec3 turn = cross(axis, side / away);
    p += (side / away) * (core * radius * 0.9) + turn * (sin(angle) * radius * 0.8);
    return core;
}

void main() {
    if (keepFaces == 0x7fffffff) {
        discard;
    }
    vec3 dir = normalize(rel);
    float base = slabBase + LUCEAT_LIFT;
    float invY = abs(dir.y) > 0.0005 ? 1.0 / dir.y : (dir.y >= 0.0 ? 2000.0 : -2000.0);
    float tLow = base * invY;
    float tHigh = (base + SLAB) * invY;
    float tMin = max(min(tLow, tHigh), 0.0);
    float tMax = max(tLow, tHigh);
    if (tMax <= 0.0) {
        discard;
    }
    float reach = max(max(FogCloudsEnd, 64.0), abs(base) * 5.0) * (1.0 + 1.5 * LUCEAT_FLAT);
    tMax = min(tMax, min(tMin + 440.0, reach));
    if (tMax <= tMin) {
        discard;
    }

    vec3 sun = CloudSun.xyz;
    float sunLength = length(sun);
    vec3 light = sunLength > 0.1 ? sun / sunLength : vec3(0.30, 0.86, 0.41);
    bool night = CloudSun.y < -0.06;
    if (night) {
        light = -light;
    }
    float warm = night ? 0.0 : (1.0 - smoothstep(0.05, 0.4, light.y)) * smoothstep(-0.12, 0.04, light.y);
    float mu = max(dot(dir, light), 0.0);
    float silver = 0.92 + 1.0 * pow(mu, 6.0);

    vec3 lit = cloudTint.rgb * 1.08 * mix(vec3(1.0), vec3(1.3, 0.8, 0.5), warm * 0.85) * silver;
    vec3 shade = mix(cloudTint.rgb, FogColor.rgb, 0.25) * 0.82 * mix(vec3(1.0), vec3(0.78, 0.72, 1.05), warm * 0.55);

    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float span = tMax - tMin;
    float transmittance = 1.0;
    vec3 accum = vec3(0.0);
    float first = -1.0;
    float time = CloudMisc.x;
    for (int i = 0; i < STEPS; i++) {
        float u = (float(i) + jitter) / float(STEPS);
        float t = tMin + span * pow(u, 1.6);
        float stride = span * 1.6 * pow(u, 0.6) / float(STEPS) + 0.25;
        vec3 p = dir * t;
        vec3 moved = p;
        float cut = wake(moved, time);
        vec2 xz = camera + moved.xz;
        float height = moved.y - base;
        Column k = column(xz);
        float d = density(xz, height, k, true) * (1.0 - 0.6 * cut);
        if (d > 0.01) {
            float od = 0.0;
            for (int j = 1; j <= LUCEAT_TAPS; j++) {
                float reachJ = 5.0 * float(j) * float(j);
                vec3 q = moved + light * reachJ;
                vec2 qxz = camera + q.xz;
                od += density(qxz, q.y - base, column(qxz), false) * reachJ;
            }
            float shadow = 0.15 + 0.85 * exp(-od * SIGMA * 0.8);
            vec3 colour = mix(shade, lit, shadow) * (0.92 + 0.16 * k.cover);
            float absorbed = 1.0 - exp(-d * SIGMA * stride);
            accum += transmittance * absorbed * colour;
            transmittance *= 1.0 - absorbed;
            if (first < 0.0) {
                first = t;
            }
            if (transmittance < 0.03) {
                break;
            }
        }
    }

    float fade = 1.0 - smoothstep(0.45 * reach, reach, max(tMin, first));
    float alpha = (1.0 - transmittance) * clamp(cloudTint.a * 1.25, 0.0, 1.0) * LUCEAT_ALPHA * fade;
    if (alpha <= 0.002 || first < 0.0) {
        discard;
    }
    vec3 colour = accum / max(1.0 - transmittance, 0.0001);
    colour = mix(colour, FogColor.rgb, 0.45 * smoothstep(0.2 * reach, reach, first));
    fragColor = vec4(colour * LUCEAT_DARK, min(alpha, 1.0));

    vec4 clip = ProjMat * ModelViewMat * vec4(dir * first, 1.0);
    float ndc = clip.z / clip.w;
    gl_FragDepth = clamp(LUCEAT_ZZERO == 1 ? ndc : ndc * 0.5 + 0.5, 0.0, 1.0);
}
