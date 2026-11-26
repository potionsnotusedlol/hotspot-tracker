package ru.moscow.lineaccess.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import ru.moscow.lineaccess.R
import ru.moscow.lineaccess.data.CatalogError
import ru.moscow.lineaccess.data.CatalogResult

@Composable
fun CatalogScreen(state: CatalogUiState, isMock: Boolean, onRefresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = colors.primary, shape = RoundedCornerShape(12.dp)) {
                    Icon(painterResource(R.drawable.ic_signal), contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(24.dp), tint = colors.onPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.app_name), color = colors.onBackground,
                    style = typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(36.dp))
            Text(stringResource(R.string.title_wifi), Modifier.semantics { heading() },
                color = colors.onBackground, style = typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(stringResource(R.string.catalog_total), style = typography.titleMedium,
                        color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        when (val result = state.result) {
                            null -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.catalog_loading), style = typography.bodyMedium)
                            }
                            is CatalogResult.Count -> {
                                val locale = LocalConfiguration.current.locales[0]
                                Text(NumberFormat.getIntegerInstance(locale).format(result.value),
                                    style = typography.displayLarge, fontWeight = FontWeight.Bold,
                                    color = colors.primary)
                            }
                            is CatalogResult.Failure -> Text(stringResource(errorMessage(result.reason)),
                                color = colors.onSurface, style = typography.bodyLarge)
                        }
                    }
                    if (isMock) {
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.mock_source), style = typography.bodyMedium,
                            color = colors.onSurfaceVariant)
                    }
                }
            }
            val failure = state.result as? CatalogResult.Failure
            if (state.result != null && failure?.reason != CatalogError.NOT_CONFIGURED) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRefresh, enabled = state.canRefresh,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (failure == null) R.string.refresh else R.string.retry))
                }
            }
        }
    }
}

private fun errorMessage(reason: CatalogError): Int = when (reason) {
    CatalogError.NETWORK -> R.string.error_network
    CatalogError.INVALID_REQUEST -> R.string.error_request
    CatalogError.SIGN_IN_REQUIRED -> R.string.error_sign_in
    CatalogError.SESSION_EXPIRED -> R.string.error_session
    CatalogError.RATE_LIMIT -> R.string.error_rate_limit
    CatalogError.INVALID_RESPONSE -> R.string.error_response
    CatalogError.SERVER -> R.string.error_server
    CatalogError.NOT_CONFIGURED -> R.string.error_configuration
}
