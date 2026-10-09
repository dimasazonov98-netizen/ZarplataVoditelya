package com.duobudget.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.duobudget.app.MainActivity
import com.duobudget.app.R

class QuickExpenseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { widgetId ->
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("openAddExpense", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.quick_expense_widget)
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
