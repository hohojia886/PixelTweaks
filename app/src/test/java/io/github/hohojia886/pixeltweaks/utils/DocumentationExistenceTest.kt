package io.github.hohojia886.pixeltweaks.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocumentationExistenceTest {

    @Test
    fun testCommentsAndLoggerDocFilesExist() {
        val commentsFile = if (File("Comments.md").exists()) File("Comments.md") else File("../Comments.md")
        assertTrue("Comments.md must exist in project root", commentsFile.exists())
        assertTrue("Comments.md must not be empty", commentsFile.length() > 0)

        val loggerFile = if (File("Logger.md").exists()) File("Logger.md") else File("../Logger.md")
        assertTrue("Logger.md must exist in project root", loggerFile.exists())
        assertTrue("Logger.md must not be empty", loggerFile.length() > 0)
    }
}
