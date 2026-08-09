package com.meta.wearable.dat.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.types.DatError
import com.meta.wearable.dat.core.types.DatResult
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Compile-only stub of Meta Wearables DAT. Used when no GitHub Packages token
 * is configured so the app still builds for HeyCyan and other non-Meta targets.
 * Runtime Meta Ray-Ban flows will report initialization failure.
 */
object Wearables {
    private val unavailable = DatError("Meta Wearables DAT SDK is not packaged in this build")

    val registrationState: StateFlow<RegistrationState> =
        MutableStateFlow(RegistrationState.UNAVAILABLE)

    val registrationErrorStream = flowOf<DatError>()

    val devices: StateFlow<Set<DeviceIdentifier>> = MutableStateFlow(emptySet())

    val devicesMetadata: Map<DeviceIdentifier, StateFlow<Device>> = emptyMap()

    fun initialize(context: Context): DatResult<Unit> = DatResult.failure(unavailable)

    fun startRegistration(activity: Activity) = Unit

    fun startUnregistration(activity: Activity) = Unit

    fun checkPermissionStatus(permission: Permission): DatResult<PermissionStatus> =
        DatResult.failure(unavailable)

    fun createSession(selector: DeviceSelector): DatResult<DeviceSession> =
        DatResult.failure(unavailable)

    /**
     * Matches the one-arg [getOrDefault] usage in MainActivity / CommunityPluginsActivity:
     * `result.getOrDefault(PermissionStatus.Denied) == PermissionStatus.Granted`
     */
    class PermissionResult(private val status: PermissionStatus = PermissionStatus.Denied) {
        fun getOrDefault(default: PermissionStatus): PermissionStatus = status.takeIf {
            it != PermissionStatus.Denied || default == PermissionStatus.Denied
        } ?: default
    }

    class RequestPermissionContract :
        ActivityResultContract<Permission, PermissionResult>() {
        override fun createIntent(context: Context, input: Permission): Intent = Intent()

        override fun parseResult(resultCode: Int, intent: Intent?): PermissionResult =
            PermissionResult(PermissionStatus.Denied)
    }
}
