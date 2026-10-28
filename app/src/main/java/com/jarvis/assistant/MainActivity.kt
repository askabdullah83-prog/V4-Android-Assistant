package com.jarvis.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var mic: Button
    private lateinit var orb: TextView
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var bengali = true
    private var animation: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 12, 28)
        window.navigationBarColor = Color.rgb(5, 12, 28)
        tts = TextToSpeech(this, this)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(5, 12, 28), Color.rgb(8, 28, 52), Color.rgb(5, 12, 28)))
        }
        setContentView(root)
        root.addView(label("J A R V I S", 25f, Color.rgb(100, 220, 255), true), params(52))
        root.addView(label("PERSONAL AI • VOICE INTERFACE", 10f, Color.rgb(120, 158, 188)), params(25))
        orb = TextView(this).apply {
            text = "✦"; textSize = 68f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(95, 225, 255))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(12, 45, 72))
                setStroke(dp(2), Color.rgb(72, 190, 245))
            }
            elevation = dp(12).toFloat()
        }
        val wrap = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(16)) }
        wrap.addView(orb, LinearLayout.LayoutParams(dp(168), dp(168)))
        root.addView(wrap, params(210))
        status = label("SYSTEM READY • সিস্টেম প্রস্তুত", 12f, Color.rgb(110, 235, 190))
        root.addView(status, params(32))
        transcript = label("হ্যালো! আমি JARVIS।\nমাইক্রোফোনে চাপ দিয়ে কথা বলো।", 16f, Color.WHITE)
        transcript.setPadding(dp(12), dp(16), dp(12), dp(16))
        val scroll = ScrollView(this).apply {
            background = rounded(Color.rgb(10, 30, 50), 18, Color.rgb(27, 75, 103))
            addView(transcript)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        })
        mic = button("🎙  কথা বলুন / TAP TO SPEAK", true)
        mic.setOnClickListener { listen() }
        root.addView(mic, params(58))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val language = button("বাংলা / EN", false)
        language.setOnClickListener {
            bengali = !bengali
            language.text = if (bengali) "Language: বাংলা" else "Language: English"
            speak(if (bengali) "বাংলা ভাষা নির্বাচন করা হয়েছে" else "English selected")
        }
        val settings = button("⚙ Settings", false)
        settings.setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        row.addView(language, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        row.addView(settings, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(row, params(56))
        root.addView(label("BENGALI + ENGLISH • VOICE COMMANDS", 9f, Color.rgb(95, 130, 155)), params(24))
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            respond("এই ফোনে speech recognition পাওয়া যাচ্ছে না। Google speech service চালু করো।"); return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101); return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "LISTENING • শুনছি…"; mic.text = "● শুনছি…"
                animation?.cancel()
                animation = ObjectAnimator.ofFloat(orb, View.ROTATION, 0f, 360f).apply {
                    duration = 5000; repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator(); start()
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "PROCESSING…" }
            override fun onError(error: Int) {
                animation?.cancel(); status.text = "READY • আবার চেষ্টা করো"
                mic.text = "🎙  কথা বলুন / TAP TO SPEAK"
                transcript.text = "কথা বুঝতে পারিনি। আবার মাইক্রোফোনে চাপ দাও।"
            }
            override fun onResults(results: Bundle?) {
                animation?.cancel(); mic.text = "🎙  কথা বলুন / TAP TO SPEAK"
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (phrase.isBlank()) respond("আমি শুনতে পাইনি। আরেকবার বলো।")
                else { transcript.text = "YOU / তুমি\n" + phrase; command(phrase) }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (bengali) "bn-BD" else "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { respond("ভয়েস ইনপুট চালু করা যায়নি।") }
    }

    private fun command(input: String) {
        val q = input.lowercase(Locale.ROOT)
        val answer: String = when {
            q.contains("সময়") || q.contains("time") ->
                (if (bengali) "এখন সময় " else "The time is ") + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            q.contains("তারিখ") || q.contains("date") || q.contains("আজ") ->
                (if (bengali) "আজ " else "Today is ") + SimpleDateFormat("EEEE, dd MMMM yyyy", if (bengali) Locale("bn", "BD") else Locale.US).format(Date())
            q.contains("ব্যাটারি") || q.contains("battery") -> {
                val level = (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (bengali) "তোমার ব্যাটারি " + level + "% আছে।" else "Your battery is at " + level + " percent."
            }
            q.contains("সেটিংস") || q.contains("settings") -> {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                if (bengali) "সেটিংস খুলছি।" else "Opening Settings."
            }
            q.contains("ক্যামেরা") || q.contains("camera") -> {
                try { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) } catch (_: Exception) {}
                if (bengali) "ক্যামেরা খুলছি।" else "Opening camera."
            }
            q.contains("youtube") || q.contains("ইউটিউব") -> {
                web("https://www.youtube.com")
                if (bengali) "YouTube খুলছি।" else "Opening YouTube."
            }
            q.contains("সার্চ") || q.contains("search") || q.contains("খুঁজে") -> {
                web("https://www.google.com/search?q=" + java.net.URLEncoder.encode(input, "UTF-8"))
                if (bengali) "ওয়েবে সার্চ করছি।" else "Searching the web."
            }
            q.contains("খোলো") || q.contains("open ") || q.contains("চালু কর") -> openApp(input)
            q.contains("হ্যালো") || q.contains("hello") || q.contains("হাই") || q.contains("hi") ->
                if (bengali) "হ্যালো! কীভাবে সাহায্য করতে পারি?" else "Hello! How can I help?"
            q.contains("তোমার নাম") || q.contains("your name") ->
                if (bengali) "আমার নাম JARVIS, তোমার ভয়েস অ্যাসিস্ট্যান্ট।" else "I'm JARVIS, your voice assistant."
            else -> if (bengali) "আমি শুনেছি: " + input + "। সময়, তারিখ, ব্যাটারি, সেটিংস, ক্যামেরা বা অ্যাপ খোলার নির্দেশ দিতে পারো।"
                    else "I heard: " + input + ". Try time, date, battery, settings, camera, web search, or opening an app."
        }
        respond(answer)
    }

    private fun openApp(input: String): String {
        val name = input.lowercase(Locale.ROOT).replace("খোলো", "").replace("চালু কর", "")
            .replace("open", "").replace("অ্যাপ", "").replace("app", "").trim()
        val pm = packageManager
        val match = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA).firstOrNull { app ->
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                label.contains(name) && pm.getLaunchIntentForPackage(app.packageName) != null
            }
        } catch (_: Exception) { null }
        if (match != null) {
            val label = pm.getApplicationLabel(match).toString()
            pm.getLaunchIntentForPackage(match.packageName)?.let { startActivity(it) }
            return if (bengali) label + " অ্যাপ খুলছি।" else "Opening " + label + "."
        }
        return if (bengali) "‘" + name + "’ নামে অ্যাপ খুঁজে পাইনি।" else "Couldn't find an app named '" + name + "'."
    }

    private fun web(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
    }
    private fun respond(text: String) {
        transcript.text = "JARVIS / জার্ভিস\n" + text
        status.text = "RESPONSE READY • উত্তর প্রস্তুত"
        mic.text = "🎙  কথা বলুন / TAP TO SPEAK"
        speak(text)
    }
    private fun speak(text: String) {
        if (::tts.isInitialized) {
            val language = if (bengali) Locale("bn", "BD") else Locale.US
            val result = tts.setLanguage(language)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts.language = Locale.US
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
        }
    }
    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private fun button(textLabel: String, primary: Boolean) = Button(this).apply {
        text = textLabel; textSize = if (primary) 13f else 11f; isAllCaps = false; setTextColor(Color.WHITE)
        background = rounded(if (primary) Color.rgb(0, 126, 174) else Color.rgb(15, 45, 66), 16,
            if (primary) Color.rgb(92, 225, 255) else Color.rgb(46, 113, 146))
        stateListAnimator = null
    }
    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun params(height: Int) = LinearLayout.LayoutParams(-1, dp(height))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onInit(code: Int) { if (code == TextToSpeech.SUCCESS) tts.language = Locale("bn", "BD") }
    override fun onDestroy() {
        animation?.cancel(); recognizer?.destroy()
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}