package com.posfingerprint.app

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the user's theme choice and keeps track of whether the app is on screen, so a
 * fingerprint request opens the scanner directly instead of posting a notification.
 */
class App : Application(), Application.ActivityLifecycleCallbacks {

    override fun onCreate() {
        super.onCreate()
        applyTheme(Prefs(this).themeMode)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        RequestState.visibleScreens++
    }

    override fun onActivityStopped(activity: Activity) {
        RequestState.visibleScreens = maxOf(0, RequestState.visibleScreens - 1)
    }

    override fun onActivityResumed(activity: Activity) {
        // A request arrived while the app was closed and the user opened the app normally:
        // show the scanner right away instead of making them find the notification.
        if (activity !is ApprovalActivity) openPendingRequest(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        fun applyTheme(mode: Int) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }

        fun themeLabel(mode: Int): String = when (mode) {
            THEME_LIGHT -> "Light"
            THEME_DARK -> "Dark"
            else -> "System"
        }

        /** Opens the scanner for the waiting request, at most once per request. */
        fun openPendingRequest(activity: Activity) {
            val req = RequestState.pending ?: return
            val token = req.optString("token")
            if (token.isEmpty() || token in RequestState.handled || token in RequestState.autoOpened) return
            if (System.currentTimeMillis() - RequestState.pendingAt > Security.REQUEST_MAX_AGE_MS) return
            RequestState.autoOpened.add(token)
            activity.getSystemService(NotificationManager::class.java).cancel(PollService.REQUEST_ID)
            activity.startActivity(approvalIntent(activity, req, RequestState.pendingAt))
        }

        fun approvalIntent(context: android.content.Context, req: org.json.JSONObject, receivedAt: Long): Intent =
            Intent(context, ApprovalActivity::class.java)
                .putExtra("request", req.toString())
                .putExtra("received_at", receivedAt)
    }
}
