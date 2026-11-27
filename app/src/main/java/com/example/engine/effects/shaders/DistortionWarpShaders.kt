package com.example.engine.effects.shaders

/**
 * Category 2: Distortion/Warp (18 Effects)
 */
object DistortionWarpShaders {
    val bodies: Map<String, String> = mapOf(
        "am_hex_tile_shift" to """
            float hexSize = max(uParams[0], 0.02);
            vec2 shift = vec2(uParams[1], uParams[2]);
            float ang = radians(uParams[3]);
            vec2 p = rotate2d(uv - 0.5, -ang);
            p.x *= aspectRatio();
            vec2 r = vec2(1.0, 1.7320508);
            vec2 h = r * 0.5;
            vec2 a = mod(p / hexSize, r) - h;
            vec2 b = mod(p / hexSize - h, r) - h;
            vec2 gv = dot(a, a) < dot(b, b) ? a : b;
            vec2 id = p / hexSize - gv;
            float rowDir = mod(floor(id.y + 100.0), 2.0) * 2.0 - 1.0;
            float colDir = mod(floor(id.x + 100.0), 2.0) * 2.0 - 1.0;
            vec2 off = vec2(shift.x * rowDir, shift.y * colDir) * hexSize;
            vec2 outP = rotate2d(vec2((p.x + off.x) / aspectRatio(), p.y + off.y), ang) + 0.5;
            c = sampleMirrored(outP);
        """.trimIndent(),

        "am_hex_tiling" to """
            float sc = max(uParams[0], 0.5);
            float rot = radians(uParams[1]);
            bool mirror = uModes[0] == 1;
            vec2 p = rotate2d((uv - 0.5) * vec2(aspectRatio(), 1.0), -rot) * sc;
            vec2 r = vec2(1.0, 1.7320508);
            vec2 h = r * 0.5;
            vec2 a = mod(p, r) - h;
            vec2 b = mod(p - h, r) - h;
            vec2 gv = dot(a, a) < dot(b, b) ? a : b;
            vec2 hexUv = gv + 0.5;
            c = mirror ? sampleMirrored(hexUv) : texture(uTexture, fract(hexUv));
        """.trimIndent(),

        "am_inner_pinch_bulge" to """
            vec2 center = vec2(uParams[0], uParams[1]);
            float strength = clamp(uParams[2], -1.0, 1.0);
            float rad = max(uParams[3], 0.02);
            float feather = clamp(uParams[4], 0.0, 1.0);
            vec2 d = uv - center;
            float dist = length(d * vec2(aspectRatio(), 1.0));
            if (dist < rad && dist > 0.0001) {
                float pct = dist / rad;
                float edgeMask = 1.0 - smoothstep(max(0.0, 1.0 - feather), 1.0, pct);
                float deform = pow(pct, 1.0 + strength * 1.45 * edgeMask);
                float factor = mix(1.0, deform / max(pct, 0.0001), edgeMask);
                d *= factor;
            }
            c = sampleMirrored(center + d);
        """.trimIndent(),

        "am_kaleidoscope" to """
            float slices = max(floor(uParams[0] + 0.5), 2.0);
            float ang = radians(uParams[1]);
            float zoom = max(uParams[2], 0.1);
            vec2 off = vec2(uParams[3], uParams[4]) - 0.5;
            vec2 p = (uv - 0.5 - off);
            p.x *= aspectRatio();
            float r = length(p) / zoom;
            float theta = atan(p.y, p.x) + ang;
            float sector = TAU / slices;
            theta = mod(theta, sector);
            theta = abs(theta - sector * 0.5);
            vec2 kUv = vec2(cos(theta), sin(theta)) * r;
            kUv.x /= aspectRatio();
            c = sampleMirrored(kUv + 0.5);
        """.trimIndent(),

        "am_mirror" to """
            float ang = radians(uParams[0]);
            float offset = clamp(uParams[1], 0.0, 1.0) - 0.5;
            bool flipSide = uModes[0] == 1;
            vec2 n = vec2(cos(ang), sin(ang));
            vec2 p = uv - 0.5;
            float dist = dot(p, n) - offset;
            if ((!flipSide && dist > 0.0) || (flipSide && dist < 0.0)) {
                p -= 2.0 * dist * n;
            }
            c = sampleMirrored(p + 0.5);
        """.trimIndent(),

        "am_omino_glass" to """
            float refractIdx = uParams[0] * 0.06;
            float sc = max(uParams[1], 0.5);
            float disp = uParams[2] * 0.025;
            float rough = uParams[3] * 0.015;
            vec2 eps = vec2(0.012, 0.0);
            float h0 = fbm(uv * sc + vec2(uTime * 0.25), 3);
            float hx = fbm((uv + eps.xy) * sc + vec2(uTime * 0.25), 3);
            float hy = fbm((uv + eps.yx) * sc + vec2(uTime * 0.25), 3);
            vec2 norm = vec2(h0 - hx, h0 - hy) * 18.0;
            vec2 jitter = (hash22(uv * 180.0 + uTime) - 0.5) * rough;
            vec2 baseUv = uv + norm * refractIdx + jitter;
            float rCh = sampleMirrored(baseUv + norm * disp).r;
            float gCh = sampleMirrored(baseUv).g;
            float bCh = sampleMirrored(baseUv - norm * disp).b;
            float aCh = sampleMirrored(baseUv).a;
            vec3 spec = vec3(pow(clamp(dot(normalize(vec3(norm, 1.0)), normalize(vec3(0.4, 0.6, 0.8))), 0.0, 1.0), 12.0) * 0.14);
            c = vec4(clamp(vec3(rCh, gCh, bCh) + spec, 0.0, 1.0), aCh);
        """.trimIndent(),

        "am_pinch_bulge" to """
            vec2 center = vec2(uParams[0], uParams[1]);
            float strength = clamp(uParams[2], -1.0, 1.0);
            float rad = max(uParams[3], 0.05);
            vec2 d = uv - center;
            float dist = length(d * vec2(aspectRatio(), 1.0));
            if (dist < rad && dist > 0.0001) {
                float pct = dist / rad;
                float s = strength >= 0.0
                    ? mix(1.0, pow(pct, 1.0 + strength * 1.8) / pct, 1.0 - pct * 0.25)
                    : mix(1.0, pow(pct, 1.0 / (1.0 - strength * 1.8)) / pct, 1.0 - pct * 0.25);
                d *= s;
            }
            c = sampleMirrored(center + d);
        """.trimIndent(),

        "am_pixelate" to """
            float pixSize = clamp(uParams[0], 0.02, 1.0);
            float asp = max(uParams[1], 0.25);
            float gridOp = clamp(uParams[2], 0.0, 1.0);
            float cellCount = mix(120.0, 10.0, pixSize);
            vec2 grid = vec2(cellCount, cellCount / asp);
            vec2 st = uv * grid;
            vec2 f = fract(st);
            vec2 qUv = (floor(st) + 0.5) / grid;
            vec4 pxCol = sampleMirrored(qUv);
            float edge = step(f.x, 0.06) + step(0.94, f.x) + step(f.y, 0.06) + step(0.94, f.y);
            float line = clamp(edge, 0.0, 1.0) * gridOp;
            c = vec4(mix(pxCol.rgb, vec3(0.05), line), pxCol.a);
        """.trimIndent(),

        "am_polar_coords" to """
            float interp = clamp(uParams[0], 0.0, 1.0);
            int mode = uModes[0];
            vec2 targetUv;
            if (mode == 0) {
                // Rect to Polar
                vec2 d = uv - 0.5;
                float ang = atan(d.y, d.x) / TAU + 0.5;
                float r = length(d) * 2.0;
                targetUv = vec2(ang, r);
            } else {
                // Polar to Rect
                float ang = (uv.x - 0.5) * TAU;
                float r = uv.y * 0.5;
                targetUv = vec2(cos(ang), sin(ang)) * r + 0.5;
            }
            vec2 p = mix(uv, targetUv, interp);
            c = sampleMirrored(p);
        """.trimIndent(),

        "am_polar_displace" to """
            float angOff = radians(uParams[0]);
            float radStr = uParams[1] * 0.2;
            float sc = max(uParams[2], 0.3);
            vec2 d = uv - 0.5;
            float r = length(d);
            float theta = atan(d.y, d.x);
            float n = fbm(vec2(r * sc * 3.0, theta * 1.5 + uTime * 0.6), 3) - 0.5;
            float newR = r + n * radStr;
            float newT = theta + n * angOff;
            vec2 p = vec2(cos(newT), sin(newT)) * newR + 0.5;
            c = sampleMirrored(p);
        """.trimIndent(),

        "am_squeeze" to """
            float sx = clamp(uParams[0], -1.0, 1.0);
            float sy = clamp(uParams[1], -1.0, 1.0);
            vec2 d = uv - 0.5;
            float yCurve = 1.0 - (d.y * d.y * 4.0);
            float xCurve = 1.0 - (d.x * d.x * 4.0);
            vec2 p;
            p.x = d.x / max(0.2, 1.0 - sx * 0.65 * yCurve);
            p.y = d.y / max(0.2, 1.0 - sy * 0.65 * xCurve);
            c = sampleMirrored(p + 0.5);
        """.trimIndent(),

        "am_stretch_segment" to """
            float ang = radians(uParams[0]);
            float off = clamp(uParams[1], 0.05, 0.95) - 0.5;
            float width = clamp(uParams[2], 0.0, 0.95) * 0.5;
            vec2 p = rotate2d(uv - 0.5, -ang);
            float d = p.x - off;
            if (abs(d) < width) {
                p.x = off;
            } else if (d >= width) {
                p.x -= width;
            } else {
                p.x += width;
            }
            p = rotate2d(p, ang) + 0.5;
            c = sampleMirrored(p);
        """.trimIndent(),

        "am_swirl" to """
            vec2 center = vec2(uParams[0], uParams[1]);
            float rad = max(uParams[2], 0.05);
            float strength = uParams[3] * 6.0;
            vec2 d = uv - center;
            float dist = length(d * vec2(aspectRatio(), 1.0));
            if (dist < rad) {
                float pct = (rad - dist) / rad;
                float theta = pct * pct * strength;
                d = rotate2d(d, theta);
            }
            c = sampleMirrored(center + d);
        """.trimIndent(),

        "am_tile_rotate" to """
            float grid = max(floor(uParams[0] + 0.5), 1.0);
            float cellAng = radians(uParams[1]);
            float sc = max(uParams[2], 0.1);
            vec2 st = uv * grid;
            vec2 f = fract(st) - 0.5;
            vec2 cell = rotate2d(f / sc, -cellAng) + 0.5;
            c = sampleMirrored(cell);
        """.trimIndent(),

        "am_tile_shift" to """
            float count = max(floor(uParams[0] + 0.5), 1.0);
            float offset = uParams[1];
            float ang = radians(uParams[2]);
            vec2 p = rotate2d(uv - 0.5, -ang) + 0.5;
            float band = floor(p.y * count);
            float dir = mod(band, 2.0) * 2.0 - 1.0;
            p.x += offset * dir;
            p = rotate2d(p - 0.5, ang) + 0.5;
            c = sampleMirrored(p);
        """.trimIndent(),

        "am_tiles" to """
            float crop = max(uParams[0], 0.2);
            bool mirror = uModes[0] == 1;
            float offset = uParams[1];
            float ang = radians(uParams[2]);
            vec2 p = rotate2d(uv - 0.5, -ang) * crop + 0.5 + vec2(offset);
            c = mirror ? sampleMirrored(p) : texture(uTexture, fract(p));
        """.trimIndent(),

        "am_turbulent_displace" to """
            vec2 off = vec2(uParams[0], uParams[1]);
            float str = uParams[2] * 0.12;
            float evol = uParams[3];
            float sc = max(uParams[4], 0.3);
            int oct = int(clamp(floor(uParams[5] + 0.5), 1.0, 6.0));
            vec2 coord = (uv + off) * sc;
            float nx = fbm(coord + vec2(uTime * 0.5 + evol, 0.0), oct) - 0.5;
            float ny = fbm(coord + vec2(17.3, uTime * 0.5 + evol + 9.2), oct) - 0.5;
            c = sampleMirrored(uv + vec2(nx, ny) * str);
        """.trimIndent(),

        "am_wave_warp" to """
            float phase = radians(uParams[0]) + uTime * 4.5;
            float ang = radians(uParams[1]);
            float spacing = max(uParams[2], 0.5);
            float mag = uParams[3] * 0.14;
            float damp = clamp(uParams[4], 0.0, 1.0);
            vec2 p = rotate2d(uv - 0.5, -ang);
            float env = mix(1.0, 1.0 - smoothstep(0.0, 0.65, length(p)), damp);
            p.y += sin(p.x * spacing * 3.0 + phase) * mag * env;
            p = rotate2d(p, ang) + 0.5;
            c = sampleMirrored(p);
        """.trimIndent()
    )
}
