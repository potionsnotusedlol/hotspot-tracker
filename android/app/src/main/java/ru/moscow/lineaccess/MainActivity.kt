package ru.moscow.lineaccess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.moscow.lineaccess.ui.CatalogScreen
import ru.moscow.lineaccess.ui.CatalogViewModel
import ru.moscow.lineaccess.ui.LineAccessTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: CatalogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LineAccessTheme {
                WithServerAccess {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    CatalogScreen(
                        state,
                        isMock = BuildConfig.FLAVOR == "dev",
                        onRefresh = viewModel::refresh
                    )
                }
            }
        }
    }
}
