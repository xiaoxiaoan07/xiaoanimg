package model

import kotlinx.serialization.Serializable

@Serializable
data class PartitionInfo(
    val name: String,
    val size: Long,
    val operations: Int,
    val hash: String? = null,
)
