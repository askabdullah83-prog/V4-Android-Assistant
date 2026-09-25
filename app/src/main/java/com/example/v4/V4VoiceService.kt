package com.example.v4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.util.Random

/** Siri-style continuous voice assistant. Can open/close ANY installed app by spoken name. */
class V4VoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.example.v4.START"
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 404
    }

    data class AppEntry(val label: String, val labelLower: String, val packageName: String)

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var waitingForCommand = false
    private var recognitionRunning = false
    private var ttsReady = false
    private var listeningForWake = false
    private var wakeDetectedInPartial = false
    private val handler = Handler(Looper.getMainLooper())
    private val commandLanguage = "bn-BD"
    private val random = Random()

    private var installedApps: List<AppEntry> = emptyList()
    private var appsLoadedAt = 0L

    private val wakeRepliesBn = listOf("জি, বলুন", "হ্যাঁ, শুনছি", "বলুন", "আমি শুনছি")

    private val appAliases = mapOf(
        "whatsapp" to listOf("হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ", "হোয়াটস এপ", "whats app"),
        "facebook" to listOf("ফেসবুক", "ফেইসবুক"),
        "messenger" to listOf("মেসেঞ্জার", "মেসেনজার"),
        "instagram" to listOf("ইনস্টাগ্রাম", "ইনস্টা"),
        "youtube" to listOf("ইউটিউব"),
        "chrome" to listOf("ক্রোম", "গুগল ক্রোম"),
        "tiktok" to listOf("টিকটক", "টিক টক"),
        "gmail" to listOf("জিমেইল", "ইমেইল"),
        "maps" to listOf("ম্যাপ", "গুগল ম্যাপ", "ম্যাপস"),
        "camera" to listOf("ক্যামেরা"),
        "gallery" to listOf("গ্যালারি", "ফটো", "photos"),
        "settings" to listOf("সেটিংস", "সেটিং"),
        "play store" to listOf("প্লে স্টোর", "প্লেস্টোর", "গুগল প্লে"),
        "free fire" to listOf("ফ্রি ফায়ার", "ফ্রি ফায়ার", "ফ্রিফায়ার"),
        "pubg" to listOf("পাবজি", "পাবজি মোবাইল"),
        "telegram" to listOf("টেলিগ্রাম"),
        "imo" to listOf("ইমো"),
        "pathao" to listOf("পাঠাও"),
        "uber" to listOf("উবার"),
        "spotify" to listOf("স্পটিফাই"),
        "netflix" to listOf("নেটফ্লিক্স"),
        "calculator" to listOf("ক্যালকুলেটর"),
        "calendar" to listOf("ক্যালেন্ডার"),
        "clock" to listOf("ঘড়ি", "ঘড়ি", "ক্লক"),
        "contacts" to listOf("কন্টাক্টস", "ফোনবুক"),
        "messages" to listOf("মেসেজ", "sms"),
        "phone" to listOf("ফোন", "ডায়ালার"),
        "files" to listOf("ফাইল", "ফাইল ম্যানেজার"),
        "browser" to listOf("ব্রাউজার")
    )

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        tts = TextToSpeech(this, this)
        loadInstalledApps()
        handler.postDelayed({ startRecognition() }, 1000)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS Voice", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("JARVIS")
        .setContentText("Hey JARVIS • সব অ্যাপ খোলা/বন্ধ করা যায়")
        .setSmallIcon(R.drawable.ic_v4)
        .setOngoing(true)
        .build()

    private fun loadInstalledApps() {
        try {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveList = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            installedApps = resolveList.mapNotNull { ri ->
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                AppEntry(label, label.lowercase(Locale.getDefault()), ri.activityInfo.packageName)
            }.distinctBy { it.packageName }.sortedBy { it.labelLower }
            appsLoadedAt = System.currentTimeMillis()
        } catch (_: Exception) {
            installedApps = emptyList()
        }
    }

    private fun ensureAppsLoaded() {
        if (System.currentTimeMillis() - appsLoadedAt > 5 * 60 * 1000L || installedApps.isEmpty()) {
            loadInstalledApps()
        }
    }

    private fun findAppBySpokenName(spoken: String): AppEntry? {
        ensureAppsLoaded()
        val query = spoken.lowercase(Locale.getDefault()).replace(Regex("[\\s\\-_.]+"), " ").trim()
        if (query.length < 2) return null

        installedApps.firstOrNull { it.labelLower == query }?.let { return it }
        installedApps.firstOrNull { it.labelLower.startsWith(query) }?.let { return it }

        for ((en, bnList) in appAliases) {
            val allNames = listOf(en) + bnList
            if (allNames.any { query.contains(it) || it.contains(query) }) {
                installedApps.firstOrNull { app ->
                    app.labelLower.contains(en) || bnList.any { app.labelLower.contains(it) }
                }?.let { return it }
            }
        }

        installedApps.firstOrNull { it.labelLower.contains(query) }?.let { return it }

        val queryWords = query.split(" ").filter { it.length > 1 }
        if (queryWords.isNotEmpty()) {
            val scored = installedApps.map { app ->
                val score = queryWords.count { w -> app.labelLower.contains(w) }
                app to score
            }.filter { it.second > 0 }.maxByOrNull { it.second }
            if (scored != null && scored.second >= 1) return scored.first
        }
        return null
    }

    private fun openAppByName(spokenName: String): Boolean {
        val app = findAppBySpokenName(spokenName) ?: return false
        val launch = packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        speakSiri("${app.label} খুলছি")
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

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
                    speakSiri("দুঃখিত, আমি শুনতে পাইনি। আবার বলবেন?")
                }
                handler.postDelayed({ startRecognition() }, 600)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
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
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) handler.postDelayed({ startRecognition() }, 350)
                else handleVoice(text)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, commandLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, commandLanguage)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 8)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            recognitionRunning = true
            wakeDetectedInPartial = false
            listeningForWake = true
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            recognitionRunning = false
            handler.postDelayed({ startRecognition() }, 900)
        }
    }

    private fun handleVoice(text: String) {
        val command = normalize(text)
        if (command.isBlank()) {
            handler.postDelayed({ startRecognition() }, 350)
            return
        }
        if (isWakePhrase(command)) {
            listeningForWake = false
            val remaining = removeWakePhrase(command)
            if (remaining.isNotBlank()) executeCommand(remaining)
            else {
                waitingForCommand = true
                listeningForWake = false
                speakSiri(wakeRepliesBn[random.nextInt(wakeRepliesBn.size)])
                waitForTtsThenListen()
            }
            return
        }
        waitingForCommand = false
        executeCommand(text)
    }

    private fun normalize(value: String): String = value.lowercase(Locale.getDefault())
        .replace("৪", "4").replace("জার্ভিস", "jarvis").replace("জারভিস", "jarvis")
        .replace("জার্ভিজ", "jarvis").replace("সিরি", "siri")
        .replace("অ্যাকটিভ", "active").replace("অ্যাক্টিভ", "active")
        .replace("এক্টিভ", "active").replace("একটিভ", "active")
        .replace("হ্যালো", "hello").replace("হাই", "hi").replace("ওহে", "hey")
        .replace("জি ", "ji ").replace(Regex("\\s+"), " ").trim()

    private fun isWakePhrase(command: String): Boolean {
        val c = command
        return c.contains("hey jarvis") || c.contains("hello jarvis") || c.contains("hi jarvis") ||
            c.contains("active jarvis") || c.contains("activejarvis") || c.contains("hey siri") ||
            c.contains("hello siri") || c.contains("ji jarvis") || c.startsWith("jarvis ") || c == "jarvis"
    }

    private fun removeWakePhrase(command: String): String {
        var result = command
        listOf("hey jarvis", "hello jarvis", "hi jarvis", "active jarvis", "activejarvis",
            "hey siri", "hello siri", "ji jarvis", "jarvis").forEach { result = result.replace(it, " ") }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun waitForTtsThenListen() {
        handler.postDelayed({ startRecognition() }, if (ttsReady) 1600 else 1100)
    }

    private fun executeCommand(original: String) {
        val command = normalize(original)
        if (command.isBlank()) {
            speakSiri("আমি বুঝতে পারিনি। আরেকবার বলবেন?")
            handler.postDelayed({ startRecognition() }, 1100)
            return
        }

        val openMatch = Regex(
            "(?:খোলো|খোল|খুলে দাও|চালু করো|চালু কর|open|launch|start)\\s+(.+)|(.+?)\\s+(?:খোলো|খোল|খুলে দাও|চালু করো|চালু কর|open)"
        ).find(command)
        if (openMatch != null) {
            val name = (openMatch.groupValues.getOrNull(1) ?: openMatch.groupValues.getOrNull(2) ?: "")
                .replace(Regex("^(the|একটা|একটি)\\s+"), "").trim()
            if (name.length >= 2 && openAppByName(name)) {
                handler.postDelayed({ startRecognition() }, 1400)
                return
            }
        }

        if ((command.length in 2..40) && (command.split(" ").size <= 3)) {
            if (openAppByName(command)) {
                handler.postDelayed({ startRecognition() }, 1400)
                return
            }
        }

        when {
            command.contains("home") || command.contains("হোম") || command.contains("হোমে যাও") || command.contains("হোম স্ক্রিন") -> {
                speakSiri("হোম স্ক্রিনে নিয়ে যাচ্ছি")
                JarvisAccessibilityService.goHome()
            }
            command.contains("বন্ধ করো") || command.contains("বন্ধ কর") || command.contains("বন্ধ করে দাও") ||
                command.contains("close app") || command.contains("close") || command.contains("exit app") ||
                command.contains("অ্যাপ বন্ধ") -> {
                if (JarvisAccessibilityService.closeCurrentApp()) speakSiri("ঠিক আছে, অ্যাপ বন্ধ করছি")
                else {
                    speakSiri("অ্যাপ বন্ধ করতে Accessibility অনুমতি চালু করুন")
                    try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                }
                handler.postDelayed({ startRecognition() }, 1400)
                return
            }
            command.contains("পেছনে") || command.contains("back") || command.contains("ফিরে যাও") -> {
                if (JarvisAccessibilityService.pressBack()) speakSiri("পেছনে যাচ্ছি")
                else speakSiri("Accessibility অনুমতি লাগবে")
            }
            command.contains("volume up") || command.contains("ভলিউম বাড়াও") || command.contains("ভলিউম বাড়াও") ||
                command.contains("শব্দ বাড়াও") || command.contains("শব্দ বাড়াও") || command.contains("আরও জোরে") -> {
                adjustVolume(AudioManager.ADJUST_RAISE); speakSiri("ভলিউম বাড়িয়ে দিলাম")
            }
            command.contains("volume down") || command.contains("ভলিউম কমাও") || command.contains("শব্দ কমাও") || command.contains("একটু কম") -> {
                adjustVolume(AudioManager.ADJUST_LOWER); speakSiri("ভলিউম কমিয়ে দিলাম")
            }
            command.contains("mute") || command.contains("মিউট") || command.contains("নিরব করো") || command.contains("নীরব করো") -> {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustVolume(AudioManager.ADJUST_MUTE, 0)
                speakSiri("ফোন মিউট করে দিলাম")
            }
            command.contains("unmute") || command.contains("আনমিউট") || command.contains("শব্দ চালু") -> {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
                speakSiri("শব্দ আবার চালু করেছি")
            }
            command.contains("সময়") || command.contains("সময়") || command.contains("কয়টা বাজে") || command.contains("কয়টা বাজে") ||
                command.contains("ঘড়ি") || command.contains("ঘড়ি") || command.contains("time") || command.contains("what time") -> {
                speakSiri("এখন সময় " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()))
            }
            command.contains("তারিখ") || command.contains("আজ কত তারিখ") || command.contains("আজকের তারিখ") ||
                command.contains("date") || command.contains("today") || command.contains("কী তারিখ") -> {
                speakSiri("আজ " + SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("bn", "BD")).format(Date()))
            }
            command.contains("ব্যাটারি") || command.contains("battery") || command.contains("চার্জ কত") || command.contains("charge") -> {
                val level = (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val msg = when {
                    level >= 80 -> "ব্যাটারি $level শতাংশ। চার্জ ভালো আছে।"
                    level >= 30 -> "ব্যাটারি $level শতাংশ।"
                    else -> "ব্যাটারি $level শতাংশ। চার্জ দেওয়ার সময় হয়েছে।"
                }
                speakSiri(msg)
            }
            command.contains("wifi") || command.contains("ওয়াইফাই") || command.contains("ওয়াইফাই") -> {
                speakSiri("ওয়াইফাই সেটিংস খুলছি")
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("bluetooth") || command.contains("ব্লুটুথ") -> {
                speakSiri("ব্লুটুথ সেটিংস খুলছি")
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("camera") || command.contains("ক্যামেরা") -> {
                speakSiri("ক্যামেরা খুলছি")
                startActivity(Intent("android.media.action.IMAGE_CAPTURE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("settings") || command.contains("সেটিংস") -> {
                speakSiri("সেটিংস খুলছি")
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("alarm") || command.contains("অ্যালার্ম") || command.contains("এলার্ম") -> {
                speakSiri("অ্যালার্ম সেট করার স্ক্রিন খুলছি")
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("call") || command.contains("কল") || command.contains("ফোন") || command.contains("phone") || command.contains("ডায়াল") -> {
                speakSiri("ফোন ডায়ালার খুলছি")
                startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            command.contains("whatsapp") || command.contains("হোয়াটসঅ্যাপ") || command.contains("হোয়াটসঅ্যাপ") ->
                openAppAny(listOf("com.whatsapp", "com.whatsapp.w4b"), "WhatsApp")
            command.contains("messenger") || command.contains("মেসেঞ্জার") ->
                openAppAny(listOf("com.facebook.orca"), "Messenger")
            command.contains("facebook lite") || command.contains("ফেসবুক লাইট") ->
                openAppAny(listOf("com.facebook.lite"), "Facebook Lite")
            command.contains("facebook") || command.contains("ফেসবুক") ->
                openAppAny(listOf("com.facebook.katana"), "Facebook")
            command.contains("instagram") || command.contains("ইনস্টাগ্রাম") ->
                openAppAny(listOf("com.instagram.android"), "Instagram")
            command.contains("tiktok") || command.contains("টিকটক") ->
                openAppAny(listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "TikTok")
            command.contains("free fire") || command.contains("ফ্রি ফায়ার") || command.contains("ফ্রি ফায়ার") ->
                openAppAny(listOf("com.dts.freefireth", "com.dts.freefiremax"), "Free Fire")
            command.contains("chrome") || command.contains("ক্রোম") ->
                openAppAny(listOf("com.android.chrome"), "Chrome")
            command.contains("gallery") || command.contains("গ্যালারি") || command.contains("ফটো") || command.contains("photos") -> openGallery()
            command.contains("youtube") || command.contains("ইউটিউব") -> {
                val search = extractAfter(command, listOf("youtube", "ইউটিউব"))
                if (search.isBlank() || search in listOf("খুলো", "খোল", "open", "খুলে দাও"))
                    openAppAny(listOf("com.google.android.youtube"), "YouTube", "https://www.youtube.com")
                else openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(search), "ইউটিউবে $search খুঁজে দিচ্ছি")
            }
            command.contains("google") || command.contains("গুগল") -> {
                val search = extractAfter(command, listOf("google", "গুগল"))
                if (search.isBlank() || search in listOf("খুলো", "খোল", "open"))
                    openAppAny(listOf("com.google.android.googlequicksearchbox"), "Google", "https://www.google.com")
                else openUrl("https://www.google.com/search?q=" + Uri.encode(search), "গুগলে $search খুঁজে দিচ্ছি")
            }
            command.startsWith("search ") || command.startsWith("খুঁজে ") || command.startsWith("সার্চ ") || command.startsWith("search for ") -> {
                val query = command.removePrefix("search for ").removePrefix("search ").removePrefix("খুঁজে ").removePrefix("সার্চ ").trim()
                if (query.isNotBlank()) openUrl("https://www.google.com/search?q=" + Uri.encode(query), "গুগলে খুঁজে দিচ্ছি")
                else speakSiri("কী খুঁজব বলুন")
            }
            command.contains("weather") || command.contains("আবহাওয়া") || command.contains("আবহাওয়া") ||
                command.contains("বৃষ্টি") || command.contains("তাপমাত্রা") ->
                openUrl("https://www.google.com/search?q=" + Uri.encode("আজকের আবহাওয়া"), "আজকের আবহাওয়া দেখাচ্ছি")
            command.contains("হ্যালো") || command.contains("hello") || command.contains("hi") || command.contains("হাই") ->
                speakSiri(listOf("হ্যালো! আমি JARVIS। কীভাবে সাহায্য করতে পারি?", "হ্যাঁ, বলুন। আমি শুনছি।", "হ্যালো! আজ কী করতে চান?").random())
            command.contains("তোমার নাম") || command.contains("নাম কি") || command.contains("নাম কী") ||
                command.contains("your name") || command.contains("who are you") || command.contains("কে তুমি") ->
                speakSiri("আমার নাম JARVIS। আমি আপনার ব্যক্তিগত ভয়েস অ্যাসিস্ট্যান্ট। ফোনের সব অ্যাপ খুলতে ও বন্ধ করতে পারি।")
            command.contains("কেমন আছ") || command.contains("কেমন আছেন") || command.contains("how are you") ->
                speakSiri("আমি ভালো আছি, ধন্যবাদ! আপনি কেমন আছেন?")
            command.contains("ধন্যবাদ") || command.contains("thank you") || command.contains("thanks") || command.contains("শুকরিয়া") ->
                speakSiri(listOf("আপনাকেও ধন্যবাদ", "স্বাগতম", "কোনো সমস্যা নেই").random())
            command.contains("শুভ সকাল") || command.contains("good morning") -> speakSiri("শুভ সকাল! আজকের দিনটা সুন্দর কাটুক।")
            command.contains("শুভ রাত্রি") || command.contains("good night") -> speakSiri("শুভ রাত্রি। ভালো ঘুমাবেন।")
            command.contains("joke") || command.contains("জোক") || command.contains("মজার") || command.contains("হাসানো") ->
                speakSiri(listOf("কম্পিউটার কেন ঠান্ডা থাকে? কারণ সেদের অনেক উইন্ডোজ খোলা থাকে!", "প্রোগ্রামার কেন রাতে ঘুমায় না? কারণ বাগে ভয় পায়।", "মোবাইল ফোন কেন স্মার্ট? কারণ সে কখনো ভুলে যায় না কাকে কল করতে হবে।").random())
            command.contains("সাহায্য") || command.contains("help") || command.contains("কী কী করতে পার") || command.contains("what can you do") ->
                speakSiri("আমি ফোনের যেকোনো অ্যাপ নাম ধরে খুলতে পারি। বন্ধ করতে, হোমে যেতে, ভলিউম নিয়ন্ত্রণ করতে, সময় তারিখ ব্যাটারি বলতে পারি। গুগলে সার্চ করতে চাইলে স্পষ্ট করে বলুন। শুধু বলুন — Hey JARVIS।")
            else -> {
                // শুধু অ্যাপ খোলার চেষ্টা — গুগল সার্চ শুধু স্পষ্ট কমান্ডে
                if (!openAppByName(command)) {
                    speakSiri("দুঃখিত, এটা বুঝতে পারিনি। অন্যভাবে বলবেন?")
                }
            }
        }
        handler.postDelayed({ startRecognition() }, 1400)
    }

    private fun adjustVolume(direction: Int) {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun extractAfter(command: String, keywords: List<String>): String {
        for (key in keywords) {
            val idx = command.indexOf(key)
            if (idx >= 0) return command.substring(idx + key.length).replace(Regex("^(খুলো|খোল|open|খুলে দাও|তে)\\s*"), "").trim()
        }
        return ""
    }

    private fun openUrl(url: String, message: String) {
        speakSiri(message)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openAppAny(packages: List<String>, name: String, fallbackUrl: String? = null) {
        val intent = packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) {
            speakSiri("$name খুলছি")
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (fallbackUrl != null) openUrl(fallbackUrl, "$name খুলছি")
        else if (!openAppByName(name)) speakSiri("$name ফোনে ইনস্টল নেই")
    }

    private fun openGallery() {
        val packages = listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery")
        val intent = packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) {
            speakSiri("গ্যালারি খুলছি")
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (!openAppByName("gallery") && !openAppByName("photos")) {
            speakSiri("গ্যালারি খুলছি")
            startActivity(Intent(Intent.ACTION_VIEW).apply { type = "image/*"; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    private fun speakSiri(text: String) {
        if (!ttsReady) return
        val utteranceId = if (text in wakeRepliesBn) "JARVIS_WAKE_REPLY" else "JARVIS_REPLY"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ttsReady = true
        try { tts.language = Locale("bn", "BD") } catch (_: Exception) {}
        val voices = tts.voices.orEmpty()
        val femaleBengali = voices.firstOrNull {
            it.locale.language == "bn" && (it.name.contains("female", true) || it.name.contains("fem", true) || it.name.contains("woman", true))
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
            try { startService(Intent(this, V4VoiceService::class.java).apply { action = ACTION_START }) } catch (_: Exception) {}
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
