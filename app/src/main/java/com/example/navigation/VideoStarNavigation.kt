package com.example.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.screens.adjust.AdjustPhotoScreen
import com.example.ui.screens.adjust.AdjustVideoScreen
import com.example.ui.screens.effects.EffectsStudioScreen
import com.example.ui.screens.effects.ShaderGalleryScreen
import com.example.ui.screens.graphs.KeyframeGraphsScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.keyframe.KeyframeEditorScreen
import com.example.ui.screens.layers.LayersScreen
import com.example.ui.screens.mix.MixClipsScreen
import com.example.ui.screens.motion.MotionScreen
import com.example.ui.screens.multilayer.MultiLayerScreen
import com.example.ui.screens.record.RecordVideoScreen
import com.example.ui.screens.reeffect.ReEffectScreen
import com.example.ui.screens.timing.TimingScreen
import com.example.ui.screens.transform.TransformScreen
import com.example.ui.screens.velocity.VelocityScreen
import com.example.ui.screens.workspace.WorkspaceScreen
import com.example.data.VideoStarRepository
import com.example.model.EffectCatalogScope
import com.example.model.StudioLayoutMode
import com.example.ui.theme.VideoStarTheme
import com.example.viewmodel.SharedProjectViewModel
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class WorkspaceRoute(val projectId: String)

@Serializable
data class RecordVideoRoute(val projectId: String)

@Serializable
data class AdjustVideoRoute(
    val projectId: String,
    val mediaUri: String,
    val durationMs: Long,
    val sourceFps: Int,
    val targetLayerIndex: Int? = null,
    val replaceSceneIndex: Int? = null
)

@Serializable
data class AdjustPhotoRoute(
    val projectId: String,
    val mediaUri: String,
    val targetLayerIndex: Int? = null,
    val replaceSceneIndex: Int? = null
)

@Serializable
data class LayersRoute(val projectId: String, val sceneIndex: Int? = null)

@Serializable
data class KeyframeRoute(val projectId: String, val layerId: String)

@Serializable
data class KeyframeGraphRoute(val projectId: String, val layerId: String, val keyframeIndex: Int)

@Serializable
data class MotionRoute(val projectId: String, val sceneIndex: Int = 0)

@Serializable
data class VelocityRoute(val projectId: String, val sceneIndex: Int = 0)

@Serializable
data class ReEffectRoute(val projectId: String, val sceneIndices: List<Int> = listOf(0))

@Serializable
data class EffectsStudioRoute(
    val projectId: String,
    val sceneIndices: List<Int> = listOf(0),
    val targetLayerId: String? = null,
    val isDarkTheme: Boolean = false,
    val catalogScope: EffectCatalogScope = EffectCatalogScope.ALL
)

@Serializable
data class ShaderGalleryRoute(
    val projectId: String,
    val sceneIndex: Int = 0,
    val targetLayerId: String? = null
)

@Serializable
data class TransformRoute(val projectId: String, val sceneIndex: Int = 0)

@Serializable
data class MixRoute(val projectId: String, val sceneIndex: Int = 0)

@Serializable
data class TimingRoute(val projectId: String, val sceneIndex: Int = 0)

@Serializable
data class MultiLayerRoute(val projectId: String, val sceneIndex: Int? = null)

@Composable
fun VideoStarNavHost(
    onDarkModeChanged: (Boolean) -> Unit,
    navController: NavHostController = rememberNavController(),
    sharedProjectViewModel: SharedProjectViewModel = viewModel()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ""
    val isEffectsDark = currentRoute.contains("EffectsStudioRoute") &&
        runCatching { backStackEntry?.toRoute<EffectsStudioRoute>()?.isDarkTheme == true }.getOrDefault(false)
    val isDarkEditor = currentRoute.contains("MultiLayerRoute") ||
        currentRoute.contains("ShaderGalleryRoute") ||
        isEffectsDark

    LaunchedEffect(isDarkEditor) {
        onDarkModeChanged(isDarkEditor)
    }

    VideoStarTheme(darkTheme = isDarkEditor) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    sharedProjectViewModel = sharedProjectViewModel,
                    onOpenProject = { projectId ->
                        navController.navigate(WorkspaceRoute(projectId = projectId))
                    },
                    onOpenAdjustVideo = { projectId, mediaUri, durationMs, sourceFps ->
                        navController.navigate(
                            AdjustVideoRoute(
                                projectId = projectId,
                                mediaUri = mediaUri,
                                durationMs = durationMs,
                                sourceFps = sourceFps,
                                targetLayerIndex = null,
                                replaceSceneIndex = null
                            )
                        )
                    }
                )
            }

            composable<WorkspaceRoute> { entry ->
                val route = entry.toRoute<WorkspaceRoute>()
                WorkspaceScreen(
                    projectId = route.projectId,
                    sharedProjectViewModel = sharedProjectViewModel,
                    onCloseToHome = {
                        navController.popBackStack()
                    },
                    onOpenRecordVideo = { id ->
                        navController.navigate(RecordVideoRoute(projectId = id))
                    },
                    onOpenAdjustVideo = { id, mediaUri, durationMs, sourceFps, replaceSceneIndex ->
                        navController.navigate(
                            AdjustVideoRoute(
                                projectId = id,
                                mediaUri = mediaUri,
                                durationMs = durationMs,
                                sourceFps = sourceFps,
                                targetLayerIndex = null,
                                replaceSceneIndex = replaceSceneIndex
                            )
                        )
                    },
                    onOpenAdjustPhoto = { id, mediaUri, replaceSceneIndex ->
                        navController.navigate(
                            AdjustPhotoRoute(
                                projectId = id,
                                mediaUri = mediaUri,
                                targetLayerIndex = null,
                                replaceSceneIndex = replaceSceneIndex
                            )
                        )
                    },
                    onOpenMotion = { id, sceneIndex ->
                        navController.navigate(MotionRoute(projectId = id, sceneIndex = sceneIndex))
                    },
                    onOpenVelocity = { id, sceneIndex ->
                        navController.navigate(VelocityRoute(projectId = id, sceneIndex = sceneIndex))
                    },
                    onOpenReEffect = { id, sceneIndices ->
                        navController.navigate(ReEffectRoute(projectId = id, sceneIndices = sceneIndices))
                    },
                    onOpenEffectsStudio = { id, sceneIndices, isDarkTheme ->
                        val proj = VideoStarRepository.getProjectById(id)
                        val layout = proj?.studioLayout ?: StudioLayoutMode.NOVA
                        val scope = if (layout == StudioLayoutMode.AFTER_EFFECTS) {
                            EffectCatalogScope.AFTER_EFFECTS_ONLY
                        } else {
                            EffectCatalogScope.VIDEO_STAR_ONLY
                        }
                        val targetAeLayer = if (layout == StudioLayoutMode.AFTER_EFFECTS) {
                            proj?.resolveActiveAeLayer(0L)?.id
                        } else null
                        navController.navigate(
                            EffectsStudioRoute(
                                projectId = id,
                                sceneIndices = sceneIndices,
                                targetLayerId = targetAeLayer,
                                isDarkTheme = isDarkTheme,
                                catalogScope = scope
                            )
                        )
                    },
                    onOpenEffectsStudioForTarget = { id, sceneIndices, targetLayerId, isDarkTheme ->
                        val proj = VideoStarRepository.getProjectById(id)
                        val layout = proj?.studioLayout ?: StudioLayoutMode.NOVA
                        val scope = if (layout == StudioLayoutMode.AFTER_EFFECTS) {
                            EffectCatalogScope.AFTER_EFFECTS_ONLY
                        } else {
                            EffectCatalogScope.VIDEO_STAR_ONLY
                        }
                        navController.navigate(
                            EffectsStudioRoute(
                                projectId = id,
                                sceneIndices = sceneIndices,
                                targetLayerId = targetLayerId,
                                isDarkTheme = isDarkTheme,
                                catalogScope = scope
                            )
                        )
                    },
                    onOpenTransform = { id, sceneIndex ->
                        navController.navigate(TransformRoute(projectId = id, sceneIndex = sceneIndex))
                    },
                    onOpenMix = { id, sceneIndex ->
                        navController.navigate(MixRoute(projectId = id, sceneIndex = sceneIndex))
                    },
                    onOpenLayers = { id ->
                        navController.navigate(LayersRoute(projectId = id))
                    },
                    onOpenLayersForScene = { id, sceneIndex ->
                        navController.navigate(LayersRoute(projectId = id, sceneIndex = sceneIndex))
                    },
                    onOpenTiming = { id, sceneIndex ->
                        navController.navigate(TimingRoute(projectId = id, sceneIndex = sceneIndex))
                    },
                    onOpenShaderGallery = { id, sceneIndex, targetLayerId ->
                        navController.navigate(
                            ShaderGalleryRoute(
                                projectId = id,
                                sceneIndex = sceneIndex,
                                targetLayerId = targetLayerId
                            )
                        )
                    }
                )
            }

            composable<RecordVideoRoute> { entry ->
                val route = entry.toRoute<RecordVideoRoute>()
                RecordVideoScreen(
                    projectId = route.projectId,
                    onCancel = {
                        navController.popBackStack()
                    },
                    onVideoRecorded = { mediaUri, durationMs, sourceFps ->
                        navController.navigate(
                            AdjustVideoRoute(
                                projectId = route.projectId,
                                mediaUri = mediaUri,
                                durationMs = durationMs,
                                sourceFps = sourceFps,
                                targetLayerIndex = null,
                                replaceSceneIndex = null
                            )
                        ) {
                            popUpTo<RecordVideoRoute> { inclusive = true }
                        }
                    }
                )
            }

            composable<AdjustVideoRoute> { entry ->
                val route = entry.toRoute<AdjustVideoRoute>()
                AdjustVideoScreen(
                    projectId = route.projectId,
                    mediaUriString = route.mediaUri,
                    initialDurationMs = route.durationMs,
                    initialSourceFps = route.sourceFps,
                    targetLayerIndex = route.targetLayerIndex,
                    replaceSceneIndex = route.replaceSceneIndex,
                    onBack = {
                        navController.popBackStack()
                    },
                    onMakeVideoComplete = {
                        if (route.targetLayerIndex != null || route.replaceSceneIndex != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(WorkspaceRoute(projectId = route.projectId)) {
                                popUpTo<HomeRoute> { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable<AdjustPhotoRoute> { entry ->
                val route = entry.toRoute<AdjustPhotoRoute>()
                AdjustPhotoScreen(
                    projectId = route.projectId,
                    mediaUriString = route.mediaUri,
                    targetLayerIndex = route.targetLayerIndex,
                    replaceSceneIndex = route.replaceSceneIndex,
                    onBack = {
                        navController.popBackStack()
                    },
                    onMakePhotoComplete = {
                        if (route.targetLayerIndex != null || route.replaceSceneIndex != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(WorkspaceRoute(projectId = route.projectId)) {
                                popUpTo<HomeRoute> { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable<LayersRoute> { entry ->
                val route = entry.toRoute<LayersRoute>()
                LayersScreen(
                    projectId = route.projectId,
                    targetSceneIndex = route.sceneIndex,
                    sharedProjectViewModel = sharedProjectViewModel,
                    onCancel = {
                        navController.popBackStack()
                    },
                    onCreated = {
                        navController.popBackStack()
                    },
                    onOpenKeyframeEditor = { projectId, layerId ->
                        navController.navigate(KeyframeRoute(projectId = projectId, layerId = layerId))
                    },
                    onOpenLayerEffectsStudio = { projectId, layerId ->
                        navController.navigate(
                            EffectsStudioRoute(
                                projectId = projectId,
                                sceneIndices = listOf(route.sceneIndex ?: 0),
                                targetLayerId = layerId,
                                isDarkTheme = false,
                                catalogScope = EffectCatalogScope.VIDEO_STAR_ONLY
                            )
                        )
                    },
                    onOpenAdjustVideoForLayer = { projectId, mediaUri, durationMs, sourceFps, layerIdx ->
                        navController.navigate(
                            AdjustVideoRoute(
                                projectId = projectId,
                                mediaUri = mediaUri,
                                durationMs = durationMs,
                                sourceFps = sourceFps,
                                targetLayerIndex = layerIdx
                            )
                        )
                    },
                    onOpenAdjustPhotoForLayer = { projectId, mediaUri, layerIdx ->
                        navController.navigate(
                            AdjustPhotoRoute(
                                projectId = projectId,
                                mediaUri = mediaUri,
                                targetLayerIndex = layerIdx
                            )
                        )
                    },
                    onSwitchToDarkTheme = {
                        navController.navigate(MultiLayerRoute(projectId = route.projectId, sceneIndex = route.sceneIndex)) {
                            popUpTo<LayersRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable<KeyframeRoute> { entry ->
                val route = entry.toRoute<KeyframeRoute>()
                KeyframeEditorScreen(
                    projectId = route.projectId,
                    layerId = route.layerId,
                    onBack = {
                        navController.popBackStack()
                    },
                    onOpenGraphs = { projectId, layerId, keyframeIndex ->
                        navController.navigate(
                            KeyframeGraphRoute(
                                projectId = projectId,
                                layerId = layerId,
                                keyframeIndex = keyframeIndex
                            )
                        )
                    }
                )
            }

            composable<KeyframeGraphRoute> { entry ->
                val route = entry.toRoute<KeyframeGraphRoute>()
                KeyframeGraphsScreen(
                    projectId = route.projectId,
                    layerId = route.layerId,
                    keyframeIndex = route.keyframeIndex,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable<MotionRoute> { entry ->
                val route = entry.toRoute<MotionRoute>()
                MotionScreen(
                    projectId = route.projectId,
                    sceneIndex = route.sceneIndex,
                    onCancel = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }

            composable<VelocityRoute> { entry ->
                val route = entry.toRoute<VelocityRoute>()
                VelocityScreen(
                    projectId = route.projectId,
                    sceneIndex = route.sceneIndex,
                    onCancel = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }

            composable<ReEffectRoute> { entry ->
                val route = entry.toRoute<ReEffectRoute>()
                ReEffectScreen(
                    projectId = route.projectId,
                    sceneIndices = route.sceneIndices,
                    onCancel = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }

            composable<EffectsStudioRoute> { entry ->
                val route = entry.toRoute<EffectsStudioRoute>()
                EffectsStudioScreen(
                    projectId = route.projectId,
                    sceneIndices = route.sceneIndices,
                    targetLayerId = route.targetLayerId,
                    isDarkTheme = route.isDarkTheme,
                    catalogScope = route.catalogScope,
                    onCancel = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }

            composable<ShaderGalleryRoute> { entry ->
                val route = entry.toRoute<ShaderGalleryRoute>()
                ShaderGalleryScreen(
                    projectId = route.projectId,
                    sceneIndex = route.sceneIndex,
                    targetLayerId = route.targetLayerId,
                    onBack = { navController.popBackStack() },
                    onOpenEffectsStudio = { sceneIndices, targetLayerId ->
                        val layout = VideoStarRepository.getProjectById(route.projectId)?.studioLayout ?: StudioLayoutMode.NOVA
                        val scope = if (layout == StudioLayoutMode.AFTER_EFFECTS) {
                            EffectCatalogScope.AFTER_EFFECTS_ONLY
                        } else {
                            EffectCatalogScope.VIDEO_STAR_ONLY
                        }
                        navController.navigate(
                            EffectsStudioRoute(
                                projectId = route.projectId,
                                sceneIndices = sceneIndices,
                                targetLayerId = targetLayerId,
                                isDarkTheme = true,
                                catalogScope = scope
                            )
                        )
                    }
                )
            }

            composable<TransformRoute> { entry ->
                val route = entry.toRoute<TransformRoute>()
                TransformScreen(
                    projectId = route.projectId,
                    sceneIndex = route.sceneIndex,
                    onCancel = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }

            composable<MixRoute> { entry ->
                val route = entry.toRoute<MixRoute>()
                MixClipsScreen(
                    projectId = route.projectId,
                    sceneIndex = route.sceneIndex,
                    onCancel = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }

            composable<TimingRoute> { entry ->
                val route = entry.toRoute<TimingRoute>()
                TimingScreen(
                    projectId = route.projectId,
                    sceneIndex = route.sceneIndex,
                    onCancel = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }

            composable<MultiLayerRoute> { entry ->
                val route = entry.toRoute<MultiLayerRoute>()
                MultiLayerScreen(
                    projectId = route.projectId,
                    targetSceneIndex = route.sceneIndex,
                    sharedProjectViewModel = sharedProjectViewModel,
                    onCancel = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() },
                    onOpenAdjustVideoForLayer = { projectId, mediaUri, durationMs, sourceFps, layerIdx ->
                        navController.navigate(
                            AdjustVideoRoute(
                                projectId = projectId,
                                mediaUri = mediaUri,
                                durationMs = durationMs,
                                sourceFps = sourceFps,
                                targetLayerIndex = layerIdx
                            )
                        )
                    },
                    onOpenAdjustPhotoForLayer = { projectId, mediaUri, layerIdx ->
                        navController.navigate(
                            AdjustPhotoRoute(
                                projectId = projectId,
                                mediaUri = mediaUri,
                                targetLayerIndex = layerIdx
                            )
                        )
                    },
                    onOpenLayerEffectsStudio = { projectId, layerId ->
                        navController.navigate(
                            EffectsStudioRoute(
                                projectId = projectId,
                                sceneIndices = listOf(route.sceneIndex ?: 0),
                                targetLayerId = layerId,
                                isDarkTheme = true,
                                catalogScope = EffectCatalogScope.VIDEO_STAR_ONLY
                            )
                        )
                    },
                    onSwitchToLightTheme = {
                        navController.navigate(LayersRoute(projectId = route.projectId, sceneIndex = route.sceneIndex)) {
                            popUpTo<MultiLayerRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
