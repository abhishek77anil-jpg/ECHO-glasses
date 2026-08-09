package com.meta.wearable.dat.core.types

import android.content.Context

data class DatError(val description: String) {
    fun getLocalizedDescription(context: Context): String = description
}

sealed class DatResult<out T> {
    data class Success<T>(val value: T) : DatResult<T>()
    data class Failure(val error: DatError, val cause: Throwable? = null) : DatResult<Nothing>()

    inline fun fold(
        onSuccess: (T) -> Unit,
        onFailure: (DatError, Throwable?) -> Unit,
    ) {
        when (this) {
            is Success -> onSuccess(value)
            is Failure -> onFailure(error, cause)
        }
    }

    inline fun onFailure(action: (DatError, Throwable?) -> Unit): DatResult<T> {
        if (this is Failure) action(error, cause)
        return this
    }

    companion object {
        fun <T> success(value: T): DatResult<T> = Success(value)
        fun failure(error: DatError, cause: Throwable? = null): DatResult<Nothing> =
            Failure(error, cause)
    }
}

enum class RegistrationState {
    UNAVAILABLE,
    AVAILABLE,
    REGISTERED,
    REGISTERING,
    UNREGISTERING,
}

enum class Permission {
    CAMERA,
}

enum class PermissionStatus {
    Granted,
    Denied,
}

enum class DeviceCompatibility {
    COMPATIBLE,
    DEVICE_UPDATE_REQUIRED,
    SDK_UPDATE_REQUIRED,
}

data class DeviceIdentifier(val id: String) {
    override fun toString(): String = id
}

data class Device(
    val name: String = "",
    val compatibility: DeviceCompatibility = DeviceCompatibility.COMPATIBLE,
    private val displayCapable: Boolean = false,
) {
    fun isDisplayCapable(): Boolean = displayCapable
}
