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

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.github.javiersantos.materialstyleddialogs.enums.Style
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.activities.ReminderNotification
import es.usc.citius.servando.calendula.activities.RoutinesActivity
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.databinding.FragmentRoutinesListBinding
import es.usc.citius.servando.calendula.events.PersistenceEvents.ActiveUserChangeEvent
import es.usc.citius.servando.calendula.events.PersistenceEvents.ModelCreateOrUpdateEvent
import es.usc.citius.servando.calendula.persistence.Routine
import es.usc.citius.servando.calendula.scheduling.AlarmScheduler
import es.usc.citius.servando.calendula.util.IconUtils
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.Closeable
import java.util.concurrent.Executors
import org.greenrobot.eventbus.Subscribe


class RoutinesListFragment : Fragment() {
    private val viewModel: RoutinesListFragViewModel by viewModels()

    private val mRoutineSelectedCallback by lazy {
        OnRoutineSelectedListener {
            val intent = Intent(requireContext(), RoutinesActivity::class.java).apply {
                putExtra(CalendulaApp.INTENT_EXTRA_ROUTINE_ID, it.id)
            }
            requireContext().startActivity(intent)
        }
    }

    private val ic: Drawable by lazy {
        IconicsDrawable(requireContext())
            .icon(CommunityMaterial.Icon.cmd_clock)
            .colorRes(R.color.agenda_item_title)
            .paddingDp(8)
            .sizeDp(40)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentRoutinesListBinding.bind(view)
        val listAdapter = RoutinesListAdapter()
        binding.routinesList.apply {
            emptyView = binding.empty
            adapter = listAdapter
        }
        viewModel.routinesListLiveData.observe(viewLifecycleOwner) { listAdapter.items = it }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_routines_list, container, false)

    private fun notifyDataChange() {
        viewModel.routinesListLiveData.reload()
    }

    private fun showDeleteConfirmationDialog(r: Routine) {
        val message = if (r.scheduleItems.size > 0) {
            String.format(getString(R.string.remove_routine_message_long), r.name)
        } else {
            String.format(getString(R.string.remove_routine_message_short), r.name)
        }

        MaterialStyledDialog.Builder(activity)
            .setTitle("")
            .setStyle(Style.HEADER_WITH_ICON)
            .setIcon(IconUtils.icon(activity, CommunityMaterial.Icon.cmd_clock, R.color.white, 100))
            .setHeaderColor(R.color.android_red)
            .withDialogAnimation(true)
            .setTitle(getString(R.string.remove_routine_dialog_title))
            .setDescription(message)
            .setCancelable(true)
            .setNeutralText(getString(R.string.dialog_no_option))
            .setPositiveText(getString(R.string.dialog_yes_option))
            .onPositive { dialog, which ->
                AlarmScheduler.instance().onDeleteRoutine(r, activity)
                DB.routines().deleteCascade(r, true)
                ReminderNotification.cancel(context, ReminderNotification.routineNotificationId(r.id.toInt()))
                notifyDataChange()
            }.onNeutral { dialog, which -> dialog.cancel() }
            .show()
    }

    private inner class RoutinesListAdapter : BaseAdapter() {
        var items: List<Routine> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int): Long = getItem(position).id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val layoutInflater = LayoutInflater.from(parent.context)
            return createRoutineListItem(layoutInflater, items[position], parent, mRoutineSelectedCallback)
        }
    }

    private fun createRoutineListItem(inflater: LayoutInflater, routine: Routine, parent: ViewGroup, mRoutineSelectedCallback: OnRoutineSelectedListener?): View {
        val hour = routine.time.hourOfDay
        val minute = routine.time.minuteOfHour

        val strHour = (if (hour >= 10) hour else "0$hour").toString()
        val strMinute = ":" + (if (minute >= 10) minute else "0$minute").toString()

        val item = inflater.inflate(R.layout.routines_list_item, parent, false)

        (item.findViewById<View>(R.id.routines_list_item_hour) as TextView).text = strHour
        (item.findViewById<View>(R.id.routines_list_item_minute) as TextView).text = strMinute
        (item.findViewById<View>(R.id.routines_list_item_name) as TextView).text = routine.name
        (item.findViewById<View>(R.id.imageButton2) as ImageButton).setImageDrawable(ic)

        val items = routine.scheduleItems.size

        val schedules = if (items > 0) parent.context.getString(R.string.schedules_for_med, items)
        else parent.context.getString(R.string.schedules_for_med_none)

        (item.findViewById<View>(R.id.routines_list_item_subtitle) as TextView).text = schedules
        val overlay = item.findViewById<View>(R.id.routine_list_item_container)
        overlay.tag = routine

        val clickListener = View.OnClickListener { view ->
            val r = view.tag as Routine?
            r?.let { mRoutineSelectedCallback?.onRoutineSelected(r) }
        }

        overlay.setOnClickListener(clickListener)
        overlay.setOnLongClickListener { view ->
            view.tag?.let { showDeleteConfirmationDialog(it as Routine) }
            true
        }
        return item
    }
}

// Container Activity must implement this interface
private fun interface OnRoutineSelectedListener {
    fun onRoutineSelected(r: Routine)
}

class RoutinesListFragViewModel: ViewModel() {
    internal val routinesListLiveData = RoutinesListLiveData(CalendulaApp.context).apply { addCloseable(this) }
}

internal class RoutinesListLiveData(private val context: Context) : AbstractReloadableLiveData<List<Routine>>() {
    override fun getScheduler() = scheduler
    override fun loadData(): List<Routine> = DB.routines().findAllForActivePatient(context)

    init {
        reload()
        eventBus().register(this)
    }

    override fun close() = eventBus().unregister(this)

    // Method called from the event bus
    @Suppress("unused")
    @Subscribe fun handleEvent(event: Any) {
        when(event) {
            is ModelCreateOrUpdateEvent -> reload()
            is ActiveUserChangeEvent -> reload()
        }
    }

    companion object {
        private val scheduler by lazy { Schedulers.from(Executors.newSingleThreadExecutor()) }
    }
}

internal abstract class AbstractReloadableLiveData<T>: LiveData<T>(), Closeable {
    protected abstract fun getScheduler(): Scheduler

    internal fun reload() {
        Completable.fromAction { postValue(loadData()) }
            .subscribeOn(getScheduler())
            .subscribe({},{})
    }

    protected abstract fun loadData(): T
}