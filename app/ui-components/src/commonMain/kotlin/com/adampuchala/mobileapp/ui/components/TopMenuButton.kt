package com.adampuchala.mobileapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BasicTooltipBox
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberBasicTooltipState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.adampuchala.mobileapp.ui.generated.resources.UiRes
import com.adampuchala.mobileapp.ui.generated.resources.bookmark_24
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopMenuButtonImpl(
    icon: DrawableResource,
    contentDescription: String,
    interactionModifier: Modifier,
    backgroundColor: Color,
    iconColor: Color,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTooltipBox(
        positionProvider = rememberTooltipPositionProvider(),
        tooltip = { Tooltip(contentDescription) },
        state = rememberBasicTooltipState()
    ) {
        Icon(
            modifier = modifier
                .padding(if (large) 0.dp else 6.dp)
                .size(if (large) 40.dp else 36.dp)
                .clip(CircleShape)
                .then(interactionModifier)
                .background(backgroundColor)
                .padding(6.dp),
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = iconColor,
        )
    }
}

/**
 * A toggleable top menu button with selection state.
 */
@Composable
fun TopMenuButton(
    icon: DrawableResource,
    contentDescription: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val backgroundColor by animateColorAsState(
        if (selected) MobileAppTheme.colors.primaryBackground
        else Color.Transparent
    )
    val iconColor by animateColorAsState(
        if (selected) MobileAppTheme.colors.primaryTextWhiteFixed
        else MobileAppTheme.colors.primaryText
    )

    TopMenuButtonImpl(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        interactionModifier = Modifier.toggleable(
            value = selected,
            enabled = true,
            onValueChange = onToggle,
            role = Role.Switch,
        ),
        backgroundColor = backgroundColor,
        iconColor = iconColor,
        large = large,
    )
}

/**
 * A clickable top menu button with no selection state.
 */
@Composable
fun TopMenuButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    TopMenuButtonImpl(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        interactionModifier = Modifier.clickable(onClick = onClick),
        backgroundColor = Color.Transparent,
        iconColor = MobileAppTheme.colors.primaryText,
        large = large,
    )
}

@PreviewLightDark
@Composable
private fun TopMenuButtonUnselectedPreview() = PreviewHelper {
    var selected by remember { mutableStateOf(false) }
    TopMenuButton(UiRes.drawable.bookmark_24, "Bookmark", selected = selected, { selected = it })
}

@PreviewLightDark
@Composable
private fun TopMenuButtonSelectedPreview() = PreviewHelper {
    var selected by remember { mutableStateOf(true) }
    TopMenuButton(UiRes.drawable.bookmark_24, "Bookmark", selected = selected, { selected = it })
}

@PreviewLightDark
@Composable
private fun TopMenuButtonLargeUnselectedPreview() = PreviewHelper {
    var selected by remember { mutableStateOf(false) }
    TopMenuButton(
        UiRes.drawable.bookmark_24,
        "Bookmark",
        selected = selected,
        { selected = it },
        large = true
    )
}

@PreviewLightDark
@Composable
private fun TopMenuButtonLargeSelectedPreview() = PreviewHelper {
    var selected by remember { mutableStateOf(true) }
    TopMenuButton(
        UiRes.drawable.bookmark_24,
        "Bookmark",
        selected = selected,
        { selected = it },
        large = true
    )
}
