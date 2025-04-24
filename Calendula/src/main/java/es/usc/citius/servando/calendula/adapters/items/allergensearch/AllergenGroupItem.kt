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
package es.usc.citius.servando.calendula.adapters.items.allergensearch

import android.text.SpannableStringBuilder
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.mikepenz.fastadapter.ClickListener
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IClickable
import com.mikepenz.fastadapter.IExpandable
import com.mikepenz.fastadapter.commons.utils.FastAdapterUIUtils
import com.mikepenz.fastadapter.expandable.ExpandableExtension
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.ClickEventHook
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.materialize.util.UIUtils
import es.usc.citius.servando.calendula.R

class AllergenGroupItem(@JvmField val title: String, private var subtitle: String) :
    AbstractExpandableItem<AllergenGroupItem.ViewHolder>(),
    Comparable<AllergenGroupItem>, IClickable<AllergenGroupItem> {
    private var titleSpannable: SpannableStringBuilder? = null

    override fun bindView(holder: ViewHolder, payloads: List<Any>) {
        super.bindView(holder, payloads)
        holder.title.text = if (titleSpannable != null) titleSpannable else title
        holder.subtitle.text = subtitle
        val rotation = (if (isExpanded) 180 else 0).toFloat()
        holder.imageButton.rotation = rotation
        val selectedColor =
            ContextCompat.getColor(holder.itemView.context, R.color.med_presentation_circle_bg)
        UIUtils.setBackground(
            holder.itemView,
            FastAdapterUIUtils.getSelectableBackground(holder.itemView.context, selectedColor, true)
        )
        holder.imageButton.setImageDrawable(
            IconicsDrawable(holder.imageButton.context)
                .icon(GoogleMaterial.Icon.gmd_chevron_down)
                .colorRes(R.color.agenda_item_title)
                .paddingDp(10)
                .sizeDp(38)
        )
    }

    override fun unbindView(holder: ViewHolder) {
        super.unbindView(holder)
        holder.title.text = null
        holder.subtitle.text = null
        holder.imageButton.rotation = 0f
    }

    override fun compareTo(o: AllergenGroupItem): Int {
        return title.compareTo(o.title)
    }

    override val type: Int
        get() = R.id.fastadapter_allergen_group_item

    override val layoutRes: Int
        get() = R.layout.allergen_search_group_list_item

    override val isAutoExpanding: Boolean
        get() = false

    @Suppress("SuspiciousVarProperty")
    override var isSelectable: Boolean = true
        get() = true

    fun setSubtitle(subtitle: String) {
        this.subtitle = subtitle
    }

    fun setTitleSpannable(titleSpannable: SpannableStringBuilder?) {
        this.titleSpannable = titleSpannable
    }

    override fun getViewHolder(v: View) = ViewHolder(v)

    class GroupExpandClickEvent : ClickEventHook<AbstractItem<ViewHolder>>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            if (viewHolder is ViewHolder) {
                return viewHolder.imageButton
            }
            return null
        }

        override fun onClick(
            v: View,
            position: Int,
            fastAdapter: FastAdapter<AbstractItem<ViewHolder>>,
            item: AbstractItem<ViewHolder>
        ) {
            val extension =
                fastAdapter.getOrCreateExtension<ExpandableExtension<AbstractItem<ViewHolder>>>(
                    ExpandableExtension::class.java
                )!!
            val it = item as IExpandable<*>
            if (it.isExpanded) {
                extension.collapse(position)
                ViewCompat.animate(v).rotation(0f)
            } else {
                extension.expand(position)
                ViewCompat.animate(v).rotation(180f)
            }
        }
    }

    class ViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        @BindView(R.id.text1)
        lateinit var title: TextView

        @BindView(R.id.text2)
        lateinit var subtitle: TextView

        @BindView(R.id.group_button)
        lateinit var imageButton: ImageButton

        init {
            ButterKnife.bind(this, itemView!!)
        }
    }

    companion object {
        @Suppress("unused")
        private val TAG = "AllergyGroupItem"
    }

    override var onItemClickListener: ClickListener<AllergenGroupItem>? = null
    override var onPreItemClickListener: ClickListener<AllergenGroupItem>? = null
}
