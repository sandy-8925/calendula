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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.github.javiersantos.materialstyleddialogs.enums.Style
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.activities.ReminderNotification
import es.usc.citius.servando.calendula.activities.ScheduleCreationActivity
import es.usc.citius.servando.calendula.adapters.items.ScheduleListItem
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.databinding.FragmentScheduleListBinding
import es.usc.citius.servando.calendula.events.PersistenceEvents.ActiveUserChangeEvent
import es.usc.citius.servando.calendula.events.PersistenceEvents.ModelCreateOrUpdateEvent
import es.usc.citius.servando.calendula.persistence.Schedule
import es.usc.citius.servando.calendula.util.IconUtils
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import java.util.concurrent.Executors

class ScheduleListFragment : Fragment() {
    private val viewModel: ScheduleListFragViewModel by viewModels()
    private lateinit var adapter: FastItemAdapter<ScheduleListItem>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_schedule_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentScheduleListBinding.bind(view)
        setupRecyclerView(binding)
    }

    private fun notifyDataChange() {
        viewModel.scheduleListLiveData.reload()
    }

    private fun showDeleteConfirmationDialog(s: Schedule) {
        MaterialStyledDialog.Builder(requireContext())
            .setStyle(Style.HEADER_WITH_ICON)
            .setIcon(
                IconUtils.icon(
                    requireContext(),
                    CommunityMaterial.Icon.cmd_calendar,
                    R.color.white,
                    100
                )
            )
            .setHeaderColor(R.color.android_red)
            .withDialogAnimation(true)
            .setTitle(getString(R.string.remove_schedule_dialog_title))
            .setDescription(
                String.format(
                    getString(R.string.remove_schedule_message),
                    s.medicine().name
                )
            )
            .setCancelable(true)
            .setNeutralText(getString(R.string.dialog_no_option))
            .setPositiveText(getString(R.string.dialog_yes_option))
            .onPositive { dialog, which ->
                DB.schedules().deleteCascade(s, true)
                ReminderNotification.cancel(
                    requireContext(),
                    ReminderNotification.scheduleNotificationId(s.id.toInt())
                )
                notifyDataChange()
            }
            .onNeutral { dialog, which -> dialog.cancel() }
            .show()
    }

    private fun setupRecyclerView(binding: FragmentScheduleListBinding) {
        adapter = FastItemAdapter()
        adapter.withSelectable(false)
        adapter.withPositionBasedStateManagement(false)
        adapter.withOnClickListener { v, adapter, item, position ->
            item.schedule?.let {
                val intent = Intent(requireActivity(), ScheduleCreationActivity::class.java)
                intent.putExtra(CalendulaApp.INTENT_EXTRA_SCHEDULE_ID, it.id)
                startActivity(intent)
            }
            true
        }
        adapter.withOnLongClickListener { v, adapter, item, position ->
            showDeleteConfirmationDialog(item.schedule)
            true
        }
        binding.scheduleList.adapter = adapter
        viewModel.scheduleListLiveData.observe(viewLifecycleOwner) {
            if(it.isEmpty()) {
                binding.empty.visibility = View.VISIBLE
                binding.scheduleList.visibility = View.INVISIBLE
            } else {
                binding.empty.visibility = View.INVISIBLE
                binding.scheduleList.visibility = View.VISIBLE
            }
            adapter.set(it)
        }
    }
}

class ScheduleListFragViewModel: ViewModel() {
    internal val scheduleListLiveData = ScheduleListLiveData().apply { addCloseable(this) }
}

internal class ScheduleListLiveData: AbstractReloadableLiveData<List<ScheduleListItem>>() {
    init {
        reload()
        eventBus().register(this)
    }

    override fun close() = eventBus().unregister(this)
    override fun getScheduler() = scheduler
    override fun loadData() = DB.schedules().findAllForActivePatient(CalendulaApp.context).map { ScheduleListItem(it) }

    @Suppress("unused")
    @Subscribe
    fun handleEvent(event: Any?) {
        when(event) {
            is ActiveUserChangeEvent -> reload()
            is ModelCreateOrUpdateEvent -> reload()
        }
    }

    companion object {
        private val scheduler by lazy { Schedulers.from(Executors.newSingleThreadExecutor()) }
    }
}