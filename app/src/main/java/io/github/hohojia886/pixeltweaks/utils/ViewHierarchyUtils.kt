package io.github.hohojia886.pixeltweaks.utils

import android.view.View
import android.view.ViewGroup

/**
 * Utility functions for inspecting and traversing View hierarchies.
 * Reusable across all Xposed hooks in PixelTweaks module.
 */
object ViewHierarchyUtils {

    /**
     * Recursively dumps View hierarchy tree with visibility, bounds, and resource IDs for debugging.
     *
     * @param view Root View to begin dumping from.
     * @param depth Current recursion depth (used for indent spacing).
     * @param logOutput Callback lambda receiving formatted log line strings.
     */
    fun dumpViewHierarchy(view: View, depth: Int = 0, logOutput: (String) -> Unit) {
        val indent = "  ".repeat(depth)
        val className = view.javaClass.simpleName.ifEmpty { view.javaClass.name }
        val idName = runCatching {
            if (view.id != View.NO_ID && view.context != null) {
                view.context.resources.getResourceEntryName(view.id)
            } else "NO_ID"
        }.getOrDefault("UNKNOWN_ID(${view.id})")

        val visibility = when (view.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> view.visibility.toString()
        }

        val composeTag = if (view.javaClass.name.contains("AndroidComposeView")) " [COMPOSE_ROOT]" else ""
        val info = "$indent-> $className (id=$idName) v=$visibility alpha=${view.alpha} scale=${view.scaleX} w=${view.width} h=${view.height}$composeTag"
        logOutput(info)

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                dumpViewHierarchy(child, depth + 1, logOutput)
            }
        }
    }

    /**
     * Searches a View tree and returns the first View matching the specified predicate.
     *
     * @param root Root View to begin depth-first search from.
     * @param predicate Evaluation predicate function.
     * @return First matching [View], or null if no view matches predicate.
     */
    fun findViewByPredicate(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i) ?: continue
                val found = findViewByPredicate(child, predicate)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Searches a View tree and returns all Views matching class name substring.
     *
     * @param root Root ViewGroup to traverse.
     * @param classNamePart Case-insensitive class name substring.
     * @return List of matching [View] instances.
     */
    fun findViewsByClassName(root: ViewGroup, classNamePart: String): List<View> {
        val results = mutableListOf<View>()
        fun search(v: View) {
            if (v.javaClass.name.contains(classNamePart, ignoreCase = true)) {
                results.add(v)
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val child = v.getChildAt(i) ?: continue
                    search(child)
                }
            }
        }
        search(root)
        return results
    }
}
