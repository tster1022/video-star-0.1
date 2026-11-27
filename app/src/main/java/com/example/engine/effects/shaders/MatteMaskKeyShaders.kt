package com.example.engine.effects.shaders

/**
 * Category 3: Matte/Mask/Key (11 Effects)
 */
object MatteMaskKeyShaders {
    val bodies: Map<String, String> = mapOf(
        "am_adv_chroma_key" to """
            vec3 keyCol = uColors[0].rgb;
            float thresh = clamp(uParams[0], 0.01, 1.0);
            float feather = clamp(uParams[1], 0.001, 0.8);
            float despill = clamp(uParams[2], 0.0, 1.0);
            float edgeRefine = clamp(uParams[3], 0.0, 1.0);
            vec3 yccSrc = rgb2ycbcr(orig.rgb);
            vec3 yccKey = rgb2ycbcr(keyCol);
            float chromaDist = length(yccSrc.yz - yccKey.yz);
            float alpha = smoothstep(thresh * 0.5, thresh * 0.5 + max(feather * 0.5, 0.01), chromaDist);
            // Edge refine morphological mini-erode
            if (edgeRefine > 0.01) {
                vec2 px = (1.5 / max(uTexSize, vec2(64.0))) * edgeRefine * 2.5;
                float a1 = smoothstep(thresh * 0.5, thresh * 0.5 + max(feather * 0.5, 0.01),
                    length(rgb2ycbcr(texture(uTexture, clamp(uv + vec2(px.x, 0.0), 0.0, 1.0)).rgb).yz - yccKey.yz));
                float a2 = smoothstep(thresh * 0.5, thresh * 0.5 + max(feather * 0.5, 0.01),
                    length(rgb2ycbcr(texture(uTexture, clamp(uv - vec2(px.x, 0.0), 0.0, 1.0)).rgb).yz - yccKey.yz));
                float a3 = smoothstep(thresh * 0.5, thresh * 0.5 + max(feather * 0.5, 0.01),
                    length(rgb2ycbcr(texture(uTexture, clamp(uv + vec2(0.0, px.y), 0.0, 1.0)).rgb).yz - yccKey.yz));
                float a4 = smoothstep(thresh * 0.5, thresh * 0.5 + max(feather * 0.5, 0.01),
                    length(rgb2ycbcr(texture(uTexture, clamp(uv - vec2(0.0, px.y), 0.0, 1.0)).rgb).yz - yccKey.yz));
                alpha = min(alpha, (a1 + a2 + a3 + a4) * 0.25 + (1.0 - edgeRefine));
            }
            vec3 rgbOut = orig.rgb;
            if (despill > 0.001) {
                float keyDom = dot(rgbOut, normalize(max(keyCol, vec3(0.001)))) - dot(rgbOut, vec3(0.333));
                float spillAmt = clamp(keyDom * despill * 1.4, 0.0, 1.0);
                float lum = dot(rgbOut, vec3(0.299, 0.587, 0.114));
                rgbOut = mix(rgbOut, vec3(lum) + (rgbOut - keyCol * spillAmt) * 0.5, spillAmt);
            }
            c = vec4(clamp(rgbOut, 0.0, 1.0), orig.a * clamp(alpha, 0.0, 1.0));
        """.trimIndent(),

        "am_chroma_key" to """
            vec3 keyCol = uColors[0].rgb;
            float thresh = clamp(uParams[0], 0.01, 1.0);
            float feather = clamp(uParams[1], 0.001, 0.8);
            float dist = distance(orig.rgb, keyCol);
            float alpha = smoothstep(thresh * 0.65, thresh * 0.65 + max(feather * 0.5, 0.01), dist);
            c = vec4(orig.rgb, orig.a * alpha);
        """.trimIndent(),

        "am_key_spill_cleaner" to """
            vec3 spillCol = uColors[0].rgb;
            float supp = clamp(uParams[0], 0.0, 1.0);
            float lumaRestore = clamp(uParams[1], 0.0, 1.0);
            float origLum = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
            vec3 hsvSrc = rgb2hsv(orig.rgb);
            vec3 hsvSpill = rgb2hsv(spillCol);
            float hueDiff = abs(hsvSrc.x - hsvSpill.x);
            hueDiff = min(hueDiff, 1.0 - hueDiff);
            float spillMask = (1.0 - smoothstep(0.05, 0.22, hueDiff)) * hsvSrc.y;
            vec3 cleaned = mix(orig.rgb, vec3(origLum), spillMask * supp);
            float newLum = dot(cleaned, vec3(0.299, 0.587, 0.114));
            cleaned += (origLum - newLum) * lumaRestore;
            c = vec4(clamp(cleaned, 0.0, 1.0), orig.a);
        """.trimIndent(),

        "am_luma_key" to """
            float lowT = clamp(uParams[0], 0.0, 1.0);
            float highT = max(lowT + 0.02, clamp(uParams[1], 0.0, 1.0));
            float feather = max(uParams[2], 0.01);
            bool inv = uModes[0] == 1;
            float lum = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
            float keep = smoothstep(lowT - feather, lowT + feather, lum) *
                         (1.0 - smoothstep(highT - feather, highT + feather, lum));
            if (inv) keep = 1.0 - keep;
            c = vec4(orig.rgb, orig.a * clamp(keep, 0.0, 1.0));
        """.trimIndent(),

        "am_luma_stamper" to """
            float thresh = clamp(uParams[0], 0.0, 1.0);
            float decay = clamp(uParams[1], 0.1, 0.99);
            int mode = uModes[0];
            vec4 prevCol = texture(uHistoryTex, uv);
            float lum = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
            float stampMask = smoothstep(thresh, min(1.0, thresh + 0.18), lum);
            vec3 trail = max(prevCol.rgb * decay, orig.rgb * stampMask);
            vec3 outRgb;
            if (mode == 0) outRgb = 1.0 - (1.0 - orig.rgb) * (1.0 - trail * 0.85);
            else if (mode == 1) outRgb = clamp(orig.rgb + trail * 0.75, 0.0, 1.0);
            else outRgb = mix(trail, orig.rgb, max(orig.a, 0.4));
            c = vec4(outRgb, max(orig.a, prevCol.a * decay));
        """.trimIndent(),

        "am_matte_choker" to """
            float choke = clamp(uParams[0], -1.0, 1.0);
            float softness = clamp(uParams[1], 0.0, 1.0);
            float passes = clamp(uParams[2], 1.0, 4.0);
            vec2 px = (1.5 / max(uTexSize, vec2(64.0))) * passes * (1.0 + abs(choke) * 2.5);
            float minA = orig.a;
            float maxA = orig.a;
            float sumA = orig.a;
            for (int i = 0; i < 8; i++) {
                float a = float(i) * (TAU / 8.0);
                float sa = texture(uTexture, clamp(uv + vec2(cos(a), sin(a)) * px, 0.0, 1.0)).a;
                minA = min(minA, sa);
                maxA = max(maxA, sa);
                sumA += sa;
            }
            float avgA = sumA / 9.0;
            float targetA = choke >= 0.0 ? mix(orig.a, minA, choke) : mix(orig.a, maxA, -choke);
            float finalA = smoothstep(0.5 - softness * 0.45 - 0.02, 0.5 + softness * 0.45 + 0.02, mix(targetA, avgA, softness * 0.6));
            c = vec4(orig.rgb, finalA);
        """.trimIndent(),

        "am_matte_fringe" to """
            float w = clamp(uParams[0], 0.02, 1.0) * 18.0;
            vec3 fringeCol = uColors[0].rgb;
            float soft = clamp(uParams[1], 0.0, 1.0);
            float op = clamp(uParams[2], 0.0, 1.0);
            vec2 px = w / max(uTexSize, vec2(64.0));
            float outerMax = 0.0;
            float innerMin = orig.a;
            for (int i = 0; i < 8; i++) {
                float a = float(i) * (TAU / 8.0);
                float sa = texture(uTexture, clamp(uv + vec2(cos(a), sin(a)) * px, 0.0, 1.0)).a;
                outerMax = max(outerMax, sa);
                innerMin = min(innerMin, sa);
            }
            float ring = clamp(outerMax - innerMin, 0.0, 1.0);
            ring = smoothstep(0.05, max(0.08, 0.9 - soft * 0.5), ring) * op;
            vec3 outRgb = mix(orig.rgb, fringeCol, ring);
            float outA = max(orig.a, ring);
            c = vec4(outRgb, outA);
        """.trimIndent(),

        "am_radial_wipe" to """
            float prog = clamp(uParams[0], 0.0, 1.0);
            float startDeg = uParams[1];
            float feather = max(uParams[2], 0.005);
            vec2 center = vec2(uParams[3], uParams[4]);
            vec2 d = uv - center;
            float ang = degrees(atan(d.y, d.x)) - startDeg;
            float normAng = fract(ang / 360.0);
            float mask = 1.0 - smoothstep(prog - feather * 0.25, prog + feather * 0.25, normAng);
            if (prog >= 0.995) mask = 1.0;
            if (prog <= 0.005) mask = 0.0;
            c = vec4(orig.rgb, orig.a * mask);
        """.trimIndent(),

        "am_solid_matte" to """
            vec4 fill = uColors[0];
            float blend = clamp(uParams[0], 0.0, 1.0);
            c = vec4(mix(orig.rgb, fill.rgb, blend * fill.a), max(orig.a, blend * fill.a));
        """.trimIndent(),

        "am_vignette" to """
            float size = max(uParams[0], 0.05);
            float roundness = clamp(uParams[1], 0.0, 1.0);
            float feather = max(uParams[2], 0.02);
            float str = clamp(uParams[3], 0.0, 1.0);
            vec3 vigCol = uColors[0].rgb;
            vec2 d = abs(uv - 0.5) * 2.0;
            float rectDist = max(d.x, d.y);
            float circDist = length(d * vec2(mix(1.0, aspectRatio(), 0.35), 1.0));
            float dist = mix(rectDist, circDist, roundness);
            float vig = smoothstep(size, size + feather * 1.1, dist) * str;
            c = vec4(mix(orig.rgb, vigCol, clamp(vig, 0.0, 1.0)), orig.a);
        """.trimIndent(),

        "am_wipe" to """
            float startP = clamp(uParams[0], 0.0, 1.0);
            float endP = clamp(uParams[1], 0.0, 1.0);
            float angRad = radians(uParams[2]);
            float feather = max(uParams[3], 0.005);
            vec2 dir = vec2(cos(angRad), sin(angRad));
            float proj = dot(uv - 0.5, dir) + 0.5;
            float mask = smoothstep(startP - feather, startP + feather, proj) *
                         (1.0 - smoothstep(endP - feather, endP + feather, proj));
            c = vec4(orig.rgb, orig.a * clamp(mask, 0.0, 1.0));
        """.trimIndent()
    )
}
