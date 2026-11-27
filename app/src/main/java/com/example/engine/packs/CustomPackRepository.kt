package com.example.engine.packs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.engine.ae.AeEffectDefinition
import com.example.engine.ae.AeEffectSource
import com.example.engine.ae.AeEngineCategory
import com.example.engine.ae.AeTileVisualStyle
import com.example.engine.effects.shaders.ShaderCommonGlsl
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogItem
import com.example.model.EffectCategory
import com.example.model.ShaderEffectPass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Zero-permission SAF + ZIP + Copy/Paste Pack Repository managing persistent custom
 * Alight Motion XML presets, transpiled GLSL shaders, and imported .ttf/.otf fonts.
 */
object CustomPackRepository {

    private const val DIR_ROOT = "custom_packs"
    private const val DIR_FONTS = "fonts"
    private const val MANIFEST_FILE = "packs_manifest.json"
    private const val CLIPBOARD_PACK_ID = "pack_clipboard_paste"
    private const val MAX_TEXT_ENTRY_BYTES = 2 * 1024 * 1024
    private const val MAX_FONT_ENTRY_BYTES = 16 * 1024 * 1024

    private val blockedDirNames = setOf("dexopt", "bin", "lib", "oat", ".git", "__macosx")
    private val blockedExtensions = setOf("dex", "jar", "so", "apk", "odex", "vdex", "class", "exe", "dll", "bin")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var isInitialized = false

    private val _packs = MutableStateFlow<List<CustomPackInfo>>(emptyList())
    val packs: StateFlow<List<CustomPackInfo>> = _packs.asStateFlow()

    private val _importedShaders = MutableStateFlow<List<ImportedShaderEffect>>(emptyList())
    val importedShaders: StateFlow<List<ImportedShaderEffect>> = _importedShaders.asStateFlow()

    private val _importedPresets = MutableStateFlow<List<ImportedXmlEffectPreset>>(emptyList())
    val importedPresets: StateFlow<List<ImportedXmlEffectPreset>> = _importedPresets.asStateFlow()

    private val _importedFonts = MutableStateFlow<List<ImportedFontAsset>>(emptyList())
    val importedFonts: StateFlow<List<ImportedFontAsset>> = _importedFonts.asStateFlow()

    private val _importStatus = MutableStateFlow<PackImportStatus>(PackImportStatus.Idle)
    val importStatus: StateFlow<PackImportStatus> = _importStatus.asStateFlow()

    // Fast lookup caches rebuilt atomically whenever packs change
    @Volatile
    private var shaderByIdCache: Map<String, ImportedShaderEffect> = emptyMap()
    @Volatile
    private var presetByIdCache: Map<String, ImportedXmlEffectPreset> = emptyMap()
    @Volatile
    private var catalogItemsCache: List<EffectCatalogItem> = emptyList()
    @Volatile
    private var catalogByIdCache: Map<String, EffectCatalogItem> = emptyMap()

    fun initialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (isInitialized) return
        isInitialized = true

        ioScope.launch {
            val loaded = loadManifestFromDisk(ctx)
            applyManifestToState(loaded)
        }
    }

    fun clearImportStatus() {
        _importStatus.value = PackImportStatus.Idle
    }

    /**
     * Installs the 10 curated GLSL ES 1.00 -> 3.00 es shaders from All_Shaders into
     * My Effects synchronously in memory and persists to disk in background.
     */
    fun installBundledShaderPack(context: Context): Int {
        val ctx = context.applicationContext
        appContext = ctx
        val pack = BundledCustomShaders.packInfo
        val bundled = BundledCustomShaders.shaders
        val manifestToPersist: CustomPackManifest
        synchronized(writeLock) {
            val basePacks = _packs.value.filterNot { it.id == pack.id }
            val baseShaders = _importedShaders.value.filterNot { it.packId == pack.id }
            manifestToPersist = CustomPackManifest(
                packs = basePacks + pack,
                shaders = (bundled + baseShaders).distinctBy { it.id },
                presets = _importedPresets.value.distinctBy { it.id },
                fonts = _importedFonts.value.distinctBy { it.id }
            )
            applyManifestToState(manifestToPersist)
        }
        ioScope.launch {
            saveManifestToDisk(ctx, manifestToPersist)
        }
        _importStatus.value = PackImportStatus.Completed(
            PackImportSummary(
                packId = pack.id,
                packName = pack.name,
                presetsAdded = 0,
                shadersAdded = bundled.size,
                fontsAdded = 0,
                skippedUnsupported = 0
            )
        )
        return bundled.size
    }

    internal fun resetAllForTesting(context: Context? = null) {
        synchronized(writeLock) {
            context?.let {
                File(it.filesDir, DIR_ROOT).deleteRecursively()
            }
            applyManifestToState(CustomPackManifest())
            _importStatus.value = PackImportStatus.Idle
            isInitialized = false
        }
    }

    // --- Public Catalog & Shader Resolvers (O(1) thread-safe snapshots) ---

    fun dynamicCatalogItems(): List<EffectCatalogItem> = catalogItemsCache

    fun findCatalogItem(effectId: String): EffectCatalogItem? = catalogByIdCache[effectId]

    fun findShaderEffect(effectId: String): ImportedShaderEffect? = shaderByIdCache[effectId]

    fun findXmlPreset(effectId: String): ImportedXmlEffectPreset? = presetByIdCache[effectId]

    fun hasCustomEffect(effectId: String): Boolean =
        effectId in shaderByIdCache || effectId in presetByIdCache

    fun findCustomShaderFragmentSource(effectId: String): String? {
        shaderByIdCache[effectId]?.let { shader ->
            if (shader.isRunnablePostProcess && shader.mainBody.isNotBlank()) {
                return ShaderCommonGlsl.buildCustomFragmentShader(shader.preambleDecls, shader.mainBody)
            }
            return null
        }
        presetByIdCache[effectId]?.let { preset ->
            val primary = preset.primaryMappedEffectId
            shaderByIdCache[primary]?.let { shader ->
                if (shader.isRunnablePostProcess && shader.mainBody.isNotBlank()) {
                    return ShaderCommonGlsl.buildCustomFragmentShader(shader.preambleDecls, shader.mainBody)
                }
            }
        }
        return null
    }

    fun findFallbackEffectId(effectId: String): String? =
        shaderByIdCache[effectId]?.fallbackEffectId
            ?: presetByIdCache[effectId]?.primaryMappedEffectId

    fun findAeDefinition(effectId: String): AeEffectDefinition? {
        shaderByIdCache[effectId]?.let { sh ->
            return AeEffectDefinition(
                id = sh.id,
                name = sh.name,
                category = AeEngineCategory.MY_EFFECTS,
                source = AeEffectSource.ALIGHT_MOTION,
                description = "Custom GLSL shader (${sh.fileName}) • ${sh.packName}",
                shaderCategory = sh.category,
                defaultIntensity = 0.85f,
                params = sh.params,
                tileStyle = AeTileVisualStyle.PROCEDURAL_NOISE
            )
        }
        presetByIdCache[effectId]?.let { preset ->
            val baseItem = catalogByIdCache[preset.id]
            return AeEffectDefinition(
                id = preset.id,
                name = preset.name,
                category = AeEngineCategory.MY_EFFECTS,
                source = AeEffectSource.ALIGHT_MOTION,
                description = "AM XML preset (${preset.passes.size} pass) • ${preset.packName}",
                shaderCategory = preset.category,
                defaultIntensity = preset.passes.firstOrNull()?.intensity ?: 0.8f,
                params = baseItem?.params.orEmpty(),
                tileStyle = AeTileVisualStyle.WAVE_STRIP
            )
        }
        return null
    }

    fun allAeDefinitions(): List<AeEffectDefinition> =
        catalogItemsCache.mapNotNull { findAeDefinition(it.id) }

    /**
     * Expands a selected ID into one or more ShaderEffectPasses.
     * - If `custom_xml_*`, returns the full stacked effect chain imported from XML.
     * - Otherwise returns single pass for the custom GLSL shader or built-in effect.
     */
    fun expandEffectPassesForSelection(effectId: String): List<ShaderEffectPass> {
        presetByIdCache[effectId]?.let { preset ->
            if (preset.passes.isNotEmpty()) {
                return preset.passes.map { pass ->
                    pass.copy(id = "xml_pass_${UUID.randomUUID().toString().take(6)}")
                }
            }
        }
        val item = findCatalogItem(effectId) ?: EffectCatalog.findById(effectId) ?: return emptyList()
        return listOf(
            ShaderEffectPass(
                id = "pass_${item.id}_${UUID.randomUUID().toString().take(6)}",
                effectId = item.id,
                effectName = item.name,
                category = item.category,
                intensity = item.defaultIntensity,
                params = item.defaultParamMap(),
                colorParams = item.defaultColorMap(),
                textParams = item.defaultTextMap()
            )
        )
    }

    // --- 1. SAF Folder Tree Batch Import (supports 900+ item folders safely) ---

    fun importFromSafTree(context: Context, treeUri: Uri, customPackName: String? = null) {
        val ctx = context.applicationContext
        appContext = ctx
        ioScope.launch {
            runCatching {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val folderName = customPackName?.takeIf { it.isNotBlank() }
                    ?: queryDocumentDisplayName(ctx, treeUri, rootDocId)
                    ?: "AM Effects Pack"
                val packId = "pack_saf_${AmXmlEffectParser.stableSlugHash(treeUri.toString())}"

                _importStatus.value = PackImportStatus.Scanning(
                    packName = folderName,
                    processedCount = 0,
                    totalEstimate = 1,
                    currentFile = "Scanning folder..."
                )

                val discoveredFiles = collectSafDocuments(ctx, treeUri, rootDocId)
                val total = discoveredFiles.size.coerceAtLeast(1)

                // Order: fonts & GLSL shaders first, then XML presets so XML can auto-bind to pack shaders
                val ordered = discoveredFiles.sortedBy { entry ->
                    when (entry.ext) {
                        "ttf", "otf" -> 0
                        "frag", "fsh", "glsl", "vert", "vsh" -> 1
                        else -> 2
                    }
                }

                val newShaders = mutableListOf<ImportedShaderEffect>()
                val newPresets = mutableListOf<ImportedXmlEffectPreset>()
                val newFonts = mutableListOf<ImportedFontAsset>()
                var skipped = 0

                ordered.forEachIndexed { idx, doc ->
                    if (idx % 8 == 0 || idx == ordered.lastIndex) {
                        _importStatus.value = PackImportStatus.Scanning(
                            packName = folderName,
                            processedCount = idx + 1,
                            totalEstimate = total,
                            currentFile = doc.name
                        )
                    }
                    when (doc.ext) {
                        "frag", "fsh", "glsl", "vert", "vsh" -> {
                            val src = readUriTextCapped(ctx, doc.uri)
                            if (src != null) {
                                newShaders += buildImportedShader(
                                    rawSource = src,
                                    fileName = doc.name,
                                    packId = packId,
                                    packName = folderName
                                )
                            } else {
                                skipped++
                            }
                        }
                        "xml" -> {
                            val xmlText = readUriTextCapped(ctx, doc.uri)
                            if (xmlText != null) {
                                val parsed = AmXmlEffectParser.parseXml(
                                    rawXml = xmlText,
                                    fileName = doc.name,
                                    packId = packId,
                                    packName = folderName,
                                    availableCustomShaders = (_importedShaders.value + newShaders)
                                )
                                if (parsed.presets.isNotEmpty()) {
                                    newPresets += parsed.presets
                                } else {
                                    skipped++
                                }
                            } else {
                                skipped++
                            }
                        }
                        "ttf", "otf" -> {
                            val fontAsset = copyFontStreamToPrivate(
                                context = ctx,
                                openStream = { ctx.contentResolver.openInputStream(doc.uri) },
                                fileName = doc.name,
                                packId = packId,
                                packName = folderName
                            )
                            if (fontAsset != null) newFonts += fontAsset else skipped++
                        }
                        else -> skipped++
                    }
                }

                val packInfo = CustomPackInfo(
                    id = packId,
                    name = folderName,
                    sourceType = ImportedPackSourceType.SAF_FOLDER,
                    treeUriString = treeUri.toString(),
                    presetCount = newPresets.size,
                    shaderCount = newShaders.size,
                    fontCount = newFonts.size,
                    skippedCount = skipped
                )
                mergeAndPersistPack(ctx, packInfo, newShaders, newPresets, newFonts)
                _importStatus.value = PackImportStatus.Completed(
                    PackImportSummary(
                        packId = packId,
                        packName = folderName,
                        presetsAdded = newPresets.size,
                        shadersAdded = newShaders.size,
                        fontsAdded = newFonts.size,
                        skippedUnsupported = skipped
                    )
                )
            }.onFailure { err ->
                _importStatus.value = PackImportStatus.Failed(
                    err.message ?: "تعذر قراءة المجلد المحدد"
                )
            }
        }
    }

    fun resyncSafPack(context: Context, packId: String) {
        val pack = _packs.value.firstOrNull { it.id == packId } ?: return
        val uriStr = pack.treeUriString ?: return
        importFromSafTree(context, Uri.parse(uriStr), pack.name)
    }

    // --- 2. Multi-File & ZIP Picker Import ---

    fun importFromDocumentUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = context.applicationContext
        appContext = ctx
        ioScope.launch {
            runCatching {
                val packId = "pack_import_${UUID.randomUUID().toString().take(8)}"
                val firstDisplay = queryUriDisplayName(ctx, uris.first()) ?: "Custom Pack"
                val packName = if (uris.size == 1) {
                    firstDisplay.substringBeforeLast('.').ifBlank { "Custom Pack" }
                } else {
                    "Imported Files (${uris.size})"
                }

                _importStatus.value = PackImportStatus.Scanning(
                    packName = packName,
                    processedCount = 0,
                    totalEstimate = uris.size,
                    currentFile = firstDisplay
                )

                val stagedShaders = mutableListOf<ImportedShaderEffect>()
                val stagedXmls = mutableListOf<Pair<String, String>>()
                val stagedFonts = mutableListOf<ImportedFontAsset>()
                var skipped = 0
                var processed = 0

                uris.forEach { uri ->
                    val name = queryUriDisplayName(ctx, uri) ?: "file_${processed}"
                    val ext = name.substringAfterLast('.', "").lowercase()
                    processed++
                    _importStatus.value = PackImportStatus.Scanning(
                        packName = packName,
                        processedCount = processed,
                        totalEstimate = maxOf(uris.size, processed),
                        currentFile = name
                    )

                    when (ext) {
                        "zip" -> {
                            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                ZipInputStream(stream).use { zis ->
                                    var entry: ZipEntry? = zis.nextEntry
                                    while (entry != null) {
                                        if (!entry.isDirectory) {
                                            val entryName = File(entry.name).name
                                            val entryExt = entryName.substringAfterLast('.', "").lowercase()
                                            val hasBlockedDir = entry.name.lowercase().split('/', '\\')
                                                .any { it in blockedDirNames }
                                            if (hasBlockedDir || entryExt in blockedExtensions) {
                                                skipped++
                                            } else {
                                                when (entryExt) {
                                                    "frag", "fsh", "glsl", "vert", "vsh" -> {
                                                        val txt = readStreamTextCapped(zis)
                                                        if (txt != null) {
                                                            stagedShaders += buildImportedShader(
                                                                rawSource = txt,
                                                                fileName = entryName,
                                                                packId = packId,
                                                                packName = packName
                                                            )
                                                        } else skipped++
                                                    }
                                                    "xml" -> {
                                                        val xml = readStreamTextCapped(zis)
                                                        if (xml != null) stagedXmls += (entryName to xml) else skipped++
                                                    }
                                                    "ttf", "otf" -> {
                                                        val fAsset = copyZipFontToPrivate(
                                                            context = ctx,
                                                            zis = zis,
                                                            fileName = entryName,
                                                            packId = packId,
                                                            packName = packName
                                                        )
                                                        if (fAsset != null) stagedFonts += fAsset else skipped++
                                                    }
                                                    else -> skipped++
                                                }
                                            }
                                        }
                                        zis.closeEntry()
                                        entry = zis.nextEntry
                                    }
                                }
                            }
                        }
                        "frag", "fsh", "glsl", "vert", "vsh" -> {
                            val txt = readUriTextCapped(ctx, uri)
                            if (txt != null) {
                                stagedShaders += buildImportedShader(txt, name, packId, packName)
                            } else skipped++
                        }
                        "xml" -> {
                            val xml = readUriTextCapped(ctx, uri)
                            if (xml != null) stagedXmls += (name to xml) else skipped++
                        }
                        "ttf", "otf" -> {
                            val fAsset = copyFontStreamToPrivate(
                                context = ctx,
                                openStream = { ctx.contentResolver.openInputStream(uri) },
                                fileName = name,
                                packId = packId,
                                packName = packName
                            )
                            if (fAsset != null) stagedFonts += fAsset else skipped++
                        }
                        else -> skipped++
                    }
                }

                val combinedShaderPool = _importedShaders.value + stagedShaders
                val stagedPresets = stagedXmls.flatMap { (xmlName, xmlBody) ->
                    AmXmlEffectParser.parseXml(
                        rawXml = xmlBody,
                        fileName = xmlName,
                        packId = packId,
                        packName = packName,
                        availableCustomShaders = combinedShaderPool
                    ).presets
                }

                val packInfo = CustomPackInfo(
                    id = packId,
                    name = packName,
                    sourceType = if (uris.any { (queryUriDisplayName(ctx, it) ?: "").endsWith(".zip", true) }) {
                        ImportedPackSourceType.ZIP_ARCHIVE
                    } else {
                        ImportedPackSourceType.FILES_PICKER
                    },
                    presetCount = stagedPresets.size,
                    shaderCount = stagedShaders.size,
                    fontCount = stagedFonts.size,
                    skippedCount = skipped
                )
                mergeAndPersistPack(ctx, packInfo, stagedShaders, stagedPresets, stagedFonts)
                _importStatus.value = PackImportStatus.Completed(
                    PackImportSummary(
                        packId = packId,
                        packName = packName,
                        presetsAdded = stagedPresets.size,
                        shadersAdded = stagedShaders.size,
                        fontsAdded = stagedFonts.size,
                        skippedUnsupported = skipped
                    )
                )
            }.onFailure { err ->
                _importStatus.value = PackImportStatus.Failed(
                    err.message ?: "فشل استيراد الملفات"
                )
            }
        }
    }

    // --- 3. Clipboard / Direct XML & GLSL Paste ---

    fun importPastedCode(
        context: Context,
        rawCode: String,
        customName: String
    ): EffectCatalogItem? {
        val ctx = context.applicationContext
        appContext = ctx
        val trimmed = rawCode.trim()
        if (trimmed.isEmpty()) {
            _importStatus.value = PackImportStatus.Failed("الكود الملصق فارغ")
            return null
        }

        val isXml = trimmed.startsWith("<") && (trimmed.contains("<effect", true) ||
            trimmed.contains("<scene", true) ||
            trimmed.contains("<preset", true) ||
            trimmed.contains("<property", true))
        val cleanName = customName.trim().ifBlank {
            if (isXml) "Pasted XML Preset" else "Pasted GLSL Shader"
        }

        val createdItem: EffectCatalogItem?
        val newShaders = mutableListOf<ImportedShaderEffect>()
        val newPresets = mutableListOf<ImportedXmlEffectPreset>()

        if (isXml) {
            val fileName = "${cleanName.replace(' ', '_')}.xml"
            val parsed = AmXmlEffectParser.parseXml(
                rawXml = trimmed,
                fileName = fileName,
                packId = CLIPBOARD_PACK_ID,
                packName = "Copied & Pasted Effects",
                availableCustomShaders = _importedShaders.value
            )
            val renamed = parsed.presets.map { it.copy(name = cleanName) }
            newPresets += renamed
            createdItem = renamed.firstOrNull()?.let { buildCatalogItemForPreset(it) }
        } else {
            val fileName = "${cleanName.replace(' ', '_')}.frag"
            val shader = buildImportedShader(
                rawSource = trimmed,
                fileName = fileName,
                packId = CLIPBOARD_PACK_ID,
                packName = "Copied & Pasted Effects",
                explicitDisplayName = cleanName
            )
            newShaders += shader
            createdItem = buildCatalogItemForShader(shader)
        }

        val existingClipboard = _packs.value.firstOrNull { it.id == CLIPBOARD_PACK_ID }
        val packInfo = CustomPackInfo(
            id = CLIPBOARD_PACK_ID,
            name = "Copied & Pasted Effects",
            sourceType = ImportedPackSourceType.CLIPBOARD_PASTE,
            presetCount = (existingClipboard?.presetCount ?: 0) + newPresets.size,
            shaderCount = (existingClipboard?.shaderCount ?: 0) + newShaders.size,
            fontCount = 0
        )
        val manifestToPersist: CustomPackManifest
        synchronized(writeLock) {
            val basePacks = _packs.value.filterNot { it.id == packInfo.id }
            val baseShaders = _importedShaders.value
            val basePresets = _importedPresets.value
            val baseFonts = _importedFonts.value

            manifestToPersist = CustomPackManifest(
                packs = basePacks + packInfo,
                shaders = (baseShaders + newShaders).distinctBy { it.id },
                presets = (basePresets + newPresets).distinctBy { it.id },
                fonts = baseFonts.distinctBy { it.id }
            )
            applyManifestToState(manifestToPersist)
        }
        ioScope.launch {
            saveManifestToDisk(ctx, manifestToPersist)
        }
        _importStatus.value = PackImportStatus.Completed(
            PackImportSummary(
                packId = CLIPBOARD_PACK_ID,
                packName = packInfo.name,
                presetsAdded = newPresets.size,
                shadersAdded = newShaders.size,
                fontsAdded = 0,
                skippedUnsupported = 0
            )
        )
        return createdItem
    }

    // --- 4. Portable ZIP Export (so users can copy/share packs safely) ---

    fun exportToZipUri(context: Context, outputUri: Uri, packId: String? = null) {
        val ctx = context.applicationContext
        ioScope.launch {
            runCatching {
                val shadersToExport = _importedShaders.value.filter { packId == null || it.packId == packId }
                val presetsToExport = _importedPresets.value.filter { packId == null || it.packId == packId }
                val fontsToExport = _importedFonts.value.filter { packId == null || it.packId == packId }
                val writtenNames = HashSet<String>()

                ctx.contentResolver.openOutputStream(outputUri)?.use { os ->
                    ZipOutputStream(os).use { zos ->
                        shadersToExport.forEach { sh ->
                            val zipName = uniqueZipPath("effects/shaders/${sh.fileName}", writtenNames)
                            zos.putNextEntry(ZipEntry(zipName))
                            zos.write(sh.rawSource.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        }
                        presetsToExport.forEach { preset ->
                            val zipName = uniqueZipPath("effects/xml/${preset.fileName}", writtenNames)
                            zos.putNextEntry(ZipEntry(zipName))
                            zos.write(preset.rawXml.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        }
                        fontsToExport.forEach { font ->
                            val f = File(font.localFilePath)
                            if (f.exists()) {
                                val zipName = uniqueZipPath("fonts/${font.fileName}", writtenNames)
                                zos.putNextEntry(ZipEntry(zipName))
                                f.inputStream().use { fis -> fis.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }.onFailure { err ->
                _importStatus.value = PackImportStatus.Failed(err.message ?: "تعذر تصدير ملف ZIP")
            }
        }
    }

    fun deletePack(context: Context, packId: String) {
        val ctx = context.applicationContext
        ioScope.launch {
            synchronized(writeLock) {
                val nextPacks = _packs.value.filterNot { it.id == packId }
                val nextShaders = _importedShaders.value.filterNot { it.packId == packId }
                val nextPresets = _importedPresets.value.filterNot { it.packId == packId }
                val removedFonts = _importedFonts.value.filter { it.packId == packId }
                removedFonts.forEach { runCatching { File(it.localFilePath).delete() } }
                val nextFonts = _importedFonts.value.filterNot { it.packId == packId }

                val manifest = CustomPackManifest(
                    packs = nextPacks,
                    shaders = nextShaders,
                    presets = nextPresets,
                    fonts = nextFonts
                )
                saveManifestToDisk(ctx, manifest)
                applyManifestToState(manifest)
            }
        }
    }

    fun deleteItem(context: Context, itemId: String) {
        val ctx = context.applicationContext
        ioScope.launch {
            synchronized(writeLock) {
                val nextShaders = _importedShaders.value.filterNot { it.id == itemId }
                val nextPresets = _importedPresets.value.filterNot { it.id == itemId }
                val removedFont = _importedFonts.value.firstOrNull { it.id == itemId }
                removedFont?.let { runCatching { File(it.localFilePath).delete() } }
                val nextFonts = _importedFonts.value.filterNot { it.id == itemId }

                val manifest = CustomPackManifest(
                    packs = _packs.value,
                    shaders = nextShaders,
                    presets = nextPresets,
                    fonts = nextFonts
                )
                saveManifestToDisk(ctx, manifest)
                applyManifestToState(manifest)
            }
        }
    }

    // --- Internal Persistence & Helpers ---

    private fun mergeAndPersistPack(
        context: Context,
        packInfo: CustomPackInfo,
        newShaders: List<ImportedShaderEffect>,
        newPresets: List<ImportedXmlEffectPreset>,
        newFonts: List<ImportedFontAsset>,
        replacePackItems: Boolean = true
    ) {
        synchronized(writeLock) {
            val basePacks = _packs.value.filterNot { it.id == packInfo.id }
            val baseShaders = _importedShaders.value.filterNot {
                replacePackItems && it.packId == packInfo.id
            }
            val basePresets = _importedPresets.value.filterNot {
                replacePackItems && it.packId == packInfo.id
            }
            val baseFonts = _importedFonts.value.filterNot {
                replacePackItems && it.packId == packInfo.id
            }

            val mergedShaders = (baseShaders + newShaders).distinctBy { it.id }
            val mergedPresets = (basePresets + newPresets).distinctBy { it.id }
            val mergedFonts = (baseFonts + newFonts).distinctBy { it.id }
            val mergedPacks = (basePacks + packInfo)

            val manifest = CustomPackManifest(
                packs = mergedPacks,
                shaders = mergedShaders,
                presets = mergedPresets,
                fonts = mergedFonts
            )
            saveManifestToDisk(context, manifest)
            applyManifestToState(manifest)
        }
    }

    private fun applyManifestToState(manifest: CustomPackManifest) {
        val allPacks = manifest.packs.distinctBy { it.id }
        val allShaders = manifest.shaders.distinctBy { it.id }
        val allPresets = manifest.presets.distinctBy { it.id }
        val allFonts = manifest.fonts.distinctBy { it.id }

        shaderByIdCache = allShaders.associateBy { it.id }
        presetByIdCache = allPresets.associateBy { it.id }
        val items = buildCatalogItems(allShaders, allPresets)
        catalogItemsCache = items
        catalogByIdCache = items.associateBy { it.id }

        CustomFontRegistry.syncFonts(allFonts)

        _packs.value = allPacks
        _importedShaders.value = allShaders
        _importedPresets.value = allPresets
        _importedFonts.value = allFonts
    }

    private fun buildCatalogItems(
        shaders: List<ImportedShaderEffect>,
        presets: List<ImportedXmlEffectPreset>
    ): List<EffectCatalogItem> {
        val shaderItems = shaders.filter { it.isRunnablePostProcess }.map(::buildCatalogItemForShader)
        val presetItems = presets.map(::buildCatalogItemForPreset)
        return presetItems + shaderItems
    }

    private fun buildCatalogItemForShader(sh: ImportedShaderEffect): EffectCatalogItem =
        EffectCatalogItem(
            id = sh.id,
            name = sh.name,
            category = sh.category,
            defaultIntensity = 0.85f,
            params = sh.params
        )

    private fun buildCatalogItemForPreset(preset: ImportedXmlEffectPreset): EffectCatalogItem {
        val firstPass = preset.passes.firstOrNull()
        val mappedCatalog = firstPass?.let { pass ->
            EffectCatalog.shaderEffects.firstOrNull { it.id == pass.effectId }
                ?: com.example.engine.effects.AlightMotionEffectRegistry.effects.firstOrNull { it.id == pass.effectId }
                ?: shaderByIdCache[pass.effectId]?.let(::buildCatalogItemForShader)
        }
        return EffectCatalogItem(
            id = preset.id,
            name = preset.name,
            category = preset.category,
            defaultIntensity = firstPass?.intensity ?: 0.8f,
            params = mappedCatalog?.params.orEmpty()
        )
    }

    private fun buildImportedShader(
        rawSource: String,
        fileName: String,
        packId: String,
        packName: String,
        explicitDisplayName: String? = null
    ): ImportedShaderEffect {
        val cleanBase = explicitDisplayName?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { w -> w.replaceFirstChar { ch -> ch.uppercaseChar() } }
                .ifBlank { "Custom Shader" }
        val transpiled = GlslCompatTranspiler.transpile(rawSource, fileName)
        val category = GlslCompatTranspiler.inferCategory(fileName, rawSource)
        val id = "custom_glsl_${AmXmlEffectParser.stableSlugHash(fileName + "_" + rawSource.take(256))}"
        return ImportedShaderEffect(
            id = id,
            name = cleanBase,
            packId = packId,
            packName = packName,
            fileName = fileName,
            category = category,
            originalGlslVersion = transpiled.originalVersion,
            rawSource = rawSource,
            preambleDecls = transpiled.preambleDecls,
            mainBody = transpiled.mainBody,
            fallbackEffectId = transpiled.fallbackEffectId,
            params = transpiled.params,
            isRunnablePostProcess = transpiled.isRunnablePostProcess,
            compileWarning = transpiled.warning
        )
    }

    private data class SafDocEntry(
        val uri: Uri,
        val name: String,
        val ext: String
    )

    private fun collectSafDocuments(context: Context, treeUri: Uri, rootDocId: String): List<SafDocEntry> {
        val result = mutableListOf<SafDocEntry>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootDocId to 0)
        val visitedDirs = HashSet<String>()
        val cr = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        while (queue.isNotEmpty() && result.size < 4_000) {
            val (dirId, depth) = queue.removeFirst()
            if (!visitedDirs.add(dirId) || depth > 6) continue

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
            runCatching {
                cr.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(idCol) ?: continue
                        val displayName = cursor.getString(nameCol) ?: continue
                        val mime = cursor.getString(mimeCol) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (displayName.lowercase() !in blockedDirNames) {
                                queue.add(childId to (depth + 1))
                            }
                        } else {
                            val ext = displayName.substringAfterLast('.', "").lowercase()
                            if (ext !in blockedExtensions) {
                                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                result += SafDocEntry(childUri, displayName, ext)
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    private fun queryDocumentDisplayName(context: Context, treeUri: Uri, docId: String): String? =
        runCatching {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            queryUriDisplayName(context, docUri)
        }.getOrNull()

    private fun queryUriDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun readUriTextCapped(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                readStreamTextCapped(stream)
            }
        }.getOrNull()

    private fun readStreamTextCapped(stream: InputStream): String? {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = stream.read(buf)
            if (read <= 0) break
            total += read
            if (total > MAX_TEXT_ENTRY_BYTES) return null
            baos.write(buf, 0, read)
        }
        return baos.toString(Charsets.UTF_8.name())
    }

    private fun copyFontStreamToPrivate(
        context: Context,
        openStream: () -> InputStream?,
        fileName: String,
        packId: String,
        packName: String
    ): ImportedFontAsset? {
        val fontsDir = File(File(context.filesDir, DIR_ROOT), DIR_FONTS).apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destFile = File(fontsDir, "${AmXmlEffectParser.stableSlugHash(packId + "_" + safeName)}_$safeName")
        val ok = runCatching {
            openStream()?.use { input ->
                destFile.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val r = input.read(buf)
                        if (r <= 0) break
                        total += r
                        if (total > MAX_FONT_ENTRY_BYTES) return@runCatching false
                        output.write(buf, 0, r)
                    }
                    true
                }
            } ?: false
        }.getOrDefault(false)
        if (!ok) {
            runCatching { destFile.delete() }
            return null
        }
        val label = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
            .ifBlank { "Custom Font" }
        return ImportedFontAsset(
            id = "custom_font_${AmXmlEffectParser.stableSlugHash(destFile.name)}",
            displayName = label,
            fileName = fileName,
            localFilePath = destFile.absolutePath,
            packId = packId,
            packName = packName
        )
    }

    private fun copyZipFontToPrivate(
        context: Context,
        zis: ZipInputStream,
        fileName: String,
        packId: String,
        packName: String
    ): ImportedFontAsset? {
        val fontsDir = File(File(context.filesDir, DIR_ROOT), DIR_FONTS).apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destFile = File(fontsDir, "${AmXmlEffectParser.stableSlugHash(packId + "_" + safeName)}_$safeName")
        val ok = runCatching {
            destFile.outputStream().use { output ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val r = zis.read(buf)
                    if (r <= 0) break
                    total += r
                    if (total > MAX_FONT_ENTRY_BYTES) return@runCatching false
                    output.write(buf, 0, r)
                }
                true
            }
        }.getOrDefault(false)
        if (!ok) {
            runCatching { destFile.delete() }
            return null
        }
        val label = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
            .ifBlank { "Custom Font" }
        return ImportedFontAsset(
            id = "custom_font_${AmXmlEffectParser.stableSlugHash(destFile.name)}",
            displayName = label,
            fileName = fileName,
            localFilePath = destFile.absolutePath,
            packId = packId,
            packName = packName
        )
    }

    private fun loadManifestFromDisk(context: Context): CustomPackManifest {
        val dir = File(context.filesDir, DIR_ROOT)
        val file = File(dir, MANIFEST_FILE)
        val tmp = File(dir, "$MANIFEST_FILE.tmp")
        val target = when {
            file.exists() -> file
            tmp.exists() -> tmp
            else -> return CustomPackManifest()
        }
        return runCatching {
            json.decodeFromString<CustomPackManifest>(target.readText())
        }.getOrDefault(CustomPackManifest())
    }

    private fun saveManifestToDisk(context: Context, manifest: CustomPackManifest) {
        val dir = File(context.filesDir, DIR_ROOT).apply { mkdirs() }
        val target = File(dir, MANIFEST_FILE)
        val tmp = File(dir, "$MANIFEST_FILE.tmp")
        runCatching {
            tmp.writeText(json.encodeToString(CustomPackManifest.serializer(), manifest))
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun uniqueZipPath(preferred: String, existing: MutableSet<String>): String {
        if (existing.add(preferred)) return preferred
        val base = preferred.substringBeforeLast('.')
        val ext = preferred.substringAfterLast('.', "")
        var idx = 2
        while (true) {
            val candidate = if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx"
            if (existing.add(candidate)) return candidate
            idx++
        }
    }
}
