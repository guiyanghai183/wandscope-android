package com.guiyanghai.wandscope

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object RunCompletionPolicy {
    fun isFinished(state: String): Boolean = state.lowercase() in setOf("finished", "completed")

    fun notificationCandidates(
        baselineInitialized: Boolean,
        runs: List<Run>,
        notifiedRunIds: Set<String>,
    ): List<Run> = if (!baselineInitialized) {
        emptyList()
    } else {
        runs.filter { isFinished(it.state) && it.id !in notifiedRunIds }
    }
}

class RunCompletionTracker(private val context: Context) {
    private val preferences = AppPreferences(context)

    fun process(entity: String, project: String, runs: List<Run>, notify: Boolean) {
        val scope = "$entity/$project"
        val previous = preferences.previousRunStates(scope)
        val notified = preferences.notifiedRunIds(scope)
        val baselineInitialized = preferences.isRunBaselineInitialized(scope)
        if (!baselineInitialized) {
            runs.forEach { previous[it.id] = it.state }
            runs.filter { RunCompletionPolicy.isFinished(it.state) }.forEach { notified += it.id }
            preferences.saveRunStates(scope, previous)
            preferences.saveNotifiedRunIds(scope, notified)
            preferences.markRunBaselineInitialized(scope)
            return
        }
        val candidateIds = RunCompletionPolicy.notificationCandidates(true, runs, notified).mapTo(mutableSetOf(), Run::id)
        runs.forEach { run ->
            if (notify && run.id in candidateIds) {
                if (publish(project, run)) notified += run.id
            }
            previous[run.id] = run.state
        }
        preferences.saveRunStates(scope, previous)
        preferences.saveNotifiedRunIds(scope, notified)
    }

    private fun publish(project: String, run: Run): Boolean {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            run.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Run 已完成")
            .setContentText("$project · ${run.displayName}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$project · ${run.displayName} 已完成"))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        if (!permissionGranted || !manager.areNotificationsEnabled()) return false
        return runCatching { manager.notify(run.id.hashCode(), notification) }.isSuccess
    }

    companion object {
        const val CHANNEL_ID = "run_completion"

        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Run 完成提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "W&B Run 完成时通知"
                },
            )
        }
    }
}

class RunMonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val key = SecretStore(applicationContext).readApiKey() ?: return Result.success()
        val prefs = AppPreferences(applicationContext)
        return runCatching {
            val api = WandbApi(key)
            prefs.monitoredProjects().forEach { monitored ->
                val runs = api.runs(monitored.entity, monitored.project, pageSize = 50).items
                RunCompletionTracker(applicationContext).process(monitored.entity, monitored.project, runs, notify = true)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val WORK_NAME = "wandscope_run_monitor"
        private const val IMMEDIATE_WORK_NAME = "wandscope_run_monitor_immediate"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<RunMonitorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RunMonitorWorker>().setConstraints(constraints).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
        }
    }
}
