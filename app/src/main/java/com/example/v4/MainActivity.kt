package com.example.v4

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import android.animation.ObjectAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var statusPill: TextView
    private lateinit var listenButton: Button
    private lateinit var wakeButton: Button
    private lateinit var languageButton: Button
    private lateinit var settingsButton: Button
    private lateinit var jarvisOrb: View
    private var orbAnimator: ObjectAnimator? = null
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var language = "bn-BD"
    private var wakeRunning = false
    private val speechRequestCode = 4001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusPill = findViewById(R.id.statusPill)
        listenButton = findViewById(R.id.listenButton)
        wakeButton = findViewById(R.id.wakeButton)
        languageButton = findViewById(R.id.languageButton)
        settingsButton = findViewById(R.id.settingsButton)
        jarvisOrb = findViewById(R.id.jarvisOrb)

        startOrbAnimation()
        tts = TextToSpeech(this, this)

        listenButton.setOnClickListener { startListening() }
        wakeButton.setOnClickListener { toggleWakeService() }
        settingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        languageButton.setOnClickListener {
            language = if (language == "bn-BD") "en-US" else "bn-BD"
            statusText.text = if (language == "bn-BD") "বাংলা মোড" else "English mode"
            applyFemaleVoice()
            speak(if (language == "bn-BD") "বাংলা ভাষা চালু হয়েছে" else "English language enabled")
        }

        requestNeededPermissions()
        startWakeServiceIfAllowed()
    }

    private fun startOrbAnimation() {
        orbAnimator?.cancel()
        // Scale pulse
        ObjectAnimator.ofFloat(jarvisOrb, View.SCALE_X, 0.92f, 1.08f).apply {
            duration = 1800
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(jarvisOrb, View.SCALE_Y, 0.92f, 1.08f).apply {
            duration = 1800
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        // Soft alpha breathe
        ObjectAnimator.ofFloat(jarvisOrb, View.ALPHA, 0.75f, 1f).apply {
            duration = 2200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 10)
        } else {
            startWakeServiceIfAllowed()
        }
    }

    private fun startWakeServiceIfAllowed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, V4VoiceService::class.java).apply { action = V4VoiceService.ACTION_START }
            )
            wakeRunning = true
            wakeButton.text = "⏹  বন্ধ করুন"
            statusPill.text = "● Listening"
            statusText.text = "Hey JARVIS বলুন"
        } catch (_: Exception) {
            statusText.text = "চালু করা যায়নি"
            statusPill.text = "● Offline"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startWakeServiceIfAllowed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!wakeRunning) startWakeServiceIfAllowed()
    }

    private fun toggleWakeService() {
        if (wakeRunning) {
            stopService(Intent(this, V4VoiceService::class.java))
            wakeRunning = false
            wakeButton.text = "🔊  Hey JARVIS চালু"
            statusPill.text = "● Off"
            statusText.text = "বন্ধ আছে"
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNeededPermissions()
                return
            }
            ContextCompat.startForegroundService(
                this,
                Intent(this, V4VoiceService::class.java).apply { action = V4VoiceService.ACTION_START }
            )
            wakeRunning = true
            wakeButton.text = "⏹  বন্ধ করুন"
            statusPill.text = "● Listening"
            statusText.text = "Hey JARVIS বলুন"
            speak(if (language == "bn-BD") "JARVIS চালু করেছি" else "JARVIS is on")
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (language == "bn-BD") "বলুন..." else "Speak...")
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                statusText.text = "ভয়েস ইনপুট..."
                startActivityForResult(intent, speechRequestCode)
            } catch (_: ActivityNotFoundException) {
                statusText.text = "স্পিচ সার্ভিস নেই"
                speak(if (language == "bn-BD") "স্পিচ রিকগনিশন পাওয়া যাচ্ছে না" else "No speech recognition")
            }
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
                    SpeechRecognizer.ERROR_NO_MATCH -> "বোঝা যায়নি, আবার বলুন"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কিছু শোনা যায়নি"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "নেটওয়ার্ক সমস্যা"
                    else -> "আবার চেষ্টা করুন"
                }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                statusText.text = text.ifBlank { "কিছু শোনা যায়নি" }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        statusText.text = "শুরু হচ্ছে..."
        recognizer?.startListening(intent)
    }

    @Deprecated("Kept for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != speechRequestCode || resultCode != RESULT_OK) return
        val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        statusText.text = text.ifBlank { "কিছু শোনা যায়নি" }
    }

    /** Force a female TTS voice whenever possible. */
    private fun applyFemaleVoice() {
        try {
            val targetLang = if (language == "bn-BD") Locale("bn", "BD") else Locale.US
            tts.language = targetLang

            val voices = tts.voices ?: return
            val femaleKeywords = listOf("female", "woman", "girl", "fem", "fema")

            // 1) Same language + female
            val femaleSameLang = voices.firstOrNull { v ->
                v.locale.language == targetLang.language &&
                    femaleKeywords.any { k -> v.name.contains(k, true) }
            }
            // 2) Any female
            val femaleAny = voices.firstOrNull { v ->
                femaleKeywords.any { k -> v.name.contains(k, true) }
            }
            // 3) Prefer higher quality
            val best = femaleSameLang ?: femaleAny
            if (best != null) {
                tts.voice = best
            }

            // Slightly higher pitch for feminine feel if only one voice available
            tts.setPitch(1.08f)
            tts.setSpeechRate(0.95f)
        } catch (_: Exception) {}
    }

    private fun speak(text: String) {
        applyFemaleVoice()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_REPLY")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyFemaleVoice()
        }
    }

    override fun onDestroy() {
        orbAnimator?.cancel()
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
