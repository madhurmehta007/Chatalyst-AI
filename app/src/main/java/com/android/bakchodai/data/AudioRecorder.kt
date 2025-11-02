package com.android.bakchodai.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class AudioRecorder @Inject constructor(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun start() {
        val file = File(context.cacheDir, "temp_audio.m4a")
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)

                prepare()
                start()
                audioFile = file
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to prepare or start recorder", e)
                recorder?.release()
                recorder = null
                audioFile = null
            }
        }
    }

    fun stop(): File? {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception)
        {
            Log.w("AudioRecorder", "Exception on stop/release: ${e.message}")
        }
        recorder = null
        return audioFile
    }
}