package com.tanush.bouncer.services

import java.io.File

/**
 * The three constants `appwrite/functions/match-sync/src/llm.js` duplicates from Kotlin, read out
 * of the real file in the repository.
 *
 * There is no shared config channel between Kotlin and two Node functions without codegen or a
 * fetched manifest, so these constants are maintained by hand in both places. That has failed
 * twice: the `(e.g., 18.5: ...)` example survived in both copies until Phase 2, and `META` plus
 * `LEADING_BALL_LABEL` drifted until 2026-07-30 — the latter silently attaching model refusals to
 * every Hundred delivery `match-sync` wrote. `match-sync` writes straight to the production
 * `match-commentary` table, so a drift is a live data defect rather than a local one.
 *
 * Parsing the JS rather than duplicating its expected text here is the whole point: a copy in the
 * test would be a third place to keep in sync, and would pass while both real copies drifted
 * together.
 */
internal object LlmJsSource {

    private const val RELATIVE_PATH = "appwrite/functions/match-sync/src/llm.js"

    /**
     * The JS source text.
     *
     * Resolved by walking up from the test's working directory (Gradle runs unit tests with the
     * module directory as `user.dir`, so the backend sits two levels up). A missing file is a hard
     * failure, never a skipped test — a parity check that quietly disables itself when the path
     * changes is worth less than no check at all, because it still reads as green.
     */
    val text: String by lazy { locate().readText(Charsets.UTF_8) }

    private fun locate(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, RELATIVE_PATH)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error(
            "could not find $RELATIVE_PATH by walking up from ${System.getProperty("user.dir")}. " +
                "If the function moved, update RELATIVE_PATH — do not delete this check."
        )
    }

    /**
     * The value of an `export const NAME = "…" + "…";` string-concatenation assignment.
     *
     * Scans the literals directly instead of slicing to the terminating `;`. The prompt text
     * contains a semicolon of its own ("Never invent, guess or alter it; if the input…"), so a
     * slice-to-`;` truncates it mid-sentence — which this test caught on its first run, reported
     * as a drift that did not exist.
     */
    fun stringConstant(name: String): String {
        var i = declarationEnd(name)
        val segments = mutableListOf<String>()
        while (true) {
            i = skipWhitespace(i)
            if (i >= text.length || text[i] != '"') break
            val closing = closingQuote(i)
            segments.add(unescape(text.substring(i + 1, closing)))
            i = skipWhitespace(closing + 1)
            if (i >= text.length || text[i] != '+') break
            i++
        }
        check(segments.isNotEmpty()) { "no string literal found for $name in $RELATIVE_PATH" }
        return segments.joinToString("")
    }

    /** The source text of a `const NAME = /…/flags;` regex literal, and its flags. */
    fun regexConstant(name: String): JsRegex {
        val from = declarationEnd(name)
        val lineEnd = text.indexOf('\n', from).let { if (it < 0) text.length else it }
        val body = text.substring(from, lineEnd).trim().removeSuffix(";")
        val match = checkNotNull(REGEX_LITERAL.find(body)) {
            "$name in $RELATIVE_PATH is not a single-line regex literal: ${body.take(80)}"
        }
        return JsRegex(source = match.groupValues[1], flags = match.groupValues[2])
    }

    data class JsRegex(val source: String, val flags: String)

    /**
     * The body of `export function NAME(…) { … }`, by brace matching.
     *
     * Only sound for bodies whose braces are all real syntax — a brace inside a string, regex or
     * comment would fool it. That holds for the functions this is used on, and a violation fails
     * loudly (unbalanced) rather than returning something plausible.
     */
    fun functionBody(name: String): String {
        val declaration = Regex("""(?:export\s+)?function\s+${Regex.escape(name)}\s*\(""")
            .find(text) ?: error("no declaration of function $name in $RELATIVE_PATH")
        val open = text.indexOf('{', declaration.range.last)
        check(open >= 0) { "no body for function $name in $RELATIVE_PATH" }
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(open + 1, i)
            }
        }
        error("unterminated body for function $name in $RELATIVE_PATH")
    }

    /** Every `return "LITERAL"` in [functionName], in source order. */
    fun returnedStringLiterals(functionName: String): List<String> =
        RETURNED_LITERAL.findAll(functionBody(functionName)).map { it.groupValues[1] }.toList()

    private val RETURNED_LITERAL = Regex("""return\s+"([^"]*)"""")

    /** Index just past the `=` of `const NAME =`. */
    private fun declarationEnd(name: String): Int {
        val declaration = Regex("""(?:export\s+)?const\s+${Regex.escape(name)}\s*=""")
            .find(text) ?: error("no declaration of $name in $RELATIVE_PATH")
        return declaration.range.last + 1
    }

    private fun skipWhitespace(from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    /** Index of the `"` closing the literal that opens at [open], skipping escaped quotes. */
    private fun closingQuote(open: Int): Int {
        var i = open + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                '"' -> return i
            }
            i++
        }
        error("unterminated string literal at offset $open in $RELATIVE_PATH")
    }

    // `/source/flags` — the source runs to the last unescaped slash.
    private val REGEX_LITERAL = Regex("""^/((?:[^/\\]|\\.)+)/([a-z]*)$""")

    private fun unescape(literal: String): String = buildString {
        var i = 0
        while (i < literal.length) {
            val c = literal[i]
            if (c != '\\' || i == literal.lastIndex) {
                append(c)
                i++
                continue
            }
            when (val escaped = literal[i + 1]) {
                'n' -> append('\n')
                't' -> append('\t')
                'r' -> append('\r')
                else -> append(escaped)
            }
            i += 2
        }
    }
}
