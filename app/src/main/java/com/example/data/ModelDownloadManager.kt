package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed class DownloadPhase {
    data object Idle : DownloadPhase()
    data object Checking : DownloadPhase()
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Double,
        val etaSeconds: Long?,
        val resumedFromBytes: Long,
        val rangeSupported: Boolean,
    ) : DownloadPhase()
    data class VerifyingArchive(val bytesTotal: Long) : DownloadPhase()
    data class Extracting(val entriesExtracted: Int, val currentEntry: String) : DownloadPhase()
    data object Completed : DownloadPhase()
    data class Cancelled(val partialBytesSaved: Long) : DownloadPhase()
    data class Failed(val message: String, val recoverable: Boolean = true) : DownloadPhase()
}

class ModelDownloadManager(
    private val repository: ModelRepository,
    private val onModelFilesChanged: () -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _states = MutableStateFlow<Map<TtsModelId, DownloadPhase>>(emptyMap())
    val states: StateFlow<Map<TtsModelId, DownloadPhase>> = _states.asStateFlow()

    private val _lexiconState = MutableStateFlow<String?>(null)
    val lexiconStatus: StateFlow<String?> = _lexiconState.asStateFlow()

    private val activeJobs = ConcurrentHashMap<TtsModelId, Job>()
    private val activeCalls = ConcurrentHashMap<TtsModelId, Call>()

    fun startDownload(spec: TtsModelSpec, scope: CoroutineScope) {
        if (activeJobs[spec.id]?.isActive == true) {
            return // Prevent duplicate download operations
        }

        val currentStatus = repository.inspectModel(spec)
        if (currentStatus.isInstalled) {
            updatePhase(spec.id, DownloadPhase.Completed)
            onModelFilesChanged()
            return
        }

        if (!spec.archiveUrl.startsWith("https://")) {
            updatePhase(spec.id, DownloadPhase.Failed("Insecure download URL rejected (HTTPS required).", recoverable = false))
            return
        }

        val job = scope.launch(Dispatchers.IO) {
            try {
                executeDownloadAndInstall(spec)
            } catch (_: CancellationException) {
                val partialSize = repository.getPartialArchiveFile(spec.id).takeIf { it.exists() }?.length() ?: 0L
                updatePhase(spec.id, DownloadPhase.Cancelled(partialSize))
            } catch (e: Exception) {
                updatePhase(
                    spec.id,
                    DownloadPhase.Failed(
                        message = e.message ?: "Download or extraction failed.",
                        recoverable = true,
                    ),
                )
            } finally {
                activeCalls.remove(spec.id)
                activeJobs.remove(spec.id)
                onModelFilesChanged()
            }
        }
        activeJobs[spec.id] = job
    }

    fun cancelDownload(id: TtsModelId) {
        activeCalls[id]?.cancel()
        activeJobs[id]?.cancel()
    }

    fun discardPartialAndReset(id: TtsModelId) {
        cancelDownload(id)
        repository.clearPartialDownload(id)
        updatePhase(id, DownloadPhase.Idle)
        onModelFilesChanged()
    }

    fun resetState(id: TtsModelId) {
        if (activeJobs[id]?.isActive != true) {
            updatePhase(id, DownloadPhase.Idle)
        }
    }

    suspend fun downloadSharedEnglishLexicon(): Result<File> {
        val target = repository.getSharedLexiconFile()
        if (target.exists() && target.length() > 10_000L) {
            _lexiconState.value = "Installed (${target.length() / 1024} KB)"
            return Result.success(target)
        }
        return try {
            _lexiconState.value = "Downloading English lexicon..."
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.part")
            val request = Request.Builder().url(ModelManifest.ENGLISH_LEXICON_URL).build()
            val call = client.newCall(request)
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} downloading lexicon")
                }
                val body = response.body ?: throw IOException("Empty response body")
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (tmp.length() < 10_000L) {
                tmp.delete()
                throw IOException("Downloaded lexicon file is unexpectedly small.")
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            _lexiconState.value = "Installed (${target.length() / 1024} KB)"
            Result.success(target)
        } catch (e: Exception) {
            _lexiconState.value = "Failed: ${e.message}"
            Result.failure(e)
        }
    }

    private suspend fun executeDownloadAndInstall(spec: TtsModelSpec) {
        updatePhase(spec.id, DownloadPhase.Checking)

        if (!repository.hasEnoughStorageFor(spec)) {
            val availMb = repository.getAvailableStorageBytes() / (1024 * 1024)
            val neededMb = spec.approxDownloadMb + spec.approxDiskMb + 64
            throw IOException("Insufficient storage: $availMb MB available, need at least $neededMb MB for safe download & extraction.")
        }

        val partFile = repository.getPartialArchiveFile(spec.id)
        val metaFile = repository.getPartialMetaFile(spec.id)
        partFile.parentFile?.mkdirs()

        var existingBytes = if (partFile.exists()) partFile.length() else 0L
        val savedEtag = if (metaFile.exists()) metaFile.readText().trim() else ""

        val requestBuilder = Request.Builder()
            .url(spec.archiveUrl)
            .header("Accept-Encoding", "identity")

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            if (savedEtag.isNotEmpty()) {
                requestBuilder.header("If-Range", savedEtag)
            }
        }

        val call = client.newCall(requestBuilder.build())
        activeCalls[spec.id] = call

        call.execute().use { response ->
            if (response.code == 416) {
                // Range not satisfiable (possibly already complete or server changed file)
                if (existingBytes >= spec.minExpectedArchiveBytes && isValidBZip2Header(partFile)) {
                    // Proceed to verify & extract
                } else {
                    partFile.delete()
                    metaFile.delete()
                    throw IOException("Server rejected byte range (HTTP 416). Cleared partial download; please retry.")
                }
            } else if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}: ${response.message}")
            } else {
                val isPartial = response.code == 206
                if (!isPartial && existingBytes > 0L) {
                    // Server ignored Range or file changed -> restart cleanly
                    existingBytes = 0L
                    partFile.delete()
                }

                val etag = response.header("ETag") ?: response.header("Last-Modified") ?: ""
                if (etag.isNotEmpty()) {
                    metaFile.writeText(etag)
                }

                val body = response.body ?: throw IOException("Empty download stream.")
                val contentLength = body.contentLength()
                val totalBytes = if (contentLength > 0L) existingBytes + contentLength else -1L
                val resumedFrom = if (isPartial) existingBytes else 0L

                val buffer = ByteArray(64 * 1024)
                var downloaded = resumedFrom
                var speedWindowBytes = 0L
                var windowStartNs = System.nanoTime()
                var smoothedSpeed = 0.0
                var lastEmitTimeMs = 0L

                FileOutputStream(partFile, isPartial).use { fos ->
                    body.byteStream().use { input ->
                        while (coroutineContext.isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            downloaded += read
                            speedWindowBytes += read

                            val nowNs = System.nanoTime()
                            val elapsedSec = (nowNs - windowStartNs) / 1_000_000_000.0
                            if (elapsedSec >= 0.4) {
                                val instantSpeed = speedWindowBytes / elapsedSec
                                smoothedSpeed = if (smoothedSpeed == 0.0) instantSpeed else (smoothedSpeed * 0.65 + instantSpeed * 0.35)
                                speedWindowBytes = 0L
                                windowStartNs = nowNs
                            }

                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastEmitTimeMs >= 200L) {
                                lastEmitTimeMs = nowMs
                                val etaSec = if (totalBytes > downloaded && smoothedSpeed > 1024.0) {
                                    ((totalBytes - downloaded) / smoothedSpeed).toLong()
                                } else null

                                updatePhase(
                                    spec.id,
                                    DownloadPhase.Downloading(
                                        bytesDownloaded = downloaded,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = smoothedSpeed,
                                        etaSeconds = etaSec,
                                        resumedFromBytes = resumedFrom,
                                        rangeSupported = isPartial,
                                    ),
                                )
                            }
                        }
                    }
                    fos.fd.sync()
                }

                if (!coroutineContext.isActive) {
                    throw CancellationException("Download cancelled by user.")
                }

                if (totalBytes > 0L && partFile.length() != totalBytes) {
                    throw IOException("Incomplete download (${partFile.length()} of $totalBytes bytes). Resume to continue.")
                }
            }
        }

        // 1. Verify archive before extraction
        updatePhase(spec.id, DownloadPhase.VerifyingArchive(partFile.length()))
        if (!partFile.exists() || partFile.length() < spec.minExpectedArchiveBytes) {
            throw IOException("Archive verification failed: file size ${partFile.length()} bytes is below minimum expected threshold.")
        }
        if (!isValidBZip2Header(partFile)) {
            partFile.delete()
            metaFile.delete()
            throw IOException("Archive verification failed: invalid BZip2 magic bytes (file may be corrupted or an HTML error page).")
        }

        // 2. Extract safely to staging directory with Zip-Slip prevention
        val stagingDir = File(repository.getModelsRootDir(), ".staging-${spec.id.storageFolderName}")
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        try {
            extractTarBz2Safely(spec, partFile, stagingDir)

            // 3. Atomic move/swap after verifying required files
            val targetDir = repository.getModelDir(spec.id)
            val backupOld = File(repository.getModelsRootDir(), ".old-${spec.id.storageFolderName}")
            if (backupOld.exists()) backupOld.deleteRecursively()
            if (targetDir.exists()) {
                if (!targetDir.renameTo(backupOld)) {
                    targetDir.deleteRecursively()
                }
            }

            if (!stagingDir.renameTo(targetDir)) {
                stagingDir.copyRecursively(targetDir, overwrite = true)
                stagingDir.deleteRecursively()
            }
            if (backupOld.exists()) backupOld.deleteRecursively()

            val postCheck = repository.inspectModel(spec)
            if (!postCheck.isInstalled) {
                targetDir.deleteRecursively()
                throw IOException("Installed model verification failed. Missing: ${postCheck.missingFiles.joinToString()}")
            }

            // Clean up archive only after verified installation
            partFile.delete()
            metaFile.delete()
            updatePhase(spec.id, DownloadPhase.Completed)
        } catch (e: Exception) {
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            throw e
        }
    }

    private fun isValidBZip2Header(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return FileInputStream(file).use { fis ->
            val header = ByteArray(3)
            val read = fis.read(header)
            read == 3 && header[0] == 'B'.code.toByte() && header[1] == 'Z'.code.toByte() && header[2] == 'h'.code.toByte()
        }
    }

    private suspend fun extractTarBz2Safely(
        spec: TtsModelSpec,
        archiveFile: File,
        stagingDir: File,
    ) {
        val canonicalRoot = stagingDir.canonicalPath
        val copyBuffer = ByteArray(64 * 1024)
        var entriesCount = 0

        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis, 64 * 1024).use { bis ->
                BZip2CompressorInputStream(bis).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) {
                            if (!coroutineContext.isActive) {
                                throw CancellationException("Extraction cancelled.")
                            }

                            val rawName = entry.name.replace('\\', '/').trimStart('/')
                            // Strip expected top-level archive folder if present
                            val strippedName = when {
                                rawName == spec.expectedArchiveTopDir || rawName == "${spec.expectedArchiveTopDir}/" -> ""
                                rawName.startsWith("${spec.expectedArchiveTopDir}/") ->
                                    rawName.removePrefix("${spec.expectedArchiveTopDir}/")
                                else -> {
                                    // Strip any single leading directory if all files share it
                                    val firstSlash = rawName.indexOf('/')
                                    if (firstSlash > 0 && rawName.substring(0, firstSlash).contains(spec.id.storageFolderName.take(6))) {
                                        rawName.substring(firstSlash + 1)
                                    } else {
                                        rawName
                                    }
                                }
                            }

                            if (strippedName.isNotEmpty() && !entry.isSymbolicLink && !entry.isLink) {
                                // Strict Zip-Slip / path traversal guard
                                if (strippedName.contains("..")) {
                                    throw SecurityException("Blocked unsafe archive path traversal entry: ${entry.name}")
                                }
                                val outFile = File(stagingDir, strippedName)
                                val canonicalOut = outFile.canonicalPath
                                if (canonicalOut != canonicalRoot && !canonicalOut.startsWith("$canonicalRoot${File.separator}")) {
                                    throw SecurityException("Blocked archive path outside staging directory: ${entry.name}")
                                }

                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { out ->
                                        while (true) {
                                            val count = tarIn.read(copyBuffer)
                                            if (count == -1) break
                                            out.write(copyBuffer, 0, count)
                                        }
                                    }
                                    entriesCount++
                                    if (entriesCount % 8 == 0) {
                                        updatePhase(spec.id, DownloadPhase.Extracting(entriesCount, strippedName))
                                    }
                                }
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        }
    }

    private fun updatePhase(id: TtsModelId, phase: DownloadPhase) {
        _states.update { current -> current + (id to phase) }
    }
}
