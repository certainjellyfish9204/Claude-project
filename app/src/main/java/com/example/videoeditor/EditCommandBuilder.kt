package com.example.videoeditor

import java.util.Locale
import kotlin.math.min

/** Pure Kotlin: turns clips into FFmpeg arguments. No Android dependencies, so it is easy to unit test. */
object EditCommandBuilder {
    private const val SAMPLE_RATE = 44100

    /** Returns a human-readable problem with the clip, or null if it is valid. */
    fun validate(c: Clip): String? = when {
        c.startSec < 0 || c.endSec <= c.startSec -> "Start must be before end"
        c.endSec > c.sourceDurationSec + 0.05 -> "End is past the clip length (${fmt(c.sourceDurationSec)}s)"
        c.speed !in 0.25..4.0 -> "Speed must be between 0.25x and 4x"
        c.volume !in 0.0..2.0 -> "Volume must be between 0 and 2 (1 = normal, 0 = mute)"
        c.fadeInSec < 0 || c.fadeOutSec < 0 -> "Fades can't be negative"
        else -> null
    }

    fun buildExport(clips: List<Clip>, outputPath: String, opts: ExportOptions = ExportOptions()): List<String> {
        require(clips.isNotEmpty()) { "Add at least one clip" }
        clips.forEachIndexed { i, c ->
            validate(c)?.let { throw IllegalArgumentException("Clip ${i + 1}: $it") }
        }

        val args = mutableListOf("-y")
        // Trim at the input level: -ss/-t before each -i keeps only the wanted range.
        clips.forEach { c ->
            args += listOf("-ss", fmt(c.startSec), "-t", fmt(c.endSec - c.startSec), "-i", c.path)
        }

        val chains = clips.indices.flatMap { i -> listOf(videoChain(i, clips[i], opts), audioChain(i, clips[i])) }
        // concat expects [v0][a0][v1][a1]... in order.
        val concatInputs = clips.indices.joinToString("") { "[v$it][a$it]" }
        val graph = chains.joinToString(";") + ";${concatInputs}concat=n=${clips.size}:v=1:a=1[outv][outa]"

        args += listOf(
            "-filter_complex", graph,
            "-map", "[outv]", "-map", "[outa]",
            "-c:v", "mpeg4", "-q:v", opts.quality.qscale.toString(),
            "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            outputPath,
        )
        return args
    }

    private fun videoChain(i: Int, c: Clip, opts: ExportOptions): String {
        val f = mutableListOf<String>()
        when (c.rotationDeg) {
            90 -> f += "transpose=1"
            180 -> f += listOf("transpose=1", "transpose=1")
            270 -> f += "transpose=2"
        }
        if (c.speed != 1.0) f += "setpts=PTS/${fmt(c.speed)}"
        val w = opts.resolution.w
        val h = opts.resolution.h
        f += "scale=$w:$h:force_original_aspect_ratio=decrease"
        f += "pad=$w:$h:(ow-iw)/2:(oh-ih)/2"
        f += "setsar=1"
        f += "fps=${opts.fps}"
        f += fadeFilters(c, "fade")
        return "[$i:v:0]" + f.joinToString(",") + "[v$i]"
    }

    private fun audioChain(i: Int, c: Clip): String {
        val f = mutableListOf("aresample=$SAMPLE_RATE", "aformat=channel_layouts=stereo")
        f += atempoChain(c.speed)
        if (c.volume != 1.0) f += "volume=${fmt(c.volume)}"
        f += fadeFilters(c, "afade")
        val chain = f.joinToString(",")
        // Clips without audio get generated silence so concat still works.
        return if (c.hasAudio) {
            "[$i:a:0]$chain[a$i]"
        } else {
            "anullsrc=r=$SAMPLE_RATE:cl=stereo:d=${fmt(c.endSec - c.startSec)},$chain[a$i]"
        }
    }

    /** atempo only accepts 0.5 to 2.0 per filter, so chain filters for other speeds. */
    private fun atempoChain(speed: Double): List<String> {
        val out = mutableListOf<String>()
        var s = speed
        while (s > 2.0) { out += "atempo=2.0"; s /= 2.0 }
        while (s < 0.5) { out += "atempo=0.5"; s /= 0.5 }
        if (s != 1.0) out += "atempo=${fmt(s)}"
        return out
    }

    private fun fadeFilters(c: Clip, filter: String): List<String> {
        val dur = c.outputDurationSec
        val out = mutableListOf<String>()
        if (c.fadeInSec > 0) out += "$filter=t=in:st=0:d=${fmt(min(c.fadeInSec, dur))}"
        if (c.fadeOutSec > 0) {
            val d = min(c.fadeOutSec, dur)
            out += "$filter=t=out:st=${fmt(dur - d)}:d=${fmt(d)}"
        }
        return out
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.3f", v)
}
