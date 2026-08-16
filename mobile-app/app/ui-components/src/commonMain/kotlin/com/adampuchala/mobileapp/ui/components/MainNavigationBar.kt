package com.adampuchala.mobileapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.adampuchala.mobileapp.ui.generated.resources.UiRes
import com.adampuchala.mobileapp.ui.generated.resources.award_28
import com.adampuchala.mobileapp.ui.generated.resources.award_28_fill
import com.adampuchala.mobileapp.ui.generated.resources.clock_28
import com.adampuchala.mobileapp.ui.generated.resources.clock_28_fill
import com.adampuchala.mobileapp.ui.generated.resources.info_28
import com.adampuchala.mobileapp.ui.generated.resources.info_28_fill
import com.adampuchala.mobileapp.ui.generated.resources.location_28
import com.adampuchala.mobileapp.ui.generated.resources.location_28_fill
import com.adampuchala.mobileapp.ui.generated.resources.now
import com.adampuchala.mobileapp.ui.generated.resources.team_28
import com.adampuchala.mobileapp.ui.generated.resources.team_28_fill
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.adampuchala.mobileapp.ui.utils.WidePreviewLightDark

@Composable
private fun MainNavigationButton(
    iconResource: DrawableResource,
    iconFilledResource: DrawableResource,
    contentDescription: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconColor by animateColorAsState(
        if (selected) MobileAppTheme.colors.primaryText
        else MobileAppTheme.colors.secondaryText
    )
    Icon(
        modifier = modifier
            .clip(MobileAppTheme.shapes.roundedCornerMd)
            .selectable(
                selected = selected,
                enabled = true,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(10.dp)
            .size(28.dp),
        painter = painterResource(if (selected) iconFilledResource else iconResource),
        contentDescription = contentDescription,
        tint = iconColor,
    )
}

@Composable
fun <T : Any> MainNavigationBar(
    currentDestination: MainNavDestination<T>?,
    destinations: List<MainNavDestination<T>>,
    onSelect: (MainNavDestination<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        destinations.forEach { destination ->
            MainNavigationButton(
                iconResource = destination.icon,
                iconFilledResource = destination.iconSelected,
                contentDescription = destination.label.let { stringResource(it) },
                selected = destination == currentDestination,
                onClick = { onSelect(destination) },
                modifier = Modifier
                    .widthIn(max = 150.dp)
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            )
        }
    }
}

@PreviewLightDark
@WidePreviewLightDark
@Composable
private fun MainNavigationBarPreview() = PreviewHelper(paddingEnabled = false) {
    var currentDestination by remember {
        mutableStateOf(
            MainNavDestination(
                label = UiRes.string.now,
                icon = UiRes.drawable.clock_28,
                iconSelected = UiRes.drawable.clock_28_fill,
                route = "Schedule"
            )
        )
    }
    MainNavigationBar(
        currentDestination = currentDestination,
        destinations = listOf(
            MainNavDestination(
                label = UiRes.string.now,
                icon = UiRes.drawable.clock_28,
                iconSelected = UiRes.drawable.clock_28_fill,
                route = "Schedule"
            ),
            MainNavDestination(
                label = UiRes.string.now,
                icon = UiRes.drawable.team_28,
                iconSelected = UiRes.drawable.team_28_fill,
                route = "Speakers"
            ),
            MainNavDestination(
                label = UiRes.string.now,
                icon = UiRes.drawable.award_28,
                iconSelected = UiRes.drawable.award_28_fill,
                route = "GoldenKodee"
            ),
            MainNavDestination(
                label = UiRes.string.now,
                icon = UiRes.drawable.location_28,
                iconSelected = UiRes.drawable.location_28_fill,
                route = "Map"
            ),
            MainNavDestination(
                label = UiRes.string.now,
                icon = UiRes.drawable.info_28,
                iconSelected = UiRes.drawable.info_28_fill,
                route = "Info"
            ),
        ),
        onSelect = { currentDestination = it },
    )
}
