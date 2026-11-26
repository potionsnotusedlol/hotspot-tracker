package ru.moscow.lineaccess.data

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import ru.moscow.lineaccess.api.AccessPointsApi
import ru.moscow.lineaccess.api.model.Problem

fun interface CatalogRepository {
    suspend fun getPointCount(): CatalogResult
}

sealed interface CatalogResult {
    data class Count(val value: Long) : CatalogResult
    data class Failure(val reason: CatalogError, val retryAfterSeconds: Int = 0) : CatalogResult
}

enum class CatalogError {
    NETWORK, INVALID_REQUEST, SIGN_IN_REQUIRED, SESSION_EXPIRED, RATE_LIMIT, INVALID_RESPONSE,
    SERVER, NOT_CONFIGURED
}

internal class NetworkCatalogRepository(
    private val api: AccessPointsApi,
    private val json: Json
) : CatalogRepository {
    override suspend fun getPointCount(): CatalogResult {
        try {
            // Для стартового экрана нужен только totalElements первой гостевой страницы.
            val response = api.searchAccessPoints(page = 0, size = 1)
            if (response.isSuccessful) {
                val count = response.body()?.totalElements
                return if (count != null && count >= 0) {
                    CatalogResult.Count(count)
                } else {
                    CatalogResult.Failure(CatalogError.INVALID_RESPONSE)
                }
            }

            val problem = response.errorBody()?.use { body ->
                if (body.contentType()?.subtype != "problem+json") return@use null
                try {
                    json.decodeFromString<Problem>(body.string())
                } catch (_: SerializationException) {
                    null
                }
            }
            val reason = when (response.code()) {
                400 -> CatalogError.INVALID_REQUEST
                401 -> if (problem?.type == "/problems/token-invalid") {
                    CatalogError.SESSION_EXPIRED
                } else {
                    CatalogError.SIGN_IN_REQUIRED
                }
                429 -> CatalogError.RATE_LIMIT
                else -> CatalogError.SERVER
            }
            val retryAfter = if (reason == CatalogError.RATE_LIMIT) {
                response.headers()["Retry-After"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            } else 0
            return CatalogResult.Failure(reason, retryAfter)
        } catch (_: IOException) {
            return CatalogResult.Failure(CatalogError.NETWORK)
        } catch (_: SerializationException) {
            return CatalogResult.Failure(CatalogError.INVALID_RESPONSE)
        }
    }
}
