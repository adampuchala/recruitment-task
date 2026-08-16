package com.adampuchala.mobileapp.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark

@Composable
fun SettingsItem(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier
            .fillMaxWidth()
            .clip(MobileAppTheme.shapes.roundedCornerMd)
            .background(MobileAppTheme.colors.tileBackground)
            .toggleable(
                value = enabled,
                enabled = true,
                role = Role.Switch,
                onValueChange = { onToggle(!enabled) },
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MobileAppTheme.typography.h3,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MobileAppTheme.typography.text2,
                    color = MobileAppTheme.colors.secondaryText,
                )
            }
        }
        Toggle(
            enabled = enabled,
            onToggle = onToggle,
            modifier = Modifier
                .focusProperties { canFocus = false }
                .clearAndSetSemantics {},
            interactionSource = interactionSource,
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsItemSimplePreview() = PreviewHelper {
    var enabled by remember { mutableStateOf(true) }
    SettingsItem(
        title = "Kodee containment",
        enabled = enabled,
        onToggle = { enabled = it },
    )
}

@PreviewLightDark
@Composable
private fun SettingsItemWithNotePreview() = PreviewHelper {
    var enabled by remember { mutableStateOf(false) }
    SettingsItem(
        title = "Conference schedule updates",
        note = "We recommend keeping this setting enabled to receive timely notifications about any changes or important information.",
        enabled = enabled,
        onToggle = { enabled = it },
    )
}
