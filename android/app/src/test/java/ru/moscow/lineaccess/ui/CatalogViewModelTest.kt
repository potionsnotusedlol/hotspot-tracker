package ru.moscow.lineaccess.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.moscow.lineaccess.data.CatalogError
import ru.moscow.lineaccess.data.CatalogResult

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun sharesOneRequestWhileTheFirstLoadIsInFlight() = runTest(dispatcher) {
        val response = CompletableDeferred<CatalogResult>()
        var calls = 0
        val model = CatalogViewModel { calls++; response.await() }
        model.refresh()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(CatalogUiState(), model.state.value)
        response.complete(CatalogResult.Count(2))
        runCurrent()
        assertEquals(CatalogUiState(CatalogResult.Count(2), canRefresh = true), model.state.value)
    }

    @Test
    fun honoursTheServerDelayBeforeAllowingRetry() = runTest(dispatcher) {
        var calls = 0
        val model = CatalogViewModel {
            calls++
            if (calls == 1) CatalogResult.Failure(CatalogError.RATE_LIMIT, 2) else CatalogResult.Count(1)
        }
        runCurrent()
        assertFalse(model.state.value.canRefresh)
        advanceTimeBy(1_999)
        model.refresh()
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(model.state.value.canRefresh)
        model.refresh()
        runCurrent()
        assertEquals(CatalogResult.Count(1), model.state.value.result)
        assertEquals(2, calls)
    }
}
