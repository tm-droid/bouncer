package com.tanush.bouncer.viewmodels

import android.content.Context
import com.tanush.bouncer.models.AchievementKind

/**
 * Record [kind] as unlocked, and return the set to keep.
 *
 * Top-level, and in its own file, because `MatchDetailViewModel` sits on detekt's
 * TooManyFunctions threshold at both the class and the file level. It needs nothing from the
 * ViewModel beyond a context and the set it already holds.
 */
internal fun unlockAchievement(
    context: Context,
    kind: AchievementKind,
    already: Set<AchievementKind>,
): Set<AchievementKind> {
    if (already.contains(kind)) return already
    runCatching { AchievementKind.unlock(context, kind) }
    return already + kind
}
