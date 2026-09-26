package com.proxyplatform.app.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.proxyplatform.app.R

/** Small notification helper kept separate from the ADB command manager. */
object AdbPairingNotifier {
    private const val CHANNEL_ID = "adb_pairing"
    private const val NOTIFICATION_ID = 5701

    fun showPairing(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "اقتران التصحيح اللاسلكي",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("اقتران Proxy Platform")
            .setContentText("أدخل رمز الاقتران الظاهر في إعدادات التصحيح اللاسلكي")
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showResult(context: Context, success: Boolean) {
        Toast.makeText(
            context,
            if (success) "تم الاقتران بنجاح" else "فشل الاقتران",
            Toast.LENGTH_SHORT
        ).show()
    }
}
