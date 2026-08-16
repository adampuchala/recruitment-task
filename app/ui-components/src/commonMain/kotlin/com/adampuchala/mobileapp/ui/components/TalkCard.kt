package com.adampuchala.mobileapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import com.adampuchala.mobileapp.ui.generated.resources.UiRes
import com.adampuchala.mobileapp.ui.generated.resources.action_bookmark_session
import com.adampuchala.mobileapp.ui.generated.resources.action_remove_session_from_bookmarks
import com.adampuchala.mobileapp.ui.generated.resources.action_state_description_bookmarked
import com.adampuchala.mobileapp.ui.generated.resources.action_state_description_not_bookmarked
import com.adampuchala.mobileapp.ui.generated.resources.bookmark_24
import com.adampuchala.mobileapp.ui.generated.resources.bookmark_24_fill
import com.adampuchala.mobileapp.ui.generated.resources.lightning_16_fill
import com.adampuchala.mobileapp.ui.generated.resources.lightning_talk
import com.adampuchala.mobileapp.ui.generated.resources.session_codelab
import com.adampuchala.mobileapp.ui.generated.resources.session_education
import com.adampuchala.mobileapp.ui.generated.resources.talk_card_icon_desc_codelab
import com.adampuchala.mobileapp.ui.generated.resources.talk_card_icon_desc_education
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.ui.theme.PreviewHelper
import androidx.compose.ui.tooling.preview.PreviewLightDark

@Composable
internal fun buildHighlightedString(
    text: String,
    highlights: List<IntRange>,
): AnnotatedString = if (highlights.isEmpty()) {
    AnnotatedString(text)
} else {
    buildAnnotatedString {
        append(text)
        highlights.forEach { range ->
            // Ignore invalid ranges
            if (!range.isEmpty() && range.first in text.indices && range.last in text.indices) {
                addStyle(
                    style = SpanStyle(
                        color = MobileAppTheme.colors.primaryTextWhiteFixed,
                        background = MobileAppTheme.colors.primaryBackground,
                    ),
                    start = range.first,
                    end = range.last + 1,
                )
            }
        }
    }
}

enum class TalkStatus {
    Past, Live, Upcoming,
}

@Composable
fun TalkCard(
    title: String,
    titleHighlights: List<IntRange>,
    bookmarked: Boolean,
    onBookmark: (Boolean) -> Unit,
    tags: Set<String>,
    tagHighlights: List<String>,
    speakers: String,
    speakerHighlights: List<IntRange>,
    location: String,
    lightning: Boolean,
    time: String,
    timeNote: String?,
    status: TalkStatus,
    onClick: () -> Unit,
    feedbackContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        if (status == TalkStatus.Past) MobileAppTheme.colors.cardBackgroundPast
        else Color.Transparent,
        animationSpec = tween(1000),
    )
    val textColor by animateColorAsState(
        if (status == TalkStatus.Past) MobileAppTheme.colors.secondaryText
        else MobileAppTheme.colors.primaryText,
        animationSpec = tween(1000),
    )
    val borderColor by animateColorAsState(
        if (bookmarked) MobileAppTheme.colors.strokeHalf
        else MobileAppTheme.colors.strokePale,
        animationSpec = tween(1000),
    )

    Column(
        modifier
            .border(
                width = 1.dp,
                color = borderColor,
                shape = MobileAppTheme.shapes.roundedCornerMd
            )
            .clip(MobileAppTheme.shapes.roundedCornerMd)
            .clickable(onClick = onClick)
            .background(backgroundColor)
    ) {
        TopBlock(
            title = title,
            titleHighlights = titleHighlights,
            textColor = textColor,
            bookmarked = bookmarked,
            onBookmark = onBookmark,
            tags = tags,
            selectedTags = tagHighlights,
            speakers = speakers,
            speakerHighlights = speakerHighlights,
            status = status,
        )
        // TODO BLOCKER double-check if removing this weight is correct
        HorizontalDivider(
            thickness = 1.dp,
            color = MobileAppTheme.colors.strokePale,
        )
        TimeBlock(
            location = location,
            textColor = textColor,
            timeNote = timeNote,
            status = status,
            lightning = lightning,
            time = time,
        )
        AnimatedVisibility(
            visible = feedbackContent != null,
            enter = fadeIn(tween(300, 70, EaseOut)) + expandVertically(tween(150, 0, EaseOut)),
            exit = fadeOut(tween(300, 70, EaseOut)) + shrinkVertically(tween(150, 0, EaseOut)),
        ) {
            Column(
                // Prevent clicks on the non-interactive elements of the feedback area
                Modifier.clickable(interactionSource = null, indication = null, onClick = {})
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MobileAppTheme.colors.strokePale,
                )
                feedbackContent?.invoke()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TopBlock(
    title: String,
    titleHighlights: List<IntRange>,
    textColor: Color,
    bookmarked: Boolean,
    onBookmark: (Boolean) -> Unit,
    tags: Set<String>,
    selectedTags: List<String>,
    speakers: String,
    speakerHighlights: List<IntRange>,
    status: TalkStatus,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            TalkTitle(
                title = buildHighlightedString(title, titleHighlights),
                tags = tags,
                textColor = textColor,
                status = status,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )

            Spacer(Modifier.width(8.dp))

            val iconColor by animateColorAsState(
                if (bookmarked) MobileAppTheme.colors.orangeText
                else MobileAppTheme.colors.primaryText
            )
            val stateDesc = stringResource(
                resource = if (bookmarked)
                    UiRes.string.action_state_description_bookmarked
                else
                    UiRes.string.action_state_description_not_bookmarked
            )
            Icon(
                modifier = Modifier
                    .toggleable(
                        value = bookmarked,
                        onValueChange = onBookmark,
                        role = Role.Switch,
                        interactionSource = null,
                        indication = null,
                    )
                    .size(24.dp)
                    .semantics {
                        stateDescription = stateDesc
                    },
                painter = painterResource(
                    if (bookmarked) UiRes.drawable.bookmark_24_fill
                    else UiRes.drawable.bookmark_24
                ),
                contentDescription = stringResource(
                    if (bookmarked) UiRes.string.action_remove_session_from_bookmarks
                    else UiRes.string.action_bookmark_session,
                    title
                ),
                tint = iconColor,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tags.forEach { tag ->
                CardTag(label = tag, selected = tag in selectedTags)
            }
        }
        Text(
            text = buildHighlightedString(speakers, speakerHighlights),
            color = MobileAppTheme.colors.secondaryText,
            style = MobileAppTheme.typography.text2,
            maxLines = 1,
        )
    }
}

private const val iconId = "iconId'"
private const val eduPlaceholder = "[e]"
private const val codelabPlaceholder = "[c]"

@Composable
private fun TalkTitle(
    title: AnnotatedString,
    tags: Set<String>,
    textColor: Color,
    status: TalkStatus,
    modifier: Modifier,
) {
    val isCodelab = "Codelab" in tags
    val isEducation = "Education" in tags
    val hasIcon = isCodelab || isEducation

    Text(
        text = if (hasIcon) {
            buildAnnotatedString {
                appendInlineContent(
                    id = iconId,
                    alternateText = when {
                        isEducation -> eduPlaceholder
                        isCodelab -> codelabPlaceholder
                        else -> codelabPlaceholder // Should never happen
                    },
                )
                append(title)
            }
        } else {
            title
        },
        style = MobileAppTheme.typography.h3,
        color = textColor,
        maxLines = 2,
        inlineContent = if (hasIcon) talkCardTitleInlineContent(status) else emptyMap(),
        modifier = modifier,
    )
}

private val iconPlaceholder = Placeholder(
    width = 1.8.em,
    height = 1.5.em,
    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
)

private fun talkCardTitleInlineContent(status: TalkStatus): Map<String, InlineTextContent> {
    return mapOf(
        iconId to InlineTextContent(iconPlaceholder) { placeholder ->
            InlineIconContent(status, placeholder)
        },
    )
}

@Composable
private fun InlineIconContent(status: TalkStatus, placeholder: String) {
    val textColor by animateColorAsState(
        if (status == TalkStatus.Past) MobileAppTheme.colors.secondaryText
        else MobileAppTheme.colors.accentText,
        animationSpec = tween(1000),
    )
    Icon(
        imageVector = vectorResource(
            when (placeholder) {
                eduPlaceholder -> UiRes.drawable.session_education
                codelabPlaceholder -> UiRes.drawable.session_codelab
                else -> UiRes.drawable.session_codelab // Shouldn't happen, but let's not throw
            }
        ),
        contentDescription = when (placeholder) {
            eduPlaceholder -> stringResource(UiRes.string.talk_card_icon_desc_education)
            codelabPlaceholder -> stringResource(UiRes.string.talk_card_icon_desc_codelab)
            else -> null
        },
        tint = textColor,
    )
}

@Composable
private fun TimeBlock(
    location: String,
    textColor: Color,
    timeNote: String?,
    status: TalkStatus,
    lightning: Boolean,
    time: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = location,
            style = MobileAppTheme.typography.text2,
            color = textColor,
        )

        Spacer(Modifier.weight(1f))

        if (timeNote != null) {
            Text(
                text = timeNote,
                style = MobileAppTheme.typography.text2,
                color = MobileAppTheme.colors.noteText,
                maxLines = 1,
            )
        }

        AnimatedVisibility(status == TalkStatus.Live, enter = fadeIn(), exit = fadeOut()) {
            NowLabel()
        }

        if (lightning) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(UiRes.drawable.lightning_16_fill),
                contentDescription = stringResource(UiRes.string.lightning_talk),
                tint = MobileAppTheme.colors.orangeText,
            )
        }

        AnimatedContent(
            targetState = time,
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) {
            Text(
                text = it,
                style = MobileAppTheme.typography.text2,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TalkCardLivePreview() = PreviewHelper {
    var bookmarked by remember { mutableStateOf(false) }
    TalkCard(
        title = "Asynchronous Programming With Kotlin Coroutines",
        titleHighlights = listOf(
            30..35,
        ),
        bookmarked = bookmarked,
        onBookmark = { bookmarked = it },
        tags = setOf(
            "Workshop", "Kotlin", "Coroutines", "Multiplatform",
            "Label", "Label", "Label", "Label", "Label",
        ),
        tagHighlights = listOf(
            "Kotlin", "Multiplatform",
        ),
        speakers = "Sebastian Aigner, Vsevolod Tolstopyatov",
        speakerHighlights = listOf(10..15),
        location = "Auditorium 14",
        lightning = true,
        time = "9:00 – 10:00",
        timeNote = null,
        status = TalkStatus.Live,
        onClick = { println("Clicked session") },
        feedbackContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp),
            ) {
                Text(
                    "Thanks for your rating!",
                    color = MobileAppTheme.colors.primaryText,
                    style = MobileAppTheme.typography.h4,
                    modifier = Modifier.weight(1f),
                )
                Emotion.entries.forEach { emotion ->
                    KodeeIconSmall(
                        emotion = emotion,
                        selected = emotion == Emotion.Positive,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun TalkCardUpcomingPreview() = PreviewHelper {
    TalkCard(
        title = "Asynchronous Programming With Kotlin Coroutines",
        titleHighlights = emptyList(),
        bookmarked = false,
        onBookmark = { },
        tags = setOf(
            "Workshop", "Kotlin", "Coroutines", "Multiplatform",
            "Label", "Label", "Label", "Label", "Label",
        ),
        tagHighlights = listOf(),
        speakers = "Sebastian Aigner, Vsevolod Tolstopyatov",
        speakerHighlights = emptyList(),
        location = "Auditorium 14",
        lightning = true,
        time = "9:00 – 10:00",
        timeNote = "In 10 min",
        status = TalkStatus.Upcoming,
        onClick = { println("Clicked session") },
        feedbackContent = null,
    )
}
