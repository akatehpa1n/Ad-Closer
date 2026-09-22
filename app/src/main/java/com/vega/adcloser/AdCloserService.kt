package com.vega.adcloser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.random.Random

class AdCloserService : AccessibilityService() {
    private val handler=Handler(Looper.getMainLooper())
    private var lastClick=0L
    private val words=setOf("x","×","close","close ad","skip","skip ad","dismiss","done","finish","continue","claim reward","fermer","passer","ignorer")
    private val ids=listOf("close","skip","dismiss","close_button","skip_button","ad_close","btn_close")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now=System.currentTimeMillis()
        if(now-lastClick<1800) return
        val root=rootInActiveWindow ?: return
        val hit=find(root) ?: return
        lastClick=now
        handler.postDelayed({ click(hit) }, Random.nextLong(350,800))
    }
    override fun onInterrupt() {}

    private fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if(n.isVisibleToUser) {
            val t=(n.text?.toString() ?: "").trim().lowercase()
            val d=(n.contentDescription?.toString() ?: "").trim().lowercase()
            val id=(n.viewIdResourceName ?: "").lowercase()
            val textMatch=t in words || d in words
            val idMatch=ids.any { id.endsWith("/$it") || id.contains(it) }
            val bounds = android.graphics.Rect()
            n.getBoundsInScreen(bounds)
            if((textMatch || idMatch) && bounds.width()>0 && bounds.height()>0) return n
        }
        for(i in 0 until n.childCount) n.getChild(i)?.let { find(it)?.let { h -> return h } }
        return null
    }
    private fun click(n: AccessibilityNodeInfo) {
        var p: AccessibilityNodeInfo?=n
        while(p!=null) {
            if(p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            p=p.parent
        }
        val r=android.graphics.Rect(); n.getBoundsInScreen(r)
        if(r.width()<=0||r.height()<=0) return
        val path=Path().apply { moveTo(r.exactCenterX(),r.exactCenterY()) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,60)).build(),null,null)
    }
}