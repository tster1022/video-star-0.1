package com.example.engine.packs

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads, caches, and resolves custom `.ttf` and `.otf` font binaries imported from
 * external packs for both Jetpack Compose TextLayer overlays and OpenGL Canvas export.
 */
object CustomFontRegistry {

    private val composeFonts = ConcurrentHashMap<String, FontFamily>()
    private val androidTypefaces = ConcurrentHashMap<String, Typeface>()
    @Volatile
    private var orderedFontEntries: List<Pair<String, String>> = emptyList()

    fun syncFonts(assets: List<ImportedFontAsset>) {
        val activeIds = assets.mapTo(HashSet()) { it.id }
        composeFonts.keys.retainAll(activeIds)
        androidTypefaces.keys.retainAll(activeIds)
        val entries = mutableListOf<Pair<String, String>>()
        assets.forEach { asset ->
            val file = File(asset.localFilePath)
            if (file.exists() && file.canRead() && file.length() > 64L) {
                runCatching {
                    val tf = Typeface.createFromFile(file)
                    androidTypefaces[asset.id] = tf
                    composeFonts[asset.id] = FontFamily(Font(file))
                    entries += (asset.id to asset.displayName)
                }
            }
        }
        orderedFontEntries = entries
    }

    fun resolveComposeFont(styleId: String): Pair<FontFamily, FontWeight>? {
        val ff = composeFonts[styleId] ?: return null
        return ff to FontWeight.Normal
    }

    fun resolveAndroidTypeface(styleId: String): Typeface? =
        androidTypefaces[styleId]

    fun importedFontOptions(): List<Pair<String, String>> = orderedFontEntries

    fun clear() {
        composeFonts.clear()
        androidTypefaces.clear()
        orderedFontEntries = emptyList()
    }
}
