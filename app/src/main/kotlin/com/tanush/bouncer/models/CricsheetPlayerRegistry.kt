package com.tanush.bouncer.models

/**
 * Represents a player entry from Cricsheet's people.csv registry.
 *
 * The identifier maps to values in each match JSON's `info.registry.people`,
 * enabling cross-match player linkage and career statistics aggregation.
 * The external keys (keyCricbuzz, keyCricinfo, etc.) enable linking to
 * third-party data sources like CricBuzz player profiles.
 */
data class CricsheetPerson(
    val identifier: String,       // Cricsheet unique ID (8-char hex UUID)
    val name: String,
    val uniqueName: String,
    // Cross-reference keys to external cricket databases
    val keyCricbuzz: String? = null,
    val keyCricinfo: String? = null,
    val keyCricinfoAlt: String? = null,  // key_cricinfo_2 / key_cricinfo_3
    val keyBcci: String? = null,
    val keyBigBash: String? = null,
    val keyCricHeroes: String? = null,
    val keyCricHQ: String? = null,
    val keyCricingIF: String? = null,
    val keyCricketArchive: String? = null,
    val keyNvPlay: String? = null,
    val keyOpta: String? = null,
    val keyPulse: String? = null
)

/**
 * In-memory registry for fast player lookups.
 * Indexes by Cricsheet identifier and by name for flexible lookups.
 */
class CricsheetPlayerRegistry(people: List<CricsheetPerson> = emptyList()) {

    private val byId = mutableMapOf<String, CricsheetPerson>()
    private val byName = mutableMapOf<String, CricsheetPerson>()
    private val byUniqueName = mutableMapOf<String, CricsheetPerson>()
    private val byCricbuzzKey = mutableMapOf<String, CricsheetPerson>()

    init {
        people.forEach { person ->
            byId[person.identifier] = person
            byName[person.name] = person
            byUniqueName[person.uniqueName] = person
            person.keyCricbuzz?.let { byCricbuzzKey[it] = person }
        }
    }

    val size: Int get() = byId.size

    fun findById(identifier: String): CricsheetPerson? = byId[identifier]

    fun findByName(name: String): CricsheetPerson? = byName[name]

    fun findByUniqueName(uniqueName: String): CricsheetPerson? = byUniqueName[uniqueName]

    fun findByCricbuzzKey(key: String): CricsheetPerson? = byCricbuzzKey[key]

    /**
     * Resolves a player name from a match's registry to the full CricsheetPerson.
     * Uses the match's `registry` map (name → identifier) to look up the person.
     */
    fun resolvePlayer(playerName: String, matchRegistry: Map<String, String>): CricsheetPerson? {
        val identifier = matchRegistry[playerName] ?: return findByName(playerName)
        return findById(identifier) ?: findByName(playerName)
    }

    fun getAllPeople(): List<CricsheetPerson> = byId.values.toList()
}
