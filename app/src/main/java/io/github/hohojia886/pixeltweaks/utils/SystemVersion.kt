package io.github.hohojia886.pixeltweaks.utils

/**
 * Utility object for inspecting system build information and version checks.
 * Provides helper methods to check Android build tags.
 */
object SystemVersion {
    val display: String
        get() = android.os.Build.DISPLAY ?: ""

    val id: String
        get() = android.os.Build.ID ?: ""

    /**
     * Checks if current system build string starts with `CP2A`.
     *
     * @return True if [display] or [id] build tag starts with `CP2A`; false otherwise.
     */
    fun isCp2a(): Boolean = display.startsWith("CP2A") || id.startsWith("CP2A")

    /**
     * Checks if current system build string starts with `CP3A`.
     *
     * @return True if [display] or [id] build tag starts with `CP3A`; false otherwise.
     */
    fun isCp3a(): Boolean = display.startsWith("CP3A") || id.startsWith("CP3A")

    /**
     * Checks if current system build string starts with `CD1A`.
     *
     * @return True if [display] or [id] build tag starts with `CD1A`; false otherwise.
     */
    fun isCd1a(): Boolean = display.startsWith("CD1A") || id.startsWith("CD1A")

    /**
     * Checks if current system build is `CD1A` prior to the `260905` build date.
     *
     * @return True if build tag is `CD1A` and build date integer is less than `260905`.
     */
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
