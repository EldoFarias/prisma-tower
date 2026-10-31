package com.farias.prismatower.gl

/** GLSL ES 3.0 sources. */
object Shaders {

    // ------------------------------------------------------------------ lit meshes

    const val LIT_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec2 aUv;
uniform mat4 uVP;
uniform mat4 uModel;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vLocal;
out vec2 vUv;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNormal;
    vLocal = aPos;
    vUv = aUv;
    gl_Position = uVP * w;
}
"""

    /**
     * uKind: 0 ring slice, 1 pillar, 2 ball, 3 additive halo, 4 danger slice, 5 goal ring.
     * uPattern: pillar style (PillarStyle) or ball style (BallStyle).
     */
    const val LIT_FS = """#version 300 es
precision highp float;
float ss(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
in vec3 vWorld;
in vec3 vNormal;
in vec3 vLocal;
in vec2 vUv;
uniform vec3 uCam;
uniform vec3 uLight;
uniform vec3 uColor;
uniform vec3 uColor2;
uniform vec3 uEmissive;
uniform vec3 uSky;
uniform vec3 uGround;
uniform vec3 uRim;
uniform vec3 uFog;
uniform vec2 uFogRange;
uniform float uAlpha;
uniform float uTime;
uniform float uGloss;
uniform float uMetal;
uniform float uSpacing;
uniform int uKind;
uniform int uPattern;
out vec4 frag;

vec3 hsv(float h, float s, float v) {
    vec3 k = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(vec3(1.0), k, s);
}
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    vec3 base = uColor;
    vec3 emis = uEmissive;
    float gloss = uGloss;
    float metal = uMetal;

    if (uKind == 3) {
        // Additive halo: brighter at grazing angles.
        float f = pow(1.0 - abs(dot(N, V)), 1.5);
        frag = vec4(uColor * (0.35 + f) * uAlpha, 1.0);
        return;
    }

    if (uKind == 1) {
        // Pillar
        float a = atan(vLocal.x, vLocal.z);
        float y = vWorld.y;
        if (uPattern == 0) {
            float band = abs(fract(y / uSpacing + 0.5) - 0.5) * uSpacing;
            float line = ss(0.09, 0.0, abs(band - uSpacing * 0.32));
            emis += uColor2 * line * (0.8 + 0.2 * sin(uTime * 3.0 + y));
            base = mix(base, base * 1.6, 0.5 + 0.5 * sin(a * 3.0));
        } else if (uPattern == 1) {
            float r = sin(a * 26.0);
            N = normalize(N + vec3(cos(a), 0.0, -sin(a)) * r * 0.35);
            base = mix(base, uColor2, 0.25 + 0.25 * r);
        } else if (uPattern == 2) {
            float s = fract(a / 6.2831 * 3.0 + y * 0.18 - uTime * 0.05);
            float m = ss(0.45, 0.5, s) - ss(0.95, 1.0, s);
            base = mix(base, uColor2, m);
        } else if (uPattern == 3) {
            float q = floor((a + 3.1416) / 6.2831 * 10.0) + 0.5;
            float qa = q / 10.0 * 6.2831 - 3.1416;
            N = normalize(vec3(sin(qa), N.y + 0.25 * sin(q * 7.0 + floor(y * 0.6)), cos(qa)));
            float edge = abs(fract((a + 3.1416) / 6.2831 * 10.0) - 0.5);
            emis += uColor2 * ss(0.47, 0.5, edge) * 0.9;
            gloss = 140.0;
            metal = 0.4;
        } else if (uPattern == 4) {
            float row = floor(y * 2.2);
            float col = (a + 3.1416) / 6.2831 * 18.0 + mod(row, 2.0) * 0.5;
            float mortar = max(ss(0.42, 0.5, abs(fract(y * 2.2) - 0.5)), ss(0.44, 0.5, abs(fract(col) - 0.5)));
            base = mix(base * (0.85 + 0.3 * hash(vec2(floor(col), row))), uColor2 * 0.35, mortar);
            N = normalize(N + vec3(0.0, (fract(y * 2.2) - 0.5) * 0.4, 0.0));
        } else {
            vec2 g = vec2((a + 3.1416) / 6.2831 * 24.0, y * 2.0);
            vec2 cell = floor(g);
            float h = hash(cell);
            vec2 f = fract(g);
            float trace = (h > 0.5) ? ss(0.08, 0.0, abs(f.x - 0.5)) : ss(0.08, 0.0, abs(f.y - 0.5));
            float pulse = 0.5 + 0.5 * sin(uTime * 4.0 - y * 1.5 + h * 6.0);
            emis += uColor2 * trace * (0.35 + 0.65 * pulse) * step(0.25, h);
        }
    } else if (uKind == 0 || uKind == 4 || uKind == 5) {
        float r = length(vLocal.xz);
        base = mix(base * 0.82, base * 1.12, ss(1.0, 2.7, r));
        if (uKind == 4) {
            float st = sin((vUv.x * 7.0 + r * 3.0) - uTime * 6.0);
            float pulse = 0.65 + 0.35 * sin(uTime * 5.0);
            emis += uColor * (0.55 + 0.25 * st) * pulse;
            gloss = 90.0;
        }
        if (uKind == 5) {
            float sh = pow(0.5 + 0.5 * sin(vUv.x * 20.0 - uTime * 3.0 + r * 4.0), 6.0);
            emis += uColor * (0.25 + 0.6 * sh);
            metal = 1.0;
            gloss = 120.0;
        }
    } else if (uKind == 2) {
        // Ball styles, from the object-space normal.
        vec3 n0 = normalize(vLocal);
        float lon = atan(n0.x, n0.z);
        if (uPattern == 2) {
            base = mix(uColor, uColor2, ss(-0.1, 0.1, sin(n0.y * 14.0)));
        } else if (uPattern == 3) {
            float seg = floor((lon + 3.1416) / 6.2831 * 6.0);
            base = (abs(n0.y) > 0.88) ? vec3(1.0) : ((mod(seg, 2.0) < 0.5) ? uColor : hsv(seg / 6.0, 0.75, 1.0));
        } else if (uPattern == 4) {
            base = hsv(fract(lon / 6.2831 + n0.y * 0.3 + uTime * 0.25), 0.8, 1.0);
            emis += base * 0.2;
        } else if (uPattern == 5) {
            emis += mix(uColor, uColor2, 0.5 + 0.5 * sin(uTime * 4.0)) * (0.55 + 0.35 * sin(uTime * 6.0));
        } else if (uPattern == 6) {
            vec2 g = vec2(lon * 4.0, n0.y * 8.0);
            vec2 cell = floor(g);
            N = normalize(N + (vec3(hash(cell), hash(cell + 3.1), hash(cell + 7.7)) - 0.5) * 0.9);
            emis += uColor2 * pow(hash(cell + floor(uTime * 6.0)), 12.0) * 2.0;
        } else if (uPattern == 7) {
            float b = noise(vec2(n0.y * 6.0, lon * 0.6)) * 0.6 + 0.4 * sin(n0.y * 18.0);
            base = mix(uColor, uColor2, ss(0.1, 0.7, b));
        } else if (uPattern == 8) {
            float spot = ss(0.80, 0.78, dot(n0, normalize(vec3(0.0, 0.25, 1.0))));
            base = mix(uColor, uColor2, 1.0 - spot);
            float eight = ss(0.93, 0.95, dot(n0, normalize(vec3(0.0, 0.25, 1.0))));
            base = mix(base, uColor, eight * 0.9);
        } else if (uPattern == 9) {
            float n = noise(vec2(lon * 2.0, n0.y * 4.0) + uTime * 0.6) * 0.7 + noise(vec2(lon * 5.0, n0.y * 9.0) - uTime) * 0.3;
            float crack = ss(0.55, 0.62, n);
            base = mix(uColor2, uColor, crack);
            emis += uColor * crack * 1.2;
        }
    }

    vec3 L = normalize(uLight);
    vec3 H = normalize(L + V);
    float diff = max(dot(N, L), 0.0);
    vec3 amb = mix(uGround, uSky, N.y * 0.5 + 0.5);
    float spec = pow(max(dot(N, H), 0.0), gloss) * (0.45 + 0.55 * metal + gloss / 300.0);
    float fres = pow(1.0 - max(dot(N, V), 0.0), 3.0);
    vec3 R = reflect(-V, N);
    vec3 env = mix(uGround * 0.6, uSky * 1.6 + vec3(0.25), ss(-0.4, 0.8, R.y));
    vec3 col = base * (amb * 0.65 + diff * 0.8);
    col = mix(col, env * base * 1.25, metal * 0.55);
    // Soft secondary light from below (bounce light from the colourful floor).
    col += base * uRim * max(-N.y, 0.0) * 0.18;
    col += vec3(spec) * mix(vec3(1.0), base + 0.3, metal);
    col += uRim * fres * 0.55;
    col += emis;

    float dist = length(uCam - vWorld);
    float fog = ss(uFogRange.x, uFogRange.y, dist);
    col = mix(col, uFog, fog);
    frag = vec4(col, uAlpha);
}
"""

    // ------------------------------------------------------------------ paint splat

    const val SPLAT_FS = """#version 300 es
precision mediump float;
float ss(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
in vec3 vWorld;
in vec3 vNormal;
in vec3 vLocal;
in vec2 vUv;
uniform vec3 uColor;
uniform float uAlpha;
uniform float uSeed;
out vec4 frag;
void main() {
    vec2 p = vUv;
    float r = length(p);
    float a = atan(p.y, p.x);
    float edge = 0.62 + 0.12 * sin(a * 5.0 + uSeed) + 0.07 * sin(a * 11.0 + uSeed * 2.3);
    float body = ss(edge, edge - 0.06, r);
    // A few droplets around the splat.
    float drops = 0.0;
    for (int k = 0; k < 5; k++) {
        float ka = uSeed * 1.7 + float(k) * 1.31;
        vec2 c = vec2(cos(ka), sin(ka)) * (0.78 + 0.12 * sin(ka * 3.0));
        drops = max(drops, ss(0.09, 0.06, length(p - c)));
    }
    float m = max(body, drops);
    vec3 col = uColor * (0.85 + 0.25 * (1.0 - r));
    frag = vec4(col, m * uAlpha);
}
"""

    // ------------------------------------------------------------------ background

    const val BG_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=2) in vec2 aUv;
out vec2 vUv;
void main() { vUv = aUv; gl_Position = vec4(aPos.xy, 0.9999, 1.0); }
"""

    const val BG_FS = """#version 300 es
precision mediump float;
float ss(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
in vec2 vUv;
uniform vec3 uTop;
uniform vec3 uBottom;
uniform vec3 uA1;
uniform vec3 uA2;
uniform vec3 uA3;
uniform float uTime;
uniform float uScroll;
uniform float uAspect;
uniform float uStars;
out vec4 frag;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
vec3 blob(vec2 uv, vec2 c, float r, vec3 col) {
    float d = length((uv - c) * vec2(uAspect, 1.0));
    return col * ss(r, 0.0, d) * 0.55;
}
void main() {
    vec2 uv = vUv;
    vec3 col = mix(uBottom, uTop, uv.y);
    float t = uTime * 0.07;
    float s = uScroll * 0.012;
    col += blob(uv, vec2(0.25 + 0.15 * sin(t), fract(0.85 + s * 0.6) ), 0.75, uA1);
    col += blob(uv, vec2(0.85 + 0.1 * cos(t * 1.3), fract(0.45 + s)), 0.6, uA2) * 0.7;
    col += blob(uv, vec2(0.3 + 0.12 * cos(t * 0.8), fract(0.05 + s * 1.4)), 0.7, uA3) * 0.8;
    if (uStars > 0.5) {
        vec2 g = (uv + vec2(0.0, s * 0.5)) * vec2(uAspect, 1.0) * 60.0;
        vec2 cell = floor(g);
        float h = hash(cell);
        float d = length(fract(g) - vec2(hash(cell + 1.3), hash(cell + 2.1)));
        float tw = 0.5 + 0.5 * sin(uTime * (1.0 + h * 3.0) + h * 20.0);
        col += vec3(ss(0.08, 0.0, d) * step(0.93, h) * tw);
    }
    // Vignette
    vec2 q = uv - 0.5;
    col *= 1.0 - dot(q, q) * 0.9;
    frag = vec4(col, 1.0);
}
"""

    // ------------------------------------------------------------------ glowing particles

    const val PT_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec4 aColor;
layout(location=2) in float aSize;
uniform mat4 uVP;
uniform float uScale;
out vec4 vColor;
void main() {
    vec4 p = uVP * vec4(aPos, 1.0);
    gl_Position = p;
    gl_PointSize = clamp(aSize * uScale / max(p.w, 0.1), 1.0, 256.0);
    vColor = aColor;
}
"""

    const val PT_FS = """#version 300 es
precision mediump float;
float ss(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
in vec4 vColor;
out vec4 frag;
void main() {
    vec2 p = gl_PointCoord * 2.0 - 1.0;
    float d = dot(p, p);
    float core = ss(1.0, 0.0, d);
    float a = core * core;
    frag = vec4(vColor.rgb * (a + ss(0.15, 0.0, d) * 0.6) * vColor.a, 1.0);
}
"""
}
