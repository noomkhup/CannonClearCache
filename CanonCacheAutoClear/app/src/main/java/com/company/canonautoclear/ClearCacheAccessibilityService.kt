package com.company.canonautoclear

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import java.util.Locale

class ClearCacheAccessibilityService : AccessibilityService() {

    companion object {
        private const val PREF = "acc_prefs"
        private const val KEY_ARM_UNTIL = "arm_until"
        private const val KEY_DONE = "done"

        private val STORAGE_TEXTS = listOf(
            "Storage", "Storage & cache", "Storage used",
            "ที่เก็บข้อมูล", "พื้นที่เก็บข้อมูล", "พื้นที่จัดเก็บ", "พื้นที่จัดเก็บข้อมูล",
            "พื้นที่จัดเก็บและแคช", "การใช้งานที่เก็บข้อมูล", "ที่จัดเก็บข้อมูล"
        )

        private val SAFE_CLEAR_TEXTS = listOf(
            "Clear cache", "ล้างแคช", "ลบแคช", "เคลียร์แคช"
        )

        private val DANGEROUS_TEXTS = listOf(
            "Clear data", "Clear storage",
            "ล้างข้อมูล", "ล้างข้อมูลทั้งหมด", "ล้างพื้นที่จัดเก็บ", "ล้างข้อมูลแอป"
        )

        fun scheduleAutoRun(ctx: Context, windowMillis: Long = 25_000L) {
            val until = SystemClock.uptimeMillis() + windowMillis
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit {
                putLong(KEY_ARM_UNTIL, until)
                putBoolean(KEY_DONE, false)
            }
        }

        fun isEnabled(ctx: Context): Boolean {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            return enabled.any {
                val si = it.resolveInfo.serviceInfo
                si.packageName == ctx.packageName &&
                si.name == ClearCacheAccessibilityService::class.java.name
            }
        }

        private fun isArmed(ctx: Context): Boolean {
            val until = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_ARM_UNTIL, 0L)
            return SystemClock.uptimeMillis() < until
        }
    }

    private fun setDone(v: Boolean) {
    getSharedPreferences(PREF, Context.MODE_PRIVATE).edit { putBoolean(KEY_DONE, v) }
}
private fun isDone(): Boolean {
    return getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
}

private val ui = Handler(Looper.getMainLooper())
    private var lastTick = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isArmed(this) || isDone()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 120) return
        lastTick = now
        ui.postDelayed({ driveFlow() }, 150)
    }

    override fun onInterrupt() {}

    private fun postClearAndExit() {
    setDone(true)
    ui.postDelayed({
        performGlobalAction(GLOBAL_ACTION_BACK)
        ui.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            ui.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 400)
        }, 400)
    }, 3000)
}

private fun driveFlow() {
        val root = rootInActiveWindow ?: return

        if (clickMenuStorage(root)) return

        if (clickClearCache(root)) return

        if (scrollAny(root)) {
            ui.postDelayed({ driveFlow() }, 250)
        }
    }

    private fun clickMenuStorage(root: AccessibilityNodeInfo): Boolean {
        return clickByTextsWithAncestor(root, STORAGE_TEXTS, exact = false, maxAncestor = 6)
    }

    private fun clickClearCache(root: AccessibilityNodeInfo): Boolean {
        if (containsTexts(root, DANGEROUS_TEXTS, exact = true)) {
            // เห็นปุ่มล้างข้อมูล แปลว่าหน้านี้น่าจะถูกต้องแล้ว ให้พยายามหา "ล้างแคช" ต่อไป
        }
        val ok = clickByTextsWithAncestor(root, SAFE_CLEAR_TEXTS, exact = true, maxAncestor = 6)
        if (ok) postClearAndExit()
        return ok
    }

    private fun nodeTexts(n: AccessibilityNodeInfo): List<String> {
    val list = ArrayList<String>()
    val t = (n.text ?: "").toString()
    val d = (n.contentDescription ?: "").toString()
    if (t.isNotBlank()) list += t
    if (d.isNotBlank()) list += d
    return list
}

private fun containsTexts(root: AccessibilityNodeInfo, texts: List<String>, exact: Boolean): Boolean {
        val nodes = findAllNodes(root)
        return nodes.any { n ->
            val txs = nodeTexts(n)
            txs.any { matches(it, texts, exact) }
        }
    }

    private fun tryClickNode(node: AccessibilityNodeInfo?): Boolean {
    if (node == null) return false
    // 1) ถ้าคลิกได้ตรงๆ
    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
    // 2) โฟกัสแล้วค่อยคลิก
    node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
    // 3) ลอง parent
    var p = node.parent
    var depth = 0
    while (p != null && depth < 8) {
        if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        p = p.parent
        depth++
    }
    return false
}

private fun clickByTextsWithAncestor(
        root: AccessibilityNodeInfo,
        texts: List<String>,
        exact: Boolean,
        maxAncestor: Int
    ): Boolean {
        val nodes = findAllNodes(root)
        val candidates = nodes.filter { n ->
            val txs = nodeTexts(n)
            txs.any { matches(it, texts, exact) }
        }
        for (n in candidates) {
            var cur: AccessibilityNodeInfo? = n
            var depth = 0
            while (cur != null && depth <= maxAncestor) {
                if (tryClickNode(cur)) return true
                cur = cur.parent
                depth++
            }
        }
        return false
    }

    private fun matches(text: String, options: List<String>, exact: Boolean): Boolean {
        val t = text.trim().lowercase(Locale.getDefault())
        if (t.isEmpty()) return false
        return options.any { opt ->
            val o = opt.lowercase(Locale.getDefault())
            if (exact) t == o else t.contains(o)
        }
    }

    private fun findAllNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            out += n
            for (i in 0 until n.childCount) {
                walk(n.getChild(i))
            }
        }
        walk(root)
        return out
    }

    private fun scrollAny(root: AccessibilityNodeInfo): Boolean {
        val nodes = findAllNodes(root)
        val scrollable = nodes.firstOrNull { it.isScrollable }
        return scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    }
}
