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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.databinding.DailyViewIntakeMedBinding
import es.usc.citius.servando.calendula.fragments.HomeProfileMgr
import es.usc.citius.servando.calendula.scheduling.AlarmScheduler
import es.usc.citius.servando.calendula.util.AvatarMgr
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.ScreenUtils
import es.usc.citius.servando.calendula.util.view.ParallaxImageView
import org.joda.time.DateTime
import org.joda.time.LocalDate

class DailyAgendaRecyclerAdapter(rv: RecyclerView, llm: LinearLayoutManager) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var items: List<DailyAgendaItemStub> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    private val window: Long
    private val SPACER = 1
    private val EMPTY = 2
    private val NORMAL = 3
    var isExpanded: Boolean = false
        private set
    private val parallaxHeight: Int
    private val enableParallax = true
    private var listener: EventListener? = null

    init {
        val delayMinutesStr = PreferenceUtils.getString(PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW, "60")
        window = delayMinutesStr.toLong()
        parallaxHeight = rv.height * 2

        if (enableParallax) {
            rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = updateParallax(llm, recyclerView)
            })
        }
    }

    fun setListener(listener: EventListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        when (viewType) {
            NORMAL -> {
                val v = layoutInflater.inflate(R.layout.daily_view_intake, parent, false)
                return NormalItemViewHolder(v, listener)
            }

            SPACER -> {
                val v = layoutInflater.inflate(R.layout.daily_view_empty_dayspacer, parent, false)
                return SpacerItemViewHolder(v, parallaxHeight)
            }

            else -> {
                val v = layoutInflater.inflate(R.layout.daily_view_empty_hour, parent, false)
                return EmptyItemViewHolder(v)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        var type = EMPTY
        when {
            item.hasEvents -> type = NORMAL
            item.isSpacer -> type = SPACER
        }
        return type
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is SpacerItemViewHolder -> onBindViewSpacerItemViewHolder(holder, item)
            is NormalItemViewHolder -> onBindNormalItemViewHolder(holder, item)
            is EmptyItemViewHolder -> onBindEmptyItemViewHolder(holder, item)
            else -> {}
        }
    }

    override fun getItemCount() = items.size

    private fun onBindViewSpacerItemViewHolder(holder: SpacerItemViewHolder, item: DailyAgendaItemStub) {
        val ctx = holder.itemView.context
        if (isExpanded) {
            val color = HomeProfileMgr.colorForCurrent(holder.itemView.context)
            val title = when (item.date) {
                LocalDate.now() -> ctx.getString(R.string.today)
                LocalDate.now().minusDays(1) -> ctx.getString(R.string.yesterday)
                LocalDate.now().plusDays(1) -> ctx.getString(R.string.tomorrow)
                else -> item.date.toString("EEEE dd")
            }

            holder.dayBg.setBackgroundColor(color)
            holder.day.visibility = View.VISIBLE
            holder.day.text = title
            holder.parallax.updateParallax()
        }

        holder.itemView.visibility = if (isExpanded) View.VISIBLE else View.GONE
        val params = holder.itemView.layoutParams
        val newHeight = if (isExpanded) ScreenUtils.dpToPx(holder.itemView.resources, 80f) else 0

        if (params.height != newHeight) {
            params.height = newHeight
            holder.itemView.layoutParams = params
        }
    }

    private fun onBindEmptyItemViewHolder(viewHolder: EmptyItemViewHolder, item: DailyAgendaItemStub) {
        if (isExpanded) {
            val d = item.date
            if (d == DateTime.now().toLocalDate()) {
                viewHolder.hourText.text = if (item.time != null) item.time.toString("HH:mm") else "--"
            } else {
                viewHolder.hourText.text = item.dateTime().toString("HH:mm")
            }
        }
        viewHolder.itemView.visibility = if (isExpanded) View.VISIBLE else View.GONE

        val params = viewHolder.container.layoutParams
        val emptyItemHeight = ScreenUtils.dpToPx(viewHolder.itemView.context.resources, 45f)
        val newHeight = if (isExpanded) emptyItemHeight else 0
        if (params.height != newHeight) {
            params.height = newHeight
            viewHolder.container.layoutParams = params
        }
    }

    private fun onBindNormalItemViewHolder(viewHolder: NormalItemViewHolder, item: DailyAgendaItemStub) {
        viewHolder.stub = item
        item.displayable = isDisplayable(item)

        if (item.displayable) {
            if (!item.isRoutine) {
                viewHolder.itemTypeIcon.setImageResource(R.drawable.ic_history_black_48dp)
            } else {
                viewHolder.itemTypeIcon.setImageResource(R.drawable.ic_alarm_black_48dp)
            }

            if (item.patient != null) {
                viewHolder.avatarIcon.setImageResource(AvatarMgr.res(item.patient.avatar))
                viewHolder.patientIndicatorBand.setBackgroundColor(item.patient.color)
                val layoutParams = viewHolder.patientIndicatorBand.layoutParams
                layoutParams.height = viewHolder.itemView.layoutParams.height
                viewHolder.patientIndicatorBand.layoutParams = layoutParams
            }

            viewHolder.title.text = item.title
            viewHolder.hour.text = String.format("%s:", item.time.toString("HH"))
            viewHolder.minute.text = item.time.toString("mm")

            val allTaken = addMeds(viewHolder, item)

            if (allTaken) {
                viewHolder.takenOverlay.visibility = View.VISIBLE
                viewHolder.actionsView.visibility = View.GONE
            } else {
                viewHolder.takenOverlay.visibility = View.GONE
                if (isAvailable(item)) {
                    viewHolder.actionsView.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(500)
                    viewHolder.actionsView.visibility = View.VISIBLE
                } else {
                    viewHolder.actionsView.visibility = View.GONE
                }
            }
        }

        viewHolder.itemView.visibility = if (item.displayable) View.VISIBLE else View.GONE

        val params = viewHolder.itemView.layoutParams
        val newHeight = if (item.displayable) ViewGroup.LayoutParams.WRAP_CONTENT else 0
        if (params.height != newHeight) {
            params.height = newHeight
            viewHolder.itemView.layoutParams = params
        }
    }

    val isShowingSomething: Boolean
        get() {
            if (isExpanded && items.isNotEmpty()) return true
            return items.any { isDisplayable(it) }
        }

    @MainThread
    fun toggleCollapseMode() {
        isExpanded = !isExpanded
        listener?.onBeforeToggleCollapse(isExpanded, isShowingSomething)
        items.indices.forEach { notifyItemChanged(it) }
        listener?.onAfterToggleCollapse(isExpanded, isShowingSomething)
    }

    fun updatePosition(position: Int) {
        if (position >= 0 && position < items.size) updateItem(position)
    }

    private fun isAvailable(stub: DailyAgendaItemStub): Boolean {
        return isAvailable(stub.dateTime())
    }

    private fun isDisplayable(stub: DailyAgendaItemStub): Boolean {
        val t = stub.dateTime()
        val midnight = DateTime.now().withTimeAtStartOfDay().plusDays(1)
        return stub.hasEvents && (isAvailable(stub) || isExpanded || (t.isAfterNow && t.isBefore(midnight)))
    }

    private fun isAvailable(time: DateTime): Boolean {
        val now = DateTime.now()
        return time.isBefore(now) && time.plusMillis(window.toInt() * 60 * 1000).isAfter(now)
    }

    fun updateParallax(lm: LinearLayoutManager, rv: RecyclerView) {
        if (!isExpanded) {
            return
        }

        val start = lm.findFirstVisibleItemPosition()
        val end = lm.findLastVisibleItemPosition()

        for (i in start until end) {
            val h = rv.findViewHolderForAdapterPosition(i)
            if (h is SpacerItemViewHolder) {
                h.parallax.updateParallax()
            }
        }
    }

    private fun addMeds(viewHolder: NormalItemViewHolder, item: DailyAgendaItemStub): Boolean {
        var allTaken = true

        viewHolder.medList.removeAllViews()

        for (element in item.meds) {
            val binding = DailyViewIntakeMedBinding.inflate(viewHolder.inflater, viewHolder.medList, true)
            val units = element.presentation.units(viewHolder.context.resources, element.dose)
            binding.imageView.setImageDrawable(
                medIcon(
                    element.presentation.icon(),
                    binding.root.context
                )
            )
            binding.medItemDose.text = "${element.displayDose} $units"
            binding.medItemName.text = element.medName
            if (element.medNameDecorator != null) {
                binding.nameDecorator.setImageDrawable(element.medNameDecorator)
            } else {
                binding.nameDecorator.visibility = View.GONE
            }

            if (element.taken) {
                binding.icDone.visibility = View.VISIBLE
            } else {
                allTaken = false
                binding.icDone.visibility = View.INVISIBLE
            }
        }

        return allTaken
    }

    private fun medIcon(icon: IIcon, ctx: Context): Drawable {
        return IconicsDrawable(ctx)
            .icon(icon)
            .colorRes(R.color.white)
            .sizeDp(24)
            .paddingDp(0)
    }

    private fun updateStub(stub: DailyAgendaItemStub) {
        if (!stub.isRoutine) {
            val s = DB.schedules().findById(stub.id)
            val dsi = DB.dailyScheduleItems().findBy(s, stub.date, stub.time)
            stub.meds[0].taken = dsi.takenToday
        } else {
            for (el in stub.meds) {
                val si = DB.scheduleItems().findById(el.scheduleItemId)
                val dsi = DB.dailyScheduleItems().findByScheduleItemAndDate(si, stub.date)
                el.taken = dsi.takenToday
            }
        }
    }

    private fun updateItem(position: Int) {
        if (position < items.size && position > -1) {
            val stub = items[position]
            updateStub(stub)
            notifyItemChanged(position)
        }
    }

    interface EventListener {
        fun onItemClick(v: View, item: DailyAgendaItemStub, position: Int)
        fun onBeforeToggleCollapse(expanded: Boolean, somethingVisible: Boolean)
        fun onAfterToggleCollapse(expanded: Boolean, somethingVisible: Boolean)
    }

    class EmptyItemViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var container: RelativeLayout = itemView.findViewById<View>(R.id.container) as RelativeLayout
        var hourText: TextView = itemView.findViewById<View>(R.id.hour_text) as TextView
    }

    class SpacerItemViewHolder internal constructor(itemView: View, parallaxHeight: Int) : RecyclerView.ViewHolder(itemView) {
        var day: TextView = itemView.findViewById<View>(R.id.day_text) as TextView
        var dayBg: ImageView = itemView.findViewById<View>(R.id.day_bg) as ImageView
        var container: View = itemView.findViewById(R.id.container)
        var parallax: ParallaxImageView = itemView.findViewById<View>(R.id.parallax_bg) as ParallaxImageView

        init {
            val layoutParams = parallax.layoutParams
            layoutParams.height = parallaxHeight
            parallax.layoutParams = layoutParams
        }
    }

    inner class NormalItemViewHolder(itemView: View, private val listener: EventListener?) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        var context: Context = itemView.context
        lateinit var stub: DailyAgendaItemStub

        var inflater: LayoutInflater = LayoutInflater.from(itemView.context)
        var medList: LinearLayout = itemView.findViewById<View>(R.id.med_item_list) as LinearLayout
        var itemTypeIcon: ImageView = itemView.findViewById<View>(R.id.imageButton2) as ImageView
        var avatarIcon: ImageView = itemView.findViewById<View>(R.id.patient_avatar) as ImageView
        var patientIndicatorBand: ImageView = itemView.findViewById<View>(R.id.patient_indicator_band) as ImageView

        var title: TextView = itemView.findViewById<View>(R.id.routines_list_item_name) as TextView
        var hour: TextView = itemView.findViewById<View>(R.id.routines_list_item_hour) as TextView
        var minute: TextView = itemView.findViewById<View>(R.id.routines_list_item_minute) as TextView

        private var arrow: View = itemView.findViewById(R.id.count_container)
        var top: View = itemView.findViewById(R.id.routine_list_item_container)
        var bottom: View = itemView.findViewById(R.id.bottom)

        var takenOverlay: View = itemView.findViewById(R.id.taken_overlay)

        var actionsView: View = itemView.findViewById(R.id.action_container)
        private var checkAll: ImageButton = itemView.findViewById<View>(R.id.check_all_button) as ImageButton

        init {
            checkAll.setImageDrawable(
                IconicsDrawable(context)
                    .colorRes(R.color.white) //agenda_item_title
                    .icon(CommunityMaterial.Icon.cmd_check_all) //cmd_arrow_right_bold
                    .paddingDp(0)
                    .sizeDp(28)
            )

            actionsView.setOnClickListener(this)
            top.setOnClickListener(this)
            arrow.setOnClickListener(this)
            itemView.setOnClickListener(this)
            checkAll.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            if (view.id == R.id.check_all_button || view.id == R.id.action_container) {
                hideCheckAllButton()
            } else {
                listener?.onItemClick(view, stub, adapterPosition)
            }
        }

        private fun hideCheckAllButton() {
            actionsView.animate().setDuration(100).alpha(0f).scaleY(0.5f).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (stub.isRoutine) {
                        AlarmScheduler.instance().onIntakeConfirmAll(DB.routines().findById(stub.id), stub.date, context)
                    } else {
                        AlarmScheduler.instance().onIntakeConfirmAll(DB.schedules().findById(stub.id), stub.time, stub.date, context)
                    }
                    updateItem(adapterPosition)
                }
            })
        }
    }

    companion object {
        private const val TAG = "DailyAgendaAdapter"
    }
}
