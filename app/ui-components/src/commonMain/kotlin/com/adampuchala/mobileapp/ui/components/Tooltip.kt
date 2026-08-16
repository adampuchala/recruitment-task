package com.adampuchala.mobileapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark

@Composable
fun Tooltip(
    text: String,
) {
    Text(
        text = text,
        style = MobileAppTheme.typography.text2,
        color = MobileAppTheme.colors.primaryTextInverted,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MobileAppTheme.colors.tooltipBackground)
            .padding(vertical = 4.dp, horizontal = 8.dp)
    )
}

@Composable
fun rememberTooltipPositionProvider(): PopupPositionProvider {
    val tooltipAnchorSpacing = with(LocalDensity.current) { 4.dp.roundToPx() }
    return remember(tooltipAnchorSpacing) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                var y = anchorBounds.bottom - tooltipAnchorSpacing
                if (y < 0) y = anchorBounds.bottom + tooltipAnchorSpacing
                return IntOffset(x, y)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TooltipPreview() = PreviewHelper {
    Tooltip("Tooltip")
}
