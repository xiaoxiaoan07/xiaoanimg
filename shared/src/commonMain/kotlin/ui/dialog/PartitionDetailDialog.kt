package ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.PartitionInfo
import org.jetbrains.compose.resources.stringResource
import payload_extract_gui.shared.generated.resources.Res
import payload_extract_gui.shared.generated.resources.close
import payload_extract_gui.shared.generated.resources.operations
import payload_extract_gui.shared.generated.resources.sha256
import payload_extract_gui.shared.generated.resources.size
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import util.formatFileSize

@Composable
fun PartitionDetailDialog(
    show: Boolean,
    partition: PartitionInfo?,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = partition?.name ?: "",
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        partition?.let { p ->
            InfoTextView(title = stringResource(Res.string.size), text = formatFileSize(p.size))
            InfoTextView(title = stringResource(Res.string.operations), text = p.operations.toString())
            p.hash?.let { hash ->
                InfoTextView(title = stringResource(Res.string.sha256), text = hash, isLast = true)
            }
            TextButton(
                text = stringResource(Res.string.close),
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
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
