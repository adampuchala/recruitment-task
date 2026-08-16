package com.adampuchala.mobileapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MobileAppTheme.typography.h2,
        color = MobileAppTheme.colors.primaryText,
    )
}

@PreviewLightDark
@Composable
private fun SectionTitleTextPreview() = PreviewHelper {
    SectionTitle("Section title")
}

@PreviewLightDark
@Composable
private fun SectionTitleTimePreview() = PreviewHelper {
    SectionTitle("7:30")
}
