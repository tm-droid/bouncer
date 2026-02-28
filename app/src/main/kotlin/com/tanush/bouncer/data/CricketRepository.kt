package com.tanush.bouncer.data

import com.tanush.bouncer.network.RetrofitClient
import com.tanush.bouncer.network.models.SanwebMatchResponse
import com.tanush.bouncer.network.models.SanwebMatchesListResponse

object CricketRepository {
	suspend fun getMatches(type: String): Result<SanwebMatchesListResponse> {
		return try {
			val resp = RetrofitClient.vercelApi.getMatches(type)
			Result.success(resp)
		} catch (t: Throwable) {
			Result.failure(t)
		}
	}

	suspend fun getMatchScore(id: String): Result<SanwebMatchResponse> {
		return try {
			val resp = RetrofitClient.vercelApi.getMatchScore(id)
			Result.success(resp)
		} catch (t: Throwable) {
			Result.failure(t)
		}
	}

	// Fetch probability history from local development API (mock server)

	suspend fun getProbabilityHistory(matchId: String, localApi: com.tanush.bouncer.network.LocalApi? = null): Result<List<com.tanush.bouncer.ui.components.premium.WinProbabilityPoint>> {
		return try {
			val api = localApi ?: RetrofitClient.localApi
			val resp = api.getProbability(matchId)
			// Map DTO to UI model used by the chart
			val mapped = resp.map { dto ->
				com.tanush.bouncer.ui.components.premium.WinProbabilityPoint(
					over = dto.over,
					team1Probability = dto.team1Probability,
					team2Probability = dto.team2Probability,
					keyEvent = dto.keyEvent
				)
			}
			Result.success(mapped)
		} catch (t: Throwable) {
			Result.failure(t)
		}
	}

	suspend fun getMatchCommentary(matchId: String): Result<String> {
		return try {
			val resp = RetrofitClient.localApi.getCommentary(matchId)
			Result.success(resp.latestCommentary)
		} catch (t: Throwable) {
			Result.failure(t)
		}
	}
}
