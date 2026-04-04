package com.guardian.dialer.api

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Bridges phone-call audio to the ElevenLabs Conversational AI WebSocket.
 *
 * Flow:
 *  1. Opens WSS to ElevenLabs with the agent_id
 *  2. Captures microphone (VOICE_COMMUNICATION source — gets caller audio during call)
 *  3. Streams PCM→base64 chunks to ElevenLabs
 *  4. Receives agent audio, plays back into VOICE_CALL stream so the caller hears it
 *  5. Collects transcript for post-call summary
 */
class ElevenLabsClient(
    private val apiKey: String,
    private val agentId: String,
    private val callerNumber: String
) {
    companion object {
        private const val TAG = "ElevenLabsClient"
        private const val WS_URL = "wss://api.elevenlabs.io/v1/convai/conversation"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_DURATION_MS = 100 // 100ms chunks
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // no read timeout for WS
        .build()

    private var ws: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Collected transcript
    private val transcriptLines = mutableListOf<String>()
    var onSessionEnded: ((summary: String) -> Unit)? = null

    private var conversationId: String? = null

    fun start() {
        Log.i(TAG, "Starting ElevenLabs bridge for caller=$callerNumber agent=$agentId")

        val url = "$WS_URL?agent_id=$agentId"
        val request = Request.Builder()
            .url(url)
            .header("xi-api-key", apiKey)
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                // Send initial config with caller context
                val init = JsonObject().apply {
                    addProperty("type", "conversation_initiation_client_data")
                    add("conversation_config_override", JsonObject().apply {
                        add("agent", JsonObject().apply {
                            add("prompt", JsonObject().apply {
                                addProperty("prompt", "The caller's phone number is $callerNumber. " +
                                    "Find out who they are, what they need, and let them know the owner " +
                                    "will get back to them. Be friendly and concise.")
                            })
                        })
                    })
                }
                webSocket.send(gson.toJson(init))
                startAudioCapture()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                stop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                finishSession()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val msg = gson.fromJson(text, JsonObject::class.java)
            when (msg.get("type")?.asString) {
                "conversation_initiation_metadata" -> {
                    conversationId = msg.getAsJsonObject("conversation_initiation_metadata_event")
                        ?.get("conversation_id")?.asString
                    Log.i(TAG, "Conversation started: $conversationId")
                }
                "audio" -> {
                    val audioEvent = msg.getAsJsonObject("audio_event")
                    val b64 = audioEvent?.get("audio_base_64")?.asString ?: return
                    playAudio(Base64.decode(b64, Base64.NO_WRAP))
                }
                "agent_response" -> {
                    val agentText = msg.getAsJsonObject("agent_response_event")
                        ?.get("agent_response")?.asString
                    if (agentText != null) {
                        transcriptLines.add("Agent: $agentText")
                    }
                }
                "user_transcript" -> {
                    val userText = msg.getAsJsonObject("user_transcription_event")
                        ?.get("user_transcript")?.asString
                    if (userText != null) {
                        transcriptLines.add("Caller: $userText")
                    }
                }
                "interruption" -> {
                    // Caller interrupted — clear playback buffer
                    audioTrack?.flush()
                }
                "ping" -> {
                    val pong = JsonObject().apply {
                        addProperty("type", "pong")
                        msg.get("ping_event")?.asJsonObject?.get("event_id")?.let {
                            addProperty("event_id", it.asInt)
                        }
                    }
                    ws?.send(gson.toJson(pong))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing message: ${e.message}")
        }
    }

    @Suppress("MissingPermission") // permission checked before start() is called
    private fun startAudioCapture() {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            SAMPLE_RATE * 2 * BUFFER_DURATION_MS / 1000 // 2 bytes per sample
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        // Playback track — plays agent responses into the call
        val playBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(playBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioTrack?.play()
        audioRecord?.startRecording()

        captureJob = scope.launch {
            val buffer = ByteArray(SAMPLE_RATE * 2 * BUFFER_DURATION_MS / 1000) // 100ms of 16-bit mono
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    val b64 = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                    val msg = JsonObject().apply {
                        addProperty("type", "user_audio_chunk")
                        addProperty("user_audio_chunk", b64)
                    }
                    ws?.send(gson.toJson(msg))
                }
            }
        }
    }

    private fun playAudio(pcmData: ByteArray) {
        audioTrack?.write(pcmData, 0, pcmData.size)
    }

    fun stop() {
        Log.i(TAG, "Stopping ElevenLabs bridge")
        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        ws?.close(1000, "call ended")
        ws = null
        finishSession()
    }

    private var finished = false

    private fun finishSession() {
        if (finished) return
        finished = true
        scope.launch {
            val summary = buildSummary()
            withContext(Dispatchers.Main) {
                onSessionEnded?.invoke(summary)
            }
        }
    }

    private fun buildSummary(): String {
        if (transcriptLines.isEmpty()) {
            return "Call from $callerNumber — no conversation captured."
        }
        val transcript = transcriptLines.joinToString("\n")
        return "Call from $callerNumber:\n$transcript"
    }
}
