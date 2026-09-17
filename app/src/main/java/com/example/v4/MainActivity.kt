package com.example.v4

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
            speak(if (language == "bn-BD") "বাংলা ভাষা চালু হয়েছে" else "English language enabled")
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is not available")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText.text = "শুনছি..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText.text = "প্রসেস করছি..." }
            override fun onError(error: Int) { statusText.text = "আবার চেষ্টা করুন" }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                statusText.text = text
                handleCommand(text.lowercase(Locale.getDefault()))
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    private fun handleCommand(command: String) {
        when {
            command.contains("সময়") || command.contains("time") -> speak(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()))
            command.contains("তারিখ") || command.contains("date") -> speak(SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()))
            command.contains("youtube") -> startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com")))
            command.contains("google") -> startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")))
            command.contains("কল") || command.contains("call") -> startActivity(Intent(Intent.ACTION_DIAL))
            else -> speak(if (language == "bn-BD") "আমি বুঝতে পারিনি" else "I did not understand")
        }
    }

    private fun speak(text: String) {
        tts.language = if (language == "bn-BD") Locale("bn", "BD") else Locale.US
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "V4_REPLY")
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) speak("V4 প্রস্তুত") }
    override fun onDestroy() { recognizer?.destroy(); tts.stop(); tts.shutdown(); super.onDestroy() }
}
