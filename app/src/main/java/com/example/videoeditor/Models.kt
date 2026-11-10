package com.example.videoeditor

/** One source clip plus all of its edit settings. Times are in seconds. */
data class Clip(
    val path: String,
    val sourceDurationSec: Double,
    val hasAudio: Boolean,
    val startSec: Double = 0.0,
    val endSec: Double = sourceDurationSec,
    val speed: Double = 1.0,        // 0.25 to 4.0
    val volume: Double = 1.0,       // 0 = mute, 1 = normal, 2 = double
    val fadeInSec: Double = 0.0,
    val fadeOutSec: Double = 0.0,
    val rotationDeg: Int = 0,       // 0, 90, 180, 270
) {
    /** Length after trimming and speed change. */
    val outputDurationSec: Double get() = (endSec - startSec) / speed

    fun summary(): String = buildString {
        append("%.1fs-%.1fs".format(startSec, endSec))
        if (speed != 1.0) append(" | %.2fx".format(speed))
        if (volume == 0.0) append(" | muted")
        else if (volume != 1.0) append(" | vol ${(volume * 100).toInt()}%")
        if (rotationDeg != 0) append(" | rot $rotationDeg deg")
        if (fadeInSec > 0 || fadeOutSec > 0) append(" | fades")
    }
}

enum class Resolution(val label: String, val w: Int, val h: Int) {
    SD("480p", 854, 480),
    HD("720p", 1280, 720),
    FHD("1080p", 1920, 1080),
}

/** MPEG-4 quality scale: lower qscale = better quality, bigger file. */
enum class Quality(val label: String, val qscale: Int) {
    HIGH("High quality", 3),
    MEDIUM("Balanced", 5),
    SMALL("Small file", 8),
}

data class ExportOptions(
    val resolution: Resolution = Resolution.HD,
    val quality: Quality = Quality.MEDIUM,
    val fps: Int = 30,
)
