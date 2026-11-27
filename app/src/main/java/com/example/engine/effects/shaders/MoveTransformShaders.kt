package com.example.engine.effects.shaders

/**
 * Category 1: Move/Transform (12 Effects)
 */
object MoveTransformShaders {
    val bodies: Map<String, String> = mapOf(
        "am_auto_shake" to """
            float mag = uParams[0] * 0.085;
            float freq = max(uParams[1], 0.1);
            float evol = uParams[2];
            float angRad = radians(uParams[3]);
            float seed = uParams[4] * 13.7;
            float slack = clamp(uParams[5], 0.0, 1.0);
            float phase = uTime * freq + evol;
            float nx = (fbm(vec2(phase, seed), 3) - 0.5) * 2.0;
            float ny = (fbm(vec2(phase + 19.3, seed + 7.1), 3) - 0.5) * 2.0;
            float nr = (fbm(vec2(phase * 0.8 + 41.0, seed + 3.0), 2) - 0.5) * 2.0;
            vec2 dir = rotate2d(vec2(nx, ny), angRad) * mag;
            float rotAmt = nr * mag * 2.2 * (1.0 - slack * 0.4);
            vec2 d = rotate2d(uv - 0.5, rotAmt) + 0.5 + dir;
            c = sampleMirrored(d);
        """.trimIndent(),

        "am_move_along_path" to """
            float t = clamp(uParams[0], 0.0, 1.0);
            bool orient = uModes[0] == 1;
            float curv = uParams[1];
            vec2 p0 = vec2(-0.35, 0.15 * curv);
            vec2 p1 = vec2(-0.10, -0.40 * curv);
            vec2 p2 = vec2(0.15, 0.40 * curv);
            vec2 p3 = vec2(0.35, -0.10 * curv);
            float omt = 1.0 - t;
            vec2 pos = omt * omt * omt * p0 + 3.0 * omt * omt * t * p1 + 3.0 * omt * t * t * p2 + t * t * t * p3;
            vec2 tang = 3.0 * omt * omt * (p1 - p0) + 6.0 * omt * t * (p2 - p1) + 3.0 * t * t * (p3 - p2);
            float tangAngle = orient ? atan(tang.y, tang.x) : 0.0;
            vec2 d = rotate2d(uv - 0.5 - pos, -tangAngle) + 0.5;
            c = sampleMirroredBounded(d, 0);
        """.trimIndent(),

        "am_offset" to """
            vec2 shift = vec2(uParams[0], uParams[1]);
            int wrapMode = uModes[0];
            c = sampleMirroredBounded(uv - shift, wrapMode);
        """.trimIndent(),

        "am_oscillate" to """
            float angRad = radians(uParams[0]);
            float freq = max(uParams[1], 0.05);
            float mag = uParams[2] * 0.25;
            float ph = radians(uParams[3]);
            int waveType = uModes[0];
            float t = uTime * freq * TAU + ph;
            float w = waveType == 1
                ? (2.0 * abs(2.0 * fract(t / TAU + 0.25) - 1.0) - 1.0)
                : sin(t);
            vec2 off = vec2(cos(angRad), sin(angRad)) * (w * mag);
            c = sampleMirrored(uv - off);
        """.trimIndent(),

        "am_pulse_size" to """
            float freq = max(uParams[0], 0.05);
            float grow = max(uParams[1], 0.1);
            float shrink = max(uParams[2], 0.1);
            float ph = radians(uParams[3]);
            float s = 0.5 + 0.5 * sin(uTime * freq * TAU + ph);
            float scaleNow = mix(shrink, grow, s);
            vec2 p = (uv - 0.5) / max(scaleNow, 0.05) + 0.5;
            c = sampleMirrored(p);
        """.trimIndent(),

        "am_random_displacement" to """
            float mag = uParams[0] * 0.18;
            float evol = uParams[1];
            float seed = uParams[2] * 7.3;
            float scatter = clamp(uParams[3], 0.0, 1.0);
            float t = uTime * 1.6 + evol;
            vec2 globalDrift = vec2(fbm(vec2(t, seed), 3) - 0.5, fbm(vec2(seed + 11.0, t), 3) - 0.5) * mag;
            vec2 localNoise = vec2(
                valueNoise(uv * 6.0 + vec2(t, seed)) - 0.5,
                valueNoise(uv * 6.0 + vec2(seed + 23.0, t)) - 0.5
            ) * mag * scatter;
            c = sampleMirrored(uv + globalDrift + localNoise);
        """.trimIndent(),

        "am_random_jitter" to """
            float posJit = uParams[0] * 0.12;
            float angJit = radians(uParams[1]);
            float freq = max(uParams[2], 1.0);
            float seed = uParams[3] * 19.1;
            float stepT = floor(uTime * freq);
            float rx = (hash12(vec2(stepT, seed)) - 0.5) * 2.0 * posJit;
            float ry = (hash12(vec2(stepT + 5.0, seed + 13.0)) - 0.5) * 2.0 * posJit;
            float ra = (hash12(vec2(stepT + 17.0, seed + 29.0)) - 0.5) * 2.0 * angJit;
            vec2 d = rotate2d(uv - 0.5, ra) + 0.5 + vec2(rx, ry);
            c = sampleMirrored(d);
        """.trimIndent(),

        "am_raster_transform" to """
            vec2 centerPos = vec2(uParams[0], uParams[1]);
            float sc = max(uParams[2], 0.05);
            float rotRad = radians(uParams[3]);
            bool aa = uModes[0] == 1;
            vec2 p = rotate2d((uv - centerPos) / sc, -rotRad) + 0.5;
            if (!aa) {
                vec2 q = floor(p * max(uTexSize, vec2(64.0))) / max(uTexSize, vec2(64.0));
                c = sampleMirroredBounded(q, 0);
            } else {
                vec2 px = 0.5 / max(uTexSize, vec2(64.0));
                vec4 s1 = sampleMirroredBounded(p + vec2(px.x, 0.0), 0);
                vec4 s2 = sampleMirroredBounded(p - vec2(px.x, 0.0), 0);
                vec4 s3 = sampleMirroredBounded(p + vec2(0.0, px.y), 0);
                vec4 s4 = sampleMirroredBounded(p - vec2(0.0, px.y), 0);
                c = (sampleMirroredBounded(p, 0) * 2.0 + s1 + s2 + s3 + s4) / 6.0;
            }
        """.trimIndent(),

        "am_scale_assist" to """
            float mult = max(uParams[0], 0.05);
            bool uniformLock = uModes[0] == 1;
            float bx = max(uParams[1], 0.05);
            float by = uniformLock ? bx : max(uParams[2], 0.05);
            vec2 sc = vec2(mult * bx, mult * by);
            vec2 p = (uv - 0.5) / sc + 0.5;
            c = sampleMirroredBounded(p, 0);
        """.trimIndent(),

        "am_spin" to """
            float rps = uParams[0];
            float offsetDeg = uParams[1];
            float dir = uModes[0] == 1 ? -1.0 : 1.0;
            float ang = dir * (uTime * rps * TAU) + radians(offsetDeg);
            vec2 p = uv - 0.5;
            p.x *= aspectRatio();
            p = rotate2d(p, -ang);
            p.x /= aspectRatio();
            c = sampleMirrored(p + 0.5);
        """.trimIndent(),

        "am_stretch_axis" to """
            float stretch = max(uParams[0], 0.08);
            float angRad = radians(uParams[1]);
            bool compensate = uModes[0] == 1;
            vec2 p = uv - 0.5;
            p = rotate2d(p, -angRad);
            p.x /= stretch;
            if (compensate) {
                p.y *= mix(1.0, sqrt(stretch), 0.65);
            }
            p = rotate2d(p, angRad) + 0.5;
            c = sampleMirrored(p);
        """.trimIndent(),

        "am_swing" to """
            float freq = max(uParams[0], 0.05);
            float a1 = radians(uParams[1]);
            float a2 = radians(uParams[2]);
            float ph = radians(uParams[3]);
            float swingT = 0.5 + 0.5 * sin(uTime * freq * TAU + ph);
            float theta = mix(a1, a2, swingT);
            vec2 p = uv - 0.5;
            p.x *= aspectRatio();
            p = rotate2d(p, -theta);
            p.x /= aspectRatio();
            c = sampleMirrored(p + 0.5);
        """.trimIndent()
    )
}
