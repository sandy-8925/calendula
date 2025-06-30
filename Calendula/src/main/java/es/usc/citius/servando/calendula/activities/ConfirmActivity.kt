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
package es.usc.citius.servando.calendula.activities

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.util.Pair
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import es.usc.citius.servando.calendula.CalendulaActivity
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.HomePagerActivity
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.persistence.DailyScheduleItem
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.persistence.Patient
import es.usc.citius.servando.calendula.persistence.Presentation
import es.usc.citius.servando.calendula.persistence.Routine
import es.usc.citius.servando.calendula.persistence.Schedule
import es.usc.citius.servando.calendula.scheduling.AlarmScheduler
import es.usc.citius.servando.calendula.util.AvatarMgr
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.ScreenUtils
import es.usc.citius.servando.calendula.util.Snack
import es.usc.citius.servando.calendula.util.view.ArcTranslateAnimation
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.hypot

class ConfirmActivity : CalendulaActivity() {
    @JvmField
    @BindView(R.id.appbar)
    var appBarLayout: AppBarLayout? = null

    @JvmField
    @BindView(R.id.collapsing_toolbar)
    var toolbarLayout: CollapsingToolbarLayout? = null

    @JvmField
    @BindView(R.id.myFAB)
    var fab: FloatingActionButton? = null

    @JvmField
    @BindView(R.id.patient_avatar)
    var avatar: ImageView? = null

    @JvmField
    @BindView(R.id.patient_avatar_title)
    var avatarTitle: ImageView? = null

    @JvmField
    @BindView(R.id.check_all_image)
    var checkAllImage: ImageView? = null

    @JvmField
    @BindView(R.id.listView)
    var listView: RecyclerView? = null

    @JvmField
    @BindView(R.id.user_friendly_time)
    var friendlyTime: TextView? = null

    @JvmField
    @BindView(R.id.routines_list_item_hour)
    var hour: TextView? = null

    @JvmField
    @BindView(R.id.routines_list_item_minute)
    var minute: TextView? = null

    @JvmField
    @BindView(R.id.textView3)
    var takeMedsMessage: TextView? = null

    @JvmField
    @BindView(R.id.routine_name)
    var title: TextView? = null

    @JvmField
    @BindView(R.id.routine_name_title)
    var titleTitle: TextView? = null

    @JvmField
    @BindView(R.id.check_overlay)
    var checkAllOverlay: View? = null

    @JvmField
    @BindView(R.id.toolbar_title)
    var toolbarTitle: View? = null

    private var fromNotification = false
    private var isDistant = false
    private var isInWindow = false
    private var isRoutine = false
    private var isToday = false
    private var stateChanged = false
    private var itemAdapter: ConfirmItemAdapter? = null
    private val dateFormatter: DateTimeFormatter = DateTimeFormat.forPattern("dd/MM/YYYY")
    private val timeFormatter: DateTimeFormatter = DateTimeFormat.forPattern("HH:mm")
    private var checkedIcon: IconicsDrawable? = null
    private var uncheckedIcon: IconicsDrawable? = null
    private var color = 0
    private var position = -1
    private val items: MutableList<DailyScheduleItem> = ArrayList()
    private var date: LocalDate? = null
    private var time: LocalTime? = null
    private var patient: Patient? = null
    private var routine: Routine? = null
    private var schedule: Schedule? = null
    private var action: String? = null
    private var relativeTime = ""

    /*
     * Returns the intake margin interval
     */
    private fun getCheckMarginInterval(intakeTime: DateTime): Pair<DateTime, DateTime> {
        val checkMarginStr = PreferenceUtils.getString(
            PreferenceKeys.CONFIRM_CHECK_WINDOW_MARGIN,
            DEFAULT_CHECK_MARGIN.toString()
        )

        val checkMargin = checkMarginStr.toInt()

        val start = intakeTime.minusMinutes(30)
        val end = intakeTime.plusHours(checkMargin)
        return Pair(start, end)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.confirm, menu)

        val item = menu.findItem(R.id.action_delay)

        if (!isInWindow) {
            item.setVisible(false)
        } else {
            item.setIcon(
                IconicsDrawable(this)
                    .icon(CommunityMaterial.Icon.cmd_history)
                    .color(Color.WHITE)
                    .sizeDp(24)
            )
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (fromNotification) {
                    startActivity(Intent(this, StartActivity::class.java))
                    finish()
                } else {
                    supportFinishAfterTransition()
                }
                return true
            }

            R.id.action_delay -> {
                showDelayDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDelayDialog() {
        //TODO allow custom delay values
        val values = this.resources.getIntArray(R.array.delays_array_values)
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.notification_delay)
            .setItems(R.array.delays_array) { dialog, which ->
                val minutes = values[which]
                if (isRoutine) {
                    AlarmScheduler.instance()
                        .onUserDelayRoutine(routine, date, this@ConfirmActivity, minutes)
                } else {
                    AlarmScheduler.instance().onUserDelayHourlySchedule(
                        schedule,
                        time,
                        date,
                        this@ConfirmActivity,
                        minutes.toLong()
                    )
                }

                // get cancelled items and uncancel them
                for (item in items) {
                    if (!item.takenToday && item.timeTaken != null) {
                        item.timeTaken = null
                        DB.dailyScheduleItems().saveAndFireEvent(item)
                    }
                }

                val msg = this@ConfirmActivity.getString(
                    R.string.alarm_delayed_message,
                    minutes.toString()
                )
                Toast.makeText(this@ConfirmActivity, msg, Toast.LENGTH_SHORT).show()
                supportFinishAfterTransition()
            }
        builder.create().show()
    }

    fun getDisplayableDose(dose: Double, doseString: String, m: Medicine): String {
        return doseString + " " + m.presentation.units(resources, dose)
    }

    fun showEnsureConfirmDialog(listener: DialogInterface.OnClickListener?, uncheck: Boolean) {
        val builder = AlertDialog.Builder(this)
        val t = date!!.toDateTime(time)

        val title =
            if (t.isAfterNow) getString(R.string.intake_not_available) else getString(R.string.meds_from) + " " + date!!.toString(
                "EEEE dd"
            ) + " " + getString(
                R.string.at_time_connector
            ) + " " + time!!.toString(timeFormatter)

        val msg = if (t.isAfterNow) getString(R.string.confirm_future_intake_warning, relativeTime)
        else if (uncheck) getString(R.string.unconfirm_past_intake_warning, relativeTime)
        else getString(R.string.confirm_past_intake_warning)


        builder.setMessage(msg)
            .setCancelable(true)
            .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_history, R.color.black, 36))
            .setTitle(title)

        if (t.isAfterNow) {
            builder.setNegativeButton(getString(R.string.tutorial_understood)) { dialog, id -> dialog.cancel() }
        } else {
            builder.setPositiveButton(
                if (uncheck) getString(R.string.meds_unconfirm_ok) else getString(
                    R.string.meds_confirm_ok
                ), listener
            )
                .setNegativeButton(
                    if (uncheck) getString(R.string.meds_unconfirm_cancel) else getString(
                        R.string.meds_confirm_cancel
                    )
                ) { dialog, id -> dialog.cancel() }
        }

        val alert = builder.create()
        alert.show()
    }

    override fun onBackPressed() {
        if (fromNotification) finishAndRemoveTask()
        else super.onBackPressed()
    }

    private fun onClickFab() {
        var somethingChecked = false
        for (item in items) {
            if (!item.takenToday) {
                item.takenToday = true
                DB.dailyScheduleItems().saveAndUpdateStock(item, true)
                somethingChecked = true
            }
        }

        if (somethingChecked) {
            itemAdapter!!.notifyDataSetChanged()
            stateChanged = true
            fab!!.postDelayed({ animateAllChecked() }, 100)
            onAllChecked()
        } else {
            supportFinishAfterTransition()
        }
    }

    private fun moveArrowsDown(duration: Int) {
        checkAllImage!!.animate()
            .translationY(ScreenUtils.dpToPx(resources, 150f).toFloat())
            .setDuration(duration.toLong())
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    fun showRippleByApi(x: Int, y: Int) {
        val duration = 500
        val arrowDuration = 400

        checkAllOverlay!!.postDelayed({
            finishAndRemoveTask()
        }, (duration + 300).toLong())


        showRipple(x, y, duration)
        moveArrowsDown(arrowDuration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processIntent()
        setContentView(R.layout.activity_confirm)
        ButterKnife.bind(this)

        val window = window
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        isToday = LocalDate.now() == date
        isInWindow = AlarmScheduler.isWithinDefaultMargins(date!!.toDateTime(time))

        val dt = date!!.toDateTime(time)
        val now = DateTime.now()
        val interval = getCheckMarginInterval(dt)
        isDistant = !Interval(interval.first, interval.second).contains(now)

        color = AvatarMgr.colorsFor(resources, patient!!.avatar)[0]
        color = Color.parseColor("#263238")

        setupStatusBar(Color.TRANSPARENT)
        setupToolbar("", Color.TRANSPARENT, Color.WHITE)
        toolbar!!.setTitleTextColor(Color.WHITE)

        avatar!!.setImageResource(AvatarMgr.res(patient!!.avatar))
        avatarTitle!!.setImageResource(AvatarMgr.res(patient!!.avatar))
        titleTitle!!.text = patient!!.name
        title!!.text = if (isRoutine) routine!!.name else schedule!!.toReadableString(this)
        takeMedsMessage!!.text =
            if (isInWindow) getString(R.string.agenda_zoom_meds_time) else getString(
                R.string.meds_from
            ) + " " + date!!.toString("EEEE dd")

        relativeTime = DateUtils.getRelativeTimeSpanString(
            dt.millis,
            now.millis,
            5 * DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_ALL
        ).toString()

        hour!!.text = time!!.toString("HH:")
        minute!!.text = time!!.toString("mm")
        friendlyTime!!.text = String.format(
            "%s%s",
            relativeTime.substring(0, 1).uppercase(Locale.getDefault()),
            relativeTime.substring(1)
        )

        if (isDistant) {
            fab!!.backgroundTintList =
                ColorStateList.valueOf(resources.getColor(R.color.android_orange_dark))
        }

        fab!!.setImageDrawable(
            IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_check_all)
                .color(Color.WHITE)
                .sizeDp(24)
                .paddingDp(0)
        )


        checkAllImage!!.setImageDrawable(
            IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_check_all)
                .color(Color.WHITE)
                .sizeDp(100)
                .paddingDp(0)
        )

        fab!!.setOnClickListener {
            var somethingToCheck = false
            for (item in items) {
                if (!item.takenToday) {
                    somethingToCheck = true
                    break
                }
            }
            if (somethingToCheck) {
                if (isDistant) {
                    showEnsureConfirmDialog({ dialog, id -> onClickFab() }, false)
                } else {
                    onClickFab()
                }
            } else {
                Snack.show(resources.getString(R.string.all_meds_taken), this@ConfirmActivity)
            }
        }


        toolbarLayout!!.setContentScrimColor(patient!!.color)
        setupListView()

        if ("delay" == action) {
            if (isRoutine && routine != null) {
                ReminderNotification.cancel(
                    this, ReminderNotification.routineNotificationId(
                        routine!!.id.toInt()
                    )
                )
            } else if (schedule != null) {
                ReminderNotification.cancel(
                    this, ReminderNotification.scheduleNotificationId(
                        schedule!!.id.toInt()
                    )
                )
            }
            showDelayDialog()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // finish and restart with the new params
        finish()
        startActivity(intent)
    }

    protected fun onDailyAgendaItemCheck(v: ImageButton?) {
        val total = items.size
        var checked = 0

        for (i in items) {
            if (i.takenToday) checked++
        }

        if (checked == total) {
            onAllChecked()
        } else {
            if (isRoutine) {
                AlarmScheduler.instance().onDelayRoutine(routine, date, this@ConfirmActivity)
            } else {
                AlarmScheduler.instance()
                    .onDelayHourlySchedule(schedule, time, date, this@ConfirmActivity)
            }
        }
    }

    override fun onDestroy() {
        if (stateChanged) {
            eventBus().post(ConfirmStateChangeEvent(position))
        }
        super.onDestroy()
    }

    private fun animateAllChecked() {
        val width = appBarLayout!!.width
        val middle = width / 2
        val fabCentered = middle - fab!!.width / 2
        val translationX = fab!!.x.toInt() - fabCentered
        val translationY = ScreenUtils.dpToPx(resources, 150f)

        val rippleX = middle
        val rippleY = (fab!!.y + fab!!.height / 2).toInt() - translationY

        val arcAnimation: Animation =
            ArcTranslateAnimation(0f, -translationX.toFloat(), 0f, -translationY.toFloat())
        arcAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
            }

            override fun onAnimationEnd(animation: Animation) {
                showRippleByApi(rippleX, rippleY)
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })

        arcAnimation.interpolator = DecelerateInterpolator()
        arcAnimation.duration = 200
        arcAnimation.fillAfter = true
        fab!!.startAnimation(arcAnimation)
    }

    private fun showRipple(x: Int, y: Int, duration: Int) {
        LogUtil.d(TAG, "Ripple x,y [$x, $y]")
        checkAllOverlay!!.visibility = View.INVISIBLE
        // get the final radius for the clipping circle
        val finalRadius = hypot(
            checkAllOverlay!!.width.toDouble(),
            checkAllOverlay!!.height.toDouble()
        ).toInt()
        // create the animator for this view (the start radius is zero)
        val anim = ViewAnimationUtils.createCircularReveal(
            checkAllOverlay,
            x,
            y,
            (fab!!.width / 2).toFloat(),
            finalRadius.toFloat()
        )
        anim.interpolator = DecelerateInterpolator()
        // make the view visible and start the animation
        checkAllOverlay!!.visibility = View.VISIBLE
        anim.setDuration(duration.toLong()).start()
    }

    private fun setupListView() {
        loadItems()
        itemAdapter = ConfirmItemAdapter()
        val llm = LinearLayoutManager(this)
        listView!!.layoutManager = llm
        listView!!.adapter = itemAdapter
        listView!!.itemAnimator = DefaultItemAnimator()
    }

    private fun processIntent() {
        val i = intent
        val routineId = i.getLongExtra(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, -1)
        val scheduleId = i.getLongExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, -1)
        val dateStr = i.getStringExtra(CalendulaApp.INTENT_EXTRA_DATE)
        val timeStr = i.getStringExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_TIME)


        action = i.getStringExtra(CalendulaApp.INTENT_EXTRA_ACTION)
        position = i.getIntExtra(CalendulaApp.INTENT_EXTRA_POSITION, -1)

        fromNotification = position == -1

        if (dateStr != null) {
            date = LocalDate.parse(dateStr, dateFormatter)
        } else {
            // this should never happen, but, just in case, redirect to home and show error
            val intent = Intent(this, HomePagerActivity::class.java)
            intent.putExtra("invalid_notification_error", true)
            startActivity(intent)
            finish()
        }

        LogUtil.d(TAG, "$timeStr, $dateStr, $routineId, $scheduleId, $date")

        if (routineId != -1L) {
            isRoutine = true
            routine = Routine.findById(routineId).also {
                time = it.time
                patient = it.patient
            }
        } else {
            time = LocalTime.parse(timeStr, timeFormatter)
            schedule = Schedule.findById(scheduleId).also {
                patient = it.patient()
            }
        }
    }

    private fun loadItems() {
        if (isRoutine) {
            val rsi = routine!!.scheduleItems
            LogUtil.d(TAG, rsi.size.toString() + " items")
            for (si in rsi) {
                val item = DB.dailyScheduleItems().findByScheduleItemAndDate(si, date)
                if (item != null) items.add(item)
            }
        } else {
            items.add(DB.dailyScheduleItems().findBy(schedule, date, time))
        }

        for (i in items) {
            LogUtil.d(TAG, i.toString())
        }
    }

    private fun onAllChecked() {
        if (isRoutine) {
            AlarmScheduler.instance().onIntakeCompleted(routine, date, this)
        } else {
            AlarmScheduler.instance().onIntakeCompleted(schedule, time, date, this)
        }
    }

    private fun getCheckedIcon(color: Int): Drawable? {
        if (checkedIcon == null) {
            checkedIcon = IconicsDrawable(
                this,
                CommunityMaterial.Icon.cmd_checkbox_marked_circle_outline
            ) //cmd_checkbox_marked_outline
                .sizeDp(30)
                .paddingDp(0)
                .color(color)
        }
        return checkedIcon
    }

    private fun getUncheckedIcon(color: Int): Drawable? {
        if (uncheckedIcon == null) {
            uncheckedIcon = IconicsDrawable(
                this,
                CommunityMaterial.Icon.cmd_checkbox_blank_circle_outline
            ) //cmd_checkbox_blank_outline
                .sizeDp(30)
                .paddingDp(0)
                .color(color)
        }
        return uncheckedIcon
    }

    class ConfirmStateChangeEvent(position: Int) {
        var position: Int = -1

        init {
            this.position = position
        }
    }

    internal inner class ConfirmItemAdapter :
        RecyclerView.Adapter<ConfirmItemAdapter.ConfirmItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfirmItemViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.confirm_activity_list_item, parent, false)
            return ConfirmItemViewHolder(v)
        }

        override fun onBindViewHolder(holder: ConfirmItemViewHolder, position: Int) {
            val i = items[position]
            val si = i.scheduleItem
            val sid: Long = if (i.boundToSchedule()) i.schedule.id else si.schedule.id
            val s = DB.schedules().findById(sid)
            val m: Medicine = s.medicine()
            val p = m.presentation

            var status = getString(R.string.med_not_taken)
            if (i.timeTaken != null) {
                status =
                    (if (i.takenToday) getString(R.string.med_taken_at) else getString(R.string.med_cancelled_at)) + " " + i.timeTaken.toString(
                        "HH:mm"
                    ) + "h"
            }

            holder.med.text = m.name
            holder.dose.text = getDisplayableDose(
                (if (i.boundToSchedule()) s.dose() else si.dose).toDouble(),
                if (i.boundToSchedule()) s.displayDose() else si.displayDose(),
                m
            )
            holder.status.text = status
            holder.dailyScheduleItem = i
            updateCheckedStatus(p, i, holder)
        }

        override fun getItemCount(): Int {
            return items.size
        }

        private fun updateCheckedStatus(
            p: Presentation,
            i: DailyScheduleItem,
            h: ConfirmItemViewHolder
        ) {
            val medDrawable: Drawable = IconicsDrawable(this@ConfirmActivity)
                .icon(p.icon())
                .color(if (i.takenToday) Color.parseColor("#81c784") else Color.parseColor("#11000000"))
                .sizeDp(36)
                .paddingDp(0)

            val checkDrawable = if (i.takenToday) getCheckedIcon(Color.parseColor("#81c784"))
            else getUncheckedIcon(Color.parseColor("#11000000"))

            h.check.setImageDrawable(checkDrawable)
            h.icon.setImageDrawable(medDrawable)
        }

        internal inner class ConfirmItemViewHolder(itemView: View) : RecyclerView.ViewHolder(
            itemView
        ), View.OnClickListener {
            @BindView(R.id.med_item_name)
            lateinit var med: TextView

            @BindView(R.id.med_item_dose)
            lateinit var dose: TextView

            @BindView(R.id.med_item_status)
            lateinit var status: TextView

            @BindView(R.id.check_button)
            lateinit var check: ImageButton

            @BindView(R.id.imageView)
            lateinit var icon: ImageView

            lateinit var dailyScheduleItem: DailyScheduleItem

            init {
                ButterKnife.bind(this, itemView)
                check.setOnClickListener(this)
            }

            override fun onClick(view: View) {
                val taken = dailyScheduleItem.takenToday
                if (isDistant) {
                    showEnsureConfirmDialog({ dialogInterface, i ->
                        dailyScheduleItem.takenToday = !taken
                        DB.dailyScheduleItems().saveAndUpdateStock(dailyScheduleItem, true)
                        stateChanged = true
                        onDailyAgendaItemCheck(check)
                        notifyItemChanged(adapterPosition)
                    }, taken)
                } else {
                    dailyScheduleItem.takenToday = !taken
                    DB.dailyScheduleItems().saveAndUpdateStock(dailyScheduleItem, true)
                    stateChanged = true
                    onDailyAgendaItemCheck(check)
                    notifyItemChanged(adapterPosition)
                }
            }
        }
    }


    companion object {
        private const val DEFAULT_CHECK_MARGIN = 3
        private const val TAG = "ConfirmActivity"
    }
}
