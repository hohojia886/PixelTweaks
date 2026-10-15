package io.github.hohojia886.pixeltweaks.utils

/**
 * SystemVersion: Centralized utility object for inspecting system build information
 * and version checks across app UI and Xposed hook modules.
 */
object SystemVersion {
    val display: String
        get() = android.os.Build.DISPLAY ?: ""

    val id: String
        get() = android.os.Build.ID ?: ""

    fun isCp2a(): Boolean = display.startsWith("CP2A") || id.startsWith("CP2A")
    fun isCp3a(): Boolean = display.startsWith("CP3A") || id.startsWith("CP3A")
    fun isCd1a(): Boolean = display.startsWith("CD1A") || id.startsWith("CD1A")

    fun isCd1aBefore260905(): Boolean {
        val targetString = if (isCd1a()) display else if (id.startsWith("CD1A")) id else return false
        val parts = targetString.split(".")
        if (parts.size >= 2) {
            val dateInt = parts[1].toIntOrNull() ?: return false
            return dateInt < 260905
        }
        return false
    }
}
