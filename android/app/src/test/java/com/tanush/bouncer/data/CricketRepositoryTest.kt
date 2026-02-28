package com.tanush.bouncer.data

import com.squareup.okhttp3.mockwebserver.MockResponse
import com.squareup.okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.tanush.bouncer.network.LocalApi

class CricketRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getProbabilityHistory maps DTO to UI model correctly`() = runBlocking {
        val json = """
        [
          {"over": 0.0, "team1Probability": 50.0, "team2Probability": 50.0, "keyEvent": "Match Start"},
          {"over": 6.0, "team1Probability": 55.0, "team2Probability": 45.0, "keyEvent": "Powerplay End"}
        ]
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val localApi = retrofit.create(LocalApi::class.java)

        val result         val result         val result      match-123", localApi)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(2, list.size)
        assertEquals(0.0f, list[0].over)
        assertEquals(50.0f, list[0].team1Probability)
        assertEquals("Match Start", list[0].keyEvent)
    }

    @Test
    fun `getMatchCommentary returns latest commentary from API`() = runBlocking {
        val json = """{"latestCommentary":"Nice cover drive"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val localApi = retrofit.create(LocalApi::class.java)

        val result = CricketRepository.getMatchCommentary("match-xyz", localApi)
        assertTrue(result.isSuccess)
        assertEquals("Nice cover drive", result.getOrThrow())
    }
}
