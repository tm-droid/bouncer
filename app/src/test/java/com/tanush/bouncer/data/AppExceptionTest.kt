package com.tanush.bouncer.data

import org.junit.Assert.*
import org.junit.Test

class AppExceptionTest {

    @Test
    fun `NetworkException has user-friendly message`() {
        val ex = AppException.NetworkException()
        assertEquals("No internet connection. Please check your network.", ex.userMessage)
    }

    @Test
    fun `ScrapingException includes source name`() {
        val ex = AppException.ScrapingException("cricbuzz")
        assertTrue(ex.userMessage.contains("cricbuzz"))
        assertEquals("cricbuzz", ex.source)
    }

    @Test
    fun `ApiException 429 shows rate limit message`() {
        val ex = AppException.ApiException(429)
        assertTrue(ex.userMessage.contains("Too many requests"))
        assertEquals(429, ex.statusCode)
    }

    @Test
    fun `ApiException 500 shows server error`() {
        val ex = AppException.ApiException(500)
        assertTrue(ex.userMessage.contains("Server error"))
    }

    @Test
    fun `ApiException 502 shows server error`() {
        val ex = AppException.ApiException(502)
        assertTrue(ex.userMessage.contains("Server error"))
    }

    @Test
    fun `ApiException other codes show generic message`() {
        val ex = AppException.ApiException(403)
        assertTrue(ex.userMessage.contains("Something went wrong"))
    }

    @Test
    fun `CacheException has correct message`() {
        val ex = AppException.CacheException()
        assertTrue(ex.userMessage.contains("cached data"))
    }

    @Test
    fun `ParseException has correct message`() {
        val ex = AppException.ParseException()
        assertTrue(ex.userMessage.contains("process data"))
    }

    @Test
    fun `UnknownException has generic message`() {
        val ex = AppException.UnknownException()
        assertTrue(ex.userMessage.contains("Something went wrong"))
    }

    @Test
    fun `exceptions preserve cause`() {
        val cause = RuntimeException("root")
        val ex = AppException.NetworkException(cause = cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `exceptions are throwable`() {
        val ex: Throwable = AppException.ApiException(404)
        assertTrue(ex is Exception)
        assertTrue(ex is AppException)
    }
}
