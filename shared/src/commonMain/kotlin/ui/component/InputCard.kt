package ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import payload_extract_gui.shared.generated.resources.Res
import payload_extract_gui.shared.generated.resources.input
import payload_extract_gui.shared.generated.resources.local
import payload_extract_gui.shared.generated.resources.local_summary
import payload_extract_gui.shared.generated.resources.parse
import payload_extract_gui.shared.generated.resources.parsing
import payload_extract_gui.shared.generated.resources.url
import payload_extract_gui.shared.generated.resources.url_summary
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.inputSection(
    inputPath: String,
    isLoading: Boolean,
    onSelectFile: () -> Unit,
    onInputUrl: () -> Unit,
    onParse: () -> Unit,
) {
    item(key = "input_select") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(top = 12.dp, bottom = 6.dp),
        ) {
            ArrowPreference(
                title = stringResource(Res.string.local),
                summary = stringResource(Res.string.local_summary),
                onClick = onSelectFile,
            )
            ArrowPreference(
                title = stringResource(Res.string.url),
                summary = stringResource(Res.string.url_summary),
                onClick = onInputUrl,
            )
        }
    }

    if (inputPath.isNotBlank()) {
        item(key = "input_info") {
            SmallTitle(text = stringResource(Res.string.input))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
            ) {
                Text(
                    text = inputPath,
                    modifier = Modifier.padding(16.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 5,
                )
            }

            TextButton(
                text = if (isLoading) stringResource(Res.string.parsing) else stringResource(Res.string.parse),
                onClick = onParse,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    if (isLoading) {
        item(key = "loading") {
            LinearProgressIndicator(
                progress = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
