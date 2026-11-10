package com.example.videoeditor

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.videoeditor.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var listAdapter: ArrayAdapter<String>
    private lateinit var preview: PreviewPlayer
    private val clips = mutableListOf<Clip>()
    private val runner: FfmpegRunner = FfmpegKitRunner()
    private val rotations = listOf(0, 90, 180, 270)

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val clip = withContext(Dispatchers.IO) {
                val path = copyUriToCache(uri, "clip_${System.currentTimeMillis()}.mp4")
                Clip(
                    path = path,
                    sourceDurationSec = MediaProbe.durationSec(path),
                    hasAudio = MediaProbe.hasAudio(path),
                )
            }
            clips += clip
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        preview = PreviewPlayer(this)
        b.playerView.player = preview.player

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf<String>())
        b.clipList.adapter = listAdapter
        b.clipList.choiceMode = ListView.CHOICE_MODE_SINGLE
        b.clipList.setOnItemClickListener { _, _, pos, _ -> showEditDialog(pos) }

        b.resSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Resolution.values().map { it.label }
        )
        b.resSpinner.setSelection(1) // 720p
        b.qualSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Quality.values().map { it.label }
        )
        b.qualSpinner.setSelection(1)

        b.btnAdd.setOnClickListener { pickVideo.launch(arrayOf("video/*")) }
        b.btnPreview.setOnClickListener { preview.playFrom(maxOf(selectedIndex(), 0)) }
        b.btnUp.setOnClickListener { move(-1) }
        b.btnDown.setOnClickListener { move(1) }
        b.btnRemove.setOnClickListener { removeSelected() }
        b.btnExport.setOnClickListener { export() }
        b.btnCancel.setOnClickListener { runner.cancel() }
        refresh()
    }

    override fun onStop() {
        preview.player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        b.playerView.player = null
        preview.release()
        super.onDestroy()
    }

    private fun selectedIndex() = b.clipList.checkedItemPosition

    private fun move(delta: Int) {
        val i = selectedIndex()
        if (i < 0) return toast("Tap a clip to select it")
        val j = i + delta
        if (j !in clips.indices) return
        clips.add(j, clips.removeAt(i))
        refresh()
        b.clipList.setItemChecked(j, true)
    }

    private fun removeSelected() {
        val i = selectedIndex()
        if (i < 0) return toast("Tap a clip to select it")
        clips.removeAt(i)
        b.clipList.clearChoices()
        refresh()
    }

    /** Per-clip editor: trim, speed, volume (0 = mute), fades, rotation. */
    private fun showEditDialog(index: Int) {
        val c = clips[index]
        val v = layoutInflater.inflate(R.layout.dialog_clip_edit, null)
        val start = v.findViewById<EditText>(R.id.startInput)
        val end = v.findViewById<EditText>(R.id.endInput)
        val speed = v.findViewById<EditText>(R.id.speedInput)
        val volume = v.findViewById<EditText>(R.id.volumeInput)
        val fadeIn = v.findViewById<EditText>(R.id.fadeInInput)
        val fadeOut = v.findViewById<EditText>(R.id.fadeOutInput)
        val rotation = v.findViewById<Spinner>(R.id.rotationSpinner)

        start.setText(c.startSec.toString())
        end.setText(c.endSec.toString())
        speed.setText(c.speed.toString())
        volume.setText(c.volume.toString())
        fadeIn.setText(c.fadeInSec.toString())
        fadeOut.setText(c.fadeOutSec.toString())
        rotation.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, rotations.map { "$it deg" }
        )
        rotation.setSelection(rotations.indexOf(c.rotationDeg))

        AlertDialog.Builder(this)
            .setTitle("Edit clip ${index + 1}")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val updated = c.copy(
                    startSec = start.num(c.startSec),
                    endSec = end.num(c.endSec),
                    speed = speed.num(c.speed),
                    volume = volume.num(c.volume),
                    fadeInSec = fadeIn.num(c.fadeInSec),
                    fadeOutSec = fadeOut.num(c.fadeOutSec),
                    rotationDeg = rotations[rotation.selectedItemPosition],
                )
                val err = EditCommandBuilder.validate(updated)
                if (err != null) toast(err) else {
                    clips[index] = updated
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun export() {
        if (clips.isEmpty()) return toast("Add at least one clip")

        val opts = ExportOptions(
            resolution = Resolution.values()[b.resSpinner.selectedItemPosition],
            quality = Quality.values()[b.qualSpinner.selectedItemPosition],
        )
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val out = File(dir, "export_${System.currentTimeMillis()}.mp4").absolutePath

        val args = try {
            EditCommandBuilder.buildExport(clips, out, opts)
        } catch (e: IllegalArgumentException) {
            return toast(e.message ?: "Invalid edit")
        }

        val total = clips.sumOf { it.outputDurationSec }
        b.progress.progress = 0
        b.btnExport.isEnabled = false
        b.btnCancel.isEnabled = true
        b.status.text = "Exporting..."
        preview.player.pause()

        lifecycleScope.launch {
            val ok = runner.run(args, total) { p ->
                runOnUiThread { b.progress.progress = (p * 100).toInt() }
            }
            b.btnExport.isEnabled = true
            b.btnCancel.isEnabled = false
            b.status.text = if (ok) "Saved: $out" else "Export failed or was cancelled"
        }
    }

    private fun refresh() {
        listAdapter.clear()
        listAdapter.addAll(clips.mapIndexed { i, c -> "${i + 1}. ${c.summary()}" })
        listAdapter.notifyDataSetChanged()
        b.totalInfo.text = if (clips.isEmpty()) "No clips yet"
        else "Total length: %.1fs".format(clips.sumOf { it.outputDurationSec })
        preview.setClips(clips)
    }

    private fun EditText.num(fallback: Double): Double =
        text.toString().toDoubleOrNull() ?: fallback

    private fun copyUriToCache(uri: Uri, name: String): String {
        // FFmpeg and ExoPlayer both work best with file paths, so copy the content:// URI into cache.
        val f = File(cacheDir, name)
        contentResolver.openInputStream(uri)!!.use { input ->
            f.outputStream().use { input.copyTo(it) }
        }
        return f.absolutePath
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
