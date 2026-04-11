package ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import payload_extract_gui.shared.generated.resources.Res
import payload_extract_gui.shared.generated.resources.cancel
import payload_extract_gui.shared.generated.resources.confirm
import payload_extract_gui.shared.generated.resources.input_url
import payload_extract_gui.shared.generated.resources.input_url_summary
import payload_extract_gui.shared.generated.resources.url
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun UrlInputDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember(show) { mutableStateOf("") }

    WindowDialog(
        show = show,
        title = stringResource(Res.string.input_url),
        summary = stringResource(Res.string.input_url_summary),
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(Res.string.url),
            useLabelAsPlaceholder = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(Res.string.confirm),
                onClick = { if (url.isNotBlank()) onConfirm(url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
