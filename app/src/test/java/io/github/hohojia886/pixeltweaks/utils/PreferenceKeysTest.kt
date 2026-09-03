package io.github.hohojia886.pixeltweaks.utils

import org.junit.Test
import kotlin.test.assertTrue

class PreferenceKeysTest {
    @Test
    fun testNoDuplicateKeys() {
        val fields = PreferenceKeys::class.java.declaredFields
        val keys = mutableSetOf<String>()
        fields.forEach { field ->
            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type == String::class.java) {
                val value = field.get(null) as String
                assertTrue(keys.add(value), "Duplicate preference key found: $value")
            }
        }
    }
}
