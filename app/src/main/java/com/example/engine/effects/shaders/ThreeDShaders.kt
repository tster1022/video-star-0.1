package com.example.engine.effects.shaders

/**
 * Category 7: 3D Geometry, Perspective & Raymarched Effects (18 Effects)
 * All 3D shaders project and map uTexture cleanly onto true 3D rotated surfaces
 * with directional diffuse + specular lighting and clean alpha cutout backgrounds.
 */
object ThreeDShaders {
    val bodies: Map<String, String> = mapOf(
        "am_3d_box" to """
            vec3 halfSize = vec3(max(uParams[0], 0.05), max(uParams[1], 0.05), max(uParams[2], 0.05)) * 0.72;
            mat3 rot = rotEulerDeg(vec3(uParams[3], uParams[4], uParams[5]));
            float fov = radians(clamp(uParams[6], 15.0, 90.0));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 1.55 / tan(fov * 0.5));
            vec3 rd = normalize(vec3(ndc, -1.55 / tan(fov * 0.5)));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                float d = sdBox(rp, halfSize);
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec3 e = vec3(0.002, 0.0, 0.0);
                vec3 n = normalize(vec3(
                    sdBox(rp + e.xyy, halfSize) - sdBox(rp - e.xyy, halfSize),
                    sdBox(rp + e.yxy, halfSize) - sdBox(rp - e.yxy, halfSize),
                    sdBox(rp + e.yyx, halfSize) - sdBox(rp - e.yyx, halfSize)
                ));
                vec2 fUv = triplanarUv(rp / max(halfSize, vec3(0.01)), n);
                vec4 tex = sampleMirrored(fUv);
                float light = 0.45 + 0.65 * max(dot(normalize(transpose(rot) * n), normalize(vec3(0.4, 0.6, 0.8))), 0.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_cube" to """
            float sz = max(uParams[0], 0.05) * 0.75;
            mat3 rot = rotEulerDeg(vec3(uParams[1], uParams[2], uParams[3]));
            float lAng = radians(uParams[4]);
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                float d = sdBox(rot * p, vec3(sz));
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec3 e = vec3(0.002, 0.0, 0.0);
                vec3 n = normalize(vec3(
                    sdBox(rp + e.xyy, vec3(sz)) - sdBox(rp - e.xyy, vec3(sz)),
                    sdBox(rp + e.yxy, vec3(sz)) - sdBox(rp - e.yxy, vec3(sz)),
                    sdBox(rp + e.yyx, vec3(sz)) - sdBox(rp - e.yyx, vec3(sz))
                ));
                vec2 fUv = triplanarUv(rp / sz, n);
                vec4 tex = sampleMirrored(fUv);
                vec3 wn = normalize(transpose(rot) * n);
                vec3 ld = normalize(vec3(cos(lAng), 0.55, sin(lAng) + 0.8));
                float light = 0.4 + 0.7 * max(dot(wn, ld), 0.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_cylinder" to """
            float rad = max(uParams[0], 0.05) * 0.9;
            float hh = max(uParams[1], 0.05) * 0.55;
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            int capMode = uModes[0];
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                vec2 d2 = abs(vec2(length(rp.xz), rp.y)) - vec2(rad, hh);
                float d = min(max(d2.x, d2.y), 0.0) + length(max(d2, 0.0));
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                bool isCap = abs(rp.y) > hh - 0.01;
                if (isCap && capMode == 2) {
                    c = vec4(0.0);
                } else {
                    vec2 cUv = isCap
                        ? (rp.xz / (rad * 2.0) + 0.5)
                        : vec2(atan(rp.z, rp.x) / TAU + 0.5, rp.y / (hh * 2.0) + 0.5);
                    vec4 tex = sampleMirrored(cUv);
                    vec3 wn = isCap ? normalize(transpose(rot) * vec3(0.0, sign(rp.y), 0.0))
                                    : normalize(transpose(rot) * vec3(rp.x, 0.0, rp.z));
                    float light = 0.42 + 0.68 * max(dot(wn, normalize(vec3(0.35, 0.55, 0.8))), 0.0);
                    vec3 col = (isCap && capMode == 1) ? vec3(0.18 * light + 0.1) : tex.rgb * light;
                    c = vec4(clamp(col, 0.0, 1.0), tex.a);
                }
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_ellipsoid" to """
            vec3 rad = vec3(max(uParams[0], 0.05), max(uParams[1], 0.05), max(uParams[2], 0.05)) * 0.9;
            mat3 rot = rotEulerDeg(vec3(uParams[3], uParams[4], uParams[5]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = (rot * p) / rad;
                float d = (length(rp) - 1.0) * min(rad.x, min(rad.y, rad.z));
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = (rot * p) / rad;
                vec2 sUv = vec2(atan(rp.z, rp.x) / TAU + 0.5, acos(clamp(rp.y, -1.0, 1.0)) / PI);
                vec4 tex = sampleMirrored(sUv);
                vec3 wn = normalize(transpose(rot) * normalize(rp / rad));
                float light = 0.4 + 0.7 * max(dot(wn, normalize(vec3(0.4, 0.6, 0.8))), 0.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_flip_layer" to """
            float theta = radians(uParams[0]);
            bool vertical = uModes[0] == 1;
            float persp = clamp(uParams[1], 0.05, 1.0) * 1.6;
            vec2 d = (uv - 0.5) * 2.0;
            vec3 ro = vec3(0.0, 0.0, 2.4 / persp);
            vec3 rd = normalize(vec3(d, -2.4 / persp));
            vec3 n = vertical ? vec3(0.0, sin(theta), cos(theta)) : vec3(sin(theta), 0.0, cos(theta));
            float denom = dot(rd, n);
            if (abs(denom) > 0.0001) {
                float t = -dot(ro, n) / denom;
                if (t > 0.0) {
                    vec3 hit = ro + rd * t;
                    vec3 uAxis = vertical ? vec3(1.0, 0.0, 0.0) : vec3(cos(theta), 0.0, -sin(theta));
                    vec3 vAxis = vertical ? vec3(0.0, cos(theta), -sin(theta)) : vec3(0.0, 1.0, 0.0);
                    vec2 pUv = vec2(dot(hit, uAxis), dot(hit, vAxis)) * 0.5 + 0.5;
                    if (pUv.x >= 0.0 && pUv.x <= 1.0 && pUv.y >= 0.0 && pUv.y <= 1.0) {
                        vec4 tex = texture(uTexture, pUv);
                        float shade = 0.55 + 0.45 * abs(cos(theta));
                        c = vec4(tex.rgb * shade, tex.a);
                    } else {
                        c = vec4(0.0);
                    }
                } else c = vec4(0.0);
            } else c = vec4(0.0);
        """.trimIndent(),

        "am_3d_heart" to """
            float depth = max(uParams[0], 0.02) * 0.55;
            float bevel = clamp(uParams[1], 0.0, 0.8) * 0.06;
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 42; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                vec2 q = rp.xy * 1.75;
                q.y = -q.y - 0.15;
                float a = q.x * q.x + pow(q.y - sqrt(abs(q.x)) * 0.65, 2.0) - 0.48;
                float d2 = a * 0.35;
                float d = max(d2, abs(rp.z) - depth) - bevel;
                if (d < 0.002) { hit = true; break; }
                t += max(d, 0.01);
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec2 fUv = clamp(rp.xy * 0.9 + 0.5, 0.0, 1.0);
                vec4 tex = texture(uTexture, fUv);
                float rim = 1.0 - smoothstep(0.0, depth, abs(rp.z));
                c = vec4(clamp(tex.rgb * (0.65 + 0.45 * rim), 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_hex_prism" to """
            float rad = max(uParams[0], 0.05) * 0.9;
            float depth = max(uParams[1], 0.02) * 0.55;
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                vec3 k3 = vec3(-0.8660254, 0.5, 0.57735);
                vec3 q = abs(rp);
                q.xy -= 2.0 * min(dot(k3.xy, q.xy), 0.0) * k3.xy;
                vec2 d2 = vec2(length(q.xy - vec2(clamp(q.x, -k3.z * rad, k3.z * rad), rad)) * sign(q.y - rad), q.z - depth);
                float d = min(max(d2.x, d2.y), 0.0) + length(max(d2, 0.0));
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec4 tex = sampleMirrored(rp.xy / (rad * 2.0) + 0.5);
                float light = 0.5 + 0.5 * (1.0 - abs(rp.z) / max(depth, 0.01) * 0.45);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_hollow_box" to """
            float sz = max(uParams[0], 0.05) * 0.78;
            float wall = clamp(uParams[1], 0.02, 0.45) * sz;
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 42; i++) {
                p = ro + rd * t;
                vec3 q = abs(rot * p) - vec3(sz);
                vec3 e = abs(q + wall) - wall;
                float d = min(min(
                    length(max(vec3(q.x, e.y, e.z), 0.0)) + min(max(q.x, max(e.y, e.z)), 0.0),
                    length(max(vec3(e.x, q.y, e.z), 0.0)) + min(max(e.x, max(q.y, e.z)), 0.0)),
                    length(max(vec3(e.x, e.y, q.z), 0.0)) + min(max(e.x, max(e.y, q.z)), 0.0));
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec4 tex = sampleMirrored(rp.xy / (sz * 2.0) + 0.5);
                float light = 0.55 + 0.45 * clamp(0.6 + rp.z, 0.0, 1.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_octahedron" to """
            float sz = max(uParams[0], 0.05) * 1.1;
            mat3 rot = rotEulerDeg(vec3(uParams[1], uParams[2], uParams[3]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 q = abs(rot * p);
                float d = (q.x + q.y + q.z - sz) * 0.57735;
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec3 n = sign(rp) * 0.57735;
                vec2 fUv = triplanarUv(rp / sz, n);
                vec4 tex = sampleMirrored(fUv);
                vec3 wn = normalize(transpose(rot) * n);
                float light = 0.42 + 0.68 * max(dot(wn, normalize(vec3(0.4, 0.6, 0.8))), 0.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_page_curl" to """
            float ang = radians(uParams[0]);
            float curlR = max(uParams[1], 0.04) * 0.45;
            float prog = clamp(uParams[2], 0.0, 1.0);
            float backOp = clamp(uParams[3], 0.0, 1.0);
            vec2 dir = vec2(cos(ang), sin(ang));
            float foldLine = mix(0.85, -0.85, prog);
            vec2 p = uv - 0.5;
            float d = dot(p, dir) - foldLine;
            if (d < 0.0) {
                c = texture(uTexture, uv);
            } else if (d < PI * curlR * 0.5) {
                float theta = d / curlR;
                vec2 curlUv = uv - dir * (d - sin(theta) * curlR);
                vec4 front = sampleMirroredBounded(curlUv, 0);
                float shade = 0.72 + 0.28 * cos(theta);
                c = vec4(front.rgb * shade, front.a);
            } else {
                vec2 backUv = uv - dir * (2.0 * d - PI * curlR);
                if (backUv.x >= 0.0 && backUv.x <= 1.0 && backUv.y >= 0.0 && backUv.y <= 1.0) {
                    vec4 backTex = texture(uTexture, backUv);
                    c = vec4(mix(vec3(0.85), backTex.rgb, 0.65) * 0.88, backTex.a * backOp);
                } else {
                    c = vec4(0.0);
                }
            }
        """.trimIndent(),

        "am_3d_pyramid" to """
            float baseSz = max(uParams[0], 0.05) * 0.9;
            float h = max(uParams[1], 0.05) * 0.95;
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = (rot * p) / baseSz + vec3(0.0, 0.35, 0.0);
                float m2 = h * h + 0.25;
                vec2 xz = abs(rp.xz);
                xz = (xz.y > xz.x) ? xz.yx : xz;
                xz -= 0.5;
                vec3 q = vec3(xz.y, h * rp.y - 0.5 * xz.x, h * xz.x + 0.5 * rp.y);
                float s = max(-q.x, 0.0);
                float tVal = clamp((q.y - 0.5 * rp.z) / (m2 + 0.25), 0.0, 1.0);
                float a = m2 * (q.x + s) * (q.x + s) + q.y * q.y;
                float b = m2 * (q.x + 0.5 * tVal) * (q.x + 0.5 * tVal) + (q.y - m2 * tVal) * (q.y - m2 * tVal);
                float d2 = min(q.y, -q.x * m2 - q.y * 0.5) > 0.0 ? 0.0 : min(a, b);
                float d = sqrt((d2 + q.z * q.z) / m2) * sign(max(q.z, -rp.y)) * baseSz;
                if (d < 0.002) { hit = true; break; }
                t += max(abs(d) * 0.7, 0.008);
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec4 tex = sampleMirrored(rp.xy / baseSz * 0.5 + 0.5);
                float light = 0.5 + 0.5 * clamp(0.6 + rp.y * 0.8 + rp.z, 0.0, 1.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_raster_extrude" to """
            float depth = clamp(uParams[0], 0.01, 0.8);
            int steps = int(clamp(floor(uParams[1] + 0.5), 4.0, 32.0));
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            float shading = clamp(uParams[5], 0.0, 1.0);
            vec4 best = vec4(0.0);
            for (int i = 0; i < 32; i++) {
                if (i >= steps) break;
                float f = float(i) / max(float(steps - 1), 1.0);
                float z = (0.5 - f) * depth;
                vec3 planePt = vec3((uv - 0.5) * 2.0, z);
                vec3 rp = rot * planePt;
                float persp = 1.0 / max(1.0 - rp.z * 0.55, 0.25);
                vec2 sUv = rp.xy * persp * 0.5 + 0.5;
                if (sUv.x >= 0.0 && sUv.x <= 1.0 && sUv.y >= 0.0 && sUv.y <= 1.0) {
                    vec4 s = texture(uTexture, sUv);
                    if (s.a > 0.1) {
                        float shade = mix(1.0, 1.0 - f * 0.65, shading);
                        best = vec4(s.rgb * shade, s.a);
                        break;
                    }
                }
            }
            c = best;
        """.trimIndent(),

        "am_3d_ring" to """
            float rOut = max(uParams[0], 0.1) * 0.85;
            float rIn = clamp(uParams[1], 0.02, rOut - 0.02);
            float hh = max(uParams[2], 0.02) * 0.5;
            mat3 rot = rotEulerDeg(vec3(uParams[3], uParams[4], uParams[5]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                float radDist = abs(length(rp.xz) - (rOut + rIn) * 0.5) - (rOut - rIn) * 0.5;
                vec2 d2 = vec2(radDist, abs(rp.y) - hh);
                float d = min(max(d2.x, d2.y), 0.0) + length(max(d2, 0.0));
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec2 rUv = vec2(atan(rp.z, rp.x) / TAU + 0.5, rp.y / (hh * 2.0) + 0.5);
                vec4 tex = sampleMirrored(rUv);
                float light = 0.45 + 0.55 * clamp(0.6 + rp.z, 0.0, 1.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_spherize" to """
            float rad = max(uParams[0], 0.05) * 0.95;
            mat3 rot = rotEulerDeg(vec3(uParams[1], uParams[2], uParams[3]));
            float str = clamp(uParams[4], 0.0, 1.0);
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            float r2 = dot(ndc, ndc);
            if (r2 <= rad * rad) {
                float z = sqrt(max(rad * rad - r2, 0.0));
                vec3 n = normalize(vec3(ndc, z));
                vec3 rn = rot * n;
                vec2 sUv = vec2(atan(rn.z, rn.x) / TAU + 0.5, acos(clamp(rn.y, -1.0, 1.0)) / PI);
                vec2 finalUv = mix(uv, sUv, str);
                vec4 tex = sampleMirrored(finalUv);
                float light = mix(1.0, 0.35 + 0.75 * max(dot(n, normalize(vec3(0.35, 0.55, 0.8))), 0.0), str);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = str > 0.95 ? vec4(0.0) : mix(orig, vec4(0.0), str);
            }
        """.trimIndent(),

        "am_3d_star_polyhedron" to """
            float pts = max(floor(uParams[0] + 0.5), 3.0);
            float depth = max(uParams[1], 0.04) * 0.7;
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                float r = length(rp);
                float th = atan(rp.y, rp.x);
                float ph = atan(rp.z, length(rp.xy));
                float spike = 0.32 + 0.22 * cos(th * pts) * cos(ph * pts * 0.5);
                float d = (r - spike * (0.7 + depth)) * 0.65;
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec4 tex = sampleMirrored(rp.xy * 0.8 + 0.5);
                float light = 0.45 + 0.6 * clamp(0.6 + rp.z, 0.0, 1.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_star_prism" to """
            float rOut = max(uParams[0], 0.05) * 0.85;
            float rIn = clamp(uParams[1], 0.02, rOut - 0.02);
            float depth = max(uParams[2], 0.02) * 0.55;
            mat3 rot = rotEulerDeg(vec3(uParams[3], uParams[4], uParams[5]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                float r = length(rp.xy);
                float th = atan(rp.y, rp.x);
                float starR = mix(rIn, rOut, 0.5 + 0.5 * cos(th * 5.0));
                vec2 d2 = vec2(r - starR, abs(rp.z) - depth);
                float d = (min(max(d2.x, d2.y), 0.0) + length(max(d2, 0.0))) * 0.7;
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec4 tex = sampleMirrored(rp.xy / (rOut * 2.0) + 0.5);
                float light = 0.5 + 0.5 * (1.0 - abs(rp.z) / max(depth, 0.01) * 0.4);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_three_axis_cross" to """
            float len = max(uParams[0], 0.05) * 0.85;
            float th = max(uParams[1], 0.02) * 0.7;
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                float d = min(sdBox(rp, vec3(len, th, th)),
                          min(sdBox(rp, vec3(th, len, th)),
                              sdBox(rp, vec3(th, th, len))));
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec4 tex = sampleMirrored(rp.xy / (len * 2.0) + 0.5);
                float light = 0.5 + 0.5 * clamp(0.6 + rp.z, 0.0, 1.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent(),

        "am_3d_torus" to """
            float maj = max(uParams[0], 0.05) * 1.1;
            float minor = max(uParams[1], 0.02) * 1.1;
            mat3 rot = rotEulerDeg(vec3(uParams[2], uParams[3], uParams[4]));
            vec2 ndc = (uv - 0.5) * 2.0;
            ndc.x *= aspectRatio();
            vec3 ro = vec3(0.0, 0.0, 2.2);
            vec3 rd = normalize(vec3(ndc, -2.2));
            float t = 0.0;
            bool hit = false;
            vec3 p = ro;
            for (int i = 0; i < 40; i++) {
                p = ro + rd * t;
                vec3 rp = rot * p;
                vec2 q = vec2(length(rp.xz) - maj, rp.y);
                float d = length(q) - minor;
                if (d < 0.002) { hit = true; break; }
                t += d;
                if (t > 5.0) break;
            }
            if (hit) {
                vec3 rp = rot * p;
                vec2 tUv = vec2(atan(rp.z, rp.x) / TAU + 0.5, atan(rp.y, length(rp.xz) - maj) / TAU + 0.5);
                vec4 tex = sampleMirrored(tUv);
                vec3 centerRing = normalize(vec3(rp.x, 0.0, rp.z)) * maj;
                vec3 wn = normalize(transpose(rot) * normalize(rp - centerRing));
                float light = 0.38 + 0.72 * max(dot(wn, normalize(vec3(0.4, 0.6, 0.8))), 0.0);
                c = vec4(clamp(tex.rgb * light, 0.0, 1.0), tex.a);
            } else {
                c = vec4(0.0);
            }
        """.trimIndent()
    )
}
