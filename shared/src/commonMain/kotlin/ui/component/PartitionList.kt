package ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.PartitionInfo
import org.jetbrains.compose.resources.stringResource
import payload_extract_gui.shared.generated.resources.Res
import payload_extract_gui.shared.generated.resources.partitions_count
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle

fun LazyListScope.partitionListSection(
    partitions: List<PartitionInfo>,
    completedPartitions: Set<String>,
    failedPartitions: Map<String, String>,
    currentPartition: String?,
    onExtract: (String) -> Unit,
    onDetail: (PartitionInfo) -> Unit,
) {
    item(key = "partition_header") {
        SmallTitle(text = stringResource(Res.string.partitions_count, partitions.size))
    }

    item(key = "partition_list") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        ) {
            partitions.forEach { partition ->
                val status = when {
                    partition.name == currentPartition -> PartitionStatus.EXTRACTING
                    partition.name in completedPartitions -> PartitionStatus.COMPLETED
                    partition.name in failedPartitions -> PartitionStatus.FAILED
                    else -> PartitionStatus.IDLE
                }
                PartitionListItem(
                    partition = partition,
                    status = status,
                    onExtract = { onExtract(partition.name) },
                    onDetail = { onDetail(partition) },
                )
            }
        }
    }
}
