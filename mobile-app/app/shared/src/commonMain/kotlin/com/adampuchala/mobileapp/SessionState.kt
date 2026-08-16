package com.adampuchala.mobileapp

import kotlinx.datetime.LocalDateTime

enum class SessionState {
    Live,
    Past,
    Upcoming,
    ;

    companion object {
        fun from(startsAt: LocalDateTime, endsAt: LocalDateTime, now: LocalDateTime): SessionState = when {
            startsAt <= now && now < endsAt -> Live
            endsAt <= now -> Past
            else -> Upcoming
        }
    }
}
