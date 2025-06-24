/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */
package es.usc.citius.servando.calendula

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import com.squareup.leakcanary.LeakCanary
import es.usc.citius.servando.calendula.modules.ModuleManager
import es.usc.citius.servando.calendula.util.CloseableUtil
import es.usc.citius.servando.calendula.util.LogUtil
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class CalendulaApp : Application() {
    fun exportDatabase(context: Context, databaseName: String?, out: File?) {
        val dbPath = context.getDatabasePath(databaseName)

        // If the database already exists, return
        if (!dbPath.exists()) {
            LogUtil.d(TAG, "Database not found")
            return
        }

        // Try to copy database file
        var inputStream: InputStream? = null
        var output: OutputStream? = null
        try {
            inputStream = FileInputStream(dbPath)
            output = FileOutputStream(out)
            val buffer = ByteArray(8192)
            var length: Int

            while ((inputStream.read(buffer, 0, 8192).also { length = it }) > 0) {
                output.write(buffer, 0, length)
            }

            output.flush()
        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to export database", e)
        } finally {
            CloseableUtil.closeQuietly(inputStream, output)
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.FINGERPRINT != "robolectric") {
            if (LeakCanary.isInAnalyzerProcess(this@CalendulaApp)) {
                // This process is dedicated to LeakCanary for heap analysis.
                return
            }

            //initialize LeakCanary
//            LeakCanary.install(CalendulaApp.this);
        }

        context = this.applicationContext

        LogUtil.d(TAG, "Application started")

        try {
            LogUtil.d(TAG, "Application flavor is \"" + BuildConfig.FLAVOR + "\"")
            val flavor = BuildConfig.FLAVOR.uppercase(Locale.getDefault())
            ModuleManager.getInstance().runModules(flavor, this.applicationContext)
        } catch (e: Exception) {
            LogUtil.e(TAG, "onCreate: Error loading module configuration", e)
            LogUtil.w(TAG, "onCreate: Loading default module configuration instead")
            ModuleManager.getInstance().runDefaultModules(this.applicationContext)
        }
    }

    private fun forceLocale(l: Locale) {
        val locale = Locale(l.language)
        Locale.setDefault(locale)
        val config = applicationContext.resources.configuration
        config.locale = locale
        applicationContext.resources.updateConfiguration(
            config,
            applicationContext.resources.displayMetrics
        )
    }

    companion object {
        // INTENTS
        const val INTENT_EXTRA_ACTION: String = "action"
        const val INTENT_EXTRA_ROUTINE_ID: String = "routine_id"
        const val INTENT_EXTRA_MEDICINE_ID: String = "medicine_id"
        const val INTENT_EXTRA_SCHEDULE_ID: String = "schedule_id"
        const val INTENT_EXTRA_SCHEDULE_TIME: String = "schedule_time"
        const val INTENT_EXTRA_DELAY_ROUTINE_ID: String = "delay_routine_id"
        const val INTENT_EXTRA_DELAY_SCHEDULE_ID: String = "delay_schedule_id"
        const val INTENT_EXTRA_DATE: String = "date"
        const val INTENT_EXTRA_POSITION: String = "position"

        // ACTIONS
        const val ACTION_ROUTINE_TIME: Int = 1
        const val ACTION_DAILY_ALARM: Int = 2
        const val ACTION_ROUTINE_DELAYED_TIME: Int = 3
        const val ACTION_DELAY_ROUTINE: Int = 4
        const val ACTION_CANCEL_ROUTINE: Int = 5
        const val ACTION_HOURLY_SCHEDULE_TIME: Int = 6
        const val ACTION_HOURLY_SCHEDULE_DELAYED_TIME: Int = 7
        const val ACTION_DELAY_HOURLY_SCHEDULE: Int = 8
        const val ACTION_CANCEL_HOURLY_SCHEDULE: Int = 9
        const val ACTION_CHECK_PICKUPS_ALARM: Int = 10
        const val ACTION_CONFIRM_ALL_ROUTINE: Int = 11
        const val ACTION_CONFIRM_ALL_SCHEDULE: Int = 12

        // REQUEST CODES
        const val RQ_SHOW_ROUTINE: Int = 1
        const val RQ_DELAY_ROUTINE: Int = 2
        private const val TAG = "CalendulaApp"

        @JvmField var disableReceivers: Boolean = false

        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
            private set

        @JvmStatic
        fun eventBus(): EventBus = EventBus.getDefault()
    }
}
