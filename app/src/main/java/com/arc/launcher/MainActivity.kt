package com.arc.launcher

import android.appwidget.AppWidgetHost
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arc.launcher.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {
    private lateinit var appWidgetHost: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetHost = AppWidgetHost(this, 1024)
        setContent {
            LauncherTheme {
                AppList(appWidgetHost = appWidgetHost)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }
}
