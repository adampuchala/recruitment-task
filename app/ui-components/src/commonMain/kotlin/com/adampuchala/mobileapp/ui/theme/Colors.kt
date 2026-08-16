package com.adampuchala.mobileapp.ui.theme

import androidx.compose.ui.graphics.Color
import com.adampuchala.mobileapp.ui.theme.Brand.magenta100
import com.adampuchala.mobileapp.ui.theme.Brand.magentaTextDark
import com.adampuchala.mobileapp.ui.theme.Brand.orange
import com.adampuchala.mobileapp.ui.theme.Brand.orangeTextDark
import com.adampuchala.mobileapp.ui.theme.Brand.pink100
import com.adampuchala.mobileapp.ui.theme.Brand.pinkTextDark
import com.adampuchala.mobileapp.ui.theme.Brand.purple100
import com.adampuchala.mobileapp.ui.theme.Brand.purpleTextDark
import com.adampuchala.mobileapp.ui.theme.UI.black05
import com.adampuchala.mobileapp.ui.theme.UI.black100
import com.adampuchala.mobileapp.ui.theme.UI.black15
import com.adampuchala.mobileapp.ui.theme.UI.black30
import com.adampuchala.mobileapp.ui.theme.UI.black40
import com.adampuchala.mobileapp.ui.theme.UI.black60
import com.adampuchala.mobileapp.ui.theme.UI.black70
import com.adampuchala.mobileapp.ui.theme.UI.black80
import com.adampuchala.mobileapp.ui.theme.UI.grey100
import com.adampuchala.mobileapp.ui.theme.UI.grey400
import com.adampuchala.mobileapp.ui.theme.UI.grey500
import com.adampuchala.mobileapp.ui.theme.UI.grey900
import com.adampuchala.mobileapp.ui.theme.UI.white05
import com.adampuchala.mobileapp.ui.theme.UI.white10
import com.adampuchala.mobileapp.ui.theme.UI.white100
import com.adampuchala.mobileapp.ui.theme.UI.white20
import com.adampuchala.mobileapp.ui.theme.UI.white40
import com.adampuchala.mobileapp.ui.theme.UI.white50
import com.adampuchala.mobileapp.ui.theme.UI.white70
import com.adampuchala.mobileapp.ui.theme.UI.white80

data class Colors(
    val isDark: Boolean,

    val mainBackground: Color,
    val mainBackgroundInverted: Color,
    val primaryBackground: Color,
    val tileBackground: Color,
    val tooltipBackground: Color,

    val cardBackgroundPast: Color,

    val strokeFull: Color,
    val strokeAccent: Color,
    val strokeInputFocus: Color,
    val strokeHalf: Color,
    val strokePale: Color,

    val accentText: Color,
    val longText: Color,
    val noteText: Color,
    val placeholderText: Color,
    val primaryText: Color,
    val primaryTextInverted: Color,
    val primaryTextWhiteFixed: Color,
    val secondaryText: Color,

    val purpleText: Color,
    val magentaText: Color,
    val pinkText: Color,
    val orangeText: Color,

    val toggleOn: Color,
    val toggleOff: Color,
)

val MobileAppLightColors = Colors(
    isDark = false,

    mainBackground = white100,
    mainBackgroundInverted = black100,
    primaryBackground = magenta100,
    tileBackground = black05,
    tooltipBackground = grey900,

    cardBackgroundPast = black05,

    strokeFull = black100,
    strokeAccent = purple100,
    strokeInputFocus = black80,
    strokeHalf = black40,
    strokePale = black15,

    accentText = magenta100,
    longText = black70,
    noteText = black40,
    placeholderText = black30,
    primaryText = black100,
    primaryTextInverted = white100,
    primaryTextWhiteFixed = white100,
    secondaryText = black60,

    purpleText = purple100,
    magentaText = magenta100,
    pinkText = pink100,
    orangeText = orange,

    toggleOff = grey400,
    toggleOn = purple100,
)

val MobileAppDarkColors = Colors(
    isDark = true,

    mainBackground = black100,
    mainBackgroundInverted = white100,
    primaryBackground = magenta100,
    tileBackground = white10,
    tooltipBackground = grey100,

    cardBackgroundPast = white05,

    strokeFull = white100,
    strokeAccent = purpleTextDark,
    strokeInputFocus = white80,
    strokeHalf = white50,
    strokePale = white20,

    accentText = magentaTextDark,
    longText = white70,
    noteText = white50,
    placeholderText = white40,
    primaryText = white100,
    primaryTextInverted = black100,
    primaryTextWhiteFixed = white100,
    secondaryText = white70,

    purpleText = purpleTextDark,
    magentaText = magentaTextDark,
    pinkText = pinkTextDark,
    orangeText = orangeTextDark,

    toggleOff = grey500,
    toggleOn = purpleTextDark,
)

val GoldenKodeeColors = MobileAppDarkColors.copy(
    mainBackground = Color(0xFF3A0057),
)
