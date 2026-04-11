package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PayloadMetadata(
    val version: Long,
    @SerialName("manifest_size") val manifestSize: Long,
    @SerialName("metadata_signature_size") val metadataSignatureSize: Int,
    @SerialName("block_size") val blockSize: Int,
    @SerialName("partition_count") val partitionCount: Int,
    @SerialName("max_timestamp") val maxTimestamp: Long? = null,
    @SerialName("partial_update") val partialUpdate: Boolean? = null,
    @SerialName("security_patch_level") val securityPatchLevel: String? = null,
    @SerialName("dynamic_partition_metadata") val dynamicPartitionMetadata: DynPartMeta? = null,
)

@Serializable
data class DynPartMeta(
    @SerialName("snapshot_enabled") val snapshotEnabled: Boolean? = null,
    @SerialName("vabc_enabled") val vabcEnabled: Boolean? = null,
    @SerialName("vabc_compression_param") val vabcCompressionParam: String? = null,
    @SerialName("cow_version") val cowVersion: Int? = null,
    val groups: List<DynPartGroup> = emptyList(),
)

@Serializable
data class DynPartGroup(
    val name: String,
    val size: Long? = null,
    @SerialName("partition_names") val partitionNames: List<String> = emptyList(),
)
