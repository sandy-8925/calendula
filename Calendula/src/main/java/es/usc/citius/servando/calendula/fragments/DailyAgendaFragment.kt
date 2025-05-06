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

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AnyThread
import androidx.annotation.Keep
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import autodispose2.AutoDispose.autoDisposable
import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider.from
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.CalendulaApp.Companion.context
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.DailyAgendaRecyclerAdapter
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.activities.ConfirmActivity
import es.usc.citius.servando.calendula.activities.ConfirmActivity.ConfirmStateChangeEvent
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.databinding.FragmentDailyAgendaBinding
import es.usc.citius.servando.calendula.events.PersistenceEvents.IntakeConfirmedEvent
import es.usc.citius.servando.calendula.events.PersistenceEvents.ModelCreateOrUpdateEvent
import es.usc.citius.servando.calendula.events.PersistenceEvents.UserUpdateEvent
import es.usc.citius.servando.calendula.fragments.HomeProfileMgr.BackgroundUpdatedEvent
import es.usc.citius.servando.calendula.persistence.Routine
import es.usc.citius.servando.calendula.scheduling.AlarmIntentParams
import es.usc.citius.servando.calendula.scheduling.DailyAgenda.AgendaUpdatedEvent
import es.usc.citius.servando.calendula.util.BooleanSharedPrefsLiveData
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub.DailyAgendaItemStubElement
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.greenrobot.eventbus.Subscribe
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.LocalDate

/**
 * Daily agenda fragment
 */
class DailyAgendaFragment : Fragment() {
    private val viewModel: DailyAgendaFragmentViewModel by viewModels()

    private lateinit var rvAdapter: DailyAgendaRecyclerAdapter
    private lateinit var rvListener: DailyAgendaRecyclerListener

    private var emptyViewIcon: IIcon = IconUtils.randomNiceIcon()

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_fragment_daily_agenda, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_expand) {
            val isExpandedPrefVal = PreferenceUtils.getBoolean(PreferenceKeys.HOME_DAILYAGENDA_EXPANDED, isExpanded)
            PreferenceUtils.edit().putBoolean(PreferenceKeys.HOME_DAILYAGENDA_EXPANDED.key(), !isExpandedPrefVal).apply()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private val icAgendaLess by lazy {
        IconicsDrawable(requireContext())
        .icon(CommunityMaterial.Icon.cmd_unfold_less_horizontal)
        .color(Color.WHITE)
        .sizeDp(24)
    }

    private val icAgendaMore by lazy {
        IconicsDrawable(requireContext())
        .icon(CommunityMaterial.Icon.cmd_unfold_more_horizontal)
        .color(Color.WHITE)
        .sizeDp(24)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val expandMenuItem = menu.findItem(R.id.action_expand)
        val drawableToSet = if (!isExpanded) icAgendaMore else icAgendaLess
        expandMenuItem.setIcon(drawableToSet)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ):View = inflater.inflate(R.layout.fragment_daily_agenda, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDailyAgendaBinding.bind(view)
        setupRecyclerView(binding)
        setupEmptyView(binding)

        viewModel.expandedPrefLiveData.observe(viewLifecycleOwner) { expandedPrefVal ->
            requireActivity().invalidateOptionsMenu()
            if(rvAdapter.isExpanded == expandedPrefVal) toggleViewMode()
//            (activity as HomePagerActivity?)!!.appBarLayout.setExpanded(!expanded)
        }
        viewModel.itemsListLiveDate.observe(viewLifecycleOwner) {
            rvAdapter.items = it
            binding.rv.postDelayed({ showOrHideEmptyView(binding, !rvAdapter.isShowingSomething) }, 100)
        }
    }

    override fun onStart() {
        super.onStart()
        eventBus().register(this)
    }

    override fun onStop() {
        super.onStop()
        eventBus().unregister(this)
    }

    @Subscribe
    @Keep
    fun handleEvent(event: Any) {
        LogUtil.d(TAG, "handleEvent: " + event.javaClass.name)
        Completable.fromAction {
            when (event) {
                is BackgroundUpdatedEvent -> refresh()
                is ConfirmStateChangeEvent -> refreshPosition(event.position)
            }
        }.subscribeOn(AndroidSchedulers.mainThread())
        .to(autoDisposable<Unit>(from(viewLifecycleOwner, ON_STOP)))
        .subscribe({},{})
    }

    fun showOrHideEmptyView(binding: FragmentDailyAgendaBinding, show: Boolean) {
        if (show) binding.emptyViewPlaceholder.visibility = View.VISIBLE
        else binding.emptyViewPlaceholder.visibility = View.GONE
    }

    private fun toggleViewMode() {
        rvAdapter.toggleCollapseMode()
    }

    private fun refresh() {
        rvAdapter.notifyDataSetChanged()
    }

    private fun refreshPosition(position: Int) = rvAdapter.updatePosition(position)

    private val isExpanded: Boolean
        get() = viewModel.expandedPrefLiveData.value ?: true

    private fun setupRecyclerView(binding: FragmentDailyAgendaBinding) {
        val llm = LinearLayoutManager(requireContext())
        rvListener = DailyAgendaRecyclerListener(binding, llm)
        rvAdapter = DailyAgendaRecyclerAdapter(binding.rv, llm, requireActivity()).apply { setListener(rvListener) }
        binding.rv.let {
            it.layoutManager = llm
            it.adapter = rvAdapter
            it.itemAnimator = DefaultItemAnimator()
        }
    }

    private fun setupEmptyView(binding: FragmentDailyAgendaBinding) {
        val color = HomeProfileMgr.colorForCurrent(activity)
        val icon: Drawable = IconicsDrawable(context)
            .icon(emptyViewIcon)
            .color(color)
            .sizeDp(90)
            .paddingDp(0)
        binding.emptyViewPlaceholderParent.imageViewOk.setImageDrawable(icon)
    }

    private fun showConfirmActivity(view: View, item: DailyAgendaItemStub, position: Int) {
        val intent = Intent(context, ConfirmActivity::class.java)
        intent.putExtra(CalendulaApp.INTENT_EXTRA_POSITION, position)
        intent.putExtra(CalendulaApp.INTENT_EXTRA_DATE, item.date.toString("dd/MM/YYYY"))

        if (item.isRoutine) {
            intent.putExtra(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, item.id)
        } else {
            intent.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, item.id)
            intent.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_TIME, item.time.toString(AlarmIntentParams.TIME_FORMAT))
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
            ActivityCompat.startActivity(requireActivity(), intent, activityOptions.toBundle())
        } else {
            startActivity(intent)
        }
    }

    inner class DailyAgendaRecyclerListener(private val binding: FragmentDailyAgendaBinding, private val linearLayoutManager: LinearLayoutManager) : DailyAgendaRecyclerAdapter.EventListener {
        private var firstTime: DateTime? = null

        override fun onItemClick(v: View, item: DailyAgendaItemStub, position: Int) {
            showConfirmActivity(v, item, position)
        }

        override fun onBeforeToggleCollapse(expanded: Boolean, somethingVisible: Boolean) {
            val firstPosition = linearLayoutManager.findFirstVisibleItemPosition()
            firstTime = if (firstPosition >= 0 && firstPosition < rvAdapter.itemCount) rvAdapter.items[firstPosition].dateTime() else null

            LogUtil.d(TAG, "OnBeforeCollapse, somethingVisible is $somethingVisible")

            if (expanded) {
                showOrHideEmptyView(binding, false)
            } else if (somethingVisible) {
                showOrHideEmptyView(binding, false)
            } else {
                showOrHideEmptyView(binding, true)
            }
        }

        override fun onAfterToggleCollapse(expanded: Boolean, somethingVisible: Boolean) {
            if (expanded)
                Completable.timer(600, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnComplete { scrollTo(DateTime.now()) }
                    .to(autoDisposable<Unit>(from(viewLifecycleOwner, ON_DESTROY)))
                    .subscribe({},{})
        }

        private fun scrollTo(time: DateTime) {
            val position = rvAdapter.items.indexOfFirst { it.dateTime().isAfter(time) }
            if (position > 0) linearLayoutManager.smoothScrollToPosition(binding.rv, null, position - 1)
        }
    }

    companion object {
        private const val TAG = "DailyAgendaFragment"
    }
}

internal class DailyAgendaFragmentViewModel: ViewModel() {
    val expandedPrefLiveData = BooleanSharedPrefsLiveData(PreferenceUtils.instance().preferences(), PreferenceKeys.HOME_DAILYAGENDA_EXPANDED.toString())
    val itemsListLiveDate = ItemsListLiveData().apply { addCloseable(this) }
}

internal class ItemsListLiveData: LiveData<List<DailyAgendaItemStub>>(), Closeable {
    init {
        value = emptyList()
        notifyDataChange()
        eventBus().register((this))
    }

    @Subscribe
    @Keep
    fun handleEvent(event: Any) {
        when (event) {
            is ModelCreateOrUpdateEvent -> notifyDataChange()
            is IntakeConfirmedEvent -> notifyDataChange()
            is UserUpdateEvent -> notifyDataChange()
            is AgendaUpdatedEvent -> notifyDataChange()
            is ConfirmStateChangeEvent -> if(event.position == -1) notifyDataChange()
        }
    }

    @SuppressLint("CheckResult")
    @AnyThread
    private fun notifyDataChange() {
        //todo: Need to dedupe multiple calls to this function, make sure only one update/item list build is taking place at a time
        Single.fromCallable { postValue(buildItems()) }
            .subscribeOn(notifyDataScheduler)
            .subscribe({},{})
    }

    override fun close() {
        eventBus().unregister(this)
    }

    @WorkerThread
    private fun buildItems(): List<DailyAgendaItemStub> {
        val stubs = mutableListOf<DailyAgendaItemStub>()
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

                    lateinit var stub: DailyAgendaItemStub

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
                        stub = routineStubs[routine] as DailyAgendaItemStub
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
                    stub.meds.add(el)
                }
            }
        }

        for (date in dateStubs.keys) {
            val routineStubs: Map<Routine, DailyAgendaItemStub> = dateStubs[date] as Map<Routine, DailyAgendaItemStub>
            for (r in routineStubs.keys) {
                stubs.add(routineStubs[r] as DailyAgendaItemStub)
            }
        }

        addEmptyHours(stubs, DateTime.now().minusDays(1), max)
        stubs.sortWith(DailyAgendaItemStubComparator)

        return stubs
    }

    private fun addEmptyHours(stubs: MutableList<DailyAgendaItemStub>, min: DateTime, max: DateTime) {
        var min = min
        var max = max
        min = min.withTimeAtStartOfDay()
        max = max.withTimeAtStartOfDay().plusDays(1) // end of the day

        // add empty hours if there is not an item with the same hour
        var start = min
        while (start.isBefore(max)) {
            var exact = false
            for (item in stubs) {
                if (start == item.dateTime()) {
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

    companion object {
        private val notifyDataScheduler by lazy { Schedulers.from(Executors.newSingleThreadExecutor()) }
    }
}

private object DailyAgendaItemStubComparator: Comparator<DailyAgendaItemStub> {
    override fun compare(a: DailyAgendaItemStub, b: DailyAgendaItemStub): Int {
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