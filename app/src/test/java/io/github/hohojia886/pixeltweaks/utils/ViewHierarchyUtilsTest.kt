package io.github.hohojia886.pixeltweaks.utils

import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class ViewHierarchyUtilsTest {

    @Test
    fun testFindViewByPredicate() {
        val root = mock(ViewGroup::class.java)
        val child = mock(View::class.java)

        `when`(root.childCount).thenReturn(1)
        `when`(root.getChildAt(0)).thenReturn(child)
        `when`(child.tag).thenReturn("target_view")

        val found = ViewHierarchyUtils.findViewByPredicate(root) { it.tag == "target_view" }
        assertNotNull(found)
        assertEquals(child, found)

        val notFound = ViewHierarchyUtils.findViewByPredicate(root) { it.tag == "non_existent" }
        assertNull(notFound)
    }

    @Test
    fun testFindViewsByClassName() {
        val root = mock(ViewGroup::class.java)
        val child1 = mock(View::class.java)
        val child2 = mock(View::class.java)

        `when`(root.childCount).thenReturn(2)
        `when`(root.getChildAt(0)).thenReturn(child1)
        `when`(root.getChildAt(1)).thenReturn(child2)

        val foundViews = ViewHierarchyUtils.findViewsByClassName(root, "View")
        assertEquals(3, foundViews.size)
    }

    @Test
    fun testDumpViewHierarchy() {
        val root = mock(ViewGroup::class.java)
        val child = mock(View::class.java)

        `when`(root.childCount).thenReturn(1)
        `when`(root.getChildAt(0)).thenReturn(child)

        val dumpedLines = mutableListOf<String>()
        ViewHierarchyUtils.dumpViewHierarchy(root, 0) { line ->
            dumpedLines.add(line)
        }

        assertEquals(2, dumpedLines.size)
    }
}
