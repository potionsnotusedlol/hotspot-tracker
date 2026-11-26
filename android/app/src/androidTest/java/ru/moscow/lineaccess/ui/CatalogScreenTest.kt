package ru.moscow.lineaccess.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.moscow.lineaccess.data.CatalogError
import ru.moscow.lineaccess.data.CatalogResult

@RunWith(AndroidJUnit4::class)
class CatalogScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun displaysTheServerCountAndRefreshAction() {
        var refreshes = 0
        compose.setContent {
            LineAccessTheme {
                CatalogScreen(CatalogUiState(CatalogResult.Count(42), true), isMock = true) { refreshes++ }
            }
        }
        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithText("Данные тестового сервера").assertIsDisplayed()
        compose.onNodeWithText("Обновить").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun disablesRetryWhileWaitingForServer() {
        compose.setContent {
            LineAccessTheme {
                val failure = CatalogResult.Failure(CatalogError.RATE_LIMIT, 3)
                CatalogScreen(CatalogUiState(failure), isMock = false, onRefresh = {})
            }
        }
        compose.onNodeWithText("Повторить").assertIsNotEnabled()
        compose.onNodeWithText("Данные тестового сервера").assertDoesNotExist()
    }

    @Test
    fun retryAfterNetworkFailureShowsTheNewCount() {
        var attempts = 0
        val model = compose.runOnUiThread {
            CatalogViewModel {
                attempts++
                if (attempts == 1) CatalogResult.Failure(CatalogError.NETWORK) else CatalogResult.Count(7)
            }
        }
        compose.setContent {
            LineAccessTheme {
                CatalogScreen(model.state.collectAsStateWithLifecycle().value, true, model::refresh)
            }
        }
        compose.onNodeWithText("Повторить").performClick()
        compose.onNodeWithText("7").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, attempts) }
    }
}
