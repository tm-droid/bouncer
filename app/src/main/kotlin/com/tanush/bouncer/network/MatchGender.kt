package com.tanush.bouncer.network

/**
 * "Women" when the series or either side is named as women's cricket, else "Men". Cricbuzz's list
 * carries no gender field; a women's side is always named as one ("India Women", "Mumbai Indians
 * Women"), which is the same test the series list uses. This was a constant "Men", so the gender
 * filter in the ViewModel had nothing to separate.
 */
internal fun matchGender(seriesName: String, team1: String, team2: String): String =
    if (isWomensSeries(seriesName) || isWomensSeries(team1) || isWomensSeries(team2)) GENDER_WOMEN else GENDER_MEN

internal const val GENDER_MEN = "Men"
internal const val GENDER_WOMEN = "Women"
