package com.fersaiyan.cyanbridge.echo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.fersaiyan.cyanbridge.echo.ui.EchoShell
import com.fersaiyan.cyanbridge.echo.ui.EchoViewModel

/**
 * Host for the ECHO experience.
 *
 * Deliberately its own activity rather than a route inside `MainActivity`:
 * that file is ~10k lines of BLE, Wi-Fi P2P, OTA and media orchestration, and
 * ECHO needs none of it. Keeping the entry point separate means this package
 * can be developed, launched and eventually deleted without touching the
 * app's most fragile file.
 *
 * Launch it directly while iterating:
 * ```
 * adb shell am start -n com.fersaiyan.cyanbridge/.echo.EchoActivity
 * ```
 *
 * The ViewModel is constructed here rather than via `viewModel()` because
 * `androidx.lifecycle:lifecycle-viewmodel-compose` is not a dependency of this
 * module — `by viewModels()` comes from activity-ktx, which activity-compose
 * already brings in.
 */
class EchoActivity : ComponentActivity() {

    private val vm: EchoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The shell paints its own near-black ground and insets itself with
        // systemBarsPadding(), so going edge-to-edge here costs nothing and
        // keeps the app correct on Android 15, where it is enforced anyway.
        enableEdgeToEdge()
        setContent { EchoShell(vm) }
    }
}
