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
package es.usc.citius.servando.calendula.fragments

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.DailyAgendaRecyclerAdapter
import es.usc.citius.servando.calendula.HomePagerActivity
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.activities.ConfirmActivity
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.fragments.HomeProfileMgr.BackgroundUpdatedEvent
import es.usc.citius.servando.calendula.persistence.Routine
import es.usc.citius.servando.calendula.scheduling.AlarmIntentParams
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub.DailyAgendaItemStubElement
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import java.util.Collections
import org.greenrobot.eventbus.Subscribe
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.LocalDate

/**
 * Daily agenda fragment
 */
class DailyAgendaFragment : Fragment() {
    var emptyView: View? = null

    var llm: LinearLayoutManager? = null

    var rv: RecyclerView? = null
    var rvAdapter: DailyAgendaRecyclerAdapter? = null
    var rvListener: DailyAgendaRecyclerListener? = null

    var items: MutableList<DailyAgendaItemStub?> = ArrayList()

    var emptyViewIcon: IIcon = IconUtils.randomNiceIcon()

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_fragment_daily_agenda, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_expand) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val expandMenuItem = menu.findItem(R.id.action_expand)

        val icAgendaLess = IconicsDrawable(requireContext())
            .icon(CommunityMaterial.Icon.cmd_unfold_less_horizontal)
            .color(Color.WHITE)
            .sizeDp(24)

        val icAgendaMore = IconicsDrawable(requireContext())
            .icon(CommunityMaterial.Icon.cmd_unfold_more_horizontal)
            .color(Color.WHITE)
            .sizeDp(24)
        val drawableToSet = if (!isExpanded) icAgendaMore
        else icAgendaLess
        expandMenuItem.setIcon(drawableToSet)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        items = ArrayList()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_daily_agenda, container, false)
        rv = rootView.findViewById<View>(R.id.rv) as RecyclerView
        emptyView = rootView.findViewById(R.id.empty_view_placeholder)
        eventBus().register(this)
        setupRecyclerView()
        setupEmptyView()

        val expanded = PreferenceUtils.getBoolean(PreferenceKeys.HOME_DAILYAGENDA_EXPANDED, false)
        if (expanded != isExpanded) {
            toggleViewMode()
            (activity as HomePagerActivity?)!!.appBarLayout.setExpanded(!expanded)
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        eventBus().unregister(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        notifyDataChange()
    }

    fun buildItems(): List<DailyAgendaItemStub?> {
        val stubs: MutableList<DailyAgendaItemStub?> = ArrayList()

        val daily = DB.dailyScheduleItems().findAll()

        var max = DateTime.now().withTimeAtStartOfDay()
        var min = DateTime.now().withTimeAtStartOfDay()

        // create stubs for hourly schedule items
        for (dailyScheduleItem in daily) {
            if (dailyScheduleItem.boundToSchedule()) {
                val schedule = dailyScheduleItem.schedule
                val medicine = schedule.medicine()
                val time = dailyScheduleItem.time
                val date = dailyScheduleItem.date

                // create a stub for this item
                val stub = DailyAgendaItemStub(date, time)
                stub.isRoutine = false
                stub.meds = ArrayList()
                stub.hasEvents = true
                stub.id = schedule.id
                stub.patient = schedule.patient()
                stub.isRoutine = false
                stub.title = schedule.toReadableString(context)
                stub.time = time

                // create a element for the schedule item
                val el = DailyAgendaItemStubElement()
                el.medName = medicine.name
                el.dose = schedule.dose().toDouble()
                el.displayDose = schedule.displayDose()
                el.presentation = medicine.presentation
                el.minute = time.toString("mm")
                el.taken = dailyScheduleItem.takenToday
                stub.meds.add(el)
                stubs.add(stub)

                val candidate = stub.date.toDateTime(stub.time)
                if (candidate.isAfter(max)) {
                    max = candidate
                }
                if (candidate.isBefore(min)) {
                    min = candidate
                }
            }
        }

        val dateStubs: MutableMap<LocalDate, MutableMap<Routine, DailyAgendaItemStub>> = HashMap()
        // create stubs for routine items
        for (routine in DB.routines().findAll()) {
            for (scheduleItem in routine.scheduleItems) {
                // get item from daily agenda if exists
                val dailyScheduleItems = DB.dailyScheduleItems().findAllByScheduleItem(scheduleItem)

                for (dailyScheduleItem in dailyScheduleItems) {
                    // break if not, this means is not enabled for today

                    if (dailyScheduleItem == null) {
                        break
                    }

                    val date = dailyScheduleItem.date

                    if (!dateStubs.containsKey(date)) {
                        dateStubs[date] = HashMap()
                    }

                    val routineStubs = dateStubs[date]!!

                    var stub: DailyAgendaItemStub?

                    val time = routine.time

                    if (!routineStubs.containsKey(routine)) {
                        // create a new stub and add it to the list
                        stub = DailyAgendaItemStub(date, routine.time)
                        stub.isRoutine = true
                        stub.id = routine.id
                        stub.patient = routine.patient
                        stub.title = routine.name
                        stub.time = time
                        stub.meds = ArrayList()
                        stub.hasEvents = true
                        routineStubs[routine] = stub

                        val candidate = stub.date.toDateTime(stub.time)
                        if (candidate.isAfter(max)) {
                            max = candidate
                        }
                        if (candidate.isBefore(min)) {
                            min = candidate
                        }
                    } else {
                        stub = routineStubs[routine]
                    }

                    val schedule = scheduleItem.schedule
                    val medicine = schedule.medicine()

                    val el = DailyAgendaItemStubElement()
                    // add element properties
                    el.medName = medicine.name
                    el.dose = scheduleItem.dose.toDouble()
                    el.scheduleItemId = scheduleItem.id
                    el.displayDose = scheduleItem.displayDose()
                    el.presentation = medicine.presentation
                    el.minute = time.toString("mm")
                    el.taken = dailyScheduleItem.takenToday
                    stub!!.meds.add(el)
                }
            }
        }

        for (date in dateStubs.keys) {
            val routineStubs: Map<Routine, DailyAgendaItemStub> = dateStubs[date]!!
            for (r in routineStubs.keys) {
                stubs.add(routineStubs[r])
            }
        }

        addEmptyHours(stubs, DateTime.now().minusDays(1), max)
        Collections.sort(stubs, DailyAgendaItemStubComparator)

        return stubs
    }

    fun addEmptyHours(stubs: MutableList<DailyAgendaItemStub?>, min: DateTime, max: DateTime) {
        var min = min
        var max = max
        min = min.withTimeAtStartOfDay()
        max = max.withTimeAtStartOfDay().plusDays(1) // end of the day

        // add empty hours if there is not an item with the same hour
        var start = min
        while (start.isBefore(max)) {
            var exact = false
            for (item in stubs) {
                if (start == item!!.dateTime()) {
                    exact = true
                    break
                }
            }


            val hour = Interval(start, start.plusHours(1))
            if (!exact || hour.contains(DateTime.now())) {
                stubs.add(DailyAgendaItemStub(start.toLocalDate(), start.toLocalTime()))
            }

            if (start.hourOfDay == 0) {
                val spacer = DailyAgendaItemStub(start.toLocalDate(), start.toLocalTime())
                spacer.isSpacer = true
                stubs.add(spacer)
            }
            start = start.plusHours(1)
        }
    }

    fun showOrHideEmptyView(show: Boolean) {
        if (show) {
            emptyView!!.visibility = View.VISIBLE
            //emptyView.animate().alpha(1);
        } else {
            emptyView!!.visibility = View.GONE

            //            emptyView.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
//                @Override
//                public void onAnimationEnd(Animator animation) {
//
//                }
//            });
        }
    }

    fun toggleViewMode() {
        rvAdapter!!.toggleCollapseMode()
    }

    fun refresh() {
        rvAdapter!!.notifyDataSetChanged()
    }

    fun refreshPosition(position: Int) {
        if (position == -1) {
            notifyDataChange()
        } else if (position >= 0 && position < items.size) {
            rvAdapter!!.updatePosition(position)
        }
    }

    fun scrollTo(time: DateTime?) {
        var position = 0
        for (stub in items) {
            if (stub!!.dateTime().isAfter(time)) {
                break
            }
            position++
        }

        if (position > 0) llm!!.smoothScrollToPosition(rv, null, position - 1)
    }

    val isExpanded: Boolean
        get() = rvAdapter!!.isExpanded

    fun notifyDataChange() {
        try {
            LogUtil.d(TAG, "AgendaView NotifyDataChange")
            items.clear()
            items.addAll(buildItems())
            LogUtil.d(TAG, "Items after rebuild " + items.size)
            rvAdapter!!.notifyDataSetChanged()
            // show empty list view if there are no items
            rv!!.postDelayed({ showOrHideEmptyView(!rvAdapter!!.isShowingSomething) }, 100)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error onPostExecute", e)
        }
    }

    fun onUserUpdate() {
        notifyDataChange()
    }

    // Method called from the event bus
    @Subscribe fun handleBackgroundUpdatedEvent(event: BackgroundUpdatedEvent?) {
        Handler().postDelayed({ onBackgroundChange(HomeProfileMgr.colorForCurrent(activity)) }, 500)
    }

    private fun setupRecyclerView() {
        llm = LinearLayoutManager(context)
        rv!!.layoutManager = llm
        rvAdapter = DailyAgendaRecyclerAdapter(items, rv, llm, activity)
        rv!!.adapter = rvAdapter
        rv!!.itemAnimator = DefaultItemAnimator()

        rvListener = DailyAgendaRecyclerListener()

        rvAdapter!!.setListener(rvListener)
    }

    private fun setupEmptyView() {
        val color = HomeProfileMgr.colorForCurrent(activity)
        val icon: Drawable = IconicsDrawable(context)
            .icon(emptyViewIcon)
            .color(color)
            .sizeDp(90)
            .paddingDp(0)
        (emptyView!!.findViewById<View>(R.id.imageView_ok) as ImageView).setImageDrawable(icon)
    }

    private fun showConfirmActivity(view: View, item: DailyAgendaItemStub, position: Int) {
        val i = Intent(context, ConfirmActivity::class.java)
        i.putExtra(CalendulaApp.INTENT_EXTRA_POSITION, position)
        i.putExtra(CalendulaApp.INTENT_EXTRA_DATE, item.date.toString("dd/MM/YYYY"))

        if (item.isRoutine) {
            i.putExtra(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, item.id)
        } else {
            i.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, item.id)
            i.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_TIME, item.time.toString(AlarmIntentParams.TIME_FORMAT))
        }

        val v1 = view.findViewById<View>(R.id.patient_avatar)
        val v2 = view.findViewById<View>(R.id.linearLayout)
        val v3 = view.findViewById<View>(R.id.routines_list_item_name)

        if (v1 != null && v2 != null && v3 != null) {
            val activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                Pair(v1, "avatar_transition"),
                Pair(v2, "time"),
                Pair(v3, "title")
            )
            ActivityCompat.startActivity(requireActivity(), i, activityOptions.toBundle())
        } else {
            startActivity(i)
        }
    }

    private fun onBackgroundChange(color: Int) {
        val icon: Drawable = IconicsDrawable(context)
            .icon(emptyViewIcon)
            .color(color)
            .sizeDp(90)
            .paddingDp(0)
        (emptyView!!.findViewById<View>(R.id.imageView_ok) as ImageView).setImageDrawable(icon)
    }

    private object DailyAgendaItemStubComparator : Comparator<DailyAgendaItemStub?> {
        override fun compare(a: DailyAgendaItemStub?, b: DailyAgendaItemStub?): Int {
            if(a==null && b==null) return 0;
            else if (a==null) return 1;
            else if(b==null) return -1;

            val aT = a.date.toDateTime(a.time)
            val bT = b.date.toDateTime(b.time)

            if (aT.compareTo(bT) == 0 && a.isSpacer) {
                return -1
            } else if (aT.compareTo(bT) == 0 && b.isSpacer) {
                return 1
            } else if (aT.compareTo(bT) == 0) {
                return if (a.hasEvents) -1 else 1
            }
            return aT.compareTo(bT)
        }
    }

    inner class DailyAgendaRecyclerListener : DailyAgendaRecyclerAdapter.EventListener {
        var firstTime: DateTime? = null

        override fun onItemClick(v: View, item: DailyAgendaItemStub, position: Int) {
            showConfirmActivity(v, item, position)
        }

        override fun onBeforeToggleCollapse(expanded: Boolean, somethingVisible: Boolean) {
            val firstPosition = llm!!.findFirstVisibleItemPosition()
            firstTime = if (firstPosition >= 0 && firstPosition < items.size) items[firstPosition]!!.dateTime() else null

            LogUtil.d(TAG, "OnBeforeCollapse, somethingVisible is $somethingVisible")

            if (expanded) {
                showOrHideEmptyView(false)
            } else if (!expanded && somethingVisible) {
                showOrHideEmptyView(false)
            } else {
                showOrHideEmptyView(true)
            }
        }

        override fun onAfterToggleCollapse(expanded: Boolean, somethingVisible: Boolean) {
            PreferenceUtils.edit().putBoolean(PreferenceKeys.HOME_DAILYAGENDA_EXPANDED.key(), expanded).apply()
            /*if (expanded && firstTime != null) {
                scrollTo(firstTime);
                firstTime = null;
            } else */
            if (expanded) {
                Handler().postDelayed({ scrollTo(DateTime.now()) }, 600)
            }
        }
    }

    companion object {
        private const val TAG = "DailyAgendaFragment"
    }
}