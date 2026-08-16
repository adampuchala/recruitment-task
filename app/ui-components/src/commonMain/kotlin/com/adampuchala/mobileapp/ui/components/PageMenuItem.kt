package com.adampuchala.mobileapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.adampuchala.mobileapp.ui.generated.resources.UiRes
import com.adampuchala.mobileapp.ui.generated.resources.arrow_right_24
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark

@Composable
fun PageMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    drawableStart: DrawableResource? = null,
    drawableEnd: DrawableResource = UiRes.drawable.arrow_right_24,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MobileAppTheme.shapes.roundedCornerMd)
            .clickable(onClick = onClick)
            .background(MobileAppTheme.colors.tileBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (drawableStart != null) {
            Image(
                painter = painterResource(drawableStart),
                modifier = Modifier.padding(end = 8.dp).size(24.dp),
                contentDescription = null,
            )
        }
        Text(
            text = label,
            style = MobileAppTheme.typography.h3,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(drawableEnd),
            contentDescription = null,
            tint = MobileAppTheme.colors.primaryText,
        )
    }
}

@PreviewLightDark
@Composable
private fun PageMenuItemPreview() = PreviewHelper {
    PageMenuItem("Name", onClick = {})
}
