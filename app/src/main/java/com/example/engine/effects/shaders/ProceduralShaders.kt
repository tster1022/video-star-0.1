package com.example.engine.effects.shaders

/**
 * Category 5: Procedural Generators & Overlays (23 Effects)
 */
object ProceduralShaders {
    val bodies: Map<String, String> = mapOf(
        "am_block_noise" to """
            float sc = max(uParams[0], 1.0);
            float evol = floor(uParams[1] * 8.0 + uTime * 4.0);
            bool colorize = uModes[0] == 1;
            float seed = uParams[2];
            vec2 cell = floor(uv * vec2(sc * aspectRatio(), sc));
            vec3 nCol = colorize
                ? vec3(hash12(cell + seed + evol), hash12(cell + seed + evol + 31.3), hash12(cell + seed + evol + 71.9))
                : vec3(hash12(cell + seed + evol));
            c = vec4(mix(orig.rgb, nCol, 0.72), max(orig.a, 0.9));
        """.trimIndent(),

        "am_checker" to """
            float sz = max(uParams[0], 1.0);
            float ang = radians(uParams[1]);
            vec4 col1 = uColors[0];
            vec4 col2 = uColors[1];
            vec2 p = rotate2d((uv - 0.5) * vec2(aspectRatio(), 1.0), -ang) * sz;
            float chk = mod(floor(p.x) + floor(p.y), 2.0);
            vec4 pat = mix(col1, col2, chk);
            c = vec4(mix(orig.rgb, pat.rgb, 0.8), max(orig.a, pat.a));
        """.trimIndent(),

        "am_clouds" to """
            float sc = max(uParams[0], 0.3);
            float evol = uParams[1] + uTime * 0.35;
            float cont = max(uParams[2], 0.1);
            vec3 c1 = uColors[0].rgb;
            vec3 c2 = uColors[1].rgb;
            vec2 p = (uv - 0.5) * vec2(aspectRatio(), 1.0) * sc;
            float n = fbm(p + vec2(evol, evol * 0.5), 5);
            n = clamp((n - 0.5) * (0.6 + cont * 1.4) + 0.5, 0.0, 1.0);
            vec3 cloudRgb = mix(c2, c1, n);
            c = vec4(mix(orig.rgb, cloudRgb, 0.75), max(orig.a, 0.95));
        """.trimIndent(),

        "am_contour_strips" to """
            float sp = max(uParams[0], 1.0);
            float th = clamp(uParams[1], 0.02, 0.95);
            float off = uParams[2] + uTime * 0.8;
            vec3 stripCol = uColors[0].rgb;
            float elev = fbm(uv * 3.5 + vec2(0.0, uTime * 0.25), 4) * sp + off;
            float band = abs(fract(elev) - 0.5);
            float line = 1.0 - smoothstep(th * 0.4, th * 0.5 + 0.03, band);
            c = vec4(mix(orig.rgb, stripCol, line * 0.9), max(orig.a, line));
        """.trimIndent(),

        "am_dots" to """
            float sp = max(uParams[0], 2.0);
            float rad = clamp(uParams[1], 0.02, 0.95) * 0.5;
            float ang = radians(uParams[2]);
            vec3 dotCol = uColors[0].rgb;
            vec2 p = rotate2d((uv - 0.5) * vec2(aspectRatio(), 1.0), -ang) * sp;
            float d = length(fract(p) - 0.5);
            float mask = 1.0 - smoothstep(rad - 0.04, rad + 0.04, d);
            c = vec4(mix(orig.rgb, dotCol, mask * 0.9), max(orig.a, mask));
        """.trimIndent(),

        "am_energy_beam" to """
            vec2 a = vec2(uParams[0], uParams[1]);
            vec2 b = vec2(uParams[2], uParams[3]);
            float thick = max(uParams[4], 0.005) * 0.14;
            float glowRad = max(uParams[5], 0.01) * 0.22;
            vec3 coreCol = uColors[0].rgb;
            vec3 outerCol = uColors[1].rgb;
            vec2 pa = (uv - a) * vec2(aspectRatio(), 1.0);
            vec2 ba = (b - a) * vec2(aspectRatio(), 1.0);
            float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
            float dist = length(pa - ba * h);
            float pulse = 0.85 + 0.15 * sin(uTime * 12.0 + h * 10.0);
            float core = exp(-dist / max(thick * 0.25, 0.002));
            float outer = exp(-dist / max(glowRad * pulse, 0.004));
            vec3 beam = outerCol * outer * 1.15 + coreCol * core * 1.3;
            c = vec4(clamp(orig.rgb + beam, 0.0, 1.0), max(orig.a, clamp(outer, 0.0, 1.0)));
        """.trimIndent(),

        "am_fractal_ridges" to """
            float sc = max(uParams[0], 0.3);
            float evol = uParams[1] + uTime * 0.35;
            float sharp = max(uParams[2], 0.1);
            vec3 ridgeCol = uColors[0].rgb;
            vec2 p = (uv - 0.5) * vec2(aspectRatio(), 1.0) * sc + vec2(evol * 0.5);
            float sum = 0.0;
            float amp = 0.6;
            for (int i = 0; i < 4; i++) {
                float n = 1.0 - abs(valueNoise(p) * 2.0 - 1.0);
                sum += pow(clamp(n, 0.0, 1.0), 1.0 + sharp * 2.5) * amp;
                p = p * 2.1 + vec2(7.3, 13.1);
                amp *= 0.5;
            }
            vec3 col = ridgeCol * clamp(sum, 0.0, 1.4);
            c = vec4(clamp(orig.rgb * 0.45 + col, 0.0, 1.0), max(orig.a, 0.95));
        """.trimIndent(),

        "am_grid" to """
            float sp = max(uParams[0], 1.0);
            float lw = clamp(uParams[1], 0.005, 0.45);
            vec3 gridCol = uColors[0].rgb;
            float op = clamp(uParams[2], 0.0, 1.0);
            vec2 st = (uv - 0.5) * vec2(aspectRatio(), 1.0) * sp;
            vec2 d = min(fract(st), 1.0 - fract(st));
            float line = 1.0 - smoothstep(lw * 0.45, lw * 0.55 + 0.015, min(d.x, d.y));
            c = vec4(mix(orig.rgb, gridCol, line * op), max(orig.a, line * op));
        """.trimIndent(),

        "am_grunge" to """
            float sc = max(uParams[0], 0.5);
            float gInt = clamp(uParams[1], 0.0, 1.0);
            float seed = uParams[2];
            float cont = max(uParams[3], 0.1);
            vec2 p = uv * vec2(aspectRatio(), 1.0) * sc + seed * 3.1;
            float scratches = fbm(vec2(p.x * 0.6, p.y * 9.0), 3);
            float dirt = fbm(p * 2.4, 4);
            float gVal = clamp(((dirt * 0.65 + scratches * 0.35) - 0.5) * (0.7 + cont) + 0.5, 0.0, 1.0);
            vec3 grunged = orig.rgb * mix(1.0, 0.35 + gVal * 0.85, gInt);
            c = vec4(clamp(grunged, 0.0, 1.0), orig.a);
        """.trimIndent(),

        "am_hex_array" to """
            float rad = max(uParams[0], 1.0);
            float gap = clamp(uParams[1], 0.01, 0.48);
            vec3 fillCol = uColors[0].rgb;
            vec3 strokeCol = uColors[1].rgb;
            vec2 p = (uv - 0.5) * vec2(aspectRatio(), 1.0) * rad;
            vec2 r = vec2(1.0, 1.7320508);
            vec2 h = r * 0.5;
            vec2 a = mod(p, r) - h;
            vec2 b = mod(p - h, r) - h;
            vec2 gv = dot(a, a) < dot(b, b) ? a : b;
            float edge = max(abs(gv.x), dot(abs(gv), normalize(vec2(1.0, 1.7320508))));
            float strokeMask = smoothstep(0.5 - gap, 0.5 - gap * 0.3, edge);
            vec3 hexCol = mix(fillCol, strokeCol, strokeMask);
            c = vec4(mix(orig.rgb, hexCol, 0.78), max(orig.a, 0.95));
        """.trimIndent(),

        "am_iridescence" to """
            float freq = max(uParams[0], 0.1);
            float ph = radians(uParams[1]) + uTime * 1.8;
            float sat = clamp(uParams[2], 0.0, 1.0);
            float ang = radians(uParams[3]);
            float lum = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
            vec2 dir = vec2(cos(ang), sin(ang));
            float wave = dot(uv - 0.5, dir) * freq * 3.0 + lum * freq * 2.5 + ph * 0.5;
            vec3 iri = 0.5 + 0.5 * cos(wave + vec3(0.0, 2.094, 4.188));
            iri = mix(vec3(lum), iri, sat);
            c = vec4(mix(orig.rgb, clamp(orig.rgb * 0.45 + iri * 0.85, 0.0, 1.0), 0.78), orig.a);
        """.trimIndent(),

        "am_lens_flare" to """
            vec2 pos = vec2(uParams[0], uParams[1]);
            float bright = max(uParams[2], 0.0);
            float sc = max(uParams[3], 0.05);
            vec3 tint = uColors[0].rgb;
            float rays = max(floor(uParams[4] + 0.5), 2.0);
            vec2 d = (uv - pos) * vec2(aspectRatio(), 1.0) / sc;
            float dist = length(d);
            float theta = atan(d.y, d.x);
            float starRay = pow(abs(cos(theta * rays * 0.5)), 8.0) * exp(-dist * 3.5);
            float anamorphic = exp(-abs(d.y) * 28.0) * exp(-abs(d.x) * 2.5);
            float core = exp(-dist * 7.5);
            vec2 ghostPos = 1.0 - pos;
            float ghost = exp(-length((uv - ghostPos) * vec2(aspectRatio(), 1.0)) * 9.0) * 0.45;
            vec3 flare = tint * (core * 1.4 + starRay * 0.8 + anamorphic * 0.7 + ghost) * bright;
            c = vec4(clamp(orig.rgb + flare, 0.0, 1.0), max(orig.a, clamp(core, 0.0, 1.0)));
        """.trimIndent(),

        "am_lightning" to """
            vec2 a = vec2(uParams[0], uParams[1]);
            vec2 b = vec2(uParams[2], uParams[3]);
            float amp = uParams[4] * 0.22;
            float branches = clamp(uParams[5], 0.0, 8.0);
            float seed = uParams[6];
            float glow = clamp(uParams[7], 0.0, 1.0);
            vec3 boltCol = uColors[0].rgb;
            vec2 pa = (uv - a) * vec2(aspectRatio(), 1.0);
            vec2 ba = (b - a) * vec2(aspectRatio(), 1.0);
            float baLen = max(length(ba), 0.001);
            vec2 dir = ba / baLen;
            vec2 perp = vec2(-dir.y, dir.x);
            float tProj = clamp(dot(pa, dir) / baLen, 0.0, 1.0);
            float tTick = floor(uTime * 18.0) + seed;
            float zig = (fbm(vec2(tProj * 10.0, tTick), 4) - 0.5) * 2.0 * amp * sin(tProj * PI);
            float distMain = abs(dot(pa, perp) - zig);
            float bolt = exp(-distMain * (90.0 - glow * 45.0)) + exp(-distMain * 14.0) * glow * 0.55;
            if (branches >= 1.0) {
                float zig2 = (fbm(vec2(tProj * 14.0 + 7.0, tTick + 3.0), 3) - 0.5) * 2.4 * amp * sin(tProj * PI);
                float distSub = abs(dot(pa, perp) - (zig + zig2 * 0.55));
                bolt += exp(-distSub * 85.0) * 0.55 * step(0.3, tProj);
            }
            vec3 outRgb = clamp(orig.rgb + boltCol * bolt * 1.4, 0.0, 1.0);
            c = vec4(outRgb, max(orig.a, clamp(bolt, 0.0, 1.0)));
        """.trimIndent(),

        "am_noise" to """
            float str = clamp(uParams[0], 0.0, 1.0) * 0.45;
            bool mono = uModes[0] == 1;
            bool dyn = uModes[1] == 1;
            float t = dyn ? fract(uTime * 24.0) * 100.0 : 1.0;
            vec3 n = mono
                ? vec3(hash12(uv * 512.0 + t) - 0.5)
                : vec3(hash12(uv * 512.0 + t), hash12(uv * 512.0 + t + 13.7), hash12(uv * 512.0 + t + 41.3)) - 0.5;
            c = vec4(clamp(orig.rgb + n * str, 0.0, 1.0), orig.a);
        """.trimIndent(),

        "am_omino_diffusion" to """
            float sc = max(uParams[0], 0.5);
            float thresh = clamp(uParams[1], 0.05, 0.95);
            float evol = uParams[2] + uTime * 0.4;
            vec2 p = uv * vec2(aspectRatio(), 1.0) * sc;
            float field = fbm(p + vec2(sin(evol), cos(evol * 0.7)), 4);
            float ridge = smoothstep(thresh - 0.15, thresh + 0.15, field);
            vec3 diffCol = hsv2rgb(vec3(fract(field + evol * 0.08), 0.65, ridge));
            c = vec4(mix(orig.rgb, diffCol, 0.72), max(orig.a, 0.9));
        """.trimIndent(),

        "am_radial_rays" to """
            float cnt = max(floor(uParams[0] + 0.5), 2.0);
            float ang = radians(uParams[1]);
            float twist = uParams[2];
            vec3 c1 = uColors[0].rgb;
            vec3 c2 = uColors[1].rgb;
            vec2 p = (uv - 0.5) * vec2(aspectRatio(), 1.0);
            float r = length(p);
            float th = atan(p.y, p.x) + ang + r * twist * 3.0;
            float sector = smoothstep(0.42, 0.58, 0.5 + 0.5 * cos(th * cnt));
            vec3 rayCol = mix(c2, c1, sector);
            c = vec4(mix(orig.rgb, rayCol, 0.78), max(orig.a, 0.95));
        """.trimIndent(),

        "am_ribbon" to """
            float amp = uParams[0];
            float freq = max(uParams[1], 0.1);
            float thick = max(uParams[2], 0.005);
            float ph = radians(uParams[3]) + uTime * 3.0;
            vec3 ribCol = uColors[0].rgb;
            float curve = 0.5 + amp * sin(uv.x * freq * TAU + ph) * cos(uv.x * freq * 1.7 - ph * 0.5);
            float dist = abs(uv.y - curve);
            float alpha = 1.0 - smoothstep(thick * 0.4, thick * 0.5 + 0.02, dist);
            float highlight = exp(-dist / max(thick * 0.2, 0.003)) * 0.45;
            c = vec4(mix(orig.rgb, clamp(ribCol + highlight, 0.0, 1.0), alpha), max(orig.a, alpha));
        """.trimIndent(),

        "am_simple_starfield" to """
            float density = clamp(uParams[0], 0.05, 1.0);
            float spd = uParams[1];
            float sz = clamp(uParams[2], 0.05, 1.0);
            float seed = uParams[3];
            vec2 p = vec2(uv.x * aspectRatio(), uv.y) * 28.0 + vec2(uTime * spd * 4.0, 0.0);
            vec2 id = floor(p);
            vec2 f = fract(p) - 0.5;
            float rnd = hash12(id + seed * 3.7);
            float star = 0.0;
            if (rnd > (1.0 - density * 0.45)) {
                vec2 centerOff = (hash22(id + 11.1) - 0.5) * 0.65;
                float d = length(f - centerOff);
                float twinkle = 0.65 + 0.35 * sin(uTime * 5.0 + rnd * 40.0);
                star = smoothstep(0.18 * sz + 0.03, 0.01, d) * twinkle;
            }
            c = vec4(clamp(orig.rgb * 0.35 + vec3(star), 0.0, 1.0), max(orig.a, star));
        """.trimIndent(),

        "am_star" to """
            float pts = max(floor(uParams[0] + 0.5), 3.0);
            float rIn = clamp(uParams[1], 0.01, 0.8);
            float rOut = max(rIn + 0.02, uParams[2]);
            float ang = radians(uParams[3]);
            float glow = clamp(uParams[4], 0.0, 1.0);
            vec3 starCol = uColors[0].rgb;
            vec2 p = rotate2d((uv - 0.5) * vec2(aspectRatio(), 1.0), -ang);
            float r = length(p);
            float th = atan(p.y, p.x);
            float wave = 0.5 + 0.5 * cos(th * pts);
            float targetR = mix(rIn, rOut, wave);
            float mask = 1.0 - smoothstep(targetR - 0.015, targetR + 0.015, r);
            float halo = exp(-max(r - targetR, 0.0) * 18.0) * glow;
            vec3 outRgb = mix(orig.rgb, starCol, clamp(mask + halo * 0.7, 0.0, 1.0));
            c = vec4(outRgb, max(orig.a, clamp(mask + halo * 0.5, 0.0, 1.0)));
        """.trimIndent(),

        "am_starfield" to """
            float spdZ = uParams[0] * 0.9;
            float density = clamp(uParams[1], 0.1, 1.0);
            float sz = clamp(uParams[2], 0.05, 1.0);
            float colorVar = clamp(uParams[3], 0.0, 1.0);
            vec2 p = (uv - 0.5) * vec2(aspectRatio(), 1.0);
            vec3 acc = vec3(0.0);
            for (int i = 0; i < 4; i++) {
                float fi = float(i);
                float depth = fract(fi * 0.25 + uTime * spdZ * 0.45);
                float scale = mix(18.0, 2.0, depth);
                float fade = smoothstep(0.0, 0.25, depth) * smoothstep(1.0, 0.75, depth);
                vec2 gv = p * scale + vec2(fi * 17.3, fi * 29.1);
                vec2 id = floor(gv);
                vec2 f = fract(gv) - 0.5;
                float rnd = hash12(id);
                if (rnd > (1.0 - density * 0.35)) {
                    float d = length(f - (hash22(id + 5.0) - 0.5) * 0.6);
                    float s = smoothstep(0.14 * sz + 0.02, 0.005, d) * fade;
                    vec3 tint = mix(vec3(1.0), hsv2rgb(vec3(fract(rnd * 9.0), 0.7, 1.0)), colorVar);
                    acc += tint * s;
                }
            }
            c = vec4(clamp(orig.rgb * 0.25 + acc, 0.0, 1.0), max(orig.a, clamp(length(acc), 0.0, 1.0)));
        """.trimIndent(),

        "am_stripes" to """
            float cnt = max(uParams[0], 1.0);
            float ang = radians(uParams[1]);
            float ph = uParams[2] / 360.0;
            vec3 col1 = uColors[0].rgb;
            vec3 col2 = uColors[1].rgb;
            vec2 p = rotate2d((uv - 0.5) * vec2(aspectRatio(), 1.0), -ang);
            float s = step(0.5, fract(p.x * cnt + ph));
            vec3 stripeRgb = mix(col1, col2, s);
            c = vec4(mix(orig.rgb, stripeRgb, 0.78), max(orig.a, 0.95));
        """.trimIndent(),

        "am_turbulence" to """
            float sc = max(uParams[0], 0.3);
            float evol = uParams[1] + uTime * 0.4;
            int oct = int(clamp(floor(uParams[2] + 0.5), 1.0, 6.0));
            float bright = max(uParams[3], 0.0);
            vec2 p = (uv - 0.5) * vec2(aspectRatio(), 1.0) * sc + vec2(evol);
            float tVal = fbm(p + vec2(fbm(p * 1.4, oct)), oct) * bright;
            vec3 turbCol = vec3(clamp(tVal, 0.0, 1.0));
            c = vec4(mix(orig.rgb, turbCol, 0.72), max(orig.a, 0.95));
        """.trimIndent(),

        "am_voronoi_cells" to """
            float sc = max(uParams[0], 1.0);
            float evol = uParams[1] + uTime * 0.6;
            float bw = clamp(uParams[2], 0.0, 0.6);
            bool colorize = uModes[0] == 1;
            vec2 st = uv * vec2(aspectRatio(), 1.0) * sc;
            vec2 i_st = floor(st);
            vec2 f_st = fract(st);
            float minDist = 8.0;
            float secondDist = 8.0;
            vec2 bestId = vec2(0.0);
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    vec2 neighbor = vec2(float(x), float(y));
                    vec2 cellId = i_st + neighbor;
                    vec2 pt = 0.5 + 0.42 * sin(evol + TAU * hash22(cellId));
                    float d = length(neighbor + pt - f_st);
                    if (d < minDist) {
                        secondDist = minDist;
                        minDist = d;
                        bestId = cellId;
                    } else if (d < secondDist) {
                        secondDist = d;
                    }
                }
            }
            float edge = smoothstep(0.0, max(bw, 0.01), secondDist - minDist);
            vec3 cellCol = colorize
                ? hsv2rgb(vec3(hash12(bestId), 0.75, 0.92))
                : vec3(1.0 - minDist * 0.8);
            vec3 vorRgb = mix(vec3(0.05), cellCol, edge);
            c = vec4(mix(orig.rgb, vorRgb, 0.78), max(orig.a, 0.95));
        """.trimIndent()
    )
}
