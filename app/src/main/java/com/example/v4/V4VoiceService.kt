package com.example.v4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    private val language = "bn-BD"

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        startRecognition()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "V4 Voice", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("V4 Active")
        .setContentText("Wake phrase: Active V4")
        .setSmallIcon(R.drawable.ic_v4)
        .setOngoing(true)
        .build()

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { restartRecognition() }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                handleVoice(text)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    private fun restartRecognition() {
        android.os.Handler(mainLooper).postDelayed({ startRecognition() }, 500)
    }

    private fun handleVoice(text: String) {
        val command = text.lowercase(Locale.getDefault()).trim()
        if (command.isBlank()) { restartRecognition(); return }
        if (!waitingForCommand && (command.contains("active v4") || command.contains("অ্যাক্টিভ ভি ফোর") || command.contains("অ্যাকটিভ ভি ফোর"))) {
            waitingForCommand = true
            speak("জি, বলুন")
            restartRecognition()
            return
        }
        if (waitingForCommand) {
            waitingForCommand = false
            executeCommand(text)
        }
        restartRecognition()
    }

    private fun executeCommand(original: String) {
        val command = original.lowercase(Locale.getDefault())
        when {
            command.contains("সময়") || command.contains("সময়") || command.contains("কয়টা বাজে") || command.contains("কয়টা বাজে") || command.contains("ঘড়ি") || command.contains("ঘড়ি") || command.contains("time") -> {
                val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                speak("এখন সময় $now")
            }
            command.contains("তারিখ") || command.contains("আজ কত তারিখ") || command.contains("আজকের তারিখ") || command.contains("date") || command.contains("today") -> speak("আজ " + SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()))
            command.contains("youtube") || command.contains("ইউটিউব") -> openUrl("https://www.youtube.com", "ইউটিউব খুলছি")
            command.contains("google") || command.contains("গুগল") -> openUrl("https://www.google.com", "গুগল খুলছি")
            command.contains("গান") || command.contains("music") || command.contains("মিউজিক") || command.contains("ভিডিও") || command.contains("video") -> openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(original), "ইউটিউবে খুঁজে দিচ্ছি")
            command.contains("হ্যালো") || command.contains("hello") || command.contains("হাই") || command.contains("hi") -> speak("হ্যালো! আমি V4।")
            else -> openUrl("https://www.google.com/search?q=" + Uri.encode(original), "এই বিষয়ে গুগলে খুঁজে দিচ্ছি")
        }
    }

    private fun openUrl(url: String, message: String) {
        speak(message)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun speak(text: String) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "V4_WAKE_REPLY") }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.language = Locale("bn", "BD") }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { recognizer?.destroy(); tts.stop(); tts.shutdown(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}