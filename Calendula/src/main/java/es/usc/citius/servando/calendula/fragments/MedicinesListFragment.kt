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
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.github.javiersantos.materialstyleddialogs.enums.Style
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.activities.MedicineInfoActivity
import es.usc.citius.servando.calendula.adapters.items.MedicineItem
import es.usc.citius.servando.calendula.adapters.items.MedicineItem.MedicineViewHolder
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.databinding.FragmentMedicinesListBinding
import es.usc.citius.servando.calendula.events.PersistenceEvents.ModelCreateOrUpdateEvent
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.medicine.MedicineSortUtil.MedSortType
import es.usc.citius.servando.calendula.util.view.CollapseExpandAnimator
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.Closeable
import java.util.concurrent.Executors
import org.greenrobot.eventbus.Subscribe

class MedicinesListFragment : Fragment() {
    private val viewModel: MLFViewModel by viewModels()
    private var mMedicineSelectedCallback: OnMedicineSelectedListener? = null

    private val adapter by lazy { FastItemAdapter<MedicineItem>() }
    private lateinit var binding: FragmentMedicinesListBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_medicines_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMedicinesListBinding.bind(view)
        setupRecyclerView(binding)
        setupSortSpinner(binding)
        binding.medListContainer.setOnTouchListener { view, motionEvent ->
            if (!isSortCollapsed) toggleSort()
            view.performClick()
            false
        }
        viewModel.medicineItemListLiveData.observe(viewLifecycleOwner) {
            updateAdapterItems(it)
            updateViewVisibility(it)
        }
    }

    private fun notifyDataChange() {
        //todo: Find out how to hook this up
    }

    @Deprecated("Deprecated in Java")
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        // If the container activity has implemented the callback interface, set it as listener
        if (activity is OnMedicineSelectedListener) {
            mMedicineSelectedCallback = activity
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_medicines_list, menu)
        menu.findItem(R.id.action_sort).setIcon(IconUtils.icon(requireContext(), CommunityMaterial.Icon.cmd_sort, R.color.white))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_sort -> {
                toggleSort()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        eventBus().register(this)
    }

    override fun onStop() {
        eventBus().unregister(this)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    private fun toggleSort() {
        LogUtil.d(TAG, "toggleSort() called")
        if (isSortCollapsed) {
            val targetHeight = resources.getDimension(R.dimen.sort_bar_height).toInt()
            CollapseExpandAnimator.expand(binding.sortLayout, 100, targetHeight)
        } else {
            CollapseExpandAnimator.collapse(binding.sortLayout, 100, 0)
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
                viewModel.medicineItemListLiveData.reload() //todo: Figure out how to get the LiveData to handle this internally
            }.onNeutral { dialog, which -> dialog.cancel() }
            .show()
    }

    private val isSortCollapsed: Boolean
        get() {
            val collapsed = binding.sortLayout.layoutParams.height == 0
            LogUtil.d(TAG, "isSortCollapsed() returned: $collapsed")
            return collapsed
        }

    private fun setupSortSpinner(binding: FragmentMedicinesListBinding) {
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.sort_spinner_item, MedSortType.entries.toTypedArray())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.medicineSortSpinner.adapter = spinnerAdapter
        binding.medicineSortSpinner.background.setColorFilter(resources.getColor(R.color.white), PorterDuff.Mode.SRC_ATOP) //change caret color
        binding.medicineSortSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                viewModel.medicineItemListLiveData.medSortType = parent.getItemAtPosition(position) as MedSortType
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupRecyclerView(binding: FragmentMedicinesListBinding) {
        val llm = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.medicinesList.layoutManager = llm
        adapter.withSelectable(false)
        adapter.withPositionBasedStateManagement(false)
        adapter.withOnLongClickListener { v, adapter, item, position ->
            showDeleteConfirmationDialog(item.medicine)
            true
        }

        adapter.withItemEvent(object : ClickEventHook<MedicineItem>() {
            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                if (viewHolder is MedicineViewHolder) return viewHolder.alertIcon
                return null
            }

            override fun onClick(v: View, position: Int, fastAdapter: FastAdapter<MedicineItem>, item: MedicineItem) =
                openMedicineInfoActivity(item.medicine, true)
        })

        adapter.withOnClickListener { v, adapter, item, position ->
            if (mMedicineSelectedCallback != null && item != null && item.medicine != null) mMedicineSelectedCallback?.onMedicineSelected(item.medicine)
            true
        }

        binding.medicinesList.adapter = adapter
        binding.medicinesList.setOnTouchListener { view, motionEvent ->
            view.performClick()
            if (!isSortCollapsed) toggleSort()
            false
        }
    }

    private fun updateViewVisibility(mMedicines: List<MedicineItem>) {
        if (mMedicines.isNotEmpty()) {
            binding.empty.visibility = View.GONE
            binding.sortLayout.visibility = View.VISIBLE
        } else {
            binding.empty.visibility = View.VISIBLE
            binding.sortLayout.visibility = View.GONE
        }
    }

    private fun updateAdapterItems(mMedicines: List<MedicineItem>) {
        adapter.setNewList(mMedicines)
    }

    //
    // Container Activity must implement this interface
    //
    interface OnMedicineSelectedListener {
        fun onMedicineSelected(m: Medicine)
        fun onCreateMedicine()
    }

    companion object {
        private const val TAG = "MedicinesListFragment"
    }
}

internal class MLFViewModel: ViewModel() {
    internal val medicineItemListLiveData = MedicineItemListLiveData(CalendulaApp.context).apply { addCloseable(this) }
}

internal class MedicineItemListLiveData(private val context: Context) : LiveData<List<MedicineItem>>(), Closeable {
    internal var medSortType = MedSortType.NAME
        set(value) {
            field = value
            reload()
        }

    init {
        reload()
        eventBus().register(this)
    }

    internal fun reload() {
        Completable.fromAction {
            postValue(
                DB.medicines().findAllForActivePatient(context)
                    .sortedWith(medSortType.comparator())
                    .map { MedicineItem(it) },
            )
        }.subscribeOn(scheduler)
            .subscribe({},{})
    }

    @Suppress("unused")
    @Subscribe
    fun handleEvent(event: Any) {
        when(event) {
            is ModelCreateOrUpdateEvent -> if (event.clazz == Medicine::class.java) reload()
            else -> reload()
        }
    }

    companion object {
        private val scheduler by lazy { Schedulers.from(Executors.newSingleThreadExecutor()) }
    }

    override fun close() {
        eventBus().unregister(this)
    }
}