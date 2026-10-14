package com.paysplit.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.paysplit.app.ui.PaySplitRoot

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionOnce()
        setContent { PaySplitRoot() }
    }

    /** Разрешение на уведомления запрашивается один раз. */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val asked = getSharedPreferences("paysplit_prefs", MODE_PRIVATE)
            .getBoolean("notifications_asked", false)
        if (!granted && !asked) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            getSharedPreferences("paysplit_prefs", MODE_PRIVATE)
                .edit().putBoolean("notifications_asked", true).apply()
        }
    }
}
