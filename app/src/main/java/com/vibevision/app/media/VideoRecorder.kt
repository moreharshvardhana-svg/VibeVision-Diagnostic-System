package com.vibevision.app.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time H.264 video recorder capturing the OpenGL ES diagnostic output surface
 * and persisting MP4 recordings to Movies/VibeVision via scoped storage.
 */
class VideoRecorder(private val context: Context) {

    companion object {
        private const val TAG = "VibeVideoRecorder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val BIT_RATE = 8_000_000 // 8 Mbps
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
        private const val TIMEOUT_USEC = 10_000L
    }

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var isMuxerStarted = false

    private var tempOutputFile: File? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    @Volatile
    var isRecording = false
        private set

    var configuredWidth: Int = 0
        private set

    var configuredHeight: Int = 0
        private set

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Initializes the MediaCodec hardware encoder and returns its input Surface.
     */
    fun start(width: Int, height: Int): Surface? {
        if (isRecording) return inputSurface

        // Ensure width and height are even numbers for H.264
        val alignedWidth = (width / 16) * 16
        val alignedHeight = (height / 16) * 16

        this.configuredWidth = alignedWidth
        this.configuredHeight = alignedHeight

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, alignedWidth, alignedHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            val codec = MediaCodec.createEncoderByType(MIME_TYPE)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            val tempFile = File.createTempFile("vibevision_rec_", ".mp4", context.cacheDir)
            val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            this.mediaCodec = codec
            this.inputSurface = surface
            this.mediaMuxer = muxer
            this.tempOutputFile = tempFile
            this.videoTrackIndex = -1
            this.isMuxerStarted = false
            this.isRecording = true
            this._recordingDurationSeconds.value = 0L

            // Start HUD timer
            timerJob?.cancel()
            timerJob = scope.launch {
                var seconds = 0L
                while (isActive && isRecording) {
                    delay(1000)
                    if (isRecording) {
                        seconds++
                        _recordingDurationSeconds.value = seconds
                    }
                }
            }

            Log.i(TAG, "Video recorder started at ${alignedWidth}x${alignedHeight}")
            return surface

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VideoRecorder", e)
            stop(null)
            return null
        }
    }

    /**
     * Drains encoded video frames from MediaCodec into MediaMuxer.
     * Called by VibeRenderer on each rendered frame.
     */
    fun drainEncoder(endOfStream: Boolean) {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return

        if (endOfStream) {
            try {
                codec.signalEndOfInputStream()
            } catch (e: Exception) {
                Log.e(TAG, "Error signaling EOS", e)
            }
        }

        while (true) {
            val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break else continue
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isMuxerStarted) {
                    throw RuntimeException("Format changed after muxer started")
                }
                val newFormat = codec.outputFormat
                videoTrackIndex = muxer.addTrack(newFormat)
                muxer.start()
                isMuxerStarted = true
            } else if (encoderStatus < 0) {
                // Ignore unexpected status
            } else {
                val encodedData = codec.getOutputBuffer(encoderStatus)
                    ?: throw RuntimeException("Encoder outputBuffer $encoderStatus was null")

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0 && isMuxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                }

                codec.releaseOutputBuffer(encoderStatus, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    /**
     * Finalizes recording and saves the resulting MP4 into Movies/VibeVision via MediaStore.
     */
    fun stop(onSaved: ((Uri?) -> Unit)?) {
        if (!isRecording) {
            onSaved?.invoke(null)
            return
        }

        isRecording = false
        timerJob?.cancel()

        scope.launch {
            var finalUri: Uri? = null
            try {
                drainEncoder(true)

                mediaCodec?.stop()
                mediaCodec?.release()
                mediaCodec = null

                if (isMuxerStarted) {
                    try {
                        mediaMuxer?.stop()
                    } catch (e: Exception) {
                        Log.e(TAG, "Muxer stop error", e)
                    }
                }
                mediaMuxer?.release()
                mediaMuxer = null
                inputSurface?.release()
                inputSurface = null

                val sourceFile = tempOutputFile
                if (sourceFile != null && sourceFile.exists() && sourceFile.length() > 0) {
                    finalUri = saveVideoToMediaStore(sourceFile)
                    sourceFile.delete()
                }

                withContext(Dispatchers.Main) {
                    if (finalUri != null) {
                        Toast.makeText(context, "Diagnostic recording saved to Gallery", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to export diagnostic recording", Toast.LENGTH_SHORT).show()
                    }
                    onSaved?.invoke(finalUri)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recorder", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
                    onSaved?.invoke(null)
                }
            }
        }
    }

    private suspend fun saveVideoToMediaStore(sourceFile: File): Uri? = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "VIBEVISION_REC_$timestamp.mp4"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/VibeVision")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (videoUri != null) {
                    resolver.openOutputStream(videoUri)?.use { out ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(out)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(videoUri, contentValues, null, null)
                    return@withContext videoUri
                }
            } else {
                @Suppress("DEPRECATION")
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val vibeDir = File(moviesDir, "VibeVision")
                if (!vibeDir.exists()) vibeDir.mkdirs()

                val targetFile = File(vibeDir, filename)
                FileInputStream(sourceFile).use { inStream ->
                    FileOutputStream(targetFile).use { out ->
                        inStream.copyTo(out)
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                }
                return@withContext context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore video save error", e)
        }
        null
    }
}
