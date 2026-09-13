package com.example.haritalar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.MainActivity

class RouteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, RouteWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.haritalar.UPDATE_WIDGET"
        const val PREFS_NAME = "WidgetPrefs"
        const val KEY_ETA = "eta"
        const val KEY_TRAFFIC = "traffic"
        const val KEY_DESTINATION = "destination"
        const val KEY_IS_NAVIGATING = "is_navigating"

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isNavigating = prefs.getBoolean(KEY_IS_NAVIGATING, false)
            val eta = prefs.getString(KEY_ETA, "--") ?: "--"
            val traffic = prefs.getString(KEY_TRAFFIC, "--") ?: "--"
            val destination = prefs.getString(KEY_DESTINATION, "Hedef") ?: "Hedef"

            val views = RemoteViews(context.packageName, R.layout.widget_route_info)

            if (isNavigating) {
                views.setTextViewText(R.id.widget_title, destination)
                views.setTextViewText(R.id.widget_eta, "Varış: $eta")
                views.setTextViewText(R.id.widget_traffic, "Trafik: $traffic")
            } else {
                views.setTextViewText(R.id.widget_title, "Haritalar")
                views.setTextViewText(R.id.widget_eta, "Navigasyon Kapalı")
                views.setTextViewText(R.id.widget_traffic, "Rota seçip başlayın.")
            }

            // Click to open app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
