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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

        extractJobs[name]?.cancel()
        extractJobs[name] = scope.launch {
            currentPartition = name
            try {
                withContext(Dispatchers.IO) {
                    PayloadExtractNative.extractPartition(
                        inputPath, outputDir, name, threadCount, verifyHash
                    )
                }
                completedPartitions = completedPartitions + name
            } catch (e: Exception) {
                failedPartitions = failedPartitions + (name to (e.message ?: "Unknown error"))
            } finally {
                if (currentPartition == name) {
                    currentPartition = null
                }
                extractJobs.remove(name)
            }
        }
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
