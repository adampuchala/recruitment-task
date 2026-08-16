package com.adampuchala.mobileapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

class Shapes(
    val roundedCornerSm: RoundedCornerShape,
    val roundedCornerMd: RoundedCornerShape,
)

internal val MobileAppShapes: Shapes
    @Composable
    get() = Shapes(
        roundedCornerSm = RoundedCornerShape(8.dp),
        roundedCornerMd = RoundedCornerShape(16.dp),
    )
