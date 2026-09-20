package com.example.v4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class V4VoiceService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START = "com.example.v4.START"
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 404
    }

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var waitingForCommand = false
    private var recognitionRunning = false
    private var ttsReady = false
    private var listeningForWake = false
    private var wakeDetectedInPartial = false
    private val handler = Handler()
    private val commandLanguage = "bn-BD"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        tts = TextToSpeech(this, this)
        handler.postDelayed({ startRecognition() }, 1200)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS Voice", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("JARVIS Active")
        .setContentText("Home Screen direct listening — যেকোনো কমান্ড বলুন")
        .setSmallIcon(R.drawable.ic_v4)
        .setOngoing(true)
        .build()

    private fun startRecognition() {
        if (recognitionRunning) return
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
                recognitionRunning = false
                if (waitingForCommand && error == SpeechRecognizer.ERROR_NO_MATCH) {
                    waitingForCommand = false
                    speak("দুঃখিত, কমান্ডটি শুনতে পারিনি।")
                }
                handler.postDelayed({ startRecognition() }, 700)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (listeningForWake && !wakeDetectedInPartial && isWakePhrase(normalize(partial))) {
                    wakeDetectedInPartial = true
                    recognitionRunning = false
                    try { recognizer?.cancel() } catch (_: Exception) {}
                    handler.post { handleVoice(partial) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                recognitionRunning = false
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, commandLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, commandLanguage)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        try {
            recognitionRunning = true
            wakeDetectedInPartial = false
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            recognitionRunning = false
            handler.postDelayed({ startRecognition() }, 1000)
        }
    }

    private fun handleVoice(text: String) {
        val command = normalize(text)

        if (command.isBlank()) {
            handler.postDelayed({ startRecognition() }, 400)
            return
        }

        if (isWakePhrase(command)) {
            val remaining = removeWakePhrase(command)
            if (remaining.isNotBlank()) {
                executeCommand(remaining)
            } else {
                waitingForCommand = true
                listeningForWake = false
                speak("জি, বলুন")
                waitForTtsThenListen()
            }
            return
        }

        waitingForCommand = false
        executeCommand(text)
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.getDefault())
            .replace("৪", "4")
            .replace("জার্ভিস", "jarvis")
            .replace("অ্যাকটিভ", "active")
            .replace("অ্যাক্টিভ", "active")
            .replace("এক্টিভ", "active")
            .replace("একটিভ", "active")
            .replace("অ্যাক্টিভেট", "active")
            .replace("অ্যাক্টিভেটেড", "active")
            .replace("active v for", "active jarvis")
            .replace("active before", "active jarvis")
            .replace("active be four", "active jarvis")
            .replace("active b4", "active jarvis")
            .replace("active v", "active jarvis")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isWakePhrase(command: String): Boolean =
        command.contains("active jarvis") || command.contains("activejarvis")

    private fun removeWakePhrase(command: String): String =
        command.replace("active jarvis", "").replace("activejarvis", "").trim()

    private fun waitForTtsThenListen() {
        if (!ttsReady) {
            handler.postDelayed({ startRecognition() }, 1200)
            return
        }
        handler.postDelayed({ startRecognition() }, 1800)
    }

    private fun executeCommand(original: String) {
        val command = normalize(original)

        if (command.isBlank()) {
            speak("কমান্ডটি বুঝতে পারিনি। আবার বলুন।")
            handler.postDelayed({ startRecognition() }, 1200)
            return
        }

        when {
            command.contains("home") || command.contains("হোম") ||
                command.contains("হোমে যাও") || command.contains("হোম স্ক্রিন") -> {
                speak("হোম স্ক্রিনে যাচ্ছি")
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            command.contains("volume up") || command.contains("ভলিউম বাড়াও") ||
                command.contains("ভলিউম বাড়াও") || command.contains("শব্দ বাড়াও") ||
                command.contains("শব্দ বাড়াও") -> {
                adjustVolume(AudioManager.ADJUST_RAISE)
                speak("ভলিউম বাড়িয়েছি")
            }
            command.contains("volume down") || command.contains("ভলিউম কমাও") ||
                command.contains("শব্দ কমাও") -> {
                adjustVolume(AudioManager.ADJUST_LOWER)
                speak("ভলিউম কমিয়েছি")
            }
            command.contains("mute") || command.contains("মিউট") ||
                command.contains("নিরব করো") || command.contains("নীরব করো") -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                speak("ফোন মিউট করেছি")
            }
            command.contains("unmute") || command.contains("আনমিউট") ||
                command.contains("শব্দ চালু") -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
                speak("শব্দ চালু করেছি")
            }
            command.contains("সময়") || command.contains("সময়") ||
                command.contains("কয়টা বাজে") || command.contains("কয়টা বাজে") ||
                command.contains("ঘড়ি") || command.contains("ঘড়ি") ||
                command.contains("time") || command.contains("what time") -> {
                val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                speak("এখন সময় $now")
            }
            command.contains("তারিখ") || command.contains("আজ কত তারিখ") ||
                command.contains("আজকের তারিখ") || command.contains("date") ||
                command.contains("today") -> {
                speak("আজ " + SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()))
            }
            command.contains("ব্যাটারি") || command.contains("battery") ||
                command.contains("চার্জ কত") || command.contains("charge") -> {
                val level = (getSystemService(BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("ব্যাটারি $level শতাংশ")
            }
            command.contains("wifi") || command.contains("ওয়াইফাই") ||
                command.contains("ওয়াইফাই") -> {
                speak("ওয়াইফাই সেটিংস খুলছি")
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("bluetooth") || command.contains("ব্লুটুথ") -> {
                speak("ব্লুটুথ সেটিংস খুলছি")
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("camera") || command.contains("ক্যামেরা") -> {
                speak("ক্যামেরা খুলছি")
                startActivity(Intent("android.media.action.IMAGE_CAPTURE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("settings") || command.contains("সেটিংস") -> {
                speak("সেটিংস খুলছি")
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("alarm") || command.contains("অ্যালার্ম") ||
                command.contains("এলার্ম") -> {
                speak("অ্যালার্ম সেট করার স্ক্রিন খুলছি")
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            // Phone / communication apps
            command.contains("call") || command.contains("কল") ||
                command.contains("ফোন") || command.contains("phone") -> {
                speak("ফোন খুলছি")
                startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("whatsapp") || command.contains("হোয়াটসঅ্যাপ") ||
                command.contains("হোয়াটসঅ্যাপ") -> openAppAny(
                listOf("com.whatsapp", "com.whatsapp.w4b"), "WhatsApp"
            )
            command.contains("messenger") || command.contains("massenger") ||
                command.contains("মেসেঞ্জার") -> openAppAny(
                listOf("com.facebook.orca"), "Messenger"
            )

            // Social apps
            command.contains("facebook lite") || command.contains("ফেসবুক লাইট") ||
                command.contains("facebooklight") || command.contains("ফেসবুকলাইট") -> openAppAny(
                listOf("com.facebook.lite"), "Facebook Lite"
            )
            command.contains("facebook") || command.contains("ফেসবুক") -> openAppAny(
                listOf("com.facebook.katana"), "Facebook"
            )
            command.contains("instagram") || command.contains("instageram") ||
                command.contains("ইনস্টাগ্রাম") -> openAppAny(
                listOf("com.instagram.android"), "Instagram"
            )
            command.contains("tiktok") || command.contains("tik tok") ||
                command.contains("টিকটক") -> openAppAny(
                listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "TikTok"
            )

            // Games
            command.contains("free fire") || command.contains("freefire") ||
                command.contains("ফ্রি ফায়ার") || command.contains("ফ্রি ফায়ার") -> openAppAny(
                listOf("com.dts.freefireth", "com.dts.freefiremax"), "Free Fire"
            )

            // Google / browser / gallery
            command.contains("chrome") || command.contains("ক্রোম") -> openAppAny(
                listOf("com.android.chrome"), "Chrome"
            )
            command == "google" || command == "গুগল" || command == "google খুলো" ||
                command == "গুগল খুলো" || command == "google open" -> openAppAny(
                listOf("com.google.android.googlequicksearchbox"), "Google"
            )
            command.contains("gallery") || command.contains("গ্যালারি") ||
                command.contains("ফটো") || command.contains("photos") -> openGallery()

            command.contains("youtube") || command.contains("ইউটিউব") -> {
                val search = extractAfter(command, listOf("youtube", "ইউটিউব"))
                if (search.isBlank() || search == "খুলো" || search == "খোল" || search == "open") {
                    openAppAny(listOf("com.google.android.youtube"), "YouTube", "https://www.youtube.com")
                } else {
                    openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(search),
                        "ইউটিউবে $search খুঁজে দিচ্ছি")
                }
            }
            command.contains("google") || command.contains("গুগল") -> {
                val search = extractAfter(command, listOf("google", "গুগল"))
                if (search.isBlank() || search == "খুলো" || search == "খোল" || search == "open") {
                    openAppAny(listOf("com.google.android.googlequicksearchbox"), "Google", "https://www.google.com")
                } else {
                    openUrl("https://www.google.com/search?q=" + Uri.encode(search),
                        "গুগলে $search খুঁজে দিচ্ছি")
                }
            }
            command.startsWith("search ") || command.startsWith("খুঁজে ") ||
                command.startsWith("সার্চ ") -> {
                val query = command.removePrefix("search ").removePrefix("খুঁজে ").removePrefix("সার্চ ").trim()
                if (query.isNotBlank()) {
                    openUrl("https://www.google.com/search?q=" + Uri.encode(query), "গুগলে খুঁজে দিচ্ছি")
                } else speak("কী খুঁজব বলুন")
            }
            command.contains("গান") || command.contains("music") ||
                command.contains("মিউজিক") || command.contains("ভিডিও") ||
                command.contains("video") || command.contains("play ") -> {
                val query = command.replace("গান", "").replace("music", "")
                    .replace("মিউজিক", "").replace("ভিডিও", "").replace("video", "")
                    .replace("play", "").trim()
                if (query.isBlank()) speak("কোন গান বা ভিডিও চালাব?")
                else openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(query),
                    "ইউটিউবে $query খুঁজে দিচ্ছি")
            }

            // JARVIS identity / greetings — use Bengali pronunciation so TTS does not spell the name.
            command.contains("হ্যালো") || command.contains("hello") ||
                command.contains("হাই") || command.contains("hi") ->
                speak("হ্যালো! আমি জার্ভিস। কী করতে পারি?")
            command.contains("তোমার নাম") || command.contains("নাম কি") ||
                command.contains("নাম কী") || command.contains("your name") ->
                speak("আমার নাম জার্ভিস")
            command.contains("কি করতে পারো") || command.contains("কী করতে পারো") ||
                command.contains("what can you do") || command.contains("help") ->
                speak("আমি হোম স্ক্রিনে যেতে, ভলিউম বাড়াতে কমাতে, মিউট করতে, সময় ও তারিখ বলতে, ব্যাটারি জানাতে, ইউটিউব ও গুগল খুলতে, অ্যাপ খুলতে, ক্যামেরা ও সেটিংস চালু করতে এবং ভয়েস কমান্ড বুঝতে পারি।")
            command.contains("কেমন আছ") || command.contains("কেমন আছেন") ||
                command.contains("how are you") -> speak("আমি ভালো আছি। আপনার কমান্ডের জন্য প্রস্তুত।")
            command.contains("ধন্যবাদ") || command.contains("thank you") ||
                command.contains("thanks") -> speak("আপনাকেও ধন্যবাদ")
            command.contains("জার্ভিস") || command.contains("jarvis") ->
                speak("জি, আমি জার্ভিস। কমান্ড দিন।")
            command.contains("বন্ধ করো") || command.contains("বন্ধ কর") ||
                command.contains("stop listening") -> {
                waitingForCommand = false
                listeningForWake = false
                speak("ঠিক আছে। Active JARVIS বন্ধ করছি।")
                stopSelf()
                return
            }
            else -> speak("দুঃখিত, এই কমান্ডটি এখনো বুঝতে পারিনি।")
        }

        listeningForWake = false
        handler.postDelayed({ startRecognition() }, 1200)
    }

    private fun adjustVolume(direction: Int) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
    }

    private fun extractAfter(command: String, keys: List<String>): String {
        for (key in keys) {
            val index = command.indexOf(key)
            if (index >= 0) {
                return command.substring(index + key.length)
                    .replace(Regex("^(খুলো|খোল|open|চালু করো|চালাও)\\s*"), "")
                    .trim()
            }
        }
        return ""
    }

    private fun openUrl(url: String, message: String) {
        speak(message)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openAppAny(
        packages: List<String>,
        name: String,
        fallbackUrl: String? = null
    ) {
        val intent = packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) {
            speak("$name খুলছি")
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (fallbackUrl != null) {
            openUrl(fallbackUrl, "$name খুলছি")
        } else {
            speak("$name ফোনে ইনস্টল নেই")
        }
    }

    private fun openGallery() {
        val packages = listOf(
            "com.google.android.apps.photos",
            "com.sec.android.gallery3d",
            "com.miui.gallery"
        )
        val intent = packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) {
            speak("গ্যালারি খুলছি")
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            speak("গ্যালারি খুলছি")
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        val utteranceId = if (text == "জি, বলুন") "JARVIS_WAKE_REPLY" else "JARVIS_REPLY"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ttsReady = true
        try { tts.language = Locale("bn", "BD") } catch (_: Exception) {}

        val voices = tts.voices.orEmpty()
        val femaleBengali = voices.firstOrNull {
            it.locale.language == "bn" &&
                (it.name.contains("female", true) || it.name.contains("fem", true) || it.name.contains("woman", true))
        }
        val femaleAny = voices.firstOrNull {
            it.name.contains("female", true) || it.name.contains("fem", true) || it.name.contains("woman", true)
        }
        try { tts.voice = femaleBengali ?: femaleAny ?: tts.voice } catch (_: Exception) {}

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "JARVIS_WAKE_REPLY" && waitingForCommand) handler.post { startRecognition() }
            }
            override fun onError(utteranceId: String?) {}
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        handler.postDelayed({
            try {
                startService(Intent(this, V4VoiceService::class.java).apply { action = ACTION_START })
            } catch (_: Exception) {}
        }, 1000)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognitionRunning = false
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}