package com.roadmatekorea.android.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.roadmatekorea.android.MainActivity
import com.roadmatekorea.android.R
import com.roadmatekorea.android.RoadMateApplication

/**
 * 내비를 앞에 띄워도 음성 안내가 계속되게 프로세스를 붙잡아 두는 포그라운드 서비스.
 *
 * Pleos Connect는 화면에서 사라진 앱의 프로세스를 얼릴 수 있다(freezer). 그러면 Vehicle·NaviHelper 콜백과
 * TTS가 멈춘다. 안내 세션 자체는 [RoadMateApplication.driveController]가 갖고 있고, 이 서비스는
 * 그 세션을 시작해 두고 프로세스를 포그라운드로 유지하는 일만 한다(판단 로직 없음).
 *
 * 화면이 보일 때(MainActivity.onStart)만 시작한다. 백그라운드에서 포그라운드 서비스를 새로 시작하는 것은 Android가 막는다.
 * 사용자가 최근 앱에서 RoadMate를 지우면 안내 세션과 함께 끝난다.
 * 알림은 Android 13 이상에서 알림 권한이 있어야 보인다(없어도 서비스는 동작한다).
 */
class DriveGuidanceService : Service() {

    override fun onCreate() {
        super.onCreate()
        // 신호 수집을 시작하고 저장된 프로필로 세션을 연다(화면 없이 서비스만 다시 만들어져도 안전 경고가 나가게)
        (application as RoadMateApplication).restoreSavedSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType())
        }
        if (started.isFailure) {
            Log.w(TAG, "startForeground failed", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /** 최근 앱에서 지우면 안내도 끝낸다(알림만 사라지고 음성이 남지 않게). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as RoadMateApplication).driveController.endSession()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.guidance_service_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rm_steering)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.guidance_service_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "RoadMateService"
        private const val CHANNEL_ID = "guidance"
        private const val NOTIFICATION_ID = 1

        /** 화면이 보일 때 부른다. 이미 돌고 있으면 아무 일도 하지 않는다. */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DriveGuidanceService::class.java))
            }.onFailure { Log.w(TAG, "service start failed", it) }
        }
    }
}
