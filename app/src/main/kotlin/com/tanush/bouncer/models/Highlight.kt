package com.tanush.bouncer.models

import androidx.compose.runtime.Immutable

@Immutable
data class Highlight(
    val id: String,
    val title: String,
    val imgUrl: String,
    val embedUrl: String?,
    val fallbackUrl: String?,
    val source: String,
    val channel: String,
    val type: String,
    val leagueName: String,
    val homeTeam: String,
    val awayTeam: String,
    val matchStartDate: String
)
