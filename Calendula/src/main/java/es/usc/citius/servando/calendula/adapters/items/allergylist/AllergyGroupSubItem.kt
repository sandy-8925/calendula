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
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.allergies.AllergenType
import es.usc.citius.servando.calendula.persistence.PatientAllergen

class AllergyGroupSubItem(vo: PatientAllergen, context: Context) :
    AbstractExpandableItem<AllergyGroupSubItem.ViewHolder>(),
    Comparable<AllergyGroupSubItem> {
    private val allergen: PatientAllergen = vo
    @JvmField
    val title: String = vo.name
    private var subtitle: String? = null
    private var holder: ViewHolder? = null

    init {
        subtitle = when (vo.type) {
            AllergenType.ACTIVE_INGREDIENT -> context.getString(R.string.active_ingredient)
            AllergenType.EXCIPIENT -> context.getString(R.string.excipient)
        }
    }

    override fun bindView(holder: ViewHolder, payloads: List<Any>) {
        super.bindView(holder, payloads)
        holder.title.text = title
        holder.subtitle.text = subtitle
        this.holder = holder
    }

    override fun unbindView(holder: ViewHolder) {
        super.unbindView(holder)
        holder.title.text = null
        holder.subtitle.text = null
    }

    override fun compareTo(o: AllergyGroupSubItem): Int {
        return title.compareTo(o.title)
    }

    fun getAllergen() = allergen

    override var isSelectable: Boolean = false
        get() = false

    override val type: Int = R.id.fastadapter_allergy_group_sub_item

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    override val layoutRes = R.layout.allergen_search_group_sub_list_item

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        @BindView(R.id.text1)
        lateinit var title: TextView

        @BindView(R.id.text2)
        lateinit var subtitle: TextView

        init {
            ButterKnife.bind(this, itemView)
        }
    }
}
