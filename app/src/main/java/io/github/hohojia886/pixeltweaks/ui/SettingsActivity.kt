package io.github.hohojia886.pixeltweaks.ui

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.slider.Slider
import io.github.hohojia886.pixeltweaks.BuildConfig
import io.github.hohojia886.pixeltweaks.R
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.IpcManager

/**
 * SettingsActivity: The main configuration interface for PixelTweaks.
 * Manages dual-preference synchronization (CE/DE storage), real-time IPC broadcasts
 * for setting updates, and UI state orchestration for all functional modules.
 */
class SettingsActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private val timeoutMillis = 3 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_settings)

        // Adjust padding for edge-to-edge transparency
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.appbar).setPadding(0, systemBars.top, 0, 0)
            val content = findViewById<View>(R.id.content_container)
            content.setPadding(
                content.paddingLeft,
                content.paddingTop,
                content.paddingRight,
                systemBars.bottom + 16
            )
            insets
        }

        val deContext = createDeviceProtectedStorageContext()
        val dePrefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, MODE_PRIVATE)
        val cePrefs = getSharedPreferences(IpcManager.PREF_NAME, MODE_PRIVATE)

        // Security
        setupSecurityBypasses(dePrefs, cePrefs)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_unrestricted_screenshots, PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true)
        
        val cardEasyUnlockReboot = findViewById<View>(R.id.card_easy_unlock_reboot)
        val switchEasyUnlockReboot = findViewById<MaterialSwitch>(R.id.switch_easy_unlock_reboot)

        setupM3Switch(cePrefs, dePrefs, R.id.switch_easy_unlock, PreferenceKeys.ENABLE_EASY_UNLOCK, true) { isChecked ->
            cardEasyUnlockReboot.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                switchEasyUnlockReboot.isChecked = false
                saveDoublePref(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false, cePrefs, dePrefs)
                IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
            }
        }
        setupM3Switch(cePrefs, dePrefs, R.id.switch_easy_unlock_reboot, PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
        cardEasyUnlockReboot.visibility = if (dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)) View.VISIBLE else View.GONE

        // Interface
        setupM3Switch(cePrefs, dePrefs, R.id.switch_clear_all, PreferenceKeys.ENABLE_CLEAR_ALL, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_network_traffic, PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true) { isChecked ->
            toggleTrafficSettingsVisibility(isChecked)
        }
        setupNetworkTrafficSettings(dePrefs, cePrefs)

        // Interaction
        setupM3Switch(cePrefs, dePrefs, R.id.switch_dt_launcher, PreferenceKeys.ENABLE_DT_LAUNCHER, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_dt_lockscreen, PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_dt_statusbar, PreferenceKeys.ENABLE_DT_STATUSBAR, true)

        // Quick Settings
        setupM3Switch(cePrefs, dePrefs, R.id.switch_qs_wifi_fix, PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_qs_data_fix, PreferenceKeys.ENABLE_QS_DATA_FIX, true)

        setupDialerMods(dePrefs, cePrefs)
        setupDebugCard(dePrefs, cePrefs)
    }

    private fun setupDialerMods(dePrefs: SharedPreferences, cePrefs: SharedPreferences) {
        val layoutCallRecordingSection = findViewById<View>(R.id.layout_call_recording_section)
        val cardDisableAnnouncement = findViewById<View>(R.id.card_disable_announcement)
        val switchDisableAnnouncement = findViewById<MaterialSwitch>(R.id.switch_disable_announcement)
        val switchCallRecording = findViewById<MaterialSwitch>(R.id.switch_call_recording)

        if (BuildConfig.ENABLE_CALL_RECORDING) {
            layoutCallRecordingSection.visibility = View.VISIBLE
            setupM3Switch(cePrefs, dePrefs, R.id.switch_disable_announcement, PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, true)
            setupM3Switch(cePrefs, dePrefs, R.id.switch_call_recording, PreferenceKeys.ENABLE_CALL_RECORDING, true) { isChecked ->
                cardDisableAnnouncement.visibility = if (isChecked) View.VISIBLE else View.GONE
                if (!isChecked) {
                    if (switchDisableAnnouncement.isChecked) {
                        switchDisableAnnouncement.isChecked = false
                    } else {
                        saveDoublePref(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, false, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, false)
                    }
                }
            }
            cardDisableAnnouncement.visibility = if (dePrefs.getBoolean(PreferenceKeys.ENABLE_CALL_RECORDING, true)) View.VISIBLE else View.GONE
            setupM3Switch(cePrefs, dePrefs, R.id.switch_disable_call_notes, PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT, true)
        } else {
            layoutCallRecordingSection.visibility = View.GONE
        }
    }

    private fun setupDebugCard(dePrefs: SharedPreferences, cePrefs: SharedPreferences) {
        val groupDebug = findViewById<View>(R.id.group_debug)
        if (!BuildConfig.DEBUG) {
            groupDebug.visibility = View.GONE
            return
        }
        groupDebug.visibility = View.VISIBLE

        val masterSwitch = findViewById<MaterialSwitch>(R.id.switch_master_log)
        val masterEnabled = dePrefs.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, false)
        
        masterSwitch.isChecked = masterEnabled
        toggleDebugSubSettingsVisibility(masterEnabled)

        masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveDoublePref(PreferenceKeys.ENABLE_MASTER_LOG, isChecked, cePrefs, dePrefs)
            IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_MASTER_LOG, isChecked)
            toggleDebugSubSettingsVisibility(isChecked)
        }

        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_call_notes, PreferenceKeys.LOG_CALL_NOTES, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_call_recording, PreferenceKeys.LOG_CALL_RECORDING, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_clear_all, PreferenceKeys.LOG_CLEAR_ALL, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_dt2s, PreferenceKeys.LOG_DT2S, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_easy_unlock, PreferenceKeys.LOG_EASY_UNLOCK, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_quick_settings, PreferenceKeys.LOG_QUICK_SETTINGS, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_unrestricted_screenshots, PreferenceKeys.LOG_UNRESTRICTED_SCREENSHOTS, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_security_bypasses, PreferenceKeys.LOG_SECURITY_BYPASSES, true)
        setupM3Switch(cePrefs, dePrefs, R.id.switch_log_network_traffic, PreferenceKeys.LOG_NETWORK_TRAFFIC, true)
    }

    private fun toggleDebugSubSettingsVisibility(visible: Boolean) {
        val ids = listOf(
            R.id.card_log_call_notes, R.id.card_log_call_recording, R.id.card_log_clear_all,
            R.id.card_log_dt2s, R.id.card_log_easy_unlock, R.id.card_log_quick_settings,
            R.id.card_log_unrestricted_screenshots, R.id.card_log_security_bypasses, R.id.card_log_network_traffic
        )
        val visibility = if (visible) View.VISIBLE else View.GONE
        ids.forEach { findViewById<View>(it).visibility = visibility }

        val cardMasterLog = findViewById<MaterialCardView>(R.id.card_master_log)
        val shapeAppearanceRes = if (visible) R.style.ShapeAppearance_Settings_Card_Top else R.style.ShapeAppearance_Settings_Card_All
        cardMasterLog.shapeAppearanceModel = ShapeAppearanceModel.builder(this, shapeAppearanceRes, 0).build()

        val params = cardMasterLog.layoutParams as LinearLayout.LayoutParams
        params.bottomMargin = if (visible) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics).toInt() else 0
        cardMasterLog.layoutParams = params
    }

    private fun setupNetworkTrafficSettings(dePrefs: SharedPreferences, cePrefs: SharedPreferences) {
        val intervalValues = listOf(1, 2, 3, 4, 5)
        val sliderInterval = findViewById<Slider>(R.id.slider_traffic_interval)
        val textInterval = findViewById<TextView>(R.id.text_traffic_interval)
        val currentInterval = dePrefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1)
        sliderInterval.value = intervalValues.indexOf(currentInterval).coerceAtLeast(0).toFloat()
        textInterval.text = getString(R.string.traffic_interval, getString(R.string.traffic_interval_format, currentInterval))
        sliderInterval.setLabelFormatter { value -> getString(R.string.traffic_interval_format, intervalValues[value.toInt()]) }
        sliderInterval.addOnChangeListener { _, value, _ -> textInterval.text = getString(R.string.traffic_interval, getString(R.string.traffic_interval_format, intervalValues[value.toInt()])) }
        sliderInterval.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                val actualVal = intervalValues[slider.value.toInt()]
                saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, actualVal, cePrefs, dePrefs)
                IpcManager.sendUpdateBroadcast(this@SettingsActivity, PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, actualVal)
            }
        })

        val fontValues = listOf(6f, 7f, 8f, 9f, 10f)
        val sliderFont = findViewById<Slider>(R.id.slider_traffic_font)
        val textFont = findViewById<TextView>(R.id.text_traffic_font)
        val currentFont = dePrefs.getFloat(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f)
        sliderFont.value = fontValues.indexOf(currentFont).coerceAtLeast(0).toFloat()
        textFont.text = getString(R.string.traffic_font_size, getString(R.string.traffic_font_format, currentFont.toInt()))
        sliderFont.setLabelFormatter { value -> getString(R.string.traffic_font_format, fontValues[value.toInt()].toInt()) }
        sliderFont.addOnChangeListener { _, value, _ -> textFont.text = getString(R.string.traffic_font_size, getString(R.string.traffic_font_format, fontValues[value.toInt()].toInt())) }
        sliderFont.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                val actualVal = fontValues[slider.value.toInt()]
                saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, actualVal, cePrefs, dePrefs)
                IpcManager.sendUpdateBroadcast(this@SettingsActivity, PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, actualVal)
            }
        })

        val thresholdValues = listOf(0, 1, 10, 100, 1024)
        val sliderThreshold = findViewById<Slider>(R.id.slider_traffic_threshold)
        val textThreshold = findViewById<TextView>(R.id.text_traffic_threshold)
        val currentThreshold = dePrefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1)
        sliderThreshold.value = thresholdValues.indexOf(currentThreshold).coerceAtLeast(0).toFloat()
        textThreshold.text = getString(R.string.traffic_threshold, formatThreshold(currentThreshold))
        sliderThreshold.setLabelFormatter { value -> formatThreshold(thresholdValues[value.toInt()]) }
        sliderThreshold.addOnChangeListener { _, value, _ -> textThreshold.text = getString(R.string.traffic_threshold, formatThreshold(thresholdValues[value.toInt()])) }
        sliderThreshold.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                val actualVal = thresholdValues[slider.value.toInt()]
                saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, actualVal, cePrefs, dePrefs)
                IpcManager.sendUpdateBroadcast(this@SettingsActivity, PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, actualVal)
            }
        })
        toggleTrafficSettingsVisibility(dePrefs.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true))
    }

    private fun toggleTrafficSettingsVisibility(visible: Boolean) {
        val ids = listOf(
            R.id.card_traffic_interval, R.id.card_traffic_font, R.id.card_traffic_threshold
        )
        val visibility = if (visible) View.VISIBLE else View.GONE
        ids.forEach { findViewById<View>(it).visibility = visibility }

        val cardNetworkTraffic = findViewById<MaterialCardView>(R.id.card_network_traffic)
        val shapeAppearanceRes = if (visible) R.style.ShapeAppearance_Settings_Card_Middle else R.style.ShapeAppearance_Settings_Card_Bottom
        cardNetworkTraffic.shapeAppearanceModel = ShapeAppearanceModel.builder(this, shapeAppearanceRes, 0).build()

        val params = cardNetworkTraffic.layoutParams as LinearLayout.LayoutParams
        params.bottomMargin = if (visible) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics).toInt() else 0
        cardNetworkTraffic.layoutParams = params
    }

    private fun formatThreshold(value: Int): String {
        return when(value) {
            0 -> getString(R.string.traffic_threshold_always)
            1024 -> getString(R.string.traffic_threshold_mb)
            else -> getString(R.string.traffic_threshold_kb, value)
        }
    }

    private fun buildWarningSpannable(mainText: CharSequence, warningSuffix: String): CharSequence {
        val errorColor = MaterialColors.getColor(this, android.R.attr.colorError, Color.RED)
        val builder = SpannableStringBuilder(mainText)
        val start = builder.length
        builder.append(warningSuffix)
        val end = builder.length
        builder.setSpan(
            ForegroundColorSpan(errorColor),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    private fun setupSecurityBypasses(dePrefs: SharedPreferences, cePrefs: SharedPreferences) {
        val switchDG = findViewById<MaterialSwitch>(R.id.switch_allow_downgrade)
        val switchSig = findViewById<MaterialSwitch>(R.id.switch_bypass_signature)
        val titleDG = findViewById<TextView>(R.id.title_allow_downgrade)
        val titleSig = findViewById<TextView>(R.id.title_bypass_signature)

        fun updateUI() {
            val now = System.currentTimeMillis()
            val dgTime = dePrefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
            val dgEnabled = dePrefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
            if (dgEnabled) {
                val remaining = timeoutMillis - (now - dgTime)
                if (remaining <= 0) {
                    saveDoublePref(PreferenceKeys.ALLOW_DOWNGRADE, false, cePrefs, dePrefs)
                    IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ALLOW_DOWNGRADE, false)
                    switchDG.isChecked = false
                    titleDG.text = getString(R.string.allow_downgrade)
                } else {
                    titleDG.text = buildWarningSpannable(
                        getString(R.string.allow_downgrade),
                        " (${remaining / 1000}s)"
                    )
                }
            } else {
                titleDG.text = getString(R.string.allow_downgrade)
            }

            val sigTime = dePrefs.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
            val sigEnabled = dePrefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
            if (sigEnabled) {
                val remaining = timeoutMillis - (now - sigTime)
                if (remaining <= 0) {
                    saveDoublePref(PreferenceKeys.BYPASS_SIGNATURE, false, cePrefs, dePrefs)
                    IpcManager.sendUpdateBroadcast(this, PreferenceKeys.BYPASS_SIGNATURE, false)
                    switchSig.isChecked = false
                    titleSig.text = getString(R.string.bypass_signature)
                } else {
                    titleSig.text = buildWarningSpannable(
                        getString(R.string.bypass_signature),
                        " (${remaining / 1000}s)"
                    )
                }
            } else {
                titleSig.text = getString(R.string.bypass_signature)
            }
        }

        switchDG.isChecked = dePrefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
        switchSig.isChecked = dePrefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
        updateUI()

        switchDG.setOnCheckedChangeListener { _, isChecked ->
            saveDoublePref(PreferenceKeys.ALLOW_DOWNGRADE, isChecked, cePrefs, dePrefs)
            if (isChecked) saveDoublePref(PreferenceKeys.DOWNGRADE_TIMESTAMP, System.currentTimeMillis(), cePrefs, dePrefs)
            IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ALLOW_DOWNGRADE, isChecked)
            updateUI()
        }

        switchSig.setOnCheckedChangeListener { _, isChecked ->
            saveDoublePref(PreferenceKeys.BYPASS_SIGNATURE, isChecked, cePrefs, dePrefs)
            if (isChecked) saveDoublePref(PreferenceKeys.SIGNATURE_TIMESTAMP, System.currentTimeMillis(), cePrefs, dePrefs)
            IpcManager.sendUpdateBroadcast(this, PreferenceKeys.BYPASS_SIGNATURE, isChecked)
            updateUI()
        }

        timerRunnable = object : Runnable {
            override fun run() {
                updateUI()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    override fun onPause() {
        super.onPause()
        val deContext = createDeviceProtectedStorageContext()
        val dePrefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, MODE_PRIVATE)
        IpcManager.syncAllSettings(this, dePrefs)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun saveDoublePref(key: String, value: Any, ce: SharedPreferences, de: SharedPreferences) {
        val ceEdit = ce.edit()
        val deEdit = de.edit()
        when (value) {
            is Boolean -> { ceEdit.putBoolean(key, value); deEdit.putBoolean(key, value) }
            is Int -> { ceEdit.putInt(key, value); deEdit.putInt(key, value) }
            is Float -> { ceEdit.putFloat(key, value); deEdit.putFloat(key, value) }
            is Long -> { ceEdit.putLong(key, value); deEdit.putLong(key, value) }
        }
        ceEdit.apply()
        deEdit.apply()
    }

    private fun setupM3Switch(ce: SharedPreferences, de: SharedPreferences, id: Int, key: String, default: Boolean, onToggle: ((Boolean) -> Unit)? = null) {
        val view = findViewById<MaterialSwitch>(id)
        view.isChecked = de.getBoolean(key, default)
        view.setOnCheckedChangeListener { _, isChecked ->
            saveDoublePref(key, isChecked, ce, de)
            onToggle?.invoke(isChecked)
            IpcManager.sendUpdateBroadcast(this, key, isChecked)
        }
    }
}
