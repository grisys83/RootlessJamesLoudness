package me.timschneeberger.rootlessjamesdsp.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.lang.ref.WeakReference

abstract class BaseAudioProcessorService : Service() {
    private var binder: LocalBinder? = null

    // Make LocalBinder static to avoid implicit reference to outer class
    class LocalBinder(service: BaseAudioProcessorService) : Binder() {
        private val serviceRef = WeakReference(service)
        
        val service: BaseAudioProcessorService?
            get() = serviceRef.get()
            
        fun clear() {
            serviceRef.clear()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        if (binder == null) {
            binder = LocalBinder(this)
        }
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Clear binder reference to prevent leaks
        binder?.clear()
        binder = null
        return false // Don't allow rebind
    }

    override fun onCreate() {
        activeServices++
        super.onCreate()
    }

    override fun onDestroy() {
        activeServices--
        // Ensure binder is cleared
        binder?.clear()
        binder = null
        super.onDestroy()
    }

    companion object {
        var activeServices: Int = 0
            private set
    }
}
