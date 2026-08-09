package com.meta.wearable.dat.camera.types

import android.graphics.Bitmap
import android.content.Context
import java.nio.ByteBuffer

enum class VideoQuality {
    LOW,
    MEDIUM,
    HIGH,
}

data class StreamConfiguration(
    val videoQuality: VideoQuality = VideoQuality.MEDIUM,
    val frameRate: Int = 24,
)

enum class StreamState {
    STARTING,
    STARTED,
    STREAMING,
    STOPPING,
    STOPPED,
    PAUSED,
    CLOSED,
}

enum class StreamError {
    STREAM_ERROR,
    UNKNOWN,
    ;

    val description: String
        get() = name

    fun getLocalizedDescription(context: Context): String = description
}

data class VideoFrame(
    val width: Int = 0,
    val height: Int = 0,
    val buffer: ByteBuffer = ByteBuffer.allocate(0),
    val isCompressed: Boolean = false,
    val isCodecConfig: Boolean = false,
)

sealed class PhotoData {
    data class Bitmap(val bitmap: android.graphics.Bitmap) : PhotoData()
    data class HEIC(val data: ByteBuffer) : PhotoData()
}
