package com.adampuchala.mobileapp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark

@Composable
fun ParagraphTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MobileAppTheme.typography.h2,
        modifier = modifier
            .padding(
                top = 24.dp,
                end = 12.dp,
                start = 12.dp,
                bottom = 16.dp,
            ),
    )
}

@PreviewLightDark
@Composable
private fun ParagraphTitlePreview() = PreviewHelper {
    ParagraphTitle("Title")
}
