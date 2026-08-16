package com.adampuchala.mobileapp.utils

import com.adampuchala.mobileapp.Score
import com.adampuchala.mobileapp.ui.components.Emotion

fun Emotion.toScore(): Score = when (this) {
    Emotion.Positive -> Score.GOOD
    Emotion.Neutral -> Score.OK
    Emotion.Negative -> Score.BAD
}
