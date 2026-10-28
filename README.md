# JARVIS — Fresh Android Assistant

Native Kotlin starter for a Bengali + English Android voice assistant with a futuristic interface.

## Features
- SpeechRecognizer voice input and TextToSpeech responses
- Bengali/English language toggle
- Commands for time, date, battery, settings, camera, web search, and opening launchable apps by name
- GitHub Actions workflow to build an installable debug APK

## Build APK
1. Open the Actions tab.
2. Select **Build JARVIS APK** and click **Run workflow** on branch `jarvis-fresh`.
3. When the run succeeds, download the `JARVIS-debug-apk` artifact ZIP and extract the APK.

Microphone permission is required. Speech recognition availability depends on the phone's installed speech service. This is a functional starter, not a full Siri clone. Always-on wake-word listening and AI/image generation are not included; AI services need a secure backend and provider API key.