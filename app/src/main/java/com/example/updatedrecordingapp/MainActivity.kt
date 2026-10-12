
package com.example.updatedrecordingapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SleepRecorderScreen()
                }
            }
        }
    }
}

private data class SavedRecording(
    val file: File,
    val durationMs: Long
)

@Composable
private fun SleepRecorderScreen() {
    val context = LocalContext.current
    val recordingsFolder = remember {
        File(context.filesDir, "recordings").apply {
            mkdirs()
        }
    }

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var playingFile by remember { mutableStateOf<String?>(null) }
    var status by remember {
        mutableStateOf("Ready to record.")
    }
    var recordings by remember {
        mutableStateOf<List<SavedRecording>>(emptyList())
    }

    fun refreshRecordings() {
        recordings = recordingsFolder.listFiles()
            ?.filter {
                it.isFile &&
                        it.extension.equals("m4a", ignoreCase = true) &&
                        it.length() > 0
            }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val duration = try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull() ?: 0L
                    } finally {
                        retriever.release()
                    }
                } catch (_: Exception) {
                    0L
                }

                SavedRecording(file, duration)
            } ?: emptyList()
    }

    fun stopPlayback() {
        val currentPlayer = player
        player = null
        playingFile = null

        try {
            currentPlayer?.stop()
        } catch (_: Exception) {
            // The player may already have stopped.
        }

        try {
            currentPlayer?.release()
        } catch (_: Exception) {
            // Nothing else is needed.
        }
    }

    fun startRecording() {
        if (isRecording) return

        try {
            recordingsFolder.mkdirs()

            val timestamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())

            val outputFile = File(
                recordingsFolder,
                "sleep_recording_$timestamp.m4a"
            )

            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            try {
                newRecorder.setAudioSource(
                    MediaRecorder.AudioSource.MIC
                )
                newRecorder.setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
                )
                newRecorder.setAudioEncoder(
                    MediaRecorder.AudioEncoder.AAC
                )
                newRecorder.setOutputFile(outputFile.absolutePath)
                newRecorder.prepare()
                newRecorder.start()

                recorder = newRecorder
                isRecording = true
                status = "Recording in progress..."
            } catch (e: Exception) {
                newRecorder.release()
                outputFile.delete()
                throw e
            }
        } catch (e: Exception) {
            isRecording = false
            status = "Could not start recording: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    fun stopAndSaveRecording() {
        val currentRecorder = recorder

        if (currentRecorder == null) {
            status = "There is no active recording."
            return
        }

        recorder = null
        isRecording = false

        try {
            currentRecorder.stop()
            status = "Recording saved successfully."
        } catch (_: Exception) {
            status = "Recording was too short or could not be saved."
        } finally {
            currentRecorder.release()
            refreshRecordings()
        }
    }

    fun playRecording(recording: SavedRecording) {
        stopPlayback()

        try {
            val newPlayer = MediaPlayer()

            newPlayer.setDataSource(recording.file.absolutePath)
            newPlayer.setOnCompletionListener { completedPlayer ->
                completedPlayer.release()

                if (player === completedPlayer) {
                    player = null
                    playingFile = null
                    status = "Playback finished."
                }
            }

            newPlayer.prepare()
            newPlayer.start()

            player = newPlayer
            playingFile = recording.file.absolutePath
            status = "Playing ${recording.file.name}"
        } catch (e: Exception) {
            player = null
            playingFile = null
            status = "Could not play recording: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            status = "Microphone permission is required to record audio."
        }
    }

    LaunchedEffect(Unit) {
        refreshRecordings()
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.release()
            } catch (_: Exception) {
                // Release during screen cleanup.
            }

            try {
                player?.release()
            } catch (_: Exception) {
                // Release during screen cleanup.
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Sleep Recorder",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Record, save, and replay audio from your microphone."
        )

        Button(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    startRecording()
                } else {
                    permissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            },
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Recording")
        }

        Button(
            onClick = { stopAndSaveRecording() },
            enabled = isRecording,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop and Save")
        }

        OutlinedButton(
            onClick = { stopPlayback() },
            enabled = player != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop Playback")
        }

        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Saved Recordings",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedButton(onClick = { refreshRecordings() }) {
                Text("Refresh")
            }
        }

        if (recordings.isEmpty()) {
            Text("No recordings yet. Make your first recording!")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = recordings,
                    key = { it.file.absolutePath }
                ) { recording ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = recording.file.name,
                                style = MaterialTheme.typography.titleSmall
                            )

                            Text(
                                text = "Duration: ${
                                    formatDuration(recording.durationMs)
                                }"
                            )

                            Text(
                                text = "Saved: ${
                                    SimpleDateFormat(
                                        "MMM d, yyyy h:mm a",
                                        Locale.getDefault()
                                    ).format(
                                        Date(recording.file.lastModified())
                                    )
                                }"
                            )

                            Button(
                                onClick = {
                                    if (playingFile == recording.file.absolutePath) {
                                        stopPlayback()
                                        status = "Playback stopped."
                                    } else {
                                        playRecording(recording)
                                    }
                                }
                            ) {
                                Text(
                                    if (playingFile == recording.file.absolutePath) {
                                        "Stop This Recording"
                                    } else {
                                        "Play Recording"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return String.format(
        Locale.US,
        "%d:%02d",
        minutes,
        seconds
    )
}
