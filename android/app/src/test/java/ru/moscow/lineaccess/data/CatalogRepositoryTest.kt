package ru.moscow.lineaccess.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.moscow.lineaccess.di.NetworkModule

class CatalogRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: CatalogRepository

    @Before
    fun setUp() {
        server.start()
        repository = NetworkModule.catalogRepository(server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun readsGuestPageAndKeepsTheInt64Total() = runTest {
        server.enqueue(okPage(total = 3_200_000_001))
        assertEquals(CatalogResult.Count(3_200_000_001), repository.getPointCount())
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/api/v1/access-points", request.requestUrl?.encodedPath)
        assertEquals("0", request.requestUrl?.queryParameter("page"))
        assertEquals("1", request.requestUrl?.queryParameter("size"))
        assertNull(request.requestUrl?.queryParameter("cursor"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun readsTheAdditionalUserFieldsInTheSamePageSchema() = runTest {
        val extra = """
            ,"nearestStop":{"name":"Метро","type":"METRO","distanceM":180},
            "peakLoad":42.5,"loadLevel":"MEDIUM"
        """.trimIndent()
        server.enqueue(okPage(total = 2, extra = extra))
        assertEquals(CatalogResult.Count(2), repository.getPointCount())
    }

    @Test
    fun handlesEmptyPage() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[],"page":0,"size":1,"totalElements":0,"totalPages":0}"""))
        assertEquals(CatalogResult.Count(0), repository.getPointCount())
    }

    @Test
    fun usesProblemTypeToDistinguishTwoUnauthorizedResponses() = runTest {
        for ((type, reason) in listOf("login-required" to CatalogError.SIGN_IN_REQUIRED,
                                      "token-invalid" to CatalogError.SESSION_EXPIRED)) {
            server.enqueue(MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type":"/problems/$type","title":"Authentication required","status":401}"""))
            assertEquals(CatalogResult.Failure(reason), repository.getPointCount())
        }
    }

    @Test
    fun exposesRetryAfterFromRateLimitResponse() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3"))
        assertEquals(CatalogResult.Failure(CatalogError.RATE_LIMIT, 3), repository.getPointCount())
    }

    @Test
    fun malformedSuccessIsAnErrorAndTheNextRequestCanRecover() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[]}"""))
        server.enqueue(okPage(total = 1))
        assertEquals(CatalogResult.Failure(CatalogError.INVALID_RESPONSE), repository.getPointCount())
        assertEquals(CatalogResult.Count(1), repository.getPointCount())
    }

    @Test
    fun missingProductionOriginDoesNotSendTraffic() = runTest {
        val unconfigured = NetworkModule.catalogRepository("")
        assertEquals(CatalogResult.Failure(CatalogError.NOT_CONFIGURED), unconfigured.getPointCount())
        assertEquals(0, server.requestCount)
    }

    private fun okPage(total: Long, extra: String = ""): MockResponse {
        val json = """
            {"content":[{"id":1001,"name":"Точка у парка","address":"Тестовый адрес",
            "borough":"ЦАО","district":"Тверской","location":{"lat":55.7648,"lon":37.6055},
            "status":"ACTIVE"$extra}],"page":0,"size":1,"totalElements":$total,"totalPages":1,"nextCursor":null}
        """.trimIndent()
        return MockResponse().setHeader("Content-Type", "application/json").setBody(json)
    }
}
