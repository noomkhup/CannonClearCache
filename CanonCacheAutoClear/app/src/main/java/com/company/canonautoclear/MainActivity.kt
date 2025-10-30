package com.company.canonautoclear

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val canonPkg = "jp.co.canon.android.printservice.plugin"

    private lateinit var tvStatus: TextView
    private lateinit var btnClear: Button
    private lateinit var btnOpenAcc: Button
    private lateinit var btnOpenCanonAppInfo: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnClear = findViewById(R.id.btnClear)
        btnOpenAcc = findViewById(R.id.btnOpenAcc)
        btnOpenCanonAppInfo = findViewById(R.id.btnOpenCanonAppInfo)

        btnOpenAcc.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOpenCanonAppInfo.setOnClickListener {
            openStorageOrAppInfo(canonPkg)
        }

        btnClear.setOnClickListener {
            if (!ClearCacheAccessibilityService.isEnabled(this)) {
                tvStatus.text = getString(R.string.enable_acc_first)
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            ClearCacheAccessibilityService.scheduleAutoRun(this, 25_000L)
            openStorageOrAppInfo(canonPkg)
            tvStatus.text = getString(R.string.running_auto_clear)
        }
    }

    private fun openStorageOrAppInfo(pkg: String) {
        try {
            val i = Intent("android.settings.APP_STORAGE_SETTINGS")
                .putExtra("android.provider.extra.APP_PACKAGE", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            return
        } catch (_: ActivityNotFoundException) { }

        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
    }
}
