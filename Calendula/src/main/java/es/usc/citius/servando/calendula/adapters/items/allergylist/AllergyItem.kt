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
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.iconics.IconicsDrawable
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.allergies.AllergenType
import es.usc.citius.servando.calendula.persistence.PatientAllergen

class AllergyItem(vo: PatientAllergen, context: Context) :
    AbstractItem<AllergyItem.ViewHolder>(), Comparable<AllergyItem> {
    @JvmField
    var title: String = vo.name
    private var allergenType: String? = null
    @JvmField
    val allergen: PatientAllergen
    private var holder: ViewHolder? = null

    init {
        allergenType = when (vo.type) {
            AllergenType.ACTIVE_INGREDIENT -> context.getString(R.string.active_ingredient)
            AllergenType.EXCIPIENT -> context.getString(R.string.excipient)
        }
        this.allergen = vo
    }

    override fun compareTo(o: AllergyItem): Int {
        return title.compareTo(o.title)
    }

    override val type: Int
        get() = R.id.fastadapter_allergy_item

    override fun getViewHolder(v: View) = ViewHolder(v)

    override val layoutRes: Int
        get() = R.layout.allergen_list_item

    @Suppress("SuspiciousVarProperty")
    override var isSelectable: Boolean = false
        get() = false

    override fun bindView(viewHolder: ViewHolder, payloads: List<Any>) {
        super.bindView(viewHolder, payloads)
        this.holder = viewHolder
        viewHolder.title.text = title
        viewHolder.subtitle!!.text = allergenType
        viewHolder.deleteButton!!.setImageDrawable(
            IconicsDrawable(viewHolder.deleteButton!!.context)
                .icon(CommunityMaterial.Icon.cmd_delete)
                .colorRes(R.color.agenda_item_title)
                .paddingDp(10)
                .sizeDp(38)
        )
    }

    //reset the view here (this is an optional method, but recommended)
    override fun unbindView(holder: ViewHolder) {
        super.unbindView(holder)
        holder.title.text = null
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        @JvmField
        @BindView(R.id.delete_button)
        var deleteButton: ImageButton? = null

        @BindView(R.id.text1)
        lateinit var title: TextView

        @BindView(R.id.text2)
        lateinit var subtitle: TextView

        init {
            ButterKnife.bind(this, view)
        }
    }
}
