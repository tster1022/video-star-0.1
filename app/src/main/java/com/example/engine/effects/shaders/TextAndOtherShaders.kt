package com.example.engine.effects.shaders

/**
 * Category 8 (Text: 6 Effects) + Category 9 (Other: 8 Effects)
 */
object TextAndOtherShaders {
    val bodies: Map<String, String> = mapOf(
        // Category 8: Text (6 Effects)
        // Note: Dynamic text string regeneration (Count Up/Down, Text Progress, Randomizer, Timecode)
        // is evaluated per-frame into the layer's text texture via ShaderUniformEvaluator.formatTextForEffects,
        // and the GPU shaders below add visual styling, per-glyph spatial transform, alignment & glow polish.
        "am_text_count" to """
            c = orig;
        """.trimIndent(),

        "am_text_progress" to """
            c = orig;
        """.trimIndent(),

        "am_text_randomizer" to """
            float reveal = clamp(uParams[0], 0.0, 1.0);
            float scanJitter = (hash12(vec2(floor(uv.y * 24.0), floor(uTime * 16.0))) - 0.5) * 0.025 * (1.0 - reveal);
            c = sampleMirroredBounded(vec2(uv.x + scanJitter, uv.y), 0);
        """.trimIndent(),

        "am_text_spacing" to """
            float letterSp = uParams[0];
            float lineSp = max(uParams[1], 0.5);
            int align = uModes[0];
            float anchorX = align == 0 ? 0.1 : (align == 2 ? 0.9 : 0.5);
            vec2 p;
            p.x = (uv.x - anchorX) / max(1.0 + letterSp * 0.85, 0.35) + anchorX;
            p.y = (uv.y - 0.5) / lineSp + 0.5;
            c = sampleMirroredBounded(p, 0);
        """.trimIndent(),

        "am_text_transform" to """
            float rStart = clamp(uParams[0], 0.0, 1.0);
            float rEnd = max(rStart + 0.02, clamp(uParams[1], 0.0, 1.0));
            vec2 off = vec2(uParams[2], uParams[3]);
            float sc = max(uParams[4], 0.1);
            float rot = radians(uParams[5]);
            float overlap = clamp(uParams[6], 0.0, 1.0);
            float charIdx = floor(uv.x * 10.0) / 10.0;
            float w = smoothstep(rStart - overlap * 0.25, rStart + 0.02, charIdx) *
                      (1.0 - smoothstep(rEnd - 0.02, rEnd + overlap * 0.25, charIdx));
            vec2 cellCenter = vec2((floor(uv.x * 10.0) + 0.5) / 10.0, 0.5);
            vec2 localD = uv - cellCenter - off * w;
            localD = rotate2d(localD / mix(1.0, sc, w), -rot * w);
            c = sampleMirroredBounded(cellCenter + localD, 0);
        """.trimIndent(),

        "am_text_timecode" to """
            c = orig;
        """.trimIndent(),

        // Category 9: Other (8 Effects)
        "am_channel_remap_hsv" to """
            vec3 hsv = rgb2hsv(orig.rgb);
            float vals[5];
            vals[0] = hsv.x;
            vals[1] = hsv.y;
            vals[2] = hsv.z;
            vals[3] = 0.0;
            vals[4] = 1.0;
            int hs = clamp(uModes[0], 0, 4);
            int ss = clamp(uModes[1], 0, 4);
            int vs = clamp(uModes[2], 0, 4);
            vec3 remapped = vec3(vals[hs], vals[ss], vals[vs]);
            c = vec4(hsv2rgb(remapped), orig.a);
        """.trimIndent(),

        "am_channel_remap_rgb" to """
            float vals[6];
            vals[0] = orig.r;
            vals[1] = orig.g;
            vals[2] = orig.b;
            vals[3] = orig.a;
            vals[4] = 0.0;
            vals[5] = 1.0;
            int rs = clamp(uModes[0], 0, 5);
            int gs = clamp(uModes[1], 0, 5);
            int bs = clamp(uModes[2], 0, 5);
            int as = clamp(uModes[3], 0, 5);
            c = vec4(vals[rs], vals[gs], vals[bs], vals[as]);
        """.trimIndent(),

        "am_copy_background" to """
            float op = clamp(uParams[0], 0.0, 1.0);
            float sc = max(uParams[1], 0.1);
            vec2 bgUv = (uv - 0.5) / sc + 0.5;
            vec4 bg = uHasBg == 1
                ? texture(uBackgroundTex, clamp(bgUv, 0.0, 1.0))
                : sampleMirrored(bgUv);
            c = vec4(mix(orig.rgb, bg.rgb, op), max(orig.a, op * bg.a));
        """.trimIndent(),

        "am_echo_keyframes" to """
            float timeOff = uParams[0];
            int count = int(clamp(floor(uParams[1] + 0.5), 1.0, 10.0));
            float startInt = clamp(uParams[2], 0.0, 1.0);
            float decay = clamp(uParams[3], 0.05, 0.99);
            int mode = uModes[0];
            vec4 hist = texture(uHistoryTex, uv);
            vec3 acc = orig.rgb;
            float alphaAcc = orig.a;
            float w = startInt;
            for (int i = 1; i <= 8; i++) {
                if (i > count) break;
                vec2 echoUv = uv - vec2(sin(uTime * 3.0) * 0.012, cos(uTime * 2.5) * 0.012) * float(i) * (timeOff * 12.0);
                vec4 s = mix(sampleMirroredBounded(echoUv, 0), hist, 0.45);
                if (mode == 1) acc = clamp(acc + s.rgb * w * s.a, 0.0, 1.0);
                else if (mode == 2) acc = 1.0 - (1.0 - acc) * (1.0 - s.rgb * w * s.a);
                else acc = mix(acc, s.rgb, w * s.a * 0.5);
                alphaAcc = max(alphaAcc, s.a * w);
                w *= decay;
            }
            c = vec4(acc, alphaAcc);
        """.trimIndent(),

        "am_fill_behind" to """
            vec4 fill = uColors[0];
            float op = clamp(uParams[0], 0.0, 1.0) * fill.a;
            vec3 outRgb = mix(fill.rgb * op, orig.rgb, orig.a);
            float outA = clamp(orig.a + op * (1.0 - orig.a), 0.0, 1.0);
            c = vec4(outRgb, outA);
        """.trimIndent(),

        "am_magnify_background" to """
            vec2 center = vec2(uParams[0], uParams[1]);
            float mag = max(uParams[2], 0.25);
            float rad = max(uParams[3], 0.04);
            float feather = max(uParams[4], 0.005);
            float border = clamp(uParams[5], 0.0, 0.2);
            vec2 d = (uv - center) * vec2(aspectRatio(), 1.0);
            float dist = length(d);
            float mask = 1.0 - smoothstep(rad - feather * 0.5, rad + feather * 0.5, dist);
            vec2 magUv = center + (uv - center) / mag;
            vec4 srcMag = uHasBg == 1
                ? texture(uBackgroundTex, clamp(magUv, 0.0, 1.0))
                : sampleMirrored(magUv);
            float ring = smoothstep(rad - border, rad - border * 0.5, dist) * (1.0 - smoothstep(rad - border * 0.2, rad + 0.005, dist));
            vec3 col = mix(orig.rgb, srcMag.rgb, mask);
            col = mix(col, vec3(1.0), ring * 0.85);
            c = vec4(clamp(col, 0.0, 1.0), max(orig.a, max(mask, ring)));
        """.trimIndent(),

        "am_parenting_helper" to """
            // Parent offset/scale/rotation follow is injected by VideoStarGlRenderer via transform matrix;
            // shader reinforces spring lag echo when delay > 0.
            float lag = clamp(uParams[3], 0.0, 1.0);
            vec4 hist = texture(uHistoryTex, uv);
            c = mix(orig, max(orig, hist * 0.92), lag * 0.35);
        """.trimIndent(),

        "am_time_quantization" to """
            // Time Quantization quantizes uTime/uProgress upstream across the pass stack.
            c = orig;
        """.trimIndent()
    )
}
