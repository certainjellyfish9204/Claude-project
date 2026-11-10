package com.example.videoeditor

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Abstraction over whichever FFmpeg Android binding you use. Swap libraries by changing only this file.
 * NOTE: the original arthenica FFmpegKit was archived in 2025. Use a maintained fork
 * (check its README for Maven coordinates) or your own FFmpeg build.
 */
interface FfmpegRunner {
    /** Returns true on success. onProgress receives 0..1 when the total duration is known. */
    suspend fun run(args: List<String>, totalDurationSec: Double, onProgress: (Float) -> Unit): Boolean
    fun cancel()
}

class FfmpegKitRunner : FfmpegRunner {
    override suspend fun run(
        args: List<String>,
        totalDurationSec: Double,
        onProgress: (Float) -> Unit,
    ): Boolean = suspendCancellableCoroutine { cont ->
        FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            { session -> cont.resume(ReturnCode.isSuccess(session.returnCode)) },
            { /* log lines: pipe to Logcat if needed */ },
            { stats ->
                if (totalDurationSec > 0) {
                    val p = (stats.time / 1000.0 / totalDurationSec).coerceIn(0.0, 1.0)
                    onProgress(p.toFloat())
                }
            },
        )
    }

    override fun cancel() {
        FFmpegKit.cancel()
    }
}
