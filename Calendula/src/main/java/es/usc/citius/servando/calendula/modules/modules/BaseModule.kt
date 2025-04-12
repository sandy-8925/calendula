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
package es.usc.citius.servando.calendula.modules.modules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.evernote.android.job.JobManager
import com.mikepenz.iconics.Iconics
import es.usc.citius.servando.calendula.DefaultDataGenerator
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.drugdb.DBRegistry
import es.usc.citius.servando.calendula.jobs.CalendulaJobCreator
import es.usc.citius.servando.calendula.jobs.CalendulaJobScheduler
import es.usc.citius.servando.calendula.jobs.CheckDatabaseUpdatesJob
import es.usc.citius.servando.calendula.jobs.PurgeCacheJob
import es.usc.citius.servando.calendula.modules.CalendulaModule
import es.usc.citius.servando.calendula.notifications.NotificationHelper.createNotificationChannels
import es.usc.citius.servando.calendula.scheduling.AlarmIntentParams
import es.usc.citius.servando.calendula.scheduling.AlarmReceiver
import es.usc.citius.servando.calendula.scheduling.AlarmScheduler
import es.usc.citius.servando.calendula.scheduling.DailyAgenda
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.PresentationsTypeface
import org.joda.time.LocalTime

class BaseModule : CalendulaModule() {
    /*----------*/
    override fun getId(): String {
        return ID
    }

    private fun initializeDatabase(ctx: Context) {
        DB.init(ctx)
        DBRegistry.init(ctx)
        try {
            if (DB.patients().count() == 0) {
                val defaultPatient = DB.helper().createDefaultPatient()
                DefaultDataGenerator.generateDefaultRoutines(defaultPatient, ctx)
                PreferenceUtils.edit().putLong(PreferenceKeys.PATIENTS_ACTIVE.key(), defaultPatient.id).apply()
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "initializeDatabase: ", e)
        }
    }

    private fun setupUpdateDailyAgendaAlarm(ctx: Context) {
        // intent our receiver will receive
        val intent = Intent(ctx, AlarmReceiver::class.java)
        val params = AlarmIntentParams.forDailyUpdate()
        AlarmScheduler.setAlarmParams(intent, params)
        val dailyAlarm = PendingIntent.getBroadcast(ctx, params.hashCode(), intent, PendingIntent.FLAG_CANCEL_CURRENT)
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, LocalTime(0, 0).toDateTimeToday().millis, AlarmManager.INTERVAL_DAY, dailyAlarm)
    }

    override fun onApplicationStartup(ctx: Context) {
        // initialize SQLite engine
        initializeDatabase(ctx)

        // create notification channels
        createNotificationChannels(ctx)

        // initialize daily agenda
        DailyAgenda.instance().setupForToday(ctx, false)
        // setup alarm for daily agenda update
        setupUpdateDailyAgendaAlarm(ctx)
        //exportDatabase(this, DB_NAME, new File(Environment.getExternalStorageDirectory() + File.separator + DB_NAME));
        //forceLocale(Locale.GERMAN);
        //only required if you add a custom or generic font on your own
        Iconics.init(ctx)
        //register custom fonts like this (or also provide a font definition file)
        Iconics.registerFont(PresentationsTypeface())

        //initialize job engine
        JobManager.create(ctx).addJobCreator(CalendulaJobCreator())
        //schedule jobs
        val jobs = arrayOf(
            CheckDatabaseUpdatesJob(),
            PurgeCacheJob()
        )
        CalendulaJobScheduler.scheduleJobs(jobs)
    }

    companion object {
        const val ID: String = "CALENDULA_BASE_MODULE"
        private const val TAG = "BaseModule"
    }
}
