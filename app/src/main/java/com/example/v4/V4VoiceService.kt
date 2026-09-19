package com.example.v4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class V4VoiceService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START = "com.example.v4.START"
        private const val CHANNEL_ID = "v4_voice"
        private const val NOTIFICATION_ID = 404
    }

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var waitingForCommand = false
    private var ttsReady = false
    private val handler = Handler()
    private val language = "bn-BD"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        tts = TextToSpeech(this, this)
        handler.postDelayed({ startRecognition() }, 1200)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "V4 Voice",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("V4 Active")
        .setContentText("Background listening — বলুন: Active V4")
        .setSmallIcon(R.drawable.ic_v4)
        .setOngoing(true)
        .build()

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            handler.postDelayed({ startRecognition() }, 2000)
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                handler.postDelayed({ startRecognition() }, 500)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (text.isBlank()) {
                    handler.postDelayed({ startRecognition() }, 400)
                } else {
                    handleVoice(text)
                }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            handler.postDelayed({ startRecognition() }, 1000)
        }
    }

    private fun handleVoice(text: String) {
        val command = text.lowercase(Locale.getDefault()).trim()

        if (command.isBlank()) {
            handler.postDelayed({ startRecognition() }, 400)
            return
        }

        if (!waitingForCommand && isWakePhrase(command)) {
            waitingForCommand = true
            speak("জি, বলুন")
            // Wait for the female TTS reply to finish before reopening the microphone.
            handler.postDelayed({ startRecognition() }, 1300)
            return
        }

        if (waitingForCommand) {
            waitingForCommand = false
            executeCommand(text)
            handler.postDelayed({ startRecognition() }, 1000)
            return
        }

        handler.postDelayed({ startRecognition() }, 400)
    }

    private fun isWakePhrase(command: String): Boolean {
        val normalized = command
            .replace("৪", "4")
            .replace("ভি ফোর", "v4")
            .replace("ভি ৪", "v4")
            .replace("v four", "v4")
            .replace("v 4", "v4")
            .replace("অ্যাকটিভ", "active")
            .replace("অ্যাক্টিভ", "active")
            .replace("এক্টিভ", "active")
            .replace("একটিভ", "active")
            .trim()

        return normalized.contains("active v4") || normalized.contains("activev4")
    }

    private fun executeCommand(original: String) {
        val command = original.lowercase(Locale.getDefault())
        when {
            command.contains("সময়") || command.contains("সময়") ||
                command.contains("কয়টা বাজে") || command.contains("কয়টা বাজে") ||
                command.contains("ঘড়ি") || command.contains("ঘড়ি") ||
                command.contains("time") -> {
                val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                speak("এখন সময় $now")
            }
            command.contains("তারিখ") || command.contains("আজ কত তারিখ") ||
                command.contains("আজকের তারিখ") || command.contains("date") ||
                command.contains("today") -> {
                speak("আজ " + SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()))
            }
            command.contains("youtube") || command.contains("ইউটিউব") ->
                openUrl("https://www.youtube.com", "ইউটিউব খুলছি")
            command.contains("google") || command.contains("গুগল") ->
                openUrl("https://www.google.com", "গুগল খুলছি")
            command.contains("গান") || command.contains("music") ||
                command.contains("মিউজিক") || command.contains("ভিডিও") ||
                command.contains("video") ->
                openUrl(
                    "https://www.youtube.com/results?search_query=" + Uri.encode(original),
                    "ইউটিউবে খুঁজে দিচ্ছি"
                )
            command.contains("হ্যালো") || command.contains("hello") ||
                command.contains("হাই") || command.contains("hi") ->
                speak("হ্যালো! আমি V4।")
            else ->
                openUrl(
                    "https://www.google.com/search?q=" + Uri.encode(original),
                    "এই বিষয়ে গুগলে খুঁজে দিচ্ছি"
                )
        }
    }

    private fun openUrl(url: String, message: String) {
        speak(message)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "V4_WAKE_REPLY")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        ttsReady = true

        // Prefer a Bengali female voice. If the device has no Bengali female
        // voice, use any available female Bengali voice; otherwise fall back
        // to the device's Bengali voice.
        val voices = tts.voices.orEmpty()
        val femaleBengali = voices.firstOrNull {
            it.locale.language == "bn" &&
                (it.name.contains("female", true) ||
                 it.name.contains("fem", true) ||
                 it.name.contains("woman", true))
        }
        val anyBengali = voices.firstOrNull { it.locale.language == "bn" }

        try {
            tts.voice = femaleBengali ?: anyBengali ?: tts.voice
        } catch (_: Exception) {
            tts.language = Locale("bn", "BD")
        }

        try {
            tts.language = Locale("bn", "BD")
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
