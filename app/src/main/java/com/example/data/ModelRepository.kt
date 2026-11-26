package com.example.data

import android.content.Context
import android.os.StatFs
import java.io.File

data class InstalledModelStatus(
    val spec: TtsModelSpec,
    val isInstalled: Boolean,
    val installedSizeBytes: Long,
    val modelDirectory: File,
    val partialDownloadBytes: Long,
    val verifiedFiles: List<String>,
    val missingFiles: List<String>,
)

class ModelRepository(private val context: Context) {

    fun getExternalAppBaseDir(): File {
        return context.getExternalFilesDir(null)
            ?: File(context.filesDir, "external_fallback").apply { mkdirs() }
    }

    fun getModelsRootDir(): File {
        return File(getExternalAppBaseDir(), "models").apply { mkdirs() }
    }

    fun getModelDir(id: TtsModelId): File {
        return File(getModelsRootDir(), id.storageFolderName)
    }

    fun getDownloadsTempDir(): File {
        return File(getModelsRootDir(), ".downloads").apply { mkdirs() }
    }

    fun getSharedLexiconFile(): File {
        return File(File(getModelsRootDir(), "shared"), ModelManifest.ENGLISH_LEXICON_FILENAME)
    }

    fun getPartialArchiveFile(id: TtsModelId): File {
        return File(getDownloadsTempDir(), "${id.storageFolderName}.tar.bz2.part")
    }

    fun getPartialMetaFile(id: TtsModelId): File {
        return File(getDownloadsTempDir(), "${id.storageFolderName}.meta")
    }

    fun getAvailableStorageBytes(): Long {
        val dir = getModelsRootDir()
        if (!dir.exists()) dir.mkdirs()
        val stat = StatFs(dir.absolutePath)
        return stat.availableBytes
    }

    fun hasEnoughStorageFor(spec: TtsModelSpec): Boolean {
        val requiredBytes = (spec.approxDownloadMb + spec.approxDiskMb + 64L) * 1024L * 1024L
        return getAvailableStorageBytes() >= requiredBytes
    }

    fun inspectModel(spec: TtsModelSpec): InstalledModelStatus {
        val dir = getModelDir(spec.id)
        val partialFile = getPartialArchiveFile(spec.id)
        val partialBytes = if (partialFile.exists()) partialFile.length() else 0L

        if (!dir.exists() || !dir.isDirectory) {
            return InstalledModelStatus(
                spec = spec,
                isInstalled = false,
                installedSizeBytes = 0L,
                modelDirectory = dir,
                partialDownloadBytes = partialBytes,
                verifiedFiles = emptyList(),
                missingFiles = spec.requiredRelativeFiles + listOf("*.onnx"),
            )
        }

        val verified = mutableListOf<String>()
        val missing = mutableListOf<String>()

        when (spec.id) {
            TtsModelId.KITTEN_NANO_V08, TtsModelId.KOKORO_EN_V019 -> {
                val onnx = findOnnxModelFile(dir)
                if (onnx != null) verified += onnx.name else missing += "model.int8.onnx (or *.onnx)"

                for (req in spec.requiredRelativeFiles) {
                    val f = File(dir, req)
                    if (f.exists() && f.length() > 0L) verified += req else missing += req
                }

                val espeakDir = File(dir, "espeak-ng-data")
                val localLexicon = findLexiconFile(dir)
                val sharedLexicon = getSharedLexiconFile().takeIf { it.exists() && it.length() > 0L }
                if (espeakDir.exists() && espeakDir.isDirectory && (espeakDir.list()?.isNotEmpty() == true)) {
                    verified += "espeak-ng-data/"
                } else if (localLexicon != null) {
                    verified += localLexicon.name
                } else if (sharedLexicon != null) {
                    verified += "shared/${sharedLexicon.name}"
                } else {
                    missing += "espeak-ng-data/ or lexicon"
                }
            }

            TtsModelId.SUPERTONIC_3_INT8 -> {
                val componentPairs = listOf(
                    "duration_predictor.int8.onnx" to "duration_predictor.onnx",
                    "text_encoder.int8.onnx" to "text_encoder.onnx",
                    "vector_estimator.int8.onnx" to "vector_estimator.onnx",
                    "vocoder.int8.onnx" to "vocoder.onnx",
                )
                for ((preferred, fallback) in componentPairs) {
                    val f = File(dir, preferred).takeIf { it.exists() && it.length() > 0L }
                        ?: File(dir, fallback).takeIf { it.exists() && it.length() > 0L }
                    if (f != null) verified += f.name else missing += preferred
                }
                for (meta in listOf("tts.json", "unicode_indexer.bin", "voice.bin")) {
                    val f = File(dir, meta)
                    if (f.exists() && f.length() > 0L) verified += meta else missing += meta
                }
            }
        }

        val totalSize = calculateDirectorySize(dir)
        val installed = missing.isEmpty() && totalSize > 1_000_000L

        return InstalledModelStatus(
            spec = spec,
            isInstalled = installed,
            installedSizeBytes = totalSize,
            modelDirectory = dir,
            partialDownloadBytes = partialBytes,
            verifiedFiles = verified,
            missingFiles = missing,
        )
    }

    fun inspectAllModels(): Map<TtsModelId, InstalledModelStatus> {
        return ModelManifest.allModels.associate { it.id to inspectModel(it) }
    }

    fun deleteModel(id: TtsModelId): Boolean {
        val dir = getModelDir(id)
        val staging = File(getModelsRootDir(), ".staging-${id.storageFolderName}")
        val part = getPartialArchiveFile(id)
        val meta = getPartialMetaFile(id)
        if (staging.exists()) staging.deleteRecursively()
        if (part.exists()) part.delete()
        if (meta.exists()) meta.delete()
        return if (dir.exists()) dir.deleteRecursively() else true
    }

    fun clearPartialDownload(id: TtsModelId) {
        getPartialArchiveFile(id).delete()
        getPartialMetaFile(id).delete()
    }

    fun findOnnxModelFile(dir: File): File? {
        val preferred = listOf("model.int8.onnx", "model.onnx", "kitten_tts_nano_v0_8.onnx")
        for (name in preferred) {
            val candidate = File(dir, name)
            if (candidate.exists() && candidate.isFile && candidate.length() > 0L) return candidate
        }
        return dir.listFiles()?.firstOrNull {
            it.isFile && it.extension.equals("onnx", ignoreCase = true) && it.length() > 0L
        }
    }

    fun findLexiconFile(dir: File): File? {
        val candidates = listOf("lexicon-en-us.txt", "lexicon-us-en.txt", "lexicon.txt")
        for (name in candidates) {
            val f = File(dir, name)
            if (f.exists() && f.isFile && f.length() > 0L) return f
        }
        return null
    }

    fun calculateDirectorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var sum = 0L
        file.listFiles()?.forEach { child ->
            sum += calculateDirectorySize(child)
        }
        return sum
    }
}
