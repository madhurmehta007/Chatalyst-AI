package com.android.chatalystai.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log // Import
import androidx.core.app.NotificationCompat
import com.android.chatalystai.ChatalystAiApplication // *** ADDED IMPORT ***
import com.android.chatalystai.MainActivity
import com.android.chatalystai.R
import com.android.chatalystai.data.local.ConversationDao // Import
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint // Import
import kotlinx.coroutines.Dispatchers // Import
import kotlinx.coroutines.runBlocking // Import
import javax.inject.Inject // Import
import kotlin.random.Random

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var conversationDao: ConversationDao

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // *** MODIFICATION: Suppress notification if app is in the foreground ***
        if (ChatalystAiApplication.isAppInForeground) {
            Log.d("FCMService", "App is in foreground, suppressing notification.")
            return
        }

        val data = remoteMessage.data
        val title = data["title"] ?: "New Message"
        val body = data["body"] ?: "You have a new message"
        val conversationId = data["conversationId"]

        if (conversationId != null) {
            try {
                val conversation = runBlocking(Dispatchers.IO) {
                    conversationDao.getConversationByIdSuspend(conversationId)
                }

                if (conversation != null) {
                    val isMutedForever = conversation.mutedUntil == -1L
                    val isMutedTemporarily = conversation.mutedUntil > System.currentTimeMillis()

                    if (isMutedForever || isMutedTemporarily) {
                        Log.d("FCMService", "Conversation $conversationId is muted. Suppressing notification.")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.e("FCMService", "Error checking mute status in Room", e)
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "chatalyst_messages_channel"

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // You should have logic here to send this token to your server/Firebase
        // associated with the logged-in user.
    }
}