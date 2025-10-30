package com.company.canonautoclear

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.TileService

class QuickTileService : TileService() {
    private val canonPkg = "jp.co.canon.android.printservice.plugin"

    override fun onClick() {
        super.onClick()

        if (!ClearCacheAccessibilityService.isEnabled(this)) {
            startActivityAndCollapse(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        ClearCacheAccessibilityService.scheduleAutoRun(this, 25_000L)
        // พยายามเข้า Storage โดยตรงก่อน
        val storage = Intent("android.settings.APP_STORAGE_SETTINGS")
            .putExtra("android.provider.extra.APP_PACKAGE", canonPkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivityAndCollapse(storage)
        } catch (_: Exception) {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$canonPkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(i)
        }
    }
}
