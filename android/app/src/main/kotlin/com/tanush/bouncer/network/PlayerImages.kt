package com.tanush.bouncer.network

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Head-shots and Cricbuzz ids learned from squads, search hits and profiles, so a row that only has
 * a player's name (scorecard, crease, fall of wickets) can show their photo and open their profile
 * by id. Names match exactly once role tags, case, accents and spacing are normalised; there is no
 * surname fallback, because a wrong photo or profile is worse than initials.
 *
 * The maps are snapshot state, so a row drawn before the squads arrive picks the photo up when they
 * do. Past [limit] the oldest entries go.
 */
open class PlayerImageRegistry(private val limit: Int = LIMIT) {

    data class Entry(val id: String, val name: String, val url: String?)

    private val byId = mutableStateMapOf<String, Entry>()
    private val idByName = mutableStateMapOf<String, String>()

    /** Ids oldest first, for trimming. Guarded by [lock], as are compound updates of the maps. */
    private val order = LinkedHashSet<String>()
    private val lock = Any()

    /** The photo for a player: by [id] when known, else by [name]. */
    fun url(id: String? = null, name: String): String? {
        if (!id.isNullOrBlank()) byId[id]?.url?.let { return it }
        return playerId(name)?.let { byId[it]?.url }
    }

    /** Cricbuzz id for a name seen in a squad, search or profile; null when unknown or shared by two ids. */
    fun playerId(name: String): String? = idByName[key(name)]?.takeIf { it != AMBIGUOUS }

    fun register(squads: MatchSquads) {
        for (side in listOf(squads.team1, squads.team2)) {
            for (p in side.playingXi + side.bench + side.squad) register(p.id, p.name, p.imageUrl)
        }
    }

    /** A null [url] never clears a photo already known for [id]; Cricbuzz's silhouette counts as none. */
    fun register(id: String, name: String, url: String?) {
        if (id.isBlank() || key(name).isEmpty()) return
        val changed = synchronized(lock) {
            val entry = Entry(id, name, url?.takeIf { it.isNotBlank() && PLACEHOLDER_PATH !in it } ?: byId[id]?.url)
            if (byId[id] == entry && nameHolder(idByName[key(name)], id) == idByName[key(name)]) {
                false
            } else {
                insert(entry)
                true
            }
        }
        if (changed) onChanged()
    }

    /** Called after a registration changed something; the app's instance saves. */
    protected open fun onChanged() = Unit

    private fun insert(e: Entry) {
        byId[e.id]?.let { old -> if (idByName[key(old.name)] == e.id) idByName.remove(key(old.name)) }
        order.remove(e.id)
        byId[e.id] = e
        // Two players with one name: the name answers neither, rather than whichever came last.
        val k = key(e.name)
        val holder = idByName[k]
        idByName[k] = nameHolder(holder, e.id)
        order.add(e.id)
        while (order.size > limit) {
            val dropped = order.first()
            order.remove(dropped)
            val gone = byId.remove(dropped)
            if (gone != null && idByName[key(gone.name)] == dropped) idByName.remove(key(gone.name))
        }
    }

    /**
     * Who a name answers to once [id] takes it. A shared name stays shared: re-registering either
     * player must not hand it to one of them.
     */
    private fun nameHolder(holder: String?, id: String): String = when {
        holder == AMBIGUOUS -> AMBIGUOUS
        holder == null || holder == id || byId[holder] == null -> id
        else -> AMBIGUOUS
    }

    /** Entries oldest first as JSON, read back by [decode]. Gson's tree API, so R8 renames nothing it reads. */
    fun encode(): String = synchronized(lock) {
        val arr = JsonArray()
        for (id in order) {
            val e = byId[id] ?: continue
            arr.add(
                JsonObject().apply {
                    addProperty("id", e.id)
                    addProperty("name", e.name)
                    e.url?.let { addProperty("url", it) }
                },
            )
        }
        arr.toString()
    }

    /** Adds saved entries without displacing any registered since launch. Bad input is ignored. */
    fun decode(json: String) {
        val arr = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull() ?: return
        synchronized(lock) {
            for (el in arr) {
                val e = entryOf(el) ?: continue
                if (!byId.containsKey(e.id)) insert(e)
            }
        }
    }

    private fun entryOf(el: JsonElement): Entry? {
        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        fun field(name: String) = o.get(name)?.takeIf { it.isJsonPrimitive }?.asString
        val id = field("id")?.takeIf { it.isNotBlank() } ?: return null
        val name = field("name")?.takeIf { key(it).isNotEmpty() } ?: return null
        return Entry(id, name, field("url"))
    }

    companion object {
        const val LIMIT = 3000

        /** Marks a name held by more than one id. Never a real id: blank ids are not registered. */
        private const val AMBIGUOUS = ""

        private val ROLE_TAG = Regex("""\s*\(\s*(?:c|wk|vc)(?:\s*&\s*(?:c|wk|vc))*\s*\)""", RegexOption.IGNORE_CASE)
        private val MARKS = Regex("\\p{Mn}+")
        private val SPACES = Regex("\\s+")
        private const val PLACEHOLDER_PATH = "/c${MatchSquadsRsc.PLACEHOLDER_IMAGE_ID}/"

        /** "Beth Mooney (c & wk)" -> "Beth Mooney". */
        fun withoutRoleTags(name: String): String = name.replace(ROLE_TAG, "").trim()

        fun key(name: String): String =
            Normalizer.normalize(withoutRoleTags(name), Normalizer.Form.NFD)
                .replace(MARKS, "")
                .lowercase(Locale.ROOT)
                .split(SPACES)
                .filter { it.isNotEmpty() }
                .joinToString(" ")

        fun headshotUrl(imageId: String): String = "https://www.cricbuzz.com/a/img/v1/152x152/i1/c$imageId/player.jpg"
    }
}

/** The app's registry, kept in the cache dir so photos survive relaunches. */
object PlayerImages : PlayerImageRegistry() {
    private const val FILE_NAME = "player-images.json"
    private const val SAVE_DELAY_MS = 2_000L

    @Volatile private var file: File? = null
    @Volatile private var scope: CoroutineScope? = null
    private var saveJob: Job? = null

    /** Loads the saved registry; call off the main thread. */
    fun initialize(context: Context, scope: CoroutineScope) {
        val f = File(context.cacheDir, FILE_NAME)
        this.scope = scope
        if (f.exists()) runCatching { f.readText() }.getOrNull()?.let(::decode)
        file = f
    }

    /** Coalesces bursts (a squad registers ~30 players) into one write. */
    override fun onChanged() {
        val f = file ?: return
        val s = scope ?: return
        synchronized(this) {
            if (saveJob?.isActive == true) return
            saveJob = s.launch(Dispatchers.IO) {
                delay(SAVE_DELAY_MS)
                // A change from here on schedules its own write rather than being lost to this one.
                synchronized(this@PlayerImages) { saveJob = null }
                val tmp = File(f.parentFile, "$FILE_NAME.tmp")
                runCatching {
                    tmp.writeText(encode())
                    if (!tmp.renameTo(f)) tmp.delete()
                }
            }
        }
    }
}
