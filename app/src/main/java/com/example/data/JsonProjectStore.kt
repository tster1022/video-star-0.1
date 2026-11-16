package com.example.data

import android.content.Context
import com.example.model.VideoProject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object JsonProjectStore {
    private const val FILE_NAME = "videostar_projects.json"

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun loadProjects(context: Context): List<VideoProject> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists() || file.length() == 0L) return emptyList()
        return runCatching {
            val raw = file.readText()
            json.decodeFromString<List<VideoProject>>(raw)
        }.getOrDefault(emptyList())
    }

    fun saveProjects(context: Context, projects: List<VideoProject>) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(projects))
            if (file.exists()) {
                file.delete()
            }
            tmp.renameTo(file)
        }
    }
}
