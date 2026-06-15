package model

/** Phase of an in-progress extraction. HTTP inputs download first, then extract. */
enum class ExtractPhase { DOWNLOAD, EXTRACT }

/** Progress of the current extraction phase, [fraction] in 0f..1f. */
data class ExtractProgress(
    val phase: ExtractPhase,
    val fraction: Float,
)
