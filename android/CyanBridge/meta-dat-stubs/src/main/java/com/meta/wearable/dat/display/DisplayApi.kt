package com.meta.wearable.dat.display

import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.types.DatError
import com.meta.wearable.dat.core.types.DatResult
import com.meta.wearable.dat.display.types.DisplayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Display {
    val state: StateFlow<DisplayState> = MutableStateFlow(DisplayState.STOPPED)

    fun stop() = Unit
}

fun DeviceSession.addDisplay(): DatResult<Display> =
    DatResult.failure(DatError("Meta DAT display is unavailable in this build"))
