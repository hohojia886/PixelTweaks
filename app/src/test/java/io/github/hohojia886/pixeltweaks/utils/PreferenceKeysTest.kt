package io.github.hohojia886.pixeltweaks.utils

import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferenceKeysTest {

    @Test
    fun testNoDuplicateKeys() {
        val fields = PreferenceKeys::class.java.declaredFields
        val keys = mutableSetOf<String>()
        fields.forEach { field ->
            if (Modifier.isStatic(field.modifiers) && field.type == String::class.java) {
                val value = field.get(null) as String
                assertFalse(value.isEmpty(), "Empty preference key found in field: ${field.name}")
                assertTrue(keys.add(value), "Duplicate preference key found: $value")
            }
        }
    }
}
