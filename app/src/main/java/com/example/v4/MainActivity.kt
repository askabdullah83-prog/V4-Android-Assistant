package com.example.v4

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var statusText: TextView
    private lateinit var listenButton: Button
    private lateinit var languageButton: Button
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var language = "bn-BD"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        listenButton = findViewById(R.id.listenButton)
        languageButton = findViewById(R.id.languageButton)
        tts = TextToSpeech(this, this)

        listenButton.setOnClickListener { startListening() }
        languageButton.setOnClickListener {
            language = if (language == "bn-BD") "en-US" else "bn-BD"
            statusText.text = if (language == "bn-BD") "বাংলা মোড" else "English mode"
            speak(if (language == "bn-BD") "বাংলা ভাষা চালু হয়েছে" else "English language enabled")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "Speech Recognition service পাওয়া যাচ্ছে না"
            speak(if (language == "bn-BD") "স্পিচ রিকগনিশন সার্ভিস পাওয়া যাচ্ছে না" else "Speech recognition service is not available")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText.text = "শুনছি..." }
            override fun onBeginningOfSpeech() { statusText.text = "বলুন..." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText.text = "প্রসেস করছি..." }

            override fun onError(error: Int) {
                statusText.text = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "অডিও সমস্যা"
                    SpeechRecognizer.ERROR_CLIENT -> "অ্যাপ থেকে রিকগনিশন শুরু করা যায়নি"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোন অনুমতি নেই"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "নেটওয়ার্ক সমস্যা"
                    SpeechRecognizer.ERROR_NO_MATCH -> "কথা বোঝা যায়নি, আবার বলুন"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "স্পিচ সার্ভিস ব্যস্ত, আবার চেষ্টা করুন"
                    SpeechRecognizer.ERROR_SERVER -> "স্পিচ সার্ভার সমস্যা"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কোনো কথা শোনা যায়নি"
                    else -> "স্পিচ রিকগনিশন সমস্যা ($error)"
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                statusText.text = text.ifBlank {
                    if (language == "bn-BD") "কিছু শোনা যায়নি" else "Nothing heard"
                }
                if (text.isNotBlank()) handleCommand(text.trim())
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        statusText.text = "শুরু হচ্ছে..."
        recognizer?.startListening(intent)
    }

    private fun handleCommand(originalCommand: String) {
        val command = originalCommand.lowercase(Locale.getDefault()).trim()

        when {
            command.contains("সময়") || command.contains("সময়") ||
            command.contains("কয়টা বাজে") || command.contains("কয়টা বাজে") ||
            command.contains("সময় কত") || command.contains("সময় কত") ||
            command.contains("time") -> {
                val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                speak(if (language == "bn-BD") "এখন সময় $now" else "The time is $now")
            }

            command.contains("তারিখ") || command.contains("আজ কত তারিখ") ||
            command.contains("date") || command.contains("today") -> {
                val today = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
                speak(if (language == "bn-BD") "আজ $today" else "Today is $today")
            }

            command.contains("youtube") || command.contains("ইউটিউব") -> {
                speak(if (language == "bn-BD") "ইউটিউব খুলছি" else "Opening YouTube")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
            }

            command.contains("google") || command.contains("গুগল") -> {
                speak(if (language == "bn-BD") "গুগল খুলছি" else "Opening Google")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
            }

            command.contains("কল") || command.contains("ফোন কর") || command.contains("call") -> {
                speak(if (language == "bn-BD") "কল করার জন্য ডায়ালার খুলছি" else "Opening the dialer")
                startActivity(Intent(Intent.ACTION_DIAL))
            }

            command.contains("হ্যালো") || command.contains("হাই") ||
            command.contains("hello") || command.contains("hi") -> {
                speak(if (language == "bn-BD") "হ্যালো! আমি V4। কী করতে পারি?" else "Hello! I am V4. How can I help?")
            }

            command.contains("তোমার নাম") || command.contains("নাম কি") ||
            command.contains("নাম কী") || command.contains("your name") -> {
                speak(if (language == "bn-BD") "আমার নাম V4" else "My name is V4")
            }

            command.contains("কেমন আছ") || command.contains("কেমন আছেন") ||
            command.contains("how are you") -> {
                speak(if (language == "bn-BD") "আমি ভালো আছি। ধন্যবাদ!" else "I am fine. Thank you!")
            }

            command.contains("ধন্যবাদ") || command.contains("thank you") ||
            command.contains("thanks") -> {
                speak(if (language == "bn-BD") "আপনাকেও ধন্যবাদ" else "You're welcome")
            }

            else -> {
                val queryUrl = "https://www.google.com/search?q=" + Uri.encode(originalCommand)
                speak(if (language == "bn-BD") "এই বিষয়ে গুগলে খুঁজে দিচ্ছি" else "I will search Google for that")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(queryUrl)))
            }
        }
    }

    private fun speak(text: String) {
        tts.language = if (language == "bn-BD") Locale("bn", "BD") else Locale.US
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "V4_REPLY")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("bn", "BD")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}