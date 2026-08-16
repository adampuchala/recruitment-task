package com.adampuchala.mobileapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark

@Composable
fun PartnerCard(
    name: String,
    logoUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MobileAppTheme.colors.tileBackground)
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .height(180.dp)
            .padding(36.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = logoUrl,
            contentDescription = name,
        )
    }
}

@PreviewLightDark
@Composable
private fun PartnerCardPreview() = PreviewHelper {
    PartnerCard("Kodee", "https://example.com/logo.png", {})
}
