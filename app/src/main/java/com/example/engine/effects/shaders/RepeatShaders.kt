package com.example.engine.effects.shaders

/**
 * Category 6: Repeat & Instancing Shaders (7 Effects)
 */
object RepeatShaders {
    val bodies: Map<String, String> = mapOf(
        "am_grid_repeat" to """
            int rows = int(clamp(floor(uParams[0] + 0.5), 1.0, 6.0));
            int cols = int(clamp(floor(uParams[1] + 0.5), 1.0, 6.0));
            float spX = max(uParams[2], 0.05);
            float spY = max(uParams[3], 0.05);
            float falloff = clamp(uParams[4], 0.0, 0.8);
            vec4 acc = vec4(0.0);
            float wSum = 0.0;
            float instanceScale = max(1.0 / max(float(rows), float(cols)), 0.18);
            for (int r = 0; r < 6; r++) {
                if (r >= rows) break;
                for (int col = 0; col < 6; col++) {
                    if (col >= cols) break;
                    vec2 center = vec2(
                        0.5 + (float(col) - float(cols - 1) * 0.5) * spX,
                        0.5 + (float(r) - float(rows - 1) * 0.5) * spY
                    );
                    vec2 p = (uv - center) / instanceScale + 0.5;
                    if (p.x >= 0.0 && p.x <= 1.0 && p.y >= 0.0 && p.y <= 1.0) {
                        float distFromCenter = length(vec2(float(col) - float(cols - 1) * 0.5, float(r) - float(rows - 1) * 0.5));
                        float alpha = clamp(1.0 - distFromCenter * falloff * 0.5, 0.1, 1.0);
                        vec4 s = texture(uTexture, p);
                        acc += s * s.a * alpha;
                        wSum = max(wSum, s.a * alpha);
                    }
                }
            }
            c = wSum > 0.001 ? vec4(clamp(acc.rgb / max(wSum, 0.01), 0.0, 1.0), clamp(wSum, 0.0, 1.0)) : vec4(0.0);
        """.trimIndent(),

        "am_linear_repeat" to """
            int count = int(clamp(floor(uParams[0] + 0.5), 1.0, 16.0));
            vec2 off = vec2(uParams[1], uParams[2]);
            float angDelta = radians(uParams[3]);
            float scDelta = max(uParams[4], 0.3);
            float falloff = clamp(uParams[5], 0.0, 0.9);
            vec3 outRgb = vec3(0.0);
            float outA = 0.0;
            float curScale = 1.0;
            for (int i = 0; i < 16; i++) {
                if (i >= count) break;
                float fi = float(i);
                vec2 center = 0.5 + off * fi;
                vec2 p = rotate2d((uv - center) / curScale, -angDelta * fi) + 0.5;
                if (p.x >= 0.0 && p.x <= 1.0 && p.y >= 0.0 && p.y <= 1.0) {
                    vec4 s = texture(uTexture, p);
                    float a = s.a * pow(max(1.0 - falloff, 0.1), fi);
                    outRgb = mix(outRgb, s.rgb, a * (1.0 - outA * 0.65));
                    outA = max(outA, a);
                }
                curScale *= scDelta;
            }
            c = vec4(clamp(outRgb, 0.0, 1.0), clamp(outA, 0.0, 1.0));
        """.trimIndent(),

        "am_radial_repeat" to """
            int count = int(clamp(floor(uParams[0] + 0.5), 1.0, 18.0));
            float rad = uParams[1];
            float startAng = radians(uParams[2]);
            float sweepAng = radians(uParams[3]);
            bool orient = uModes[0] == 1;
            float instScale = 0.42;
            vec3 outRgb = vec3(0.0);
            float outA = 0.0;
            for (int i = 0; i < 18; i++) {
                if (i >= count) break;
                float frac = float(i) / max(float(count), 1.0);
                float theta = startAng + sweepAng * frac;
                vec2 center = vec2(0.5 + cos(theta) * rad / aspectRatio(), 0.5 + sin(theta) * rad);
                vec2 d = (uv - center) / instScale;
                if (orient) d = rotate2d(d, -theta);
                vec2 p = d + 0.5;
                if (p.x >= 0.0 && p.x <= 1.0 && p.y >= 0.0 && p.y <= 1.0) {
                    vec4 s = texture(uTexture, p);
                    outRgb = mix(outRgb, s.rgb, s.a * (1.0 - outA * 0.5));
                    outA = max(outA, s.a);
                }
            }
            c = vec4(clamp(outRgb, 0.0, 1.0), clamp(outA, 0.0, 1.0));
        """.trimIndent(),

        "am_repeat" to """
            int count = int(clamp(floor(uParams[0] + 0.5), 1.0, 12.0));
            vec2 off = vec2(uParams[1], uParams[2]);
            float sc = max(uParams[3], 0.25);
            float rot = radians(uParams[4]);
            vec3 outRgb = vec3(0.0);
            float outA = 0.0;
            for (int i = 0; i < 12; i++) {
                if (i >= count) break;
                float fi = float(i);
                float curSc = pow(sc, fi);
                vec2 p = rotate2d((uv - 0.5 - off * fi) / curSc, -rot * fi) + 0.5;
                if (p.x >= 0.0 && p.x <= 1.0 && p.y >= 0.0 && p.y <= 1.0) {
                    vec4 s = texture(uTexture, p);
                    outRgb = mix(outRgb, s.rgb, s.a * (1.0 - outA * 0.5));
                    outA = max(outA, s.a);
                }
            }
            c = vec4(clamp(outRgb, 0.0, 1.0), clamp(outA, 0.0, 1.0));
        """.trimIndent(),

        "am_repeat_along_path" to """
            int count = int(clamp(floor(uParams[0] + 0.5), 2.0, 16.0));
            float startP = clamp(uParams[1], 0.0, 1.0);
            float endP = clamp(uParams[2], 0.0, 1.0);
            bool align = uModes[0] == 1;
            vec2 p0 = vec2(0.15, 0.65);
            vec2 p1 = vec2(0.35, 0.20);
            vec2 p2 = vec2(0.65, 0.80);
            vec2 p3 = vec2(0.85, 0.35);
            vec3 outRgb = vec3(0.0);
            float outA = 0.0;
            for (int i = 0; i < 16; i++) {
                if (i >= count) break;
                float t = mix(startP, endP, float(i) / max(float(count - 1), 1.0));
                float omt = 1.0 - t;
                vec2 pos = omt*omt*omt*p0 + 3.0*omt*omt*t*p1 + 3.0*omt*t*t*p2 + t*t*t*p3;
                vec2 tang = 3.0*omt*omt*(p1-p0) + 6.0*omt*t*(p2-p1) + 3.0*t*t*(p3-p2);
                float a = align ? atan(tang.y, tang.x) : 0.0;
                vec2 p = rotate2d((uv - pos) / 0.34, -a) + 0.5;
                if (p.x >= 0.0 && p.x <= 1.0 && p.y >= 0.0 && p.y <= 1.0) {
                    vec4 s = texture(uTexture, p);
                    outRgb = mix(outRgb, s.rgb, s.a * (1.0 - outA * 0.5));
                    outA = max(outA, s.a);
                }
            }
            c = vec4(clamp(outRgb, 0.0, 1.0), clamp(outA, 0.0, 1.0));
        """.trimIndent(),

        "am_scatter_field" to """
            int count = int(clamp(floor(uParams[0] + 0.5), 1.0, 20.0));
            float seed = uParams[1];
            float scVar = clamp(uParams[2], 0.0, 1.0);
            float rotVar = clamp(uParams[3], 0.0, 1.0);
            vec3 outRgb = vec3(0.0);
            float outA = 0.0;
            for (int i = 0; i < 20; i++) {
                if (i >= count) break;
                float fi = float(i);
                vec2 center = vec2(
                    0.15 + 0.7 * hash12(vec2(fi, seed * 3.1)),
                    0.15 + 0.7 * hash12(vec2(fi + 19.0, seed * 7.3))
                );
                float sc = mix(0.32, 0.14 + 0.36 * hash12(vec2(fi + 41.0, seed)), scVar);
                float rot = (hash12(vec2(fi + 73.0, seed)) - 0.5) * TAU * rotVar;
                vec2 p = rotate2d((uv - center) / sc, -rot) + 0.5;
                if (p.x >= 0.0 && p.x <= 1.0 && p.y >= 0.0 && p.y <= 1.0) {
                    vec4 s = texture(uTexture, p);
                    outRgb = mix(outRgb, s.rgb, s.a * (1.0 - outA * 0.45));
                    outA = max(outA, s.a);
                }
            }
            c = vec4(clamp(outRgb, 0.0, 1.0), clamp(outA, 0.0, 1.0));
        """.trimIndent(),

        "am_scatter_repeat" to """
            int count = int(clamp(floor(uParams[0] + 0.5), 1.0, 18.0));
            float spread = max(uParams[1], 0.02);
            float seed = uParams[2];
            float scDecay = clamp(uParams[3], 0.0, 0.9);
            vec3 outRgb = vec3(0.0);
            float outA = 0.0;
            for (int i = 0; i < 18; i++) {
                if (i >= count) break;
                float fi = float(i);
                float ang = hash12(vec2(fi, seed)) * TAU;
                float r = sqrt(hash12(vec2(fi + 13.0, seed))) * spread;
                vec2 center = vec2(0.5 + cos(ang) * r / aspectRatio(), 0.5 + sin(ang) * r);
                float sc = max(0.45 * pow(1.0 - scDecay * 0.25, fi), 0.12);
                vec2 p = (uv - center) / sc + 0.5;
                if (p.x >= 0.0 && p.x <= 1.0 && p.y >= 0.0 && p.y <= 1.0) {
                    vec4 s = texture(uTexture, p);
                    outRgb = mix(outRgb, s.rgb, s.a * (1.0 - outA * 0.45));
                    outA = max(outA, s.a);
                }
            }
            c = vec4(clamp(outRgb, 0.0, 1.0), clamp(outA, 0.0, 1.0));
        """.trimIndent()
    )
}
