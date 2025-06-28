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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog.SingleButtonCallback
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.github.javiersantos.materialstyleddialogs.enums.Style
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.ClickEventHook
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import es.usc.citius.servando.calendula.CalendulaActivity
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.adapters.items.allergylist.AllergyGroupItem
import es.usc.citius.servando.calendula.adapters.items.allergylist.AllergyGroupSubItem
import es.usc.citius.servando.calendula.adapters.items.allergylist.AllergyItem
import es.usc.citius.servando.calendula.allergies.AllergenConversionUtil
import es.usc.citius.servando.calendula.allergies.AllergenFacade
import es.usc.citius.servando.calendula.allergies.AllergenGroupWrapper
import es.usc.citius.servando.calendula.allergies.AllergenVO
import es.usc.citius.servando.calendula.allergies.AllergyAlertUtil
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.databinding.ActivityAllergiesBinding
import es.usc.citius.servando.calendula.events.PersistenceEvents
import es.usc.citius.servando.calendula.persistence.PatientAllergen
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.Snack
import es.usc.citius.servando.calendula.util.alerts.AlertManager
import java.sql.SQLException
import java.util.Collections

class AllergiesActivity : CalendulaActivity() {
    private val addButton: FloatingActionButton by lazy { binding.addButton }
    private val allergiesRecycler by lazy { binding.allergiesRecycler }
    private val allergiesPlaceholder by lazy { binding.textviewNoAllergiesPlaceholder }
    private val progressBar by lazy { binding.mainProgressBar }

    private var color = 0
    private val allergiesAdapter by lazy {  FastItemAdapter<AbstractItem<*, *>>() }
    private var store: AllergiesStore? = null

    private fun showSearchView() {
        val i = Intent(this, AllergiesSearchActivity::class.java)
        startActivityForResult(i, AllergiesSearchActivity.REQUEST_NEW_ALLERGIES)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AllergiesSearchActivity.REQUEST_NEW_ALLERGIES && resultCode == RESULT_OK) {
            data?.getParcelableArrayListExtra<AllergenGroupWrapper>("result")?.also {
                SaveAllergiesTask().execute(it)
            }
        }
    }

    private val binding by lazy { ActivityAllergiesBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        addButton.setOnClickListener { showSearchView() }
        addButton.setImageDrawable(
            IconicsDrawable(this)
                .icon(GoogleMaterial.Icon.gmd_plus)
                .paddingDp(5)
                .sizeDp(24)
                .colorRes(R.color.fab_default_icon_color)
        )

        //setup toolbar and status bar
        val patient = DB.patients().getActive(this)
        color = patient.color
        val name = patient.name
        setupToolbar(
            getString(
                R.string.relation_user_possession_thing,
                name,
                getString(R.string.title_activity_allergies)
            ), color
        )
        setupStatusBar(color)

        //initialize allergies store
        store = AllergiesStore()

        //setup recycler
        setupAllergiesList()

        progressBar.indeterminateDrawable.setColorFilter(
            color,
            PorterDuff.Mode.MULTIPLY
        )

        //load allergies, set placeholder if needed
        LoadAllergiesTask().execute()

        showWarningIfNeeded()
    }

    private fun showWarningIfNeeded() {
        val prefs = PreferenceUtils.instance().preferences()
        if (!prefs.getBoolean(PreferenceKeys.ALLERGIES_WARNING_SHOWN.key(), false)) {
            MaterialStyledDialog.Builder(this)
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(
                    IconUtils.icon(
                        this,
                        GoogleMaterial.Icon.gmd_alert_circle,
                        R.color.white,
                        100
                    )
                )
                .setHeaderColor(R.color.android_orange_dark)
                .withDialogAnimation(true)
                .setTitle(R.string.important_info)
                .setDescription(R.string.message_allergies_experimental_warning)
                .setCancelable(false)
                .setPositiveText(getString(R.string.dialog_continue_option))
                .onPositive { dialog, which ->
                    prefs.edit().putBoolean(PreferenceKeys.ALLERGIES_WARNING_SHOWN.key(), true)
                        .apply()
                    dialog.dismiss()
                }
                .setNegativeText(R.string.dialog_get_me_out)
                .onNegative { dialog, which ->
                    dialog.cancel()
                    finish()
                }
                .show()
        }
    }

    private fun checkConflictsAndCreateAlerts(allergen: AllergenVO): Boolean {
        val conflicts = AllergenFacade.checkNewMedicineAllergies(this, allergen)
        if (conflicts.isNotEmpty()) {
            DB.transaction {
                for (conflict in conflicts) {
//                        AlertManager.createAlert(new AllergyPatientAlert(conflict, allergen), AllergiesActivity.this);
                    val list = AllergyAlertUtil.getAlertsForMedicine(conflict)
                    if (list.size > 0) {
                        if (list.size == 1) {
                            val a = list[0].map() as AllergyPatientAlert
                            val d = a.details
                            d.allergens.add(allergen)
                            a.details = d
                            DB.alerts().save(a)
                        } else {
                            LogUtil.wtf(TAG, "Duplicate alerts: $list")
                        }
                    } else {
                        AlertManager.createAlert(
                            AllergyPatientAlert(
                                conflict,
                                object : ArrayList<AllergenVO?>() {
                                    init {
                                        add(allergen)
                                    }
                                })
                        )
                    }
                }
                null
            }
        }
        return conflicts.isNotEmpty()
    }

    private fun checkPlaceholder() {
        if (allergiesPlaceholder.visibility == View.VISIBLE) {
            if (!store!!.isEmpty) allergiesPlaceholder.visibility = View.GONE
        } else if (store!!.isEmpty) {
            allergiesPlaceholder.visibility = View.VISIBLE
        }
    }

    private val allergyItems: List<AbstractItem<*, *>>
        get() {
            val allergies: MutableList<PatientAllergen> = ArrayList(
                store!!.allergies
            )

            val items: MutableList<AbstractItem<*, *>> = ArrayList(allergies.size)
            val toRemove: MutableList<PatientAllergen> = ArrayList()

            val groups: MutableMap<String, MutableList<AllergyGroupSubItem>> = HashMap()

            for (allergen in allergies) {
                val group = allergen.group
                if (group != null && group.isNotEmpty()) {
                    if (!groups.keys.contains(group)) {
                        groups[group] = ArrayList()
                    }
                    groups[group]!!.add(AllergyGroupSubItem(allergen, this))
                    toRemove.add(allergen)
                }
            }
            allergies.removeAll(toRemove)
            for (key in groups.keys) {
                val g = AllergyGroupItem(key, this)
                g.withSubItems(groups[key])
                items.add(g)
            }
            for (allergen in allergies) {
                items.add(AllergyItem(allergen, this))
            }
            return items
        }

    @SuppressLint("RestrictedApi")
    private fun hideAllergiesView(hide: Boolean) {
        val visibility = if (hide) View.GONE else View.VISIBLE
        allergiesRecycler.visibility = visibility
        addButton.visibility = visibility
        if (!hide) checkPlaceholder()
    }

    private fun setupAllergiesList() {
        val llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        allergiesRecycler.layoutManager = llm
        allergiesAdapter
        allergiesAdapter.withSelectable(false)
        allergiesAdapter.withItemEvent(MyItemEventHook())
        allergiesRecycler.adapter = allergiesAdapter
    }

    private inner class MyItemEventHook: ClickEventHook<AbstractItem<*, *>>() {
        override fun onClick(
            view: View?,
            i: Int,
            fastAdapter: FastAdapter<AbstractItem<*, *>>?,
            item: AbstractItem<*, *>?
        ) {
            LogUtil.d(
                TAG,
                "onClick() called with: view = [$view, i = [$i], fastAdapter = [$fastAdapter], item = [$item]"
            )

            view ?: return
            item ?: return

            when (view.id) {
                R.id.delete_button -> when (item.type) {
                    R.id.fastadapter_allergy_group_item -> showDeleteConfirmationDialog(item as AllergyGroupItem)
                    R.id.fastadapter_allergy_item -> showDeleteConfirmationDialog(item as AllergyItem)
                    else -> LogUtil.w(TAG, "onClick: Unexpected item type: $item")
                }

                R.id.group_button -> {
                    val g = item as AllergyGroupItem
                    val expand = !g.isExpanded
                    val angle = (if (expand) 180 else 0).toFloat()
                    ViewCompat.animate(view).rotation(angle)
                    if (expand) allergiesAdapter.expand(i)
                    else allergiesAdapter.collapse(i)
                }

                else -> LogUtil.w(TAG, "onClick: Unexpected view type on click hook: $view")
            }
        }

        override fun onBindMany(viewHolder: RecyclerView.ViewHolder): List<View>? {
            val vl: MutableList<View> = ArrayList()
            if (viewHolder is AllergyGroupItem.ViewHolder) {
                val vh = viewHolder
                vl.add(vh.deleteButton)
                vl.add(vh.dropButton)
                return vl
            } else if (viewHolder is AllergyItem.ViewHolder) {
                vl.add(viewHolder.deleteButton)
                return vl
            }
            return null
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun showDeleteConfirmationDialog(a: AllergyItem) {
        showDeleteConfirmationDialog(
            getString(
                R.string.remove_allergy_message_short,
                a.allergen.name
            ), { dialog, which ->
                DeleteAllergyTask().execute(a)
                dialog.dismiss()
            }, { dialog, which -> dialog.cancel() })
    }

    @SuppressLint("StringFormatInvalid")
    private fun showDeleteConfirmationDialog(a: AllergyGroupItem) {
        showDeleteConfirmationDialog(
            getString(R.string.remove_allergy_message_short, a.title),
            { dialog, which ->
                DeleteAllergyGroupTask().execute(a)
                dialog.dismiss()
            },
            { dialog, which -> dialog.cancel() })
    }

    private fun showDeleteConfirmationDialog(
        message: String,
        onPositive: SingleButtonCallback,
        onNegative: SingleButtonCallback
    ) {
        MaterialStyledDialog.Builder(this)
            .setStyle(Style.HEADER_WITH_ICON)
            .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_delete, R.color.white, 100))
            .setHeaderColor(R.color.android_red)
            .withDialogAnimation(true)
            .setDescription(message)
            .setCancelable(true)
            .setNegativeText(getString(R.string.dialog_no_option))
            .setPositiveText(getString(R.string.dialog_yes_option))
            .onPositive(onPositive)
            .onNegative(onNegative)
            .show()
    }

    private fun showNewAllergyConflictDialog() {
        MaterialStyledDialog.Builder(this)
            .setStyle(Style.HEADER_WITH_ICON)
            .setIcon(
                IconUtils.icon(
                    this,
                    CommunityMaterial.Icon.cmd_exclamation,
                    R.color.white,
                    100
                )
            )
            .setHeaderColor(R.color.android_red)
            .withDialogAnimation(true)
            .setTitle(R.string.title_allergies_detected_dialog)
            .setDescription(R.string.message_allergies_detected_dialog)
            .setCancelable(false)
            .setPositiveText(getString(R.string.ok))
            .onPositive { dialog, which -> dialog.dismiss() }
            .show()
    }

    enum class SaveResult {
        OK, ERROR, ALLERGY
    }

    inner class AllergiesStore {
        private var currentAllergies: MutableList<PatientAllergen>? = null
        private var context: Context? = null

        fun deleteAllergen(a: PatientAllergen, notify: Boolean): Int {
            try {
                val index = currentAllergies!!.indexOf(a)
                DB.patientAllergens().delete(a)
                AllergyAlertUtil.removeAllergyAlerts(a)
                currentAllergies!!.remove(a)
                if (notify) {
                    eventBus().post(PersistenceEvents.MEDICINE_EVENT)
                }
                return index
            } catch (e: SQLException) {
                LogUtil.e(TAG, "Couldn't delete allergen $a", e)
                return -2
            }
        }

        fun deleteAllergens(a: List<PatientAllergen>): Int {
            val res = DB.transaction {
                var res = 0
                for (patientAllergen in a) {
                    res = deleteAllergen(patientAllergen, false)
                    if (res == -2) break
                }
                res
            } as Int
            eventBus().post(PersistenceEvents.MEDICINE_EVENT)
            return res
        }

        val allergies: List<PatientAllergen>?
            get() = currentAllergies

        val allergiesVO: List<AllergenVO>
            get() = AllergenConversionUtil.toVO(currentAllergies)

        val isEmpty: Boolean
            get() = currentAllergies!!.isEmpty()

        fun load(ctx: Context?) {
            context = ctx
            reload()
        }

        fun reload() {
            currentAllergies = DB.patientAllergens().findAllForActivePatient(context)
            Collections.sort(currentAllergies) { o1, o2 -> o1.name.compareTo(o2.name) }
        }

        private fun storeAllergen(allergen: PatientAllergen): SaveResult {
            val rows: Int
            try {
                rows = DB.patientAllergens().create(allergen)
            } catch (e: SQLException) {
                LogUtil.e(TAG, "storeAllergen: couldn't create allergy", e)
                return SaveResult.ERROR
            }
            LogUtil.d(TAG, "storeAllergen: inserted allergen into database: $allergen")
            if (rows == 1) {
                val r = checkConflictsAndCreateAlerts(AllergenVO(allergen))
                currentAllergies!!.add(allergen)
                if (r) return SaveResult.ALLERGY
                return SaveResult.OK
            }
            return SaveResult.ERROR
        }

        fun storeAllergens(allergens: Collection<PatientAllergen>): SaveResult {
            return DB.transaction {
                var res = SaveResult.OK
                for (allergen in allergens) {
                    val r = storeAllergen(allergen)
                    if (r == SaveResult.ALLERGY && res != SaveResult.ERROR) res = SaveResult.ALLERGY
                    if (r == SaveResult.ERROR) res = SaveResult.ERROR
                }
                res
            } as SaveResult
        }
    }

    private inner class DeleteAllergyGroupTask : AsyncTask<AllergyGroupItem, Void?, Int>() {
        override fun doInBackground(vararg params: AllergyGroupItem): Int {
            LogUtil.d(
                TAG,
                "doInBackground() called with: params = [" + params.contentToString() + "]"
            )
            if (params.size != 1) {
                LogUtil.e(
                    TAG,
                    "doInBackground: invalid argument length. Expected 1, got " + params.size
                )
                throw IllegalArgumentException("Invalid argument length")
            }
            val index = allergiesAdapter.getAdapterPosition(params[0])

            val subItems = params[0].subItems
            val allergens: MutableList<PatientAllergen> = ArrayList(subItems.size)
            for (subItem in subItems) {
                allergens.add(subItem.allergen)
            }

            val k = store!!.deleteAllergens(allergens)
            return if (k >= -1) index else k
        }

        override fun onPreExecute() {
            super.onPreExecute()
            progressBar.visibility = View.VISIBLE
        }

        override fun onPostExecute(index: Int) {
            if (index >= 0) {
                store!!.reload()
                checkPlaceholder()
                allergiesAdapter.collapse(index)
                allergiesAdapter.remove(index)
            } else {
                Snack.show(R.string.delete_allergen_error, this@AllergiesActivity)
            }
            progressBar.visibility = View.GONE
            Handler().postDelayed({ checkPlaceholder() }, 200)
            hideAllergiesView(false)
        }
    }

    private inner class DeleteAllergyTask : AsyncTask<AllergyItem, Void?, Int>() {
        override fun onPreExecute() {
            super.onPreExecute()
            progressBar.visibility = View.VISIBLE
        }

        override fun onPostExecute(index: Int) {
            if (index >= 0) {
                store!!.reload()
                checkPlaceholder()
                allergiesAdapter.remove(index)
            } else {
                Snack.show(R.string.delete_allergen_error, this@AllergiesActivity)
            }
            progressBar.visibility = View.GONE
            Handler().postDelayed({ checkPlaceholder() }, 200)
            hideAllergiesView(false)
        }

        override fun doInBackground(vararg params: AllergyItem): Int {
            if (params.size != 1) {
                LogUtil.e(
                    TAG,
                    "doInBackground: invalid argument length. Expected 1, got " + params.size
                )
                throw IllegalArgumentException("Invalid argument length")
            }
            val index = allergiesAdapter.getAdapterPosition(params[0])
            store!!.deleteAllergen(params[0].allergen, true)
            return index
        }
    }

    private inner class LoadAllergiesTask : AsyncTask<Void, Void?, List<AbstractItem<*, *>>>() {
        override fun onPreExecute() {
            super.onPreExecute()
            hideAllergiesView(true)
            progressBar.visibility = View.VISIBLE
        }

        override fun onPostExecute(items: List<AbstractItem<*, *>>?) {
            allergiesAdapter.add(items)
            progressBar.visibility = View.GONE
            checkPlaceholder()
            hideAllergiesView(false)
        }

        override fun doInBackground(vararg params: Void): List<AbstractItem<*, *>> {
            store!!.load(this@AllergiesActivity)
            return allergyItems
        }
    }

    private inner class SaveAllergiesTask :
        AsyncTask<Collection<AllergenGroupWrapper>, Void?, SaveAllergiesTask.Result>() {
        @SafeVarargs
        override fun doInBackground(vararg items: Collection<AllergenGroupWrapper>): Result {
            if (items.size != 1) {
                LogUtil.e(
                    TAG,
                    "doInBackground: invalid argument length. Expected 1, got " + items.size
                )
                throw IllegalArgumentException("Invalid argument length")
            }
            val ws = items[0]
            val pa: MutableCollection<PatientAllergen> = ArrayList(ws.size)
            val p = DB.patients().getActive(this@AllergiesActivity)
            for (w in ws) {
                if (w.group != null) pa.add(PatientAllergen(w.vo, p, w.group))
                else pa.add(PatientAllergen(w.vo, p))
            }

            val r = store!!.storeAllergens(pa)
            return Result(
                r == SaveResult.OK || r == SaveResult.ALLERGY,
                r == SaveResult.ALLERGY,
                allergyItems
            )
        }

        override fun onPostExecute(res: Result) {
            progressBar.visibility = View.GONE
            if (res.saved) {
                if (res.allergyItems.isNotEmpty()) {
                    allergiesAdapter.set(res.allergyItems)
                    store!!.reload()
                }
                if (res.allergies) showNewAllergyConflictDialog()
                hideAllergiesView(false)
                Snack.show(
                    getString(R.string.message_allergy_add_multiple_success),
                    this@AllergiesActivity
                )
            } else {
                Snack.show(R.string.message_allergy_add_failure, this@AllergiesActivity)
            }
        }

        override fun onPreExecute() {
            super.onPreExecute()
            hideAllergiesView(true)
            progressBar.visibility = View.VISIBLE
        }

        inner class Result(
            val saved: Boolean,
            val allergies: Boolean,
            val allergyItems: List<AbstractItem<*, *>>
        )
    }

    companion object {
        private const val TAG = "AllergiesActivity"
    }
}
