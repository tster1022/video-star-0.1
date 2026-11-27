package com.example.engine.packs

import com.example.model.EffectCategory
import com.example.model.EffectParamSpec
import com.example.model.ShaderEffectPass
import kotlinx.serialization.Serializable

@Serializable
enum class ImportedPackSourceType {
    SAF_FOLDER,
    ZIP_ARCHIVE,
    FILES_PICKER,
    CLIPBOARD_PASTE,
    BUNDLED_SHADERS
}

@Serializable
data class CustomPackInfo(
    val id: String,
    val name: String,
    val sourceType: ImportedPackSourceType,
    val treeUriString: String? = null,
    val importedAtMs: Long = System.currentTimeMillis(),
    val presetCount: Int = 0,
    val shaderCount: Int = 0,
    val fontCount: Int = 0,
    val skippedCount: Int = 0
)

@Serializable
data class ImportedShaderEffect(
    val id: String,
    val name: String,
    val packId: String,
    val packName: String,
    val fileName: String,
    val category: EffectCategory = EffectCategory.AM_PROCEDURAL,
    val originalGlslVersion: String = "100",
    val rawSource: String,
    val preambleDecls: String = "",
    val mainBody: String = "",
    val fallbackEffectId: String = "am_ripple",
    val params: List<EffectParamSpec> = emptyList(),
    val isRunnablePostProcess: Boolean = true,
    val compileWarning: String? = null
)

@Serializable
data class ImportedXmlEffectPreset(
    val id: String,
    val name: String,
    val packId: String,
    val packName: String,
    val fileName: String,
    val category: EffectCategory = EffectCategory.AM_OTHER,
    val rawXml: String,
    val passes: List<ShaderEffectPass> = emptyList(),
    val primaryMappedEffectId: String = "am_tiles",
    val unresolvedEffectIds: List<String> = emptyList()
)

@Serializable
data class ImportedFontAsset(
    val id: String,
    val displayName: String,
    val fileName: String,
    val localFilePath: String,
    val packId: String,
    val packName: String
)

@Serializable
data class CustomPackManifest(
    val packs: List<CustomPackInfo> = emptyList(),
    val shaders: List<ImportedShaderEffect> = emptyList(),
    val presets: List<ImportedXmlEffectPreset> = emptyList(),
    val fonts: List<ImportedFontAsset> = emptyList()
)

data class PackImportSummary(
    val packId: String,
    val packName: String,
    val presetsAdded: Int,
    val shadersAdded: Int,
    val fontsAdded: Int,
    val skippedUnsupported: Int
) {
    val totalAdded: Int
        get() = presetsAdded + shadersAdded + fontsAdded
}

sealed interface PackImportStatus {
    data object Idle : PackImportStatus
    data class Scanning(
        val packName: String,
        val processedCount: Int,
        val totalEstimate: Int,
        val currentFile: String
    ) : PackImportStatus

    data class Completed(val summary: PackImportSummary) : PackImportStatus
    data class Failed(val message: String) : PackImportStatus
}
