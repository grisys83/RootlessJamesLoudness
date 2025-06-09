package me.timschneeberger.rootlessjamesdsp.utils.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.activity.LoudnessControllerActivity
import me.timschneeberger.rootlessjamesdsp.utils.LoudnessController
import me.timschneeberger.rootlessjamesdsp.utils.preferences.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import kotlin.math.roundToInt

object LoudnessNotificationHelper : KoinComponent {
    private val preferences: Preferences.App by inject()
    
    private const val ACTION_INCREASE_1 = "me.timschneeberger.rootlessjamesdsp.INCREASE_1"
    private const val ACTION_DECREASE_1 = "me.timschneeberger.rootlessjamesdsp.DECREASE_1"
    private const val ACTION_INCREASE_5 = "me.timschneeberger.rootlessjamesdsp.INCREASE_5"
    private const val ACTION_DECREASE_5 = "me.timschneeberger.rootlessjamesdsp.DECREASE_5"
    private const val ACTION_TOGGLE_LOUDNESS = "me.timschneeberger.rootlessjamesdsp.TOGGLE_LOUDNESS"
    
    fun createLoudnessNotification(context: Context): Notification {
        Timber.d("createLoudnessNotification called")
        
        // Create new instance each time to avoid memory leaks
        val controller = LoudnessController(context)
        
        // Get current values
        val targetPhon = controller.getTargetPhon()
        val actualPhon = controller.getActualPhon()
        val isEnabled = controller.isLoudnessEnabled()
        
        Timber.d("Loudness values: targetPhon=$targetPhon, actualPhon=$actualPhon, isEnabled=$isEnabled")
        
        // Use compact notification with actions for better compatibility
        val notification = createCompactNotification(context, targetPhon, actualPhon, isEnabled)
        Timber.d("Notification created successfully")
        return notification
    }
    
    private fun createExpandedNotification(
        context: Context,
        targetPhon: Float,
        actualPhon: Float,
        isEnabled: Boolean
    ): Notification {
        // Create custom RemoteViews for expanded notification
        val expandedView = RemoteViews(context.packageName, R.layout.notification_loudness_expanded)
        
        // Update views
        expandedView.setTextViewText(R.id.target_phon_text, "Target: ${targetPhon.roundToInt()} phon")
        expandedView.setTextViewText(R.id.actual_phon_text, "Actual: ${actualPhon.roundToInt()} phon")
        
        // Set up click listeners for fine adjustment
        expandedView.setOnClickPendingIntent(R.id.decrease_1_button, 
            createPendingIntent(context, ACTION_DECREASE_1))
        expandedView.setOnClickPendingIntent(R.id.increase_1_button, 
            createPendingIntent(context, ACTION_INCREASE_1))
        
        // Set up click listeners for coarse adjustment
        expandedView.setOnClickPendingIntent(R.id.decrease_5_button, 
            createPendingIntent(context, ACTION_DECREASE_5))
        expandedView.setOnClickPendingIntent(R.id.increase_5_button, 
            createPendingIntent(context, ACTION_INCREASE_5))
        
        // Toggle button
        expandedView.setOnClickPendingIntent(R.id.toggle_button, 
            createPendingIntent(context, ACTION_TOGGLE_LOUDNESS))
        
        // Update toggle button state
        expandedView.setTextViewText(R.id.toggle_button, if (isEnabled) "ON" else "OFF")
        expandedView.setInt(R.id.toggle_button, "setBackgroundColor", 
            if (isEnabled) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
        
        // Create compact view for collapsed state
        val compactView = RemoteViews(context.packageName, R.layout.notification_loudness_compact)
        compactView.setTextViewText(R.id.notification_title, "Loudness Controller")
        compactView.setTextViewText(R.id.notification_text, "Target: ${targetPhon.roundToInt()} phon")
        compactView.setOnClickPendingIntent(R.id.notification_decrease, 
            createPendingIntent(context, ACTION_DECREASE_1))
        compactView.setOnClickPendingIntent(R.id.notification_increase, 
            createPendingIntent(context, ACTION_INCREASE_1))
        compactView.setOnClickPendingIntent(R.id.notification_toggle, 
            createPendingIntent(context, ACTION_TOGGLE_LOUDNESS))
        compactView.setTextViewText(R.id.notification_toggle, if (isEnabled) "ON" else "OFF")
        
        // Create notification with actions for better compatibility
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_LOUDNESS_CONTROL)
            .setSmallIcon(R.drawable.ic_tune_vertical_variant_24dp)
            .setContentTitle("Loudness: ${if (isEnabled) "ON" else "OFF"}")
            .setContentText("Target: ${targetPhon.roundToInt()} phon, Actual: ${actualPhon.roundToInt()} phon")
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for lock screen
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Category for ongoing service
            .setContentIntent(createMainActivityIntent(context))
            
        // Add action buttons for better visibility
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_twotone_remove_24dp,
                "-1",
                createPendingIntent(context, ACTION_DECREASE_1)
            ).build()
        )
        
        builder.addAction(
            NotificationCompat.Action.Builder(
                if (isEnabled) R.drawable.ic_twotone_volume_up_24dp else R.drawable.ic_twotone_volume_off_24dp,
                if (isEnabled) "ON" else "OFF",
                createPendingIntent(context, ACTION_TOGGLE_LOUDNESS)
            ).build()
        )
        
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_twotone_add_24dp,
                "+1",
                createPendingIntent(context, ACTION_INCREASE_1)
            ).build()
        )
            
        return builder.build()
    }
    
    private fun createCompactNotification(
        context: Context,
        targetPhon: Float,
        actualPhon: Float,
        isEnabled: Boolean
    ): Notification {
        Timber.d("Creating compact notification with channel: ${Notifications.CHANNEL_LOUDNESS_CONTROL}")
        
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_LOUDNESS_CONTROL)
            .setSmallIcon(R.drawable.ic_tune_vertical_variant_24dp)
            .setContentTitle("Loudness: ${if (isEnabled) "ON" else "OFF"}")
            .setContentText("Target: ${targetPhon.roundToInt()} phon, Actual: ${actualPhon.roundToInt()} phon")
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(createMainActivityIntent(context))
        
        // Add actions for compact notification
        builder.addAction(
            R.drawable.ic_baseline_remove_24dp,
            "-1",
            createPendingIntent(context, ACTION_DECREASE_1)
        )
        
        builder.addAction(
            R.drawable.ic_twotone_volume_up_24dp,
            if (isEnabled) "ON" else "OFF",
            createPendingIntent(context, ACTION_TOGGLE_LOUDNESS)
        )
        
        builder.addAction(
            R.drawable.ic_baseline_add_24dp,
            "+1",
            createPendingIntent(context, ACTION_INCREASE_1)
        )
        
        return builder.build()
    }
    
    private fun createBasicNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, Notifications.CHANNEL_LOUDNESS_CONTROL)
            .setSmallIcon(R.drawable.ic_tune_vertical_variant_24dp)
            .setContentTitle("Loudness Controller")
            .setContentText("Tap to open")
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(createMainActivityIntent(context))
            .build()
    }
    
    private fun createPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createMainActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, LoudnessControllerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun updateNotification(context: Context) {
        context.getSystemService<NotificationManager>()?.notify(
            Notifications.ID_LOUDNESS_CONTROL,
            createLoudnessNotification(context)
        )
    }
    
    // Broadcast receiver for handling notification actions
    class LoudnessNotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val controller = LoudnessController(context)
            
            when (intent.action) {
                ACTION_INCREASE_1 -> {
                    controller.setTargetPhon(controller.getTargetPhon() + 1f)
                }
                ACTION_DECREASE_1 -> {
                    controller.setTargetPhon(controller.getTargetPhon() - 1f)
                }
                ACTION_INCREASE_5 -> {
                    controller.setTargetPhon(controller.getTargetPhon() + 5f)
                }
                ACTION_DECREASE_5 -> {
                    controller.setTargetPhon(controller.getTargetPhon() - 5f)
                }
                ACTION_TOGGLE_LOUDNESS -> {
                    controller.setLoudnessEnabled(!controller.isLoudnessEnabled())
                }
            }
            
            // Force update loudness to ensure filter is changed
            if (controller.isLoudnessEnabled()) {
                controller.updateLoudness()
            }
            
            // Update notification
            LoudnessNotificationHelper.updateNotification(context)
        }
    }
}