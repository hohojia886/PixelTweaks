package io.github.hohojia886.pixeltweaks.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TranslationCompletenessTest {

    @Test
    fun testAllLanguagesHaveAllStringKeys() {
        val resDir = File("src/main/res")
        if (!resDir.exists()) {
            println("Resource directory not found relative to working directory")
            return
        }

        val baseStringsFile = File(resDir, "values/strings.xml")
        assertTrue("Base strings.xml must exist", baseStringsFile.exists())

        val baseKeys = extractStringKeys(baseStringsFile)
        assertTrue("Base keys must not be empty", baseKeys.isNotEmpty())

        val langDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        } ?: emptyArray()

        val missingKeysMap = mutableMapOf<String, MutableList<String>>()

        langDirs.forEach { langDir ->
            val langStringsFile = File(langDir, "strings.xml")
            if (langStringsFile.exists()) {
                val langKeys = extractStringKeys(langStringsFile)
                val missing = baseKeys.filter { it !in langKeys }
                if (missing.isNotEmpty()) {
                    missingKeysMap[langDir.name] = missing.toMutableList()
                }
            }
        }

        if (missingKeysMap.isNotEmpty()) {
            val failureMsg = buildString {
                appendLine("Missing translation keys found in language resources:")
                missingKeysMap.forEach { (lang, missing) ->
                    appendLine("  Language [$lang] missing (${missing.size}) keys: $missing")
                }
            }
            assertTrue(failureMsg, missingKeysMap.isEmpty())
        }
    }

    private fun extractStringKeys(file: File): Set<String> {
        val keys = mutableSetOf<String>()
        val regex = Regex("""<string\s+name="([^"]+)"""")
        file.forEachLine { line ->
            val match = regex.find(line)
            if (match != null) {
                keys.add(match.groupValues[1])
            }
        }
        return keys
    }
}
