package ru.moscow.lineaccess

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
internal fun ComponentActivity.WithServerAccess(content: @Composable () -> Unit) {
    // Мост эмулятора к компьютеру считается локальной сетью. USB-проброс использует loopback.
    if (Build.VERSION.SDK_INT < 37 || Uri.parse(BuildConfig.API_BASE_URL).host != "10.0.2.2") {
        content()
        return
    }
    val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
    fun hasAccess() = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(hasAccess()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        requested = true
    }
    LifecycleResumeEffect(Unit) {
        allowed = hasAccess()
        onPauseOrDispose { }
    }
    if (allowed) {
        content()
        return
    }

    val needsSettings = requested && !shouldShowRequestPermissionRationale(permission)
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.mock_access_title), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.mock_access_description), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = {
                        if (needsSettings) {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)))
                        } else {
                            request.launch(permission)
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(if (needsSettings) R.string.open_settings else R.string.allow_access))
                }
            }
        }
    }
}
