package app.untilparking.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.untilparking.domain.model.Destination
import app.untilparking.domain.repository.DestinationAlertRepository

/**
 * 목적지 알림 스펙 N1. 알림 하나를 고정 ID로 띄우고 바꾼다.
 * 알림 권한이 없으면 시스템이 조용히 버리므로 여기서 따로 확인하지 않는다.
 */
class NotificationDestinationAlert(context: Context) : DestinationAlertRepository {

    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // 차량 OS는 중요도 HIGH 이상이고 분류가 내비게이션일 때만 화면 위 카드로 띄운다(스펙 「알림이 보이는 방식」)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "목적지 주차장 알림", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun show(destination: Destination) {
        // 런처 인텐트에 SINGLE_TOP을 더한다. 앱이 어떤 인텐트로 처음 열렸든 떠 있던 화면이 새로 만들어지지 않고 그대로 앞으로 온다
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) ?: return
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("${destination.name.ifBlank { "목적지" }} 주변 주차장 보기")
            .setContentText("눌러서 주차장을 확인하세요")
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentIntent(
                PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun clear() = manager.cancel(NOTIFICATION_ID)

    private companion object {
        const val CHANNEL_ID = "destination_parking"
        const val NOTIFICATION_ID = 1
    }
}
