package ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.PartitionInfo
import org.jetbrains.compose.resources.stringResource
import payload_extract_gui.shared.generated.resources.Res
import payload_extract_gui.shared.generated.resources.done
import payload_extract_gui.shared.generated.resources.extract
import payload_extract_gui.shared.generated.resources.retry
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import util.formatFileSize

enum class PartitionStatus {
    IDLE, EXTRACTING, COMPLETED, FAILED,
}

@Composable
fun PartitionListItem(
    partition: PartitionInfo,
    status: PartitionStatus,
    onExtract: () -> Unit,
    onDetail: () -> Unit,
) {
    BasicComponent(
        title = partition.name,
        summary = buildString {
            append(formatFileSize(partition.size))
            partition.hash?.let { append("  (${it.take(10)})") }
        },
        endActions = {
            when (status) {
                PartitionStatus.EXTRACTING -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                PartitionStatus.COMPLETED -> {
                    Text(
                        text = stringResource(Res.string.done),
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }

                PartitionStatus.FAILED -> {
                    TextButton(
                        text = stringResource(Res.string.retry),
                        onClick = onExtract,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                    )
                }

                PartitionStatus.IDLE -> {
                    TextButton(
                        text = stringResource(Res.string.extract),
                        onClick = onExtract,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        },
        onClick = onDetail,
    )
}
