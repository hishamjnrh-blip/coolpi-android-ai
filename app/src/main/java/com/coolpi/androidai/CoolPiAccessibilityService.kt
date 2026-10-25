package com.coolpi.androidai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CoolPiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen content is inspected only when requested by the app.
    }

    override fun onInterrupt() {
        // Android interrupted the accessibility service.
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun readVisibleText(): String {
        val root = rootInActiveWindow ?: return "No accessible active screen."
        val output = StringBuilder()
        collectText(root, output, 0)
        return output.toString().take(MAX_TEXT_LENGTH)
            .ifBlank { "No readable text is exposed on the current screen." }
    }

    private fun collectText(
        node: AccessibilityNodeInfo,
        output: StringBuilder,
        depth: Int
    ) {
        if (depth > 30 || output.length >= MAX_TEXT_LENGTH) return

        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()

        if (text.isNotBlank()) output.appendLine(text)
        if (description.isNotBlank() && description != text) {
            output.appendLine(description)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectText(child, output, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    fun clickVisibleText(target: String): Boolean {
        if (target.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        return try {
            clickNode(root, target.trim(), 0)
        } finally {
            root.recycle()
        }
    }

    private fun clickNode(
        node: AccessibilityNodeInfo,
        target: String,
        depth: Int
    ): Boolean {
        if (depth > 30) return false

        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()

        if (node.isVisibleToUser &&
            (text.equals(target, true) || description.equals(target, true))
        ) {
            if (node.isClickable && node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            ) return true

            val parent = node.parent
            if (parent != null) {
                try {
                    if (parent.isClickable && parent.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    ) return true
                } finally {
                    parent.recycle()
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val clicked = try {
                clickNode(child, target, depth + 1)
            } finally {
                child.recycle()
            }
            if (clicked) return true
        }
        return false
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            scrollNode(root, 0)
        } finally {
            root.recycle()
        }
    }

    private fun scrollNode(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 30) return false
        if (node.isVisibleToUser && node.isScrollable &&
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        ) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val scrolled = try {
                scrollNode(child, depth + 1)
            } finally {
                child.recycle()
            }
            if (scrolled) return true
        }
        return false
    }

    companion object {
        @Volatile
        var instance: CoolPiAccessibilityService? = null
            private set

        private const val MAX_TEXT_LENGTH = 8000
    }
}
