package com.adampuchala.mobileapp

import kotlinx.datetime.LocalDateTime
import com.adampuchala.mobileapp.ui.components.Emotion
import com.adampuchala.mobileapp.utils.DateTimeFormatting
import com.adampuchala.mobileapp.Score
import com.adampuchala.mobileapp.Session
import com.adampuchala.mobileapp.SessionId
import com.adampuchala.mobileapp.SpeakerId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class SessionCardView(
    val id: SessionId,
    val title: String,
    val speakerLine: String,
    val locationLine: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val state: SessionState,
    val speakerIds: List<SpeakerId>,
    val vote: Score?,
    val isFavorite: Boolean,
    val description: String,
    val tags: Set<String>,
    val startsInMinutes: Int?,
    val videoUrl: String?,
) {
    val fullTimeline: String = DateTimeFormatting.dateAndTime(startsAt, endsAt)
    val shortTimeline: String = DateTimeFormatting.timeToTime(startsAt, endsAt)
    val isLightning: Boolean = endsAt - startsAt <= LIGHTNING_TALK_LIMIT
}

val SessionCardView.isLive get() = state == SessionState.Live

val SessionCardView.isServiceEvent: Boolean
    get() = speakerIds.isEmpty() && tags.isEmpty()

val Session.isLightning: Boolean
    get() = endsAt - startsAt <= LIGHTNING_TALK_LIMIT

// Maximum duration of a talk for it to be considered a lighting talk
private val LIGHTNING_TALK_LIMIT: Duration = 15.minutes

fun Score.toEmotion(): Emotion {
    return when (this) {
        Score.GOOD -> Emotion.Positive
        Score.OK -> Emotion.Neutral
        Score.BAD -> Emotion.Negative
    }
}
