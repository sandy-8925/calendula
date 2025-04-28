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

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile
import es.usc.citius.servando.calendula.HomePagerActivity
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.adapters.HomePages
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.modules.ModuleManager
import es.usc.citius.servando.calendula.modules.modules.AllergiesModule
import es.usc.citius.servando.calendula.persistence.Patient
import es.usc.citius.servando.calendula.settings.CalendulaSettingsActivity
import es.usc.citius.servando.calendula.util.AvatarMgr
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.ScreenUtils

class LeftDrawerMgr(private val homeActivity: HomePagerActivity, private val toolbar: Toolbar) {
    private lateinit var headerResult: AccountHeader
    private lateinit var drawer: Drawer
    private var currentPatient: Patient? = null

    fun init(savedInstanceState: Bundle?) {
        headerResult = AccountHeaderBuilder()
            .withActivity(homeActivity)
            .withHeaderBackground(R.drawable.drawer_header)
            .withHeaderBackgroundScaleType(ImageView.ScaleType.CENTER_CROP)
            .withCompactStyle(false)
            .withProfiles(genProfiles())
            .withAlternativeProfileHeaderSwitching(true)
            .withThreeSmallProfileImages(true)
            .withOnAccountHeaderListener(this)
            .withSavedInstance(savedInstanceState)
            .build()

        //Create the drawer
        val b: DrawerBuilder = DrawerBuilder()
            .withActivity(homeActivity)
            .withFullscreen(true)
            .withToolbar(toolbar)
            .withAccountHeader(headerResult)
            .withOnDrawerItemClickListener(this)
            .withDelayOnDrawerClose(70)
            .withStickyFooterShadow(true)
            .withScrollToTopAfterClick(true)
            .withSavedInstance(savedInstanceState)

        b.addDrawerItems(
            PrimaryDrawerItem()
                .withName(R.string.title_home)
                .withIcon(
                    IconUtils.icon(homeActivity, GoogleMaterial.Icon.gmd_home, R.color.black)
                        .alpha(110)
                )
                .withIdentifier(HOME),
            PrimaryDrawerItem()
                .withName(R.string.title_activity_patients)
                .withIcon(
                    IconUtils.icon(
                        homeActivity,
                        CommunityMaterial.Icon.cmd_account_multiple,
                        R.color.black
                    ).alpha(110)
                )
                .withIdentifier(PATIENTS),
            DividerDrawerItem(),
            PrimaryDrawerItem()
                .withName(R.string.title_activity_medicines)
                .withIcon(
                    IconUtils.icon(
                        homeActivity,
                        CommunityMaterial.Icon.cmd_pill,
                        R.color.black
                    ).alpha(110)
                )
                .withIdentifier(MEDICINES),
            PrimaryDrawerItem()
                .withName(R.string.title_activity_routines)
                .withIcon(
                    IconUtils.icon(homeActivity, GoogleMaterial.Icon.gmd_alarm, R.color.black)
                        .alpha(110)
                )
                .withIdentifier(ROUTINES),
            PrimaryDrawerItem()
                .withName(R.string.title_activity_schedules)
                .withIcon(
                    IconUtils.icon(
                        homeActivity,
                        GoogleMaterial.Icon.gmd_calendar,
                        R.color.black
                    ).alpha(110)
                )
                .withIdentifier(SCHEDULES)
        )
        if (ModuleManager.isEnabled(AllergiesModule.ID)) {
            b.addDrawerItems(
                PrimaryDrawerItem()
                    .withName(R.string.home_menu_allergies)
                    .withIcon(
                        IconUtils.icon(
                            homeActivity,
                            CommunityMaterial.Icon.cmd_alert,
                            R.color.black
                        ).alpha(110)
                    )
                    .withIdentifier(ALLERGIES)
            )
        }

        b.addDrawerItems(
            DividerDrawerItem(),
            PrimaryDrawerItem()
                .withName(R.string.drawer_help_option)
                .withIcon(
                    IconUtils.icon(
                        homeActivity,
                        GoogleMaterial.Icon.gmd_pin_assistant,
                        R.color.black
                    ).alpha(130)
                )
                .withIdentifier(HELP),
            PrimaryDrawerItem()
                .withName(R.string.drawer_settings_option)
                .withIcon(
                    IconUtils.icon(
                        homeActivity,
                        CommunityMaterial.Icon.cmd_settings,
                        R.color.black
                    ).alpha(110)
                )
                .withIdentifier(SETTINGS),
            DividerDrawerItem(),
            PrimaryDrawerItem()
                .withName(R.string.drawer_about_option)
                .withIcon(
                    IconUtils.icon(
                        homeActivity,
                        CommunityMaterial.Icon.cmd_information,
                        R.color.black
                    ).alpha(110)
                )
                .withIdentifier(ABOUT)
        )

        drawer = b.build()

        val p = DB.patients().getActive(homeActivity)
        headerResult.setActiveProfile(p.id, false)
        updateHeaderBackground(p)
    }

    fun onItemClick(view: View?, i: Int, iDrawerItem: IDrawerItem<Any>): Boolean {
        val identifier = iDrawerItem.identifier.toInt()

        when (identifier) {
            HOME -> homeActivity.showPagerItem(HomePages.HOME.ordinal, false)
            ROUTINES -> homeActivity.showPagerItem(HomePages.ROUTINES.ordinal, false)
            MEDICINES -> homeActivity.showPagerItem(HomePages.MEDICINES.ordinal, false)
            SCHEDULES -> homeActivity.showPagerItem(HomePages.SCHEDULES.ordinal, false)
            CALENDAR -> {
                launchActivity(Intent(homeActivity, CalendarActivity::class.java))
                drawer.setSelection(HOME, false)
            }

            HELP -> {
                //homeActivity.showTutorial();
                launchActivity(Intent(homeActivity, MaterialIntroActivity::class.java))
                drawer.setSelection(HOME, false)
            }

            PATIENTS -> {
                launchActivity(Intent(homeActivity, PatientsActivity::class.java))
                drawer.setSelection(HOME, false)
            }

            SETTINGS -> {
                launchActivity(Intent(homeActivity, CalendulaSettingsActivity::class.java))
                drawer.setSelection(HOME, false)
            }

            ABOUT -> {
                showAbout()
                drawer.setSelection(HOME, false)
            }

            ALLERGIES -> {
                launchActivity(Intent(homeActivity, AllergiesActivity::class.java))
                drawer.setSelection(HOME, false)
            }

            else -> return false
        }
        drawer.closeDrawer()
        return true
    }

    fun onPagerPositionChange(pagerPosition: Int) {
        LogUtil.d(TAG, "onPagerPositionChange: $pagerPosition")
        when (pagerPosition) {
            0 -> drawer.setSelection(HOME, false)
            1 -> drawer.setSelection(ROUTINES, false)
            2 -> drawer.setSelection(MEDICINES, false)
            3 -> drawer.setSelection(SCHEDULES, false)
        }
    }

    fun onProfileChanged(view: View?, profile: IProfile<Any>, current: Boolean): Boolean {
        if (profile.identifier == PATIENT_ADD_PROFILE_ID) {
            val intent = Intent(homeActivity, PatientDetailActivity::class.java)
            launchActivity(intent)
            return true
        } else {
            val id = profile.identifier
            val p = DB.patients().findById(id)
            val isActive = DB.patients().isActive(p, homeActivity)
            if (isActive) {
                val intent = Intent(homeActivity, PatientDetailActivity::class.java)
                intent.putExtra("patient_id", id)
                launchActivity(intent)
            } else {
                DB.patients().setActive(p)
                updateHeaderBackground(p)
            }
        }
        return false
    }

    private fun updateHeaderBackground(p: Patient) {
        currentPatient = p
        //int colors[] = AvatarMgr.colorsFor(homeActivity.getResources(), p.avatar());
        val layers = headerResult.headerBackgroundView.getDrawable() as LayerDrawable
        val color = layers.findDrawableByLayerId(R.id.color_layer) as ColorDrawable
        color.color = ScreenUtils.equivalentNoAlpha(p.color, 1f)
    }

    fun drawer(): Drawer {
        return drawer
    }

    private fun header(): AccountHeader {
        return headerResult
    }

    fun onActivityResume(p: Patient) {
        currentPatient = p

        val patients = DB.patients().findAll()
        val profiles: List<IProfile<Any>> = headerResult.profiles
        val toRemove = ArrayList<IProfile<Any>>()
        if (patients.size != profiles.size) {
            for (pr in profiles) {
                val id = pr.identifier
                var remove = true
                for (pat in patients) {
                    if (pat.id == id || id == PATIENT_ADD_PROFILE_ID) {
                        remove = false
                        break
                    }
                }
                if (remove) {
                    toRemove.add(pr)
                }
            }
            for (pr in toRemove) {
                headerResult.removeProfile(pr)
            }
        }

        headerResult.setActiveProfile(p.id, false)

        if (p != currentPatient || header().activeProfile.icon
                .iconRes !== AvatarMgr.res(p.avatar)
        ) {
            headerResult.setActiveProfile(p.id, false)
            val profile: IProfile<Any> = headerResult.activeProfile
            profile.withIcon(AvatarMgr.res(p.avatar))
            headerResult.updateProfile(profile)
        }
        updateHeaderBackground(p)
    }

    fun onPatientCreated(p: Patient?) {
        headerResult.setProfiles(genProfiles())
    }

    fun onPatientUpdated(p: Patient?) {
        headerResult.setProfiles(genProfiles())
    }

    private fun addCalendarItem() {
        drawer.addItemAtPosition(
            PrimaryDrawerItem()
                .withName(R.string.title_activity_pickup_calendar_short)
                .withIcon(
                    IconUtils.icon(
                        homeActivity,
                        CommunityMaterial.Icon.cmd_calendar_check,
                        R.color.black
                    ).alpha(110)
                )
                .withEnabled(true)
                .withIdentifier(CALENDAR), 7
        )
    }

    private fun launchActivity(i: Intent) {
        homeActivity.startActivity(i)
        homeActivity.overridePendingTransition(0, 0)
    }

    private fun showAbout() {
        launchActivity(Intent(homeActivity, AboutActivity::class.java))
    }

    private fun genProfiles(): List<IProfile<Any>> {
        val profiles = ArrayList<IProfile<Any>>()

        for (p in DB.patients().findAll()) {
            LogUtil.d(TAG, "Adding patient to drawer: " + p.name)
            profiles.add(genProfile(p) as IProfile<Any>)
        }

        profiles.add(
            ProfileSettingDrawerItem()
                .withName(homeActivity.getString(R.string.patient_add))
                .withIcon(
                    IconicsDrawable(homeActivity, GoogleMaterial.Icon.gmd_account_add)
                        .sizeDp(24)
                        .paddingDp(1)
                        .colorRes(R.color.dark_grey_home)
                ).withIdentifier(PATIENT_ADD_PROFILE_ID) as IProfile<Any>
        )
        return profiles
    }

    private fun genProfile(p: Patient): IProfile<ProfileDrawerItem> {
        val schedules = DB.schedules().findAll(p).size
        val fakeMail = if (schedules > 0) {
            homeActivity.getString(
                R.string.active_schedules_number,
                schedules
            )
        } else {
            homeActivity.getString(R.string.active_schedules_none)
        }

        return ProfileDrawerItem()
            .withIdentifier(p.id)
            .withName(p.name)
            .withEmail(fakeMail)
            .withIcon(AvatarMgr.res(p.avatar))
            .withNameShown(true)
    }

    companion object {
        const val HOME: Long = 0
        const val ROUTINES: Long = 1
        const val MEDICINES: Long = 2
        const val SCHEDULES: Long = 3

        const val PATIENTS: Long = 4
        const val HELP: Long = 5
        const val SETTINGS: Long = 6
        const val TRAVELPLAN: Int = 8
        const val PHARMACIES: Int = 9
        const val ABOUT: Long = 10

        const val PATIENT_ADD_PROFILE_ID: Long = 15
        const val CALENDAR: Long = 12

        const val ALLERGIES: Long = 11

        private const val TAG = "LeftDrawerMgr"
    }
}
