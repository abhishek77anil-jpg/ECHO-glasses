package com.meta.wearable.dat.core.session

import com.meta.wearable.dat.core.types.DatError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

enum class DeviceSessionState {
    IDLE,
    STARTING,
    STARTED,
    PAUSED,
    STOPPING,
    STOPPED,
}

class DeviceSession {
    val state: StateFlow<DeviceSessionState> = MutableStateFlow(DeviceSessionState.IDLE)
    val errors = flowOf<DatError>()

    fun start() = Unit
    fun stop() = Unit
}
