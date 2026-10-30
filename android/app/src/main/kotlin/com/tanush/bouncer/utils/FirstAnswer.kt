package com.tanush.bouncer.utils

/**
 * The first source that answers, asking each only after the one before it gave nothing: a fallback
 * ladder without a chain of elvis operators, and without asking a later (slower, or billed) source
 * when an earlier one answered.
 */
internal suspend fun <T : Any> firstAnswer(vararg sources: suspend () -> T?): T? {
    for (source in sources) source()?.let { return it }
    return null
}
