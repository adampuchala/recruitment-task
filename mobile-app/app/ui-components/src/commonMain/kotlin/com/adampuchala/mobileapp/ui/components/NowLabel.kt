package com.adampuchala.mobileapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.adampuchala.mobileapp.ui.generated.resources.UiRes
import com.adampuchala.mobileapp.ui.generated.resources.now
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark


@Composable
fun NowLabel(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MobileAppTheme.typography.text2,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp)
                .clip(CircleShape)
                .background(MobileAppTheme.colors.accentText)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(UiRes.string.now),
            color = MobileAppTheme.colors.accentText,
            style = textStyle,
            maxLines = 1,
        )
    }
}

@Composable
@PreviewLightDark
private fun NowLabelPreview() = PreviewHelper {
    NowLabel()
}
