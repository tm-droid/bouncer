package com.tanush.bouncer

/**
 * Non-secret build configuration.
 *
 * **Nothing in here may be a credential.** Every value becomes a `BuildConfig` string constant,
 * which lands in clear text in the shipped APK — `unzip` + `strings` over `classes*.dex` reads them
 * out, and R8 does not obfuscate string literals. That is how the Groq, Cerebras, SambaNova,
 * RapidAPI and Highlightly keys were distributed to every user until 2026-08-04.
 *
 * Secrets now live only in Appwrite function environments. If a feature needs one, it calls a
 * function; it does not get a key. The ids below name public `read("any")` resources and are safe
 * to publish.
 */
object ApiKeys {
    val appwriteApiFunctionId = BuildConfig.APPWRITE_API_FUNCTION_ID

    // Appwrite
    val appwriteEndpoint        = BuildConfig.APPWRITE_ENDPOINT
    val appwriteProjectId       = BuildConfig.APPWRITE_PROJECT_ID
    val appwriteAiCacheDbId     = BuildConfig.APPWRITE_AI_CACHE_DB_ID
    val appwriteAiCacheColId    = BuildConfig.APPWRITE_AI_CACHE_COL_ID
    val appwriteFavouritesColId = BuildConfig.APPWRITE_FAVOURITES_COL_ID
    val appwriteHighlightsDbId  = BuildConfig.APPWRITE_HIGHLIGHTS_DB_ID
    val appwriteHighlightsColId = BuildConfig.APPWRITE_HIGHLIGHTS_COL_ID
    // Historical name: this is the shared bouncer-db id, used by AppwritePlayerProfilesClient.
    // The player-forms table itself was never created and its client has been removed.
    val appwritePlayerFormsDbId     = BuildConfig.APPWRITE_PLAYER_FORMS_DB_ID
    val appwritePlayerProfilesColId = BuildConfig.APPWRITE_PLAYER_PROFILES_COL_ID
}
