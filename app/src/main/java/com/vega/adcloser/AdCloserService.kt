package com.vega.adcloser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import kotlin.math.abs

class AdCloserService : AccessibilityService() {
    private val handler=Handler(Looper.getMainLooper())
    private val screenshotExecutor=Executors.newSingleThreadExecutor()
    private var lastClick=0L
    private var lastShot=0L
    private var lastAdSignal=0L
    private var lastPackage=""
    private val exact=setOf("x","×","✕","✖","close","close ad","skip","skip ad","dismiss","done","finish","no thanks","not now","google play","play store")
    private val ids=listOf("close","skip","dismiss","close_button","skip_button","ad_close","btn_close","closebutton","skipbutton","iv_close","img_close","image_close","x_button")
    private val adWords=listOf("ad","advertisement","reward","sponsored","skip","close","google play","play store")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now=System.currentTimeMillis()
        val pkg=event?.packageName?.toString() ?: ""
        val root=rootInActiveWindow

        if(pkg=="com.android.vending" && lastPackage!="com.android.vending" && now-lastAdSignal<15000) {
            lastPackage=pkg
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) },900)
            return
        }
        if(pkg.isNotBlank()) lastPackage=pkg
        if(root!=null) {
            if(hasAdSignal(root)) lastAdSignal=now
            if(now-lastClick>900) {
                val hit=find(root)
                if(hit!=null) {
                    lastClick=now; lastAdSignal=now
                    handler.postDelayed({ click(hit) },180)
                    return
                }
            }
        }

        // Android 11+ screenshot fallback: this sees pixel-drawn X buttons that
        // WebViews/video ads do not expose in the accessibility tree.
        if(Build.VERSION.SDK_INT>=30 && now-lastShot>650 && now-lastClick>900) {
            lastShot=now
            takeScreenshot(Display.DEFAULT_DISPLAY,screenshotExecutor,object:TakeScreenshotCallback {
                override fun onSuccess(r:ScreenshotResult) {
                    val b=Bitmap.wrapHardwareBuffer(r.hardwareBuffer,r.colorSpace)?.copy(Bitmap.Config.ARGB_8888,false)
                    r.hardwareBuffer.close()
                    if(b!=null) {
                        val p=findVisualX(b)
                        b.recycle()
                        if(p!=null) handler.post { visualTap(p.first,p.second) }
                    }
                }
                override fun onFailure(errorCode:Int) {}
            })
        }
    }
    override fun onInterrupt() {}
    override fun onDestroy() { screenshotExecutor.shutdownNow(); super.onDestroy() }

    private fun norm(x:CharSequence?)=(x?.toString()?:"").trim().lowercase()

    private fun hasAdSignal(n:AccessibilityNodeInfo):Boolean {
        val s=norm(n.text)+" "+norm(n.contentDescription)+" "+norm(n.viewIdResourceName)
        if(adWords.any{s.contains(it)}) return true
        for(i in 0 until n.childCount) n.getChild(i)?.let{if(hasAdSignal(it))return true}
        return false
    }

    private fun find(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
        if(n.isVisibleToUser) {
            val t=norm(n.text); val d=norm(n.contentDescription); val id=norm(n.viewIdResourceName)
            val match=t in exact || d in exact || ids.any{id.contains(it)}
            val r=Rect(); n.getBoundsInScreen(r)
            if(match && r.width()>0 && r.height()>0) return n
        }
        for(i in 0 until n.childCount) n.getChild(i)?.let{find(it)?.let{return it}}
        return null
    }

    // Searches only the upper outer portions of the screen. An X candidate must
    // contain BOTH diagonals with similar contrast, which avoids blindly tapping
    // arbitrary bright/dark corner pixels.
    private fun findVisualX(b:Bitmap):Pair<Float,Float>? {
        val w=b.width; val h=b.height
        val y0=(h*.025).toInt(); val y1=(h*.28).toInt()
        val ranges=listOf(0 until (w*.30).toInt(), (w*.70).toInt() until w)
        var best=0.0; var bx=0; var by=0
        val sizes=intArrayOf(24,32,40,48,56,64,72,84)
        fun lum(x:Int,y:Int):Int {
            val c=b.getPixel(x.coerceIn(0,w-1),y.coerceIn(0,h-1))
            return (Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11)/100
        }
        for(range in ranges) for(size in sizes) {
            val step=(size/4).coerceAtLeast(6)
            var cy=y0+size/2
            while(cy<y1-size/2) {
                var cx=range.first+size/2
                while(cx<range.last-size/2) {
                    var d1=0.0; var d2=0.0; var bg=0.0; var n=0
                    val half=size/2
                    var q=-half
                    while(q<=half) {
                        val a=lum(cx+q,cy+q); val aa=(lum(cx+q,cy+q-4)+lum(cx+q,cy+q+4))/2
                        val z=lum(cx+q,cy-q); val zz=(lum(cx+q,cy-q-4)+lum(cx+q,cy-q+4))/2
                        d1+=abs(a-aa); d2+=abs(z-zz); bg+=abs(aa-zz); n++; q+=4
                    }
                    val s=(d1/n)*(d2/n)/(1.0+bg/n)
                    if(s>best) {best=s; bx=cx; by=cy}
                    cx+=step
                }
                cy+=step
            }
        }
        return if(best>18.0) Pair(bx.toFloat(),by.toFloat()) else null
    }

    private fun visualTap(x:Float,y:Float) {
        val now=System.currentTimeMillis()
        if(now-lastClick<900) return
        lastClick=now; lastAdSignal=now
        gestureTap(x,y)
    }

    private fun click(n:AccessibilityNodeInfo) {
        var p:AccessibilityNodeInfo?=n
        while(p!=null) {
            if(p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            p=p.parent
        }
        val r=Rect(); n.getBoundsInScreen(r)
        if(r.width()>0&&r.height()>0) gestureTap(r.exactCenterX(),r.exactCenterY())
    }
    private fun gestureTap(x:Float,y:Float) {
        val path=Path().apply{moveTo(x,y)}
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,55)).build(),null,null)
    }
}