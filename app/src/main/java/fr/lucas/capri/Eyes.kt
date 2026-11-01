package fr.lucas.capri

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

// Ses « yeux » : un service d'accessibilité qui relève seulement OÙ sont les choses à l'écran
// (le haut des boutons, icônes, messages, cases, et du clavier) et quelle appli est devant.
// Il ne lit pas le texte et n'envoie rien nulle part.
class Eyes : AccessibilityService() {

    data class Ledge(val left: Int, val right: Int, val top: Int, val keyboard: Boolean = false)

    private var lastScan = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        scan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val name = event.packageName?.toString() ?: ""
            if (name.isNotEmpty() && name != packageName && !name.startsWith("com.android.systemui")) {
                if (name != app) { app = name; appSince = System.currentTimeMillis() }
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastScan > 400) scan()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        ledges = emptyList()
        super.onDestroy()
    }

    private fun scan() {
        lastScan = System.currentTimeMillis()
        val found = ArrayList<Ledge>()
        val density = resources.displayMetrics.density
        val minWide = (48 * density).toInt()
        val minTall = (20 * density).toInt()
        val bounds = Rect()
        try {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    window.getBoundsInScreen(bounds)
                    if (bounds.width() > minWide) found.add(Ledge(bounds.left, bounds.right, bounds.top, keyboard = true))
                    continue
                }
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = window.root ?: continue
                if (root.packageName?.toString() == packageName) continue
                collect(root, found, minWide, minTall, 0, intArrayOf(0))
            }
        } catch (e: Exception) {
        }
        ledges = found
    }

    // Parcourt l'arbre de la fenêtre (400 éléments au plus) et garde le haut des éléments visibles assez grands.
    private fun collect(node: AccessibilityNodeInfo, found: ArrayList<Ledge>, minWide: Int, minTall: Int, depth: Int, count: IntArray) {
        if (count[0] > 400 || depth > 30) return
        count[0]++
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (node.isVisibleToUser && bounds.width() >= minWide && bounds.height() >= minTall &&
            (node.isClickable || node.text != null || node.contentDescription != null || node.isEditable)
        ) {
            found.add(Ledge(bounds.left, bounds.right, bounds.top))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, found, minWide, minTall, depth + 1, count)
        }
    }

    companion object {
        @Volatile var running = false
            private set
        @Volatile var ledges: List<Ledge> = emptyList()
            private set
        @Volatile var app: String = ""
            private set
        @Volatile var appSince: Long = 0L
            private set
    }
}
