package com.tanush.bouncer

object ApiKeys {
    // AI providers (Groq → Cerebras → SambaNova rotation)
    val groq          = BuildConfig.GROQ_API_KEY
    val cerebras      = BuildConfig.CEREBRAS_API_KEY
    val sambaNova     = BuildConfig.SAMBANOVA_API_KEY

    // Cricket data
    val rapidApi      = BuildConfig.RAPIDAPI_KEY

    // Appwrite
    val appwriteEndpoint        = BuildConfig.APPWRITE_ENDPOINT
    val appwriteProjectId       = BuildConfig.APPWRITE_PROJECT_ID
    val appwriteAiCacheDbId     = BuildConfig.APPWRITE_AI_CACHE_DB_ID
    val appwriteAiCacheColId    = BuildConfig.APPWRITE_AI_CACHE_COL_ID
    val appwriteFavouritesColId = BuildConfig.APPWRITE_FAVOURITES_COL_ID
}
