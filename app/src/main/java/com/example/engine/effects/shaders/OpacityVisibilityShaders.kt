package com.example.engine.effects.shaders

/**
 * Category 4: Opacity/Visibility (7 Effects)
 */
object OpacityVisibilityShaders {
    val bodies: Map<String, String> = mapOf(
        "am_blink" to """
            float freq = max(uParams[0], 0.1);
            float duty = clamp(uParams[1], 0.01, 0.99);
            float ph = uParams[2] / 360.0;
            float cycle = fract(uTime * freq + ph);
            float vis = step(cycle, duty);
            c = vec4(orig.rgb, orig.a * vis);
        """.trimIndent(),

        "am_block_dissolve" to """
            float prog = clamp(uParams[0], 0.0, 1.0);
            float blk = clamp(uParams[1], 0.02, 1.0);
            float feather = max(uParams[2], 0.01);
            float seed = uParams[3];
            float count = mix(64.0, 6.0, blk);
            vec2 cell = floor(uv * vec2(count * aspectRatio(), count));
            float rnd = hash12(cell + seed * 7.31);
            float vis = smoothstep(prog - feather * 0.5, prog + feather * 0.5 + 0.001, rnd);
            if (prog <= 0.001) vis = 1.0;
            if (prog >= 0.999) vis = 0.0;
            c = vec4(orig.rgb, orig.a * vis);
        """.trimIndent(),

        "am_dissolve" to """
            float prog = clamp(uParams[0], 0.0, 1.0);
            float grainSz = clamp(uParams[1], 0.02, 1.0);
            float seed = uParams[2];
            float scale = mix(max(uTexSize.y, 120.0), 40.0, grainSz);
            float rnd = hash12(floor(uv * scale) + seed * 11.7);
            float vis = step(prog, rnd);
            c = vec4(orig.rgb, orig.a * vis);
        """.trimIndent(),

        "am_fade_in_out" to """
            float inDur = clamp(uParams[0], 0.0, 0.95);
            float outDur = clamp(uParams[1], 0.0, 0.95);
            int easingMode = uModes[0];
            float p = clamp(uProgress, 0.0, 1.0);
            float fadeIn = inDur > 0.001 ? clamp(p / inDur, 0.0, 1.0) : 1.0;
            float fadeOut = outDur > 0.001 ? clamp((1.0 - p) / outDur, 0.0, 1.0) : 1.0;
            float alpha = min(fadeIn, fadeOut);
            if (easingMode == 1) alpha = smoothstep(0.0, 1.0, alpha);
            else if (easingMode == 2) alpha = alpha * alpha * (3.0 - 2.0 * alpha);
            c = vec4(orig.rgb, orig.a * alpha);
        """.trimIndent(),

        "am_feather" to """
            float rad = clamp(uParams[0], 0.0, 0.6);
            float roundness = clamp(uParams[1], 0.0, 1.0);
            vec2 edgeDist = min(uv, 1.0 - uv);
            float boxEdge = min(edgeDist.x, edgeDist.y);
            vec2 corner = max(vec2(0.0), vec2(roundness * 0.25) - edgeDist);
            float roundEdge = mix(boxEdge, roundness * 0.25 - length(corner), step(0.001, length(corner)));
            float mask = rad > 0.001 ? smoothstep(0.0, rad, roundEdge) : 1.0;
            c = vec4(orig.rgb, orig.a * clamp(mask, 0.0, 1.0));
        """.trimIndent(),

        "am_flicker" to """
            float freq = max(uParams[0], 1.0);
            float str = clamp(uParams[1], 0.0, 1.0);
            float seed = uParams[2];
            float stepT = floor(uTime * freq);
            float n = hash11(stepT + seed * 19.3);
            float n2 = hash11(stepT * 1.37 + seed * 3.1);
            float alpha = 1.0 - (1.0 - n * n2) * str;
            c = vec4(orig.rgb, orig.a * clamp(alpha, 0.0, 1.0));
        """.trimIndent(),

        "am_pulse_opacity" to """
            float freq = max(uParams[0], 0.05);
            float minOp = clamp(uParams[1], 0.0, 1.0);
            float maxOp = clamp(uParams[2], 0.0, 1.0);
            float ph = radians(uParams[3]);
            float w = 0.5 + 0.5 * sin(uTime * freq * TAU + ph);
            float alpha = mix(minOp, maxOp, w);
            c = vec4(orig.rgb, orig.a * alpha);
        """.trimIndent()
    )
}
