package com.khaleel.newleaf.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.khaleel.newleaf.MainActivity
import com.khaleel.newleaf.R
import com.khaleel.newleaf.data.EntryKind
import com.khaleel.newleaf.data.Journal
import com.khaleel.newleaf.data.JournalRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Optional inexact check-ins, one hour after meals. Nothing is scheduled by default. */
object MealReminders {
    private const val CHANNEL = "meal_checkins"
    private const val PREFS = "meal_reminders"

    fun sync(context: Context, journal: Journal) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scheduled = prefs.getStringSet("scheduled", emptySet()).orEmpty().toSet()
        val delivered = prefs.getStringSet("delivered", emptySet()).orEmpty().toSet()
        val now = Instant.now().toEpochMilli()
        val eligible = if (journal.settings.remindersEnabled) journal.entries.filter {
            it.kind == EntryKind.MEAL && !it.isLibrary &&
                runCatching { Instant.parse(it.occurredAt).toEpochMilli() + 3_600_000L > now }.getOrDefault(false)
        }.filter { it.id !in delivered }.associateBy { it.id } else emptyMap()
        val alarms = app.getSystemService(AlarmManager::class.java)
        (scheduled - eligible.keys).forEach { alarms.cancel(pending(app, it)) }
        eligible.values.forEach {
            alarms.set(AlarmManager.RTC_WAKEUP, Instant.parse(it.occurredAt).toEpochMilli() + 3_600_000L, pending(app, it.id))
        }
        prefs.edit().putStringSet("scheduled", eligible.keys).apply()
    }

    private fun pending(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, MealReminderReceiver::class.java)
            .setData(Uri.parse("newleaf://meal-checkin/$id")).putExtra("meal_id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    internal fun notify(context: Context, mealId: String, name: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Meal check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Optional reminders to record how you felt after a meal"
        })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setData(Uri.parse("newleaf://meal/$mealId")).putExtra("checkin_meal_id", mealId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(mealId.hashCode(), NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle("How did you feel after $name?")
            .setContentText("Add a quick check-in to your Newleaf journal.")
            .setContentIntent(open).setAutoCancel(true).build())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("delivered", prefs.getStringSet("delivered", emptySet()).orEmpty() + mealId).apply()
    }
}

class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mealId = intent.getStringExtra("meal_id") ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val repository = JournalRepository(context.applicationContext)
            try {
                repository.awaitLoaded()
                val journal = repository.state.value
                val meal = journal.entries.find { it.id == mealId && it.kind == EntryKind.MEAL && !it.isLibrary }
                if (journal.settings.remindersEnabled && meal != null) MealReminders.notify(context, mealId, meal.name)
            } catch (_: Exception) {
                // Storage/device state failures should not crash a background notification.
            } finally {
                repository.close()
                result.finish()
            }
        }
    }
}
