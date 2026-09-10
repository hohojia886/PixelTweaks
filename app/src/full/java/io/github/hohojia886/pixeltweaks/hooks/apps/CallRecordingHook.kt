package io.github.hohojia886.pixeltweaks.hooks.apps

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.TelephonyManager
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import org.luckypray.dexkit.DexKitBridge

/**
 * CallRecordingHook: Unlocks native call recording in Google Dialer and silences announcements.
 * Bypasses regional geo-fencing via Telephony ISO spoofing and dynamic DexKit method hijacking.
 * Mutes voice announcements using language-agnostic resource ID and time-window TTS interception.
 */
@SuppressLint("DiscouragedApi", "SoonBlockedPrivateApi")
object CallRecordingHook {

    private const val TAG = "CallRec"
    private const val CACHE_FILE = "call_rec_v1.cache" // Persistent cache for DexKit method locations

    // String keywords used by DexKit to locate internal boolean flag methods
    private val DEX_KEYWORDS = listOf(
        "canRecordCall", "Crosby", "GeoFence", "isCallRecordingCountry"
    )

    @Volatile private var isRecordingEnabled = false // Master toggle for call recording enablement
    @Volatile private var isSilenceEnabled = true // Master toggle for announcement silencing
    private var sessionRetryCount = 0 // Tracks background DexKit retry attempts
    
    @Volatile private var lastListener: WeakReference<UtteranceProgressListener>? = null // Captures Dialer's progress listener
    @Volatile private var lastResourceReadTime = 0L // Timestamp of recent call_recording_voice string resource read
    @Volatile private var startId = -1 // Cached resource ID for call_recording_starting_voice
    @Volatile private var endId = -1 // Cached resource ID for call_recording_ending_voice

    // Generates a minimal 44-byte silent WAV header to bypass TTS audio playback
    private fun buildSilentWav(sampleRate: Int = 8000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val dataSize = 0 
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM format
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        return buffer.array()
    }

    // Inspects current thread stack trace to verify if caller originates from Call Recording
    private fun isCallRecordingAnnouncementCaller(): Boolean {
        val stack = Thread.currentThread().stackTrace
        return stack.any {
            val cls = it.className
            cls.contains("CallRecording", true) ||
            cls.contains("AudioInjector", true) ||
            cls.contains("Crosby", true) ||
            cls.contains("callrecording", true) ||
            cls.contains("Fermat", true)
        }
    }

    // Synchronizes feature toggles from RemotePrefProvider with ContentProvider fallback
    private fun syncState(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
            var recordingEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_CALL_RECORDING, false)
            var silenceEnabled = prefs.getBoolean(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, true)
            
            // Fallback: Query ContentProvider DE storage
            runCatching {
                val ctx = IpcManager.getSafeContext(classLoader, "com.google.android.dialer") ?: IpcManager.getSystemContext(classLoader)
                if (ctx != null) {
                    val uri = Uri.parse("content://io.github.hohojia886.pixeltweaks")
                    val bundle = ctx.contentResolver.call(uri, "get", null, null)
                    if (bundle != null) {
                        recordingEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_CALL_RECORDING, false)
                        silenceEnabled = bundle.getBoolean(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, silenceEnabled)
                    }
                }
            }

            isRecordingEnabled = recordingEnabled
            isSilenceEnabled = silenceEnabled
            Logger.i(TAG, "Sync", "State synced: recording=$isRecordingEnabled, silence=$isSilenceEnabled")
            Logger.sync(module)
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to sync state via RemotePrefProvider", e)
        }
    }

    // Primary entry point for basic framework-level hooks (Telephony, Application, Resources, TTS)
    fun hook(module: XposedModule, classLoader: ClassLoader, packageName: String) {
        Logger.i(TAG, "Init", "Initializing CallRecording module")
        syncState(module, classLoader)
        val moduleUid = module.getModuleApplicationInfo().uid

        // Early synchronization for system-level processes
        if (Process.myUid() == 1000) {
            IpcManager.getSafeContext(classLoader, packageName)?.let { ctx ->
                registerReceiver(ctx, moduleUid)
                syncState(module, classLoader)
            }
        }

        try {
            // 1. Telephony ISO Hook: Spoof country to "us" to bypass regional recording restrictions
            val tm = TelephonyManager::class.java
            val isoInterceptor: (XposedInterface.Chain) -> Any? = { chain ->
                if (isRecordingEnabled) "us" else chain.proceed()
            }

            runCatching {
                module.hook(tm.getDeclaredMethod("getSimCountryIso")).intercept(isoInterceptor)
                module.hook(tm.getDeclaredMethod("getNetworkCountryIso")).intercept(isoInterceptor)
                module.hook(tm.getDeclaredMethod("getSimCountryIso", Int::class.javaPrimitiveType)).intercept(isoInterceptor)
                module.hook(tm.getDeclaredMethod("getNetworkCountryIso", Int::class.javaPrimitiveType)).intercept(isoInterceptor)
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Telephony ISO hooks failed", e)
            }

            // 2. Application Lifecycle: Register secure IPC broadcast receiver
            val appClass = classLoader.loadClass("android.app.Application")
            module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                val app = chain.thisObject as? Context
                if (app != null) {
                    registerReceiver(app, moduleUid)
                    syncState(module, classLoader)
                }
            }

            // 3. Resource Hook (getString): Language-agnostic muting of starting/ending announcement strings
            module.hook(Resources::class.java.getDeclaredMethod("getString", Int::class.java)).intercept { chain ->
                if (!isRecordingEnabled || !isSilenceEnabled) return@intercept chain.proceed()
                val res = chain.thisObject as Resources
                if (startId == -1) {
                    startId = res.getIdentifier("call_recording_starting_voice", "string", packageName)
                    endId = res.getIdentifier("call_recording_ending_voice", "string", packageName)
                }
                val resId = chain.args[0] as Int
                if (resId != 0 && (resId == startId || resId == endId)) {
                    lastResourceReadTime = SystemClock.uptimeMillis()
                    val name = if (resId == startId) "call_recording_starting_voice" else "call_recording_ending_voice"
                    Logger.i(TAG, "Active", "[Resource Mute] Intercepted getString($name, ID: $resId)")
                    ""
                } else chain.proceed()
            }

            // 4. Resource Hook (getText): Alternative CharSequence access point muting
            runCatching {
                module.hook(Resources::class.java.getDeclaredMethod("getText", Int::class.java)).intercept { chain ->
                    if (!isRecordingEnabled || !isSilenceEnabled) return@intercept chain.proceed()
                    val res = chain.thisObject as Resources
                    if (startId == -1) {
                        startId = res.getIdentifier("call_recording_starting_voice", "string", packageName)
                        endId = res.getIdentifier("call_recording_ending_voice", "string", packageName)
                    }
                    val resId = chain.args[0] as Int
                    if (resId != 0 && (resId == startId || resId == endId)) {
                        lastResourceReadTime = SystemClock.uptimeMillis()
                        val name = if (resId == startId) "call_recording_starting_voice" else "call_recording_ending_voice"
                        Logger.i(TAG, "Active", "[Resource Mute] Intercepted getText($name, ID: $resId)")
                        ""
                    } else chain.proceed()
                }
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Resources.getText hook failed", e)
            }

            hookTtsHooks(module) // Apply non-destructive TTS speech synthesis hooks
        } catch (e: Throwable) {
            Logger.e(TAG, "Error", "Framework hook application failed", e)
        }
    }

    // Entry point for full edition: Orchestrates DexKit bytecode scanning or cache loading
    fun hookFull(module: XposedModule, classLoader: ClassLoader, packageName: String, targetSourceDir: String?) {
        hook(module, classLoader, packageName)
        if (targetSourceDir == null) {
            Logger.e(TAG, "Error", "Cannot run DexKit: targetSourceDir is null")
            return
        }

        val cacheFile = File(module.getModuleApplicationInfo().dataDir, CACHE_FILE)
        val currentVersion = try {
            IpcManager.getSystemContext(classLoader)?.packageManager?.getPackageInfo(packageName, 0)?.longVersionCode ?: 0L
        } catch (_: Exception) { 0L }

        // Fast path: reuse cached method signatures if version matches
        if (loadFromCache(module, classLoader, cacheFile, currentVersion)) {
            Logger.i(TAG, "Hook", "Call recording flags applied from cache")
            return
        }

        // Slow path: scan APK bytecode in a background thread
        sessionRetryCount = 0
        Thread {
            while (sessionRetryCount < 3) {
                try {
                    Logger.i(TAG, "Hook", "Starting background DexKit scan (Attempt ${sessionRetryCount + 1})")
                    performDexKitScan(module, classLoader, targetSourceDir, cacheFile, currentVersion)
                    Logger.i(TAG, "Hook", "DexKit scan completed and hooks applied")
                    return@Thread
                } catch (e: Exception) {
                    sessionRetryCount++
                    Logger.e(TAG, "Error", "DexKit scan attempt failed", e)
                    if (sessionRetryCount < 3) Thread.sleep(2000)
                }
            }
        }.start()
    }

    // Uses DexKit to find and hook obfuscated recording boolean flags and locale provider methods
    private fun performDexKitScan(module: XposedModule, classLoader: ClassLoader, targetSourceDir: String, cacheFile: File, version: Long) {
        runCatching {
            val moduleLibDir = module.getModuleApplicationInfo().nativeLibraryDir
            val dexKitLib = File(moduleLibDir, "libdexkit.so")
            if (dexKitLib.exists()) {
                @Suppress("UnsafeDynamicallyLoadedCode")
                System.load(dexKitLib.absolutePath)
            } else {
                System.loadLibrary("dexkit")
            }
        }.onFailure { e ->
            Logger.w(TAG, "Hook", "DexKit native lib load fallback: ${e.message}")
        }

        val foundMethods = mutableListOf<String>()
        foundMethods.add("VERSION|$version")

        DexKitBridge.create(targetSourceDir).use { bridge ->
            DEX_KEYWORDS.forEach { word ->
                val candidates = bridge.findMethod { matcher { usingStrings(word); returnType = "boolean" } }
                candidates.forEach { data ->
                    runCatching {
                        val method = data.getMethodInstance(classLoader)
                        Logger.i(TAG, "Hook", "Found flag '$word' -> ${data.className}#${data.methodName}")
                        module.hook(method).intercept {
                            if (isRecordingEnabled) true else it.proceed()
                        }
                        foundMethods.add("FLAG|$word|${data.className}#${data.methodName}")
                    }.onFailure { e ->
                        Logger.w(TAG, "Hook", "Failed to hook candidate for '$word': ${e.message}")
                    }
                }
            }

            // Find and spoof Dialer's internal locale provider
            val localeCands = bridge.findMethod { matcher { usingStrings("getSupportedLocaleFromCountryCode"); returnType = "java.util.Locale" } }
            localeCands.firstOrNull()?.let { data ->
                runCatching {
                    val m = data.getMethodInstance(classLoader)
                    Logger.i(TAG, "Hook", "Found LocaleProvider -> ${data.className}#${data.methodName}")
                    module.hook(m).intercept { if (isRecordingEnabled) Locale.US else it.proceed() }
                    foundMethods.add("LOCALE|${data.className}#${data.methodName}")
                }.onFailure { e ->
                    Logger.w(TAG, "Hook", "Failed to hook locale provider: ${e.message}")
                }
            }
        }
        
        if (foundMethods.size > 1) {
            runCatching { cacheFile.writeText(foundMethods.joinToString("\n")) }
        } else {
            throw Exception("No valid methods found during DexKit scan")
        }
    }

    // Applies method hooks directly from the persistent cache file
    private fun loadFromCache(module: XposedModule, cl: ClassLoader, cacheFile: File, currentVersion: Long): Boolean {
        if (!cacheFile.exists()) return false
        return runCatching {
            val lines = cacheFile.readLines()
            if (lines.isEmpty() || !lines[0].startsWith("VERSION|$currentVersion")) return false

            lines.drop(1).forEach { line ->
                runCatching {
                    val parts = line.split("|")
                    if (parts.size < 2) return@forEach
                    val type = parts[0]
                    val methodDesc = parts.last()
                    val mParts = methodDesc.split("#")
                    val clazz = cl.loadClass(mParts[0])
                    val method = clazz.declaredMethods.find { it.name == mParts[1] } ?: return@forEach

                    module.hook(method).intercept { chain ->
                        when (type) {
                            "LOCALE" -> if (isRecordingEnabled) {
                                Logger.i(TAG, "Active", "[DexKit] LocaleProvider intercepted -> returning Locale.US")
                                Locale.US
                            } else chain.proceed()
                            "FLAG" -> if (isRecordingEnabled) {
                                Logger.i(TAG, "Active", "[DexKit] Flag '${mParts.getOrNull(1) ?: mParts[0]}' intercepted -> returning true")
                                true
                            } else chain.proceed()
                            else -> chain.proceed()
                        }
                    }
                }.onFailure { e ->
                    Logger.w(TAG, "Hook", "Failed to apply cached hook line: $line (${e.message})")
                }
            }
            true
        }.getOrDefault(false)
    }

    // Intercepts TTS speech synthesis using stack-trace and time-window signals
    private fun hookTtsHooks(module: XposedModule) {
        // Captures Dialer's utterance listener so we can trigger completion callbacks when bypassing TTS synthesis
        runCatching {
            val m = TextToSpeech::class.java.getDeclaredMethod("setOnUtteranceProgressListener", UtteranceProgressListener::class.java)
            module.hookBefore(m) { chain ->
                lastListener = (chain.args[0] as? UtteranceProgressListener)?.let { WeakReference(it) }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "setOnUtteranceProgressListener hook failed", e)
        }

        val speakInterceptor: (XposedInterface.Chain) -> Any? = { chain ->
            val utteranceId = chain.args.getOrNull(3) as? String
            val targetFile = if (chain.args.size >= 3 && chain.args[2] is File) chain.args[2] as File else null
            
            val now = SystemClock.uptimeMillis()
            val isRecentResourceRead = (now - lastResourceReadTime) < 500L
            val isRecordingCaller = isCallRecordingAnnouncementCaller()

            if (isRecordingEnabled && isSilenceEnabled && (isRecentResourceRead || isRecordingCaller)) {
                if (targetFile != null) {
                    Logger.i(TAG, "Active", "[TTS Silent WAV] Intercepted synthesizeToFile -> wrote 44-byte silent WAV to ${targetFile.name}")
                    runCatching { targetFile.outputStream().use { it.write(buildSilentWav()) } }
                } else {
                    Logger.i(TAG, "Active", "[TTS Speak Mute] Intercepted speak -> returning SUCCESS for utterance: $utteranceId")
                }

                // Notify Dialer that speech synthesis completed so recording starts immediately without timing out
                lastListener?.get()?.let { listener ->
                    utteranceId?.let { id ->
                        runCatching {
                            listener.onStart(id)
                            listener.onDone(id)
                        }
                    }
                }

                TextToSpeech.SUCCESS
            } else {
                chain.proceed()
            }
        }

        runCatching {
            val mSpeak = TextToSpeech::class.java.getDeclaredMethod("speak", CharSequence::class.java, Int::class.javaPrimitiveType, Bundle::class.java, String::class.java)
            module.hook(mSpeak).intercept(speakInterceptor)
            val vSynth = TextToSpeech::class.java.getDeclaredMethod("synthesizeToFile", CharSequence::class.java, Bundle::class.java, File::class.java, String::class.java)
            module.hook(vSynth).intercept(speakInterceptor)
        }.onFailure { e ->
            Logger.e(TAG, "Error", "TTS playback hooks failed", e)
        }
    }

    // Registers a secure broadcast receiver for real-time setting updates
    private fun registerReceiver(context: Context, moduleUid: Int) {
        IpcManager.registerSecureReceiver(context, moduleUid) { intent ->
            val action = intent.action ?: return@registerSecureReceiver
            if (action == IpcManager.ACTION_SETTINGS_SYNC) {
                isSilenceEnabled = intent.getBooleanExtra(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, true)
                isRecordingEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_CALL_RECORDING, false)
                Logger.i(TAG, "Sync", "Full sync received: recording=$isRecordingEnabled, silence=$isSilenceEnabled")
            } else {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY) ?: return@registerSecureReceiver
                val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                when (key) {
                    PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT -> {
                        isSilenceEnabled = value
                        Logger.i(TAG, "Sync", "Setting [disable_voice_announcement] updated to $value")
                    }
                    PreferenceKeys.ENABLE_CALL_RECORDING -> {
                        isRecordingEnabled = value
                        Logger.i(TAG, "Sync", "Setting [enable_call_recording] updated to $value")
                    }
                }
            }
        }
    }
}
