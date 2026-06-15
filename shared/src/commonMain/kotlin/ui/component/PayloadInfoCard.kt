package ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.PayloadMetadata
import org.jetbrains.compose.resources.stringResource
import payload_extract_gui.shared.generated.resources.Res
import payload_extract_gui.shared.generated.resources.block_size
import payload_extract_gui.shared.generated.resources.ota_info
import payload_extract_gui.shared.generated.resources.partitions
import payload_extract_gui.shared.generated.resources.security_patch
import payload_extract_gui.shared.generated.resources.update_type
import payload_extract_gui.shared.generated.resources.update_type_full
import payload_extract_gui.shared.generated.resources.update_type_incremental
import payload_extract_gui.shared.generated.resources.version
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.payloadInfoSection(metadata: PayloadMetadata?) {
    if (metadata == null) return

    item(key = "payload_info") {
        SmallTitle(text = stringResource(Res.string.ota_info))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            insideMargin = PaddingValues(16.dp),
        ) {
            // Bottom padding is dropped on whichever optional row is actually last.
            val hasSecurityPatch = metadata.securityPatchLevel != null
            val hasUpdateType = metadata.partialUpdate != null

            InfoTextView(title = stringResource(Res.string.version), text = metadata.version.toString())
            InfoTextView(title = stringResource(Res.string.partitions), text = metadata.partitionCount.toString())
            InfoTextView(
                title = stringResource(Res.string.block_size),
                text = "${metadata.blockSize}",
                isLast = !hasSecurityPatch && !hasUpdateType,
            )
            metadata.securityPatchLevel?.let {
                InfoTextView(
                    title = stringResource(Res.string.security_patch),
                    text = it,
                    isLast = !hasUpdateType,
                )
            }
            metadata.partialUpdate?.let {
                InfoTextView(
                    title = stringResource(Res.string.update_type),
                    text = if (it) stringResource(Res.string.update_type_incremental) else stringResource(Res.string.update_type_full),
                    isLast = true,
                )
            }
        }
    }
}

@Composable
private fun InfoTextView(title: String, text: String, isLast: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 16.dp),
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSecondaryVariant,
            fontSize = 13.sp,
        )
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
