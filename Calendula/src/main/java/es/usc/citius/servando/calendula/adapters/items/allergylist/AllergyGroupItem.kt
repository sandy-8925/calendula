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
package es.usc.citius.servando.calendula.adapters.items.allergylist

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import es.usc.citius.servando.calendula.R

abstract class AllergyGroupItem(@JvmField val title: String, private val context: Context) :
    AbstractExpandableItem<AllergyGroupItem.ViewHolder>(),
    Comparable<AllergyGroupItem> {

    override fun bindView(holder: ViewHolder, payloads: List<Any>) {
        super.bindView(holder, payloads)
        holder.title!!.text = title
        holder.subtitle!!.text = context.getString(
            R.string.allergies_group_elements_number,
            subItems.size
        )
        //        UIUtils.setBackground(holder.itemView, FastAdapterUIUtils.getSelectableBackground(holder.itemView.getContext(), Color.CYAN, true));
        holder.dropButton!!.setImageDrawable(
            IconicsDrawable(holder.dropButton!!.context)
                .icon(GoogleMaterial.Icon.gmd_chevron_down) //.color(0xFF222222)
                .colorRes(R.color.agenda_item_title)
                .paddingDp(10)
                .sizeDp(38)
        )
        val rotation = (if (isExpanded) 180 else 0).toFloat()
        holder.itemView.rotation = rotation
        holder.deleteButton!!.setImageDrawable(
            IconicsDrawable(holder.deleteButton!!.context)
                .icon(CommunityMaterial.Icon.cmd_delete)
                .colorRes(R.color.agenda_item_title)
                .paddingDp(10)
                .sizeDp(38)
        )
    }

    override fun unbindView(holder: ViewHolder) {
        super.unbindView(holder)
        holder.title.setText(null)
        holder.subtitle.setText(null)
        holder.dropButton!!.rotation = 0f
    }

    override fun compareTo(o: AllergyGroupItem): Int {
        return title.compareTo(o.title)
    }

    override val type: Int
        get() = R.id.fastadapter_allergy_group_item

    override val layoutRes: Int
        get() = R.layout.allergen_group_list_item

    override val isAutoExpanding: Boolean
        get() = false

    override var isSelectable: Boolean = false
        get() = false

    class ViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        @JvmField
        @BindView(R.id.group_button)
        var dropButton: ImageButton? = null

        @JvmField
        @BindView(R.id.delete_button)
        var deleteButton: ImageButton? = null

        @BindView(R.id.text1)
        lateinit var title: TextView

        @BindView(R.id.text2)
        lateinit var subtitle: TextView

        init {
            ButterKnife.bind(this, itemView!!)
        }
    }

    companion object {
        @Suppress("unused")
        private val TAG = "AllergyGroupItem"
    }
}
