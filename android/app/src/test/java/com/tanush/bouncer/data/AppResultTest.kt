package com.tanush.bouncer.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppResultTest {

    @Test
    fun `Success returns data`() {
        val result = AppResult.Success("hello")
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `Error returns null for getOrNull`() {
        val result: AppResult<String> = AppResult.Error(AppException.NetworkException())
        assertFalse(result.isSuccess)
        assertTrue(result.isError)
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrDefault returns default on error`() {
        val result: AppResult<String> = AppResult.Error(AppException.NetworkException())
        assertEquals("fallback", result.getOrDefault("fallback"))
    }

    @Test
    fun `getOrDefault returns data on success`() {
        val result = AppResult.Success("actual")
        assertEquals("actual", result.getOrDefault("fallback"))
    }

    @Test
    fun `map transforms Success`() {
        val result = AppResult.Success(42).map { it * 2 }
        assertEquals(84, (result as AppResult.Success).data)
    }

    @Test
    fun `map preserves Error`() {
        val error: AppResult<Int> = AppResult.Error(AppException.NetworkException())
        val mapped = error.map { it * 2 }
        assertTrue(mapped.isError)
    }

    @Test
    fun `map preserves Loading`() {
        val loading: AppResult<Int> = AppResult.Loading
        val mapped = loading.map { it * 2 }
        assertTrue(mapped.isLoading)
    }

    @Test
    fun `flatMap chains success`() = runTest {
        val result = AppResult.Success(5)
            .flatMap { AppResult.Success(it * 3) }
        assertEquals(15, (result as AppResult.Success).data)
    }

    @Test
    fun `flatMap short-circuits on error`() = runTest {
        val error: AppResult<Int> = AppResult.Error(AppException.CacheException())
        val result = error.flatMap { AppResult.Success(it * 3) }
        assertTrue(result.isError)
    }

    @Test
    fun `onSuccess called for Success`() {
        var called = false
        AppResult.Success("ok").onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun `onSuccess not called for Error`() {
        var called = false
        AppResult.Error(AppException.NetworkException()).onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `onError called for Error`() {
        var called = false
        AppResult.Error(AppException.ApiException(500)).onError { _ -> called = true }
        assertTrue(called)
    }

    @Test
    fun `runCatching catches IOException as NetworkException`() {
        val result = AppResult.runCatching<String> {
            throw java.io.IOException("no internet")
        }
        assertTrue(result.isError)
        val error = result as AppResult.Error
        assertTrue(error.exception is AppException.NetworkException)
    }

    @Test
    fun `runCatching catches AppException`() {
        val result = AppResult.runCatching<String> {
            throw AppException.ScrapingException("cricbuzz")
        }
        assertTrue(result.isError)
        val error = result as AppResult.Error
        assertTrue(error.exception is AppException.ScrapingException)
    }

    @Test
    fun `runCatching wraps unknown exceptions`() {
        val result = AppResult.runCatching<String> {
            throw IllegalStateException("bad state")
        }
        assertTrue(result.isError)
        val error = result as AppResult.Error
        assertTrue(error.exception is AppException.UnknownException)
    }

    @Test
    fun `runCatching returns Success on no exception`() {
        val result = AppResult.runCatching { 42 }
        assertTrue(result.isSuccess)
        assertEquals(42, (result as AppResult.Success).data)
    }

    @Test
    fun `Loading state properties`() {
        val result: AppResult<String> = AppResult.Loading
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
        assertNull(result.getOrNull())
    }
}
