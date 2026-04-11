package ui.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import payload_extract_gui.shared.generated.resources.Res
import payload_extract_gui.shared.generated.resources.about
import payload_extract_gui.shared.generated.resources.app_name
import payload_extract_gui.shared.generated.resources.icon
import payload_extract_gui.shared.generated.resources.opensource_info
import payload_extract_gui.shared.generated.resources.view_source
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutDialog(
    show: Boolean,
    onShow: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    IconButton(
        onClick = onShow,
        holdDownState = show,
    ) {
        Image(
            painter = painterResource(Res.drawable.icon),
            contentDescription = "About",
            modifier = Modifier
                .size(32.dp)
                .padding(4.dp),
        )
    }

    OverlayDialog(
        show = show,
        title = stringResource(Res.string.about),
        onDismissRequest = onDismissRequest,
    ) {
        val uriHandler = LocalUriHandler.current
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.icon),
                contentDescription = "Icon",
                modifier = Modifier.size(45.dp),
            )
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = "1.0.0")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(Res.string.view_source) + " ")
            Text(
                text = AnnotatedString(
                    text = "GitHub",
                    spanStyle = SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = MiuixTheme.colorScheme.primary,
                    ),
                ),
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/YuKongA/payload_extract_gui")
                },
            )
        }
        Text(
            modifier = Modifier.padding(top = 10.dp),
            text = stringResource(Res.string.opensource_info),
        )
    }
}
