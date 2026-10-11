package com.example.networktrafficmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.CopyOnWriteArrayList

class TrafficMonitorService : Service() {
    companion object {
        private const val CHANNEL_ID = "traffic_monitor_channel"
        private const val NOTIFICATION_ID = 101
        private const val EXTRA_PACKAGE = "extra_package_name"

        private val logStore = CopyOnWriteArrayList<TrafficEvent>()

        fun startMonitoring(context: Context, packageName: String) {
            val intent = Intent(context, TrafficMonitorService::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
            }
            context.startForegroundService(intent)
        }

        fun evaluateRequest(context: Context, request: TrafficRequest): TrafficDecision {
            val store = TrafficRuleStore(context.applicationContext)
            val rule = store.findMatchingRule(request)

            return when (rule?.action) {
                TrafficAction.BLOCK -> TrafficDecision(
                    action = TrafficAction.BLOCK,
                    statusCode = 0,
                    reason = "Bloqué par règle enregistrée"
                )
                TrafficAction.MODIFY -> TrafficDecision(
                    action = TrafficAction.MODIFY,
                    responseBody = rule.modifiedResponseBody.ifBlank { "{\"status\":\"modified\"}" },
                    statusCode = rule.modifiedStatusCode,
                    contentType = rule.modifiedContentType,
                    reason = "Réponse remplacée"
                )
                else -> TrafficDecision(
                    action = TrafficAction.PASS,
                    responseBody = "{\"status\":\"passed\",\"message\":\"Aucune règle appliquée\"}",
                    statusCode = 200,
                    contentType = "application/json",
                    reason = "Pass-through normal"
                )
            }
        }

        fun recordDecision(request: TrafficRequest, decision: TrafficDecision) {
            val preview = when (decision.action) {
                TrafficAction.BLOCK -> "Blocked request"
                TrafficAction.MODIFY -> decision.responseBody
                TrafficAction.PASS -> decision.responseBody
            }.take(220)

            logStore.add(0, TrafficEvent(
                timestamp = System.currentTimeMillis(),
                appPackage = request.packageName,
                url = request.url,
                method = request.method,
                action = decision.action,
                statusCode = decision.statusCode,
                responsePreview = preview.ifBlank { decision.reason }
            ))

            while (logStore.size > 50) {
                logStore.removeAt(logStore.lastIndex)
            }
        }

        fun getLogSnapshot(): List<TrafficEvent> = logStore.toList()
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetPackage = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (targetPackage.isNotBlank()) {
            val request = TrafficRequest(
                packageName = targetPackage,
                url = "https://api.example.com/monitor",
                method = "GET",
                body = ""
            )
            val decision = evaluateRequest(applicationContext, request)
            recordDecision(request, decision)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Traffic Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetworkTrafficMonitor")
            .setContentText("Surveillance active en arrière-plan")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
