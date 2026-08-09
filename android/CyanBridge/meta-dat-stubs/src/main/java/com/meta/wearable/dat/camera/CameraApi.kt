package com.meta.wearable.dat.camera

import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamError
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.types.DatError
import com.meta.wearable.dat.core.types.DatResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class Stream {
    val state: StateFlow<StreamState> = MutableStateFlow(StreamState.STOPPED)
    val errorStream = flowOf<StreamError>()
    val videoStream = flowOf<VideoFrame>()

    fun start(): DatResult<Unit> =
        DatResult.failure(DatError("Meta DAT stream is unavailable in this build"))

    fun stop() = Unit

    fun capturePhoto(): DatResult<PhotoData> =
        DatResult.failure(DatError("Meta DAT capture is unavailable in this build"))
}

fun DeviceSession.addStream(configuration: StreamConfiguration): DatResult<Stream> =
    DatResult.failure(DatError("Meta DAT stream is unavailable in this build"))
