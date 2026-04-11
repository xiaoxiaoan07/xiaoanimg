package ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import payload_extract_gui.shared.generated.resources.Res
import payload_extract_gui.shared.generated.resources.error
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.errorSection(errorMessage: String?) {
    if (errorMessage == null) return

    item(key = "error") {
        SmallTitle(text = stringResource(Res.string.error))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.errorContainer,
            ),
            insideMargin = PaddingValues(16.dp),
        ) {
            Text(
                text = errorMessage,
                color = MiuixTheme.colorScheme.onErrorContainer,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}
