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

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.toPublisher
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import autodispose2.AutoDispose.autoDisposable
import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider.from
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.github.javiersantos.materialstyleddialogs.enums.Style
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.tabs.TabLayout
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import es.usc.citius.servando.calendula.activities.CalendarActivity
import es.usc.citius.servando.calendula.activities.LeftDrawerMgr
import es.usc.citius.servando.calendula.activities.MaterialIntroActivity
import es.usc.citius.servando.calendula.activities.MedicineInfoActivity
import es.usc.citius.servando.calendula.activities.RoutinesActivity
import es.usc.citius.servando.calendula.activities.ScheduleCreationActivity
import es.usc.citius.servando.calendula.activities.SchedulesHelpActivity
import es.usc.citius.servando.calendula.adapters.HomePageAdapter
import es.usc.citius.servando.calendula.adapters.HomePages
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.databinding.ActivityMainBinding
import es.usc.citius.servando.calendula.events.PersistenceEvents.ActiveUserChangeEvent
import es.usc.citius.servando.calendula.events.PersistenceEvents.DatabaseUpdateEvent
import es.usc.citius.servando.calendula.events.PersistenceEvents.ModelCreateOrUpdateEvent
import es.usc.citius.servando.calendula.events.PersistenceEvents.UserCreateEvent
import es.usc.citius.servando.calendula.events.PersistenceEvents.UserUpdateEvent
import es.usc.citius.servando.calendula.events.StockRunningOutEvent
import es.usc.citius.servando.calendula.fragments.HomeProfileMgr
import es.usc.citius.servando.calendula.fragments.MedicinesListFragment.OnMedicineSelectedListener
import es.usc.citius.servando.calendula.fragments.RoutinesListFragment
import es.usc.citius.servando.calendula.fragments.RoutinesListFragment.OnRoutineSelectedListener
import es.usc.citius.servando.calendula.fragments.ScheduleListFragment
import es.usc.citius.servando.calendula.fragments.ScheduleListFragment.OnScheduleSelectedListener
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.persistence.Patient
import es.usc.citius.servando.calendula.persistence.Routine
import es.usc.citius.servando.calendula.persistence.Schedule
import es.usc.citius.servando.calendula.scheduling.DailyAgenda.AgendaUpdatedEvent
import es.usc.citius.servando.calendula.settings.CalendulaSettingsActivity
import es.usc.citius.servando.calendula.util.BooleanSharedPrefsLiveData
import es.usc.citius.servando.calendula.util.FragmentUtils
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.stock.StockDisplayUtils.showStockRunningOutDialog
import es.usc.citius.servando.calendula.util.view.DisableableAppBarLayoutBehavior
import es.usc.citius.servando.calendula.util.view.ExpandableFAB
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.LinkedList
import java.util.Queue
import org.greenrobot.eventbus.Subscribe

class HomePagerActivity : CalendulaActivity(), OnRoutineSelectedListener, OnMedicineSelectedListener, OnScheduleSelectedListener {
    private val viewModel: HomePagerActivityViewModel by viewModels()
    val appBarLayout: AppBarLayout by lazy { binding.appbar }
    private val toolbarLayout: CollapsingToolbarLayout by lazy { binding.collapsingToolbar }
    val fab: ExpandableFAB by lazy { binding.addButton }
    private val userInfoFragment: View by lazy { binding.userInfoFragment }
    private val toolbarTitle: TextView by lazy { binding.toolbarTitle }
    private val mViewPager: ViewPager by lazy { binding.container }
    private val tabLayout: TabLayout by lazy { binding.slidingTabs }

    private var appBarLayoutExpanded = true
    private var active = false
    private var icAgendaMore: Drawable? = null
    private var icAgendaLess: Drawable? = null
    private var fabMgr: FabMenuMgr? = null
    private val homeProfileMgr by lazy { HomeProfileMgr() }
    private val drawerMgr: LeftDrawerMgr by lazy { LeftDrawerMgr(this, toolbar) }
    private var activePatient: Patient? = null
    private val pendingEvents: Queue<Any> = LinkedList()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private lateinit var menuItems: SparseArray<MenuItem>

    @ColorInt
    private var previousColor = -1

    @JvmOverloads fun showPagerItem(position: Int, updateDrawer: Boolean = true) {
        if (position >= 0 && position < mViewPager.childCount) {
            mViewPager.currentItem = position
            if (updateDrawer) {
                drawerMgr.onPagerPositionChange(position)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        menuItems = SparseArray(menu.size())
        for (menuItem in MENU_ITEMS) {
            menuItems.put(menuItem, menu.findItem(menuItem))
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val pageNum = mViewPager.currentItem
        val page = HomePages.getPage(pageNum)

        // hide all items first
        for (menuItem in MENU_ITEMS) {
            menuItems[menuItem].setVisible(false)
        }

        when (page) {
            HomePages.SCHEDULES -> menuItems[R.id.action_schedules_help].setVisible(true)
            else -> {}
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_calendar -> {
                startActivity(Intent(this, CalendarActivity::class.java))
                return true
            }

            R.id.action_schedules_help -> {
                launchActivity(Intent(this, SchedulesHelpActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun launchActivityDelayed(activityClazz: Class<*>?, delay: Int) {
        Handler().postDelayed({
            startActivity(Intent(this@HomePagerActivity, activityClazz))
            overridePendingTransition(0, 0)
        }, delay.toLong())
    }

    override fun onRoutineSelected(r: Routine) {
        val i = Intent(this, RoutinesActivity::class.java)
        i.putExtra(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, r.id)
        launchActivity(i)
    }

    override fun onCreateRoutine() {
        //do nothing
    }

    override fun onMedicineSelected(m: Medicine) {
        val i = Intent(this, MedicineInfoActivity::class.java)
        i.putExtra(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, m.id)
        launchActivity(i)
    }

    override fun onCreateMedicine() {
        //do nothing
    }

    override fun onScheduleSelected(r: Schedule) {
        val i = Intent(this, ScheduleCreationActivity::class.java)
        i.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, r.id)
        launchActivity(i)
    }

    override fun onCreateSchedule() {
    }

    // Method called from the event bus
    @Subscribe fun handleEvent(event: Any) {
        if (active) {
            handler.post {
                if (event is ModelCreateOrUpdateEvent) {
                    LogUtil.d(TAG, "handleEvent: " + event.clazz.name)
                    (getViewPagerFragment(HomePages.ROUTINES) as RoutinesListFragment?)!!.notifyDataChange()
                    (getViewPagerFragment(HomePages.SCHEDULES) as ScheduleListFragment?)!!.notifyDataChange()
                } else if (event is ActiveUserChangeEvent) {
                    activePatient = event.patient
                    updateTitle(mViewPager.currentItem)
                    activePatient?.let { toolbarLayout.setContentScrimColor(it.color) }
                    fabMgr!!.onPatientUpdate(activePatient)
                } else if (event is UserUpdateEvent) {
                    val p = event.patient
                    drawerMgr.onPatientUpdated(p)
                    if (DB.patients().isActive(p, this@HomePagerActivity)) {
                        activePatient = p
                        updateTitle(mViewPager.currentItem)
                        toolbarLayout.setContentScrimColor(activePatient!!.color)
                        fabMgr!!.onPatientUpdate(activePatient)
                    }
                } else if (event is UserCreateEvent) {
                    val created = event.patient
                    drawerMgr.onPatientCreated(created)
                } else if (event is AgendaUpdatedEvent) {
                    homeProfileMgr.updateDate()
                } else if (event is StockRunningOutEvent) {
                    handler.postDelayed({ showStockRunningOutDialog(this@HomePagerActivity, event.m, event.days) }, 1000)
                } else if (event is DatabaseUpdateEvent) {
                    checkDatabaseUpdateNeeded()
                }
            }
        } else {
            pendingEvents.add(event)
        }
    }

    /**
     * If the app has been updated from an old Drug DB model, we need to re-install it and re-link the meds to their prescription.
     */
    private fun checkDatabaseUpdateNeeded() {
        val needPrompt = PreferenceUtils.getBoolean(PreferenceKeys.DRUGDB_DB_PROMPT, false)
        if (needPrompt) {
            MaterialStyledDialog.Builder(this)
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_database, R.color.white, 100))
                .setHeaderColor(R.color.android_blue)
                .withDialogAnimation(true)
                .setTitle(R.string.enable_prescriptions_dialog_title)
                .setDescription(R.string.reinstall_prescriptions_dialog_message)
                .setCancelable(false)
                .setPositiveText(getString(R.string.dialog_yes_option))
                .onPositive { dialog, which ->
                    val i = Intent(this@HomePagerActivity, CalendulaSettingsActivity::class.java)
                    i.putExtra(CalendulaSettingsActivity.EXTRA_SHOW_DB_DIALOG, true)
                    startActivity(i)
                    PreferenceUtils.edit().remove(PreferenceKeys.DRUGDB_DB_PROMPT.key()).apply()
                }
                .setNegativeText(R.string.dialog_no_option)
                .onNegative { dialog, which ->
                    dialog.cancel()
                    PreferenceUtils.edit().remove(PreferenceKeys.DRUGDB_DB_PROMPT.key()).apply()
                }
                .show()
        }
    }

    private fun getViewPagerFragment(page: HomePages): Fragment? {
        val tag = FragmentUtils.makeViewPagerFragmentName(R.id.container, page.ordinal)
        return supportFragmentManager.findFragmentByTag(tag)
    }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
//        ButterKnife.bind(this)

        setupToolbar(null, Color.TRANSPARENT)
        initializeDrawer(savedInstanceState)
        setupStatusBar(Color.TRANSPARENT)

        // Set up home profile
        homeProfileMgr.init(userInfoFragment, this)

        subscribeToEvents()

        // Set up the ViewPager with the sections adapter.
        mViewPager.adapter = HomePageAdapter(supportFragmentManager, this, this)
        mViewPager.addOnPageChangeListener(pageChangeListener)
        mViewPager.offscreenPageLimit = 5

        activePatient = DB.patients().getActive(this)
        updateScrim(0)


        fabMgr = FabMenuMgr(fab, drawerMgr, this)
        fabMgr!!.init()

        fabMgr!!.onPatientUpdate(activePatient)


        // Setup the tabLayout
        setupTabLayout()

        val mListener =
            OnOffsetChangedListener { appBarLayout, verticalOffset -> //LogUtil.d(TAG, "Values: (" + toolbarLayout.getHeight()+ " + " +verticalOffset + ") < (2 * " + ViewCompat.getMinimumHeight(toolbarLayout) + ")");
                if ((toolbarLayout.height + verticalOffset) < (1.8 * ViewCompat.getMinimumHeight(toolbarLayout))) {
                    homeProfileMgr.onCollapse()
                    toolbarTitle.animate().alpha(1f)
                    appBarLayoutExpanded = false
                    LogUtil.d(TAG, "OnCollapse")
                } else {
                    appBarLayoutExpanded = true
                    if (mViewPager.currentItem == 0) {
                        toolbarTitle.animate().alpha(0f)
                    }
                    homeProfileMgr.onExpand()
                    LogUtil.d(TAG, "OnExpand")
                }
            }
        appBarLayout.addOnOffsetChangedListener(mListener)

        icAgendaLess = IconicsDrawable(this)
            .icon(CommunityMaterial.Icon.cmd_unfold_less_horizontal)
            .color(Color.WHITE)
            .sizeDp(24)

        icAgendaMore = IconicsDrawable(this)
            .icon(CommunityMaterial.Icon.cmd_unfold_more_horizontal)
            .color(Color.WHITE)
            .sizeDp(24)

        if (!PreferenceUtils.getBoolean(PreferenceKeys.HOME_INTRO_SHOWN, false)) {
            handler.postDelayed({ launchActivity(Intent(this@HomePagerActivity, MaterialIntroActivity::class.java)) }, 500)
        }

        if (intent != null && intent.getBooleanExtra("invalid_notification_error", false)) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
            handler.postDelayed({ showInvalidNotificationError() }, 500)
        }

        //check for DB update needed
        checkDatabaseUpdateNeeded()

        val expandedPrefObservable = Observable.fromPublisher(viewModel.expandedPrefLiveData.toPublisher(this)).startWithItem(viewModel.expandedPrefLiveData.value ?: false)
        val pagePositionObservable = pagePositionPubSub.startWithItem(HomePages.HOME)

        Observable.combineLatest(expandedPrefObservable, pagePositionObservable) { expandedPrefVal, pagePosition -> if(pagePosition == HomePages.HOME) expandedPrefVal else false }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                val disableableAppBarLayoutBehavior = (appBarLayout.layoutParams as CoordinatorLayout.LayoutParams).behavior as DisableableAppBarLayoutBehavior
                appBarLayout.setExpanded(it)
                disableableAppBarLayoutBehavior.isEnabled = it
            }.to(autoDisposable(from(this)))
            .subscribe({}, {})
    }

    private val pagePositionPubSub = PublishSubject.create<HomePages>()

    override fun onResume() {
        super.onResume()
        val p = DB.patients().getActive(this)
        drawerMgr.onActivityResume(p)
        active = true

        // process pending events
        while (!pendingEvents.isEmpty()) {
            LogUtil.d(TAG, "Processing pending event...")
            handleEvent(pendingEvents.poll())
        }
    }


    // Interface implementations
    override fun onPause() {
        active = false
        super.onPause()
    }

    private fun showInvalidNotificationError() {

        AlertDialog.Builder(this)
            .setTitle(R.string.notification_error_title)
            .setMessage(R.string.notification_error_msg)
            .setCancelable(true)
            .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_bug, R.color.black))
            .setPositiveButton(R.string.tutorial_understood) { dialog, which ->
                dialog.dismiss()
//                val expanded = (getViewPagerFragment(HomePages.HOME) as DailyAgendaFragment?)!!.isExpanded
//                if (!expanded) {
//                    appBarLayout.setExpanded(false)
//                    menuItems[R.id.action_expand].setIcon(icAgendaLess)
//                    handler.postDelayed({ (getViewPagerFragment(HomePages.HOME) as DailyAgendaFragment?)!!.toggleViewMode() }, 200)
//                    handler.postDelayed({ (getViewPagerFragment(HomePages.HOME) as DailyAgendaFragment?)!!.scrollTo(DateTime.now()) }, 600)
//                }
            }.create().show()
    }

    private fun setupTabLayout() {
        tabLayout.setupWithViewPager(mViewPager)

        for (i in 0 until tabLayout.tabCount) {
            val icon: Drawable = IconicsDrawable(this)
                .icon(HomePages.entries[i].icon)
                .alpha(80)
                .paddingDp(2)
                .color(Color.WHITE)
                .sizeDp(24)

            tabLayout.getTabAt(i)!!.setIcon(icon)
        }
    }

    private val pageChangeListener: OnPageChangeListener
        get() = object : OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageSelected(position: Int) {
                updateTitle(position)
                updateScrim(position)
                fabMgr?.onViewPagerItemChange(position)
                val homepage = HomePages.entries.find { it.ordinal == position } ?: HomePages.HOME
                pagePositionPubSub.onNext(homepage)
                invalidateOptionsMenu()
            }

            override fun onPageScrollStateChanged(state: Int) = Unit
        }

    private fun updateScrim(position: Int) {
        @ColorInt val color = if ((position == HomePages.HOME.ordinal)) ContextCompat.getColor(this, R.color.transparent_black) else activePatient!!.color

        if (previousColor != -1) {
            val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), previousColor, color)
            colorAnimation.setDuration(250) // milliseconds
            colorAnimation.addUpdateListener { animator -> toolbarLayout.setContentScrimColor(animator.animatedValue as Int) }
            colorAnimation.start()
        } else {
            toolbarLayout.setContentScrimColor(color)
        }
        previousColor = color
    }

    private fun updateTitle(page: Int) {
        val title = if (page == HomePages.HOME.ordinal) {
            getString(R.string.app_name)
        } else {
            getString(R.string.relation_user_possession_thing, activePatient!!.name, getString(HomePages.entries[page].title))
        }

        toolbarTitle.text = title
    }

    private fun initializeDrawer(savedInstanceState: Bundle?) {
        drawerMgr.init(savedInstanceState)
    }

    private fun launchActivity(i: Intent) {
        startActivity(i)
        this.overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "HomePagerActivity"

        @IdRes
        private val MENU_ITEMS = intArrayOf(R.id.action_calendar, R.id.action_schedules_help)
    }
}

internal class HomePagerActivityViewModel: ViewModel() {
    val expandedPrefLiveData = BooleanSharedPrefsLiveData(PreferenceUtils.instance().preferences(), PreferenceKeys.HOME_DAILYAGENDA_EXPANDED.toString())
}