package com.example.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.audio.AudioFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BenchmarkRepository(
    private val context: Context,
    private val database: BenchmarkDatabase,
    private val audioFileManager: AudioFileManager,
) {
    private val benchmarkDao = database.benchmarkDao()
    private val ratingDao = database.subjectiveRatingDao()

    val benchmarksFlow: Flow<List<BenchmarkEntity>> = benchmarkDao.observeAllBenchmarks()
    val ratingsFlow: Flow<List<SubjectiveRatingEntity>> = ratingDao.observeAllRatings()

    suspend fun insertBenchmark(entity: BenchmarkEntity): Long {
        return benchmarkDao.insertBenchmark(entity)
    }

    suspend fun saveSubjectiveRating(
        batchId: String,
        modelId: String,
        naturalness: Int,
        expressiveness: Int,
        characterIdentity: Int,
        pronunciation: Int,
        overallPreference: Int,
        notes: String,
    ) {
        val existing = ratingDao.findByBatchAndModel(batchId, modelId)
        val entry = SubjectiveRatingEntity(
            id = existing?.id ?: 0L,
            batchId = batchId,
            modelId = modelId,
            naturalness = naturalness.coerceIn(1, 5),
            expressiveness = expressiveness.coerceIn(1, 5),
            characterIdentity = characterIdentity.coerceIn(1, 5),
            pronunciation = pronunciation.coerceIn(1, 5),
            overallPreference = overallPreference.coerceIn(1, 5),
            notes = notes.trim(),
            updatedAtMs = System.currentTimeMillis(),
        )
        ratingDao.upsertRating(entry)
    }

    suspend fun deleteBenchmark(entity: BenchmarkEntity) {
        audioFileManager.deleteAudioFile(entity.wavFilePath)
        benchmarkDao.deleteBenchmarkById(entity.id)
    }

    suspend fun clearHistory() {
        val all = benchmarkDao.getAllBenchmarksOnce()
        all.forEach { audioFileManager.deleteAudioFile(it.wavFilePath) }
        benchmarkDao.clearAllBenchmarks()
        ratingDao.clearAllRatings()
    }

    fun getExportsDir(): File {
        val base = context.getExternalFilesDir(null) ?: File(context.filesDir, "external_fallback")
        return File(base, "exports").apply { mkdirs() }
    }

    suspend fun exportCsv(): Pair<File, Uri> = withContext(Dispatchers.IO) {
        val rows = benchmarkDao.getAllBenchmarksOnce()
        val ratings = ratingDao.getAllRatingsOnce()
        val ratingMap = ratings.associateBy { "${it.batchId}|${it.modelId}" }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(getExportsDir(), "sae_tts_benchmarks_$stamp.csv")

        val header = listOf(
            "id", "created_at_iso", "run_type", "batch_id", "preset_name",
            "model_id", "model_name", "speaker_id", "speaker_name",
            "word_count", "char_count", "threads", "speed", "supertonic_steps",
            "cold_init", "init_ms", "ttfa_ms", "synthesis_ms", "audio_duration_ms",
            "rtf", "sample_rate", "rss_before_mb", "rss_after_mb", "rss_delta_mb",
            "subj_naturalness", "subj_expressiveness", "subj_character_identity",
            "subj_pronunciation", "subj_overall", "subj_notes", "text",
        ).joinToString(",")

        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val lines = buildString {
            appendLine(header)
            for (r in rows) {
                val subj = if (r.batchId.isNotEmpty()) ratingMap["${r.batchId}|${r.modelId}"] else null
                val cols = listOf(
                    r.id.toString(),
                    csvEscape(isoFmt.format(Date(r.createdAtMs))),
                    csvEscape(r.runType),
                    csvEscape(r.batchId),
                    csvEscape(r.presetName),
                    csvEscape(r.modelId),
                    csvEscape(r.modelDisplayName),
                    r.speakerId.toString(),
                    csvEscape(r.speakerName),
                    r.wordCount.toString(),
                    r.charCount.toString(),
                    r.threadCount.toString(),
                    String.format(Locale.US, "%.2f", r.speed),
                    r.supertonicSteps?.toString().orEmpty(),
                    r.isColdInit.toString(),
                    r.initTimeMs.toString(),
                    r.timeToFirstAudioMs.toString(),
                    r.synthesisTimeMs.toString(),
                    r.audioDurationMs.toString(),
                    String.format(Locale.US, "%.4f", r.realTimeFactor),
                    r.sampleRate.toString(),
                    r.rssBeforeMb?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                    r.rssAfterMb?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                    r.rssDeltaMb?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                    subj?.naturalness?.toString().orEmpty(),
                    subj?.expressiveness?.toString().orEmpty(),
                    subj?.characterIdentity?.toString().orEmpty(),
                    subj?.pronunciation?.toString().orEmpty(),
                    subj?.overallPreference?.toString().orEmpty(),
                    csvEscape(subj?.notes.orEmpty()),
                    csvEscape(r.text),
                )
                appendLine(cols.joinToString(","))
            }
        }
        outFile.writeText(lines, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        outFile to uri
    }

    suspend fun exportJson(): Pair<File, Uri> = withContext(Dispatchers.IO) {
        val rows = benchmarkDao.getAllBenchmarksOnce()
        val ratings = ratingDao.getAllRatingsOnce()

        val root = JSONObject()
        root.put("app", "Sae TTS Benchmark")
        root.put("exportedAtMs", System.currentTimeMillis())

        val benchmarksArray = JSONArray()
        for (r in rows) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("batchId", r.batchId)
            obj.put("runType", r.runType)
            obj.put("presetName", r.presetName)
            obj.put("modelId", r.modelId)
            obj.put("modelDisplayName", r.modelDisplayName)
            obj.put("speakerId", r.speakerId)
            obj.put("speakerName", r.speakerName)
            obj.put("text", r.text)
            obj.put("wordCount", r.wordCount)
            obj.put("charCount", r.charCount)
            obj.put("threadCount", r.threadCount)
            obj.put("speed", r.speed.toDouble())
            if (r.supertonicSteps != null) obj.put("supertonicSteps", r.supertonicSteps)
            obj.put("isColdInit", r.isColdInit)
            obj.put("initTimeMs", r.initTimeMs)
            obj.put("timeToFirstAudioMs", r.timeToFirstAudioMs)
            obj.put("synthesisTimeMs", r.synthesisTimeMs)
            obj.put("audioDurationMs", r.audioDurationMs)
            obj.put("realTimeFactor", r.realTimeFactor)
            obj.put("sampleRate", r.sampleRate)
            if (r.rssBeforeMb != null) obj.put("rssBeforeMb", r.rssBeforeMb)
            if (r.rssAfterMb != null) obj.put("rssAfterMb", r.rssAfterMb)
            if (r.rssDeltaMb != null) obj.put("rssDeltaMb", r.rssDeltaMb)
            obj.put("wavFilePath", r.wavFilePath ?: JSONObject.NULL)
            obj.put("createdAtMs", r.createdAtMs)
            benchmarksArray.put(obj)
        }
        root.put("measuredBenchmarks", benchmarksArray)

        val ratingsArray = JSONArray()
        for (s in ratings) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("batchId", s.batchId)
            obj.put("modelId", s.modelId)
            obj.put("naturalness", s.naturalness)
            obj.put("expressiveness", s.expressiveness)
            obj.put("characterIdentity", s.characterIdentity)
            obj.put("pronunciation", s.pronunciation)
            obj.put("overallPreference", s.overallPreference)
            obj.put("notes", s.notes)
            obj.put("updatedAtMs", s.updatedAtMs)
            ratingsArray.put(obj)
        }
        root.put("subjectiveEvaluations", ratingsArray)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(getExportsDir(), "sae_tts_benchmarks_$stamp.json")
        outFile.writeText(root.toString(2), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        outFile to uri
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
