package state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import model.ExtractPhase
import model.ExtractProgress
import model.PartitionInfo
import model.PayloadMetadata
import native.PayloadExtractNative
import util.defaultOutputDir
import util.prefGet
import util.prefSet

class AppViewModel {
    // Input
    var inputPath by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set

    // Payload data
    var metadata by mutableStateOf<PayloadMetadata?>(null)
        private set
    var partitions by mutableStateOf<List<PartitionInfo>>(emptyList())
        private set
    private var nativeHandle = 0L

    // Search
    var searchQuery by mutableStateOf("")

    val filteredPartitions: List<PartitionInfo>
        get() = if (searchQuery.isBlank()) {
            partitions
        } else {
            partitions.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

    // Extract settings (persisted)
    var outputDir by mutableStateOf(prefGet("outputDir") ?: defaultOutputDir())
    var threadCount by mutableIntStateOf(prefGet("threadCount")?.toIntOrNull() ?: 0)
    var verifyHash by mutableStateOf(prefGet("verifyHash")?.toBooleanStrictOrNull() ?: false)

    fun updateOutputDir(value: String) {
        outputDir = value
        prefSet("outputDir", value)
    }

    fun updateVerifyHash(value: Boolean) {
        verifyHash = value
        prefSet("verifyHash", value.toString())
    }

    // Extraction state (per-partition)
    var completedPartitions by mutableStateOf<Set<String>>(emptySet())
        private set
    var failedPartitions by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var currentPartition by mutableStateOf<String?>(null)
        private set

    // Per-partition extraction progress (phase + fraction) for the current phase
    // (HTTP download, then extraction). Absent when not extracting.
    var partitionProgress by mutableStateOf<Map<String, ExtractProgress>>(emptyMap())
        private set
    private var nextToken = 1L

    // Dialogs
    var showUrlDialog by mutableStateOf(false)
    var showAboutDialog by mutableStateOf(false)
    var showPartitionDetail by mutableStateOf(false)
    var partitionDetailData by mutableStateOf<PartitionInfo?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    fun openPartitionDetail(partition: PartitionInfo) {
        partitionDetailData = partition
        showPartitionDetail = true
    }

    fun dismissPartitionDetail() {
        showPartitionDetail = false
    }

    fun clearPartitionDetail() {
        partitionDetailData = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val extractJobs = mutableMapOf<String, Job>()

    private val json = Json { ignoreUnknownKeys = true }

    fun loadPayload() {
        if (inputPath.isBlank()) return
        closePayload()

        isLoading = true
        errorMessage = null
        metadata = null
        partitions = emptyList()
        completedPartitions = emptySet()
        failedPartitions = emptyMap()

        scope.launch {
            try {
                val handle = withContext(Dispatchers.IO) {
                    PayloadExtractNative.open(inputPath)
                }
                nativeHandle = handle

                val partitionsJson = withContext(Dispatchers.IO) {
                    PayloadExtractNative.listPartitionsJson(handle, true)
                }
                val metadataJson = withContext(Dispatchers.IO) {
                    PayloadExtractNative.getMetadataJson(handle)
                }

                val parsedPartitions = json.decodeFromString<List<PartitionInfo>>(partitionsJson)
                val parsedMetadata = json.decodeFromString<PayloadMetadata>(metadataJson)

                isLoading = false
                partitions = parsedPartitions
                metadata = parsedMetadata
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.message ?: "Unknown error"
            }
        }
    }

    fun extractPartition(name: String) {
        if (inputPath.isBlank() || outputDir.isBlank()) return
        if (currentPartition == name) return

        failedPartitions = failedPartitions - name

        val token = nextToken++
        val isUrl = inputPath.startsWith("http://") || inputPath.startsWith("https://")
        val targetDir = extractOutputDir()
        extractJobs[name]?.cancel()
        extractJobs[name] = scope.launch {
            currentPartition = name
            // Seed with the expected first phase so the label is correct before
            // the first native reading (URLs download first; local goes straight to extract).
            partitionProgress = partitionProgress +
                (name to ExtractProgress(if (isUrl) ExtractPhase.DOWNLOAD else ExtractPhase.EXTRACT, 0f))
            // Poll native two-phase progress while the (blocking) extract runs on IO.
            val poller = launch {
                while (isActive) {
                    val raw = withContext(Dispatchers.IO) {
                        PayloadExtractNative.getExtractProgress(token)
                    }
                    if (raw >= 0) {
                        val phase = if ((raw shr 16) and 0xF == 0L) ExtractPhase.DOWNLOAD else ExtractPhase.EXTRACT
                        val permille = (raw and 0xFFFF).toInt()
                        partitionProgress = partitionProgress + (name to ExtractProgress(phase, permille / 1000f))
                    }
                    delay(120)
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    PayloadExtractNative.extractPartition(
                        inputPath, targetDir, name, threadCount, verifyHash, token
                    )
                }
                completedPartitions = completedPartitions + name
            } catch (e: Exception) {
                failedPartitions = failedPartitions + (name to (e.message ?: "Unknown error"))
            } finally {
                poller.cancel()
                partitionProgress = partitionProgress - name
                if (currentPartition == name) {
                    currentPartition = null
                }
                extractJobs.remove(name)
            }
        }
    }

    /**
     * Per-payload output subfolder: `<outputDir>/<input file name without .zip/.bin>`,
     * so extractions from different ROMs don't mix. Joined with '/' (the native side's
     * Rust `Path` accepts forward slashes on Windows too). Falls back to "payload".
     */
    private fun extractOutputDir(): String {
        val fileName = inputPath
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBefore('?')
        val base = when {
            fileName.endsWith(".zip", ignoreCase = true) -> fileName.dropLast(4)
            fileName.endsWith(".bin", ignoreCase = true) -> fileName.dropLast(4)
            else -> fileName
        }.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "payload" }
        return outputDir.trimEnd('/', '\\') + "/" + base
    }

    private fun closePayload() {
        if (nativeHandle != 0L) {
            PayloadExtractNative.close(nativeHandle)
            nativeHandle = 0L
        }
    }

    fun dispose() {
        extractJobs.values.forEach { it.cancel() }
        extractJobs.clear()
        closePayload()
        scope.cancel()
    }
}
