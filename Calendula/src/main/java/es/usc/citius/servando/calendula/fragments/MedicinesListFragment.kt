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

import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.Unbinder
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.github.javiersantos.materialstyleddialogs.enums.Style
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.activities.MedicineInfoActivity
import es.usc.citius.servando.calendula.adapters.items.MedicineItem
import es.usc.citius.servando.calendula.adapters.items.MedicineItem.MedicineViewHolder
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.events.PersistenceEvents.ModelCreateOrUpdateEvent
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.medicine.MedicineSortUtil.MedSortType
import es.usc.citius.servando.calendula.util.view.CollapseExpandAnimator
import java.util.Collections
import org.greenrobot.eventbus.Subscribe

class MedicinesListFragment : Fragment() {
    var mMedicines: MutableList<Medicine> = mutableListOf()
    private var mMedicineSelectedCallback: OnMedicineSelectedListener? = null

    @JvmField @BindView(R.id.medicines_list)
    var recyclerView: RecyclerView? = null

    @JvmField @BindView(android.R.id.empty)
    var emptyView: View? = null

    @JvmField @BindView(R.id.sort_layout)
    var sortLayout: View? = null

    @JvmField @BindView(R.id.medicine_sort_spinner)
    var sortSpinner: AppCompatSpinner? = null

    @JvmField @BindView(R.id.med_list_container)
    var medListContainer: View? = null

    private val adapter by lazy { FastItemAdapter<MedicineItem>() }
    private val handler: Handler = Handler()
    var unbinder: Unbinder? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_medicines_list, container, false)
        unbinder = ButterKnife.bind(this, rootView)
        mMedicines = DB.medicines().findAllForActivePatient(context)
        setupRecyclerView()
        setupSortSpinner()
        medListContainer?.setOnTouchListener { view, motionEvent ->
            if (!isSortCollapsed) toggleSort()
            view.performClick()
            false
        }
        updateViewVisibility()
        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unbinder?.unbind()
    }

    fun notifyDataChange() {
        ReloadItemsTask().execute()
    }

    @Deprecated("Deprecated in Java")
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        // If the container activity has implemented the callback interface, set it as listener
        if (activity is OnMedicineSelectedListener) {
            mMedicineSelectedCallback = activity
        }
    }

    override fun onStart() {
        super.onStart()
        eventBus().register(this)
    }

    override fun onStop() {
        eventBus().unregister(this)
        super.onStop()
    }

    // Method called from the event bus
    @Suppress("unused") @Subscribe fun handleActiveUserChange() {
        notifyDataChange()
    }

    @Suppress("unused") @Subscribe fun handleModelCreateOrUpdate(event: ModelCreateOrUpdateEvent) {
        if (event.clazz == Medicine::class.java) {
            handler.post { notifyDataChange() }
        }
    }

    fun toggleSort() {
        LogUtil.d(TAG, "toggleSort() called")
        if (isSortCollapsed) {
            val targetHeight = resources.getDimension(R.dimen.sort_bar_height).toInt()
            CollapseExpandAnimator.expand(sortLayout, 100, targetHeight)
        } else {
            CollapseExpandAnimator.collapse(sortLayout, 100, 0)
        }
    }

    fun openMedicineInfoActivity(medicine: Medicine, showAlerts: Boolean) {
        val intent = Intent(activity, MedicineInfoActivity::class.java).apply {
            putExtra("medicine_id", medicine.id)
            putExtra("show_alerts", showAlerts)
        }
        requireContext().startActivity(intent)
    }

    private fun showDeleteConfirmationDialog(m: Medicine) {
        val message = if (DB.schedules().findByMedicine(m).isNotEmpty()) {
            String.format(getString(R.string.remove_medicine_message_long), m.name)
        } else {
            String.format(getString(R.string.remove_medicine_message_short), m.name)
        }

        MaterialStyledDialog.Builder(activity)
            .setStyle(Style.HEADER_WITH_ICON)
            .setIcon(IconUtils.icon(activity, CommunityMaterial.Icon.cmd_pill, R.color.white, 100))
            .setHeaderColor(R.color.android_red)
            .withDialogAnimation(true)
            .setTitle(getString(R.string.remove_medicine_dialog_title))
            .setDescription(message)
            .setCancelable(true)
            .setNeutralText(getString(R.string.dialog_no_option))
            .setPositiveText(getString(R.string.dialog_yes_option))
            .onPositive { dialog, which ->
                DB.medicines().deleteCascade(m, true)
                notifyDataChange()
            }.onNeutral { dialog, which -> dialog.cancel() }
            .show()
    }

    private val isSortCollapsed: Boolean
        get() {
            val collapsed = sortLayout!!.layoutParams.height == 0
            LogUtil.d(TAG, "isSortCollapsed() returned: $collapsed")
            return collapsed
        }

    private fun setupSortSpinner() {
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.sort_spinner_item, MedSortType.entries.toTypedArray())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner!!.adapter = spinnerAdapter
        sortSpinner!!.background.setColorFilter(resources.getColor(R.color.white), PorterDuff.Mode.SRC_ATOP) //change caret color
        sortSpinner!!.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val type = parent.getItemAtPosition(position) as MedSortType
                val cmp = type.comparator()
                if (cmp != null) {
                    Collections.sort(mMedicines, cmp)
                    updateAdapterItems()
                } else {
                    LogUtil.e(TAG, "onItemSelected: null comparator! wrong sort type?")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                //noop
            }
        }
    }

    private fun setupRecyclerView() {
        val llm = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView!!.layoutManager = llm
        adapter.withSelectable(false)
        adapter.withPositionBasedStateManagement(false)
        for (mMedicine in mMedicines) {
            adapter.add(MedicineItem(mMedicine))
        }
        adapter.withOnLongClickListener { v, adapter, item, position ->
            showDeleteConfirmationDialog(item.medicine)
            true
        }

        adapter.withItemEvent(object : ClickEventHook<MedicineItem>() {
            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                if (viewHolder is MedicineViewHolder) return viewHolder.alertIcon
                return null
            }

            override fun onClick(v: View, position: Int, fastAdapter: FastAdapter<MedicineItem>, item: MedicineItem) {
                openMedicineInfoActivity(item.medicine, true)
            }
        })

        adapter.withOnClickListener { v, adapter, item, position ->
            if (mMedicineSelectedCallback != null && item != null && item.medicine != null) mMedicineSelectedCallback!!.onMedicineSelected(item.medicine)
            true
        }

        recyclerView!!.adapter = adapter
        recyclerView?.setOnTouchListener { view, motionEvent ->
            view.performClick()
            if (!isSortCollapsed) toggleSort()
            false
        }
    }

    private fun updateViewVisibility() {
        if (mMedicines.size > 0) {
            emptyView!!.visibility = View.GONE
            sortLayout!!.visibility = View.VISIBLE
        } else {
            emptyView!!.visibility = View.VISIBLE
            sortLayout!!.visibility = View.GONE
        }
    }

    private fun updateAdapterItems() {
        adapter.clear()
        for (m in mMedicines) {
            adapter.add(MedicineItem(m))
        }
        adapter.notifyAdapterDataSetChanged()
    }

    //
    // Container Activity must implement this interface
    //
    interface OnMedicineSelectedListener {
        fun onMedicineSelected(m: Medicine)
        fun onCreateMedicine()
    }

    private inner class ReloadItemsTask : AsyncTask<Void, Void, Void?>() {
        override fun doInBackground(vararg params: Void): Void? {
            LogUtil.d(TAG, "Reloading items...")
            mMedicines = DB.medicines().findAllForActivePatient(context)
            return null
        }

        override fun onPostExecute(aVoid: Void?) {
            super.onPostExecute(aVoid)
            val sortType = sortSpinner!!.selectedItem as MedSortType
            Collections.sort(mMedicines, sortType.comparator())
            updateViewVisibility()
            updateAdapterItems()
            LogUtil.d(TAG, "Reloaded items, count: " + mMedicines.size)
        }
    }

    companion object {
        private const val TAG = "MedicinesListFragment"
    }
}