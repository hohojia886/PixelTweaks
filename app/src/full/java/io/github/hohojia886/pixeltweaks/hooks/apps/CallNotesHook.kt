package io.github.hohojia886.pixeltweaks.hooks.apps

import android.content.Context
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.ToneGenerator
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookAfter
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Collections
import java.util.WeakHashMap

/**
 * CallNotesHook: Silences AI recording announcements.
 * Intercepts MediaPlayer and AudioTrack playbacks by analyzing stack traces 
 * for AI-related components like Fermat or SODA, then mutes the audio.
 */
object CallNotesHook {

    private const val TAG = "CallNotes"
    @Volatile private var isSilenceEnabled = true // Global toggle for silence logic
    private var currentPkg = "unknown" // Current process package name
    
    // Performance: Cache instances identified as AI to avoid redundant stack trace scans
    private val mutedInstances = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    // Identifies if the current playback call originates from AI components
    private fun isFermatCaller(instance: Any, context: String): Boolean {
        if (mutedInstances.contains(instance)) return true

        val stack = Thread.currentThread().stackTrace
        val isFermat = stack.any {
            val cls = it.className
            cls.contains("AudioInjector", true) ||
            cls.contains("Fermat", true) ||
            cls.contains("tidepods", true) ||
            cls.contains("callrecording", true) ||
            cls.contains("soda", true) ||
            cls.contains("intelligence", true) ||
            cls.contains("NotificationPlayer", true) ||
            cls.contains("transcript", true) ||
            cls.contains("recorder", true) ||
            (cls.contains("media", true) && currentPkg.contains("dialer"))
        }

        if (isFermat) {
            mutedInstances.add(instance)
            Logger.e(TAG, "Active", "Identified AI Announcer via [$context] in $currentPkg")
        }
        
        return isFermat
    }

    // Synchronizes the silence toggle state from remote preferences
    private fun syncState(module: XposedModule) {
        runCatching {
            val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
            isSilenceEnabled = prefs.getBoolean(PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT, true)
            Logger.i(TAG, "Sync", "Settings synced: silenceEnabled=$isSilenceEnabled")
        }
    }

    // Entry point: Decides which hooks to apply based on process identity
    fun hook(module: XposedModule, classLoader: ClassLoader, packageName: String) {
        currentPkg = packageName
        Logger.i(TAG, "Init", "Initializing CallNotesHook")
        syncState(module)
        val moduleUid = module.getModuleApplicationInfo().uid

        // Immediate registration for system_server to ensure early synchronization
        if (android.os.Process.myUid() == 1000) {
            IpcManager.getSafeContext(classLoader, packageName)?.let { ctx ->
                registerReceiver(ctx, moduleUid)
            }
        }

        // Secondary registration via lifecycle hooks for normal app processes
        runCatching {
            if (packageName == "android") {
                val ssClass = runCatching { classLoader.loadClass("com.android.server.SystemServer") }.getOrNull()
                if (ssClass != null) {
                    module.hookBefore(ssClass.getDeclaredMethod("run")) {
                        IpcManager.getSystemContext(classLoader)?.let { registerReceiver(it, moduleUid) }
                    }
                }
            } else {
                val appClass = runCatching { classLoader.loadClass("android.app.Application") }.getOrNull()
                if (appClass != null) {
                    module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                        val app = chain.thisObject as? Context
                        if (app != null) registerReceiver(app, moduleUid)
                    }
                }
            }
        }

        hookMediaPlayer(module) // Hook standard media player components
        hookAudioTrack(module) // Hook low-level audio track components
        hookToneAndRingtone(module) // Hook specialized tone generators
    }

    // Intercepts MediaPlayer preparation to mute AI audio streams early
    private fun hookMediaPlayer(module: XposedModule) {
        val mpClass = MediaPlayer::class.java
        mpClass.declaredMethods.filter { it.name == "start" || it.name == "prepare" || it.name == "prepareAsync" }.forEach { m ->
            runCatching {
                module.hookBefore(m) { chain ->
                    val instance = chain.thisObject ?: return@hookBefore
                    if (isSilenceEnabled && isFermatCaller(instance, "MediaPlayer.${m.name}")) {
                        (instance as? MediaPlayer)?.runCatching { setVolume(0f, 0f) }
                    }
                }
            }
        }
    }

    // Intercepts AudioTrack to zero out PCM data for AI recording prompts
    private fun hookAudioTrack(module: XposedModule) {
        runCatching {
            AudioTrack::class.java.declaredConstructors.forEach { ctor ->
                module.hookAfter(ctor) { chain, _ ->
                    val instance = chain.thisObject ?: return@hookAfter
                    if (isSilenceEnabled && isFermatCaller(instance, "AudioTrackCtor")) {
                        (instance as? AudioTrack)?.runCatching { setVolume(0f) }
                    }
                }
            }
        }
        
        // Zeroes out actual PCM buffers for instances in the muted list
        AudioTrack::class.java.declaredMethods.filter { it.name == "write" }.forEach { m ->
            runCatching {
                module.hookBefore(m) { chain ->
                    val instance = chain.thisObject ?: return@hookBefore
                    if (isSilenceEnabled && mutedInstances.contains(instance)) {
                        when (val buf = chain.args[0]) {
                            is ByteArray -> Arrays.fill(buf, 0.toByte())
                            is ShortArray -> Arrays.fill(buf, 0.toShort())
                            is ByteBuffer -> if (!buf.isReadOnly) {
                                val size = if (chain.args.size > 1 && chain.args[1] is Int) chain.args[1] as Int else buf.remaining()
                                val p = buf.position()
                                for (i in 0 until size) if (p + i < buf.capacity()) buf.put(p + i, 0.toByte())
                            }
                        }
                    }
                }
            }
        }
    }

    // Prevents audible beep/tones from AI recording components
    private fun hookToneAndRingtone(module: XposedModule) {
        runCatching {
            val m = ToneGenerator::class.java.getDeclaredMethod("startTone", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            module.hookBefore(m) { chain ->
                val instance = chain.thisObject ?: return@hookBefore
                if (isSilenceEnabled && isFermatCaller(instance, "ToneGenerator")) { /* Blocked */ } 
            }
        }
        runCatching {
            val m = Ringtone::class.java.getDeclaredMethod("play")
            module.hookBefore(m) { chain ->
                val instance = chain.thisObject ?: return@hookBefore
                if (isSilenceEnabled && isFermatCaller(instance, "Ringtone")) { /* Blocked */ } 
            }
        }
    }

    // Registers a secure receiver to handle real-time setting updates
    private fun registerReceiver(context: Context, moduleUid: Int) {
        IpcManager.registerSecureReceiver(context, moduleUid) { intent ->
            val action = intent.action ?: return@registerSecureReceiver
            if (action == IpcManager.ACTION_SETTINGS_SYNC) {
                isSilenceEnabled = intent.getBooleanExtra(PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT, true)
            } else {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                if (key == PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT) {
                    isSilenceEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
                }
            }
            mutedInstances.clear() // Clear cache on change to re-evaluate new streams
            Logger.i(TAG, "Sync", "isSilenceEnabled updated to: $isSilenceEnabled")
        }
        Logger.d(TAG, "Receiver", "Registered for $currentPkg")
    }
}
