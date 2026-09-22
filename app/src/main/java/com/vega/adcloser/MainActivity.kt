package com.vega.adcloser
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(48,48,48,48) }
        box.addView(TextView(this).apply { text="AdCloser\n\nUses Android Accessibility to press legitimate visible Close / Skip / Done controls in ads. It does not bypass timers or spoof reward completion."; textSize=18f })
        box.addView(Button(this).apply { text="Enable Accessibility Service"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        setContentView(box)
    }
}