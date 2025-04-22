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

package es.usc.citius.servando.calendula.adapters.items.allergensearch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IExpandable;
import com.mikepenz.fastadapter.IParentItem;
import com.mikepenz.fastadapter.ISubItem;
import com.mikepenz.fastadapter.commons.items.AbstractExpandableItem;
import com.mikepenz.fastadapter.commons.utils.FastAdapterUIUtils;
import com.mikepenz.fastadapter.expandable.ExpandableExtension;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.materialize.util.UIUtils;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.R;

public class AllergenGroupItem extends AbstractExpandableItem<AllergenGroupItem, AllergenGroupItem.ViewHolder, AllergenGroupSubItem> implements Comparable<AllergenGroupItem> {
    @SuppressWarnings("unused")
    private static final String TAG = "AllergyGroupItem";
    private String title;
    private SpannableStringBuilder titleSpannable;
    private String subtitle;


    public AllergenGroupItem(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    @Override
    public void bindView(@NonNull ViewHolder holder, @NonNull List<?> payloads) {
        super.bindView(holder, payloads);
        holder.title.setText(titleSpannable != null ? titleSpannable : title);
        holder.subtitle.setText(this.subtitle);
        final float rotation = isExpanded() ? 180 : 0;
        holder.imageButton.setRotation(rotation);
        final int selectedColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.med_presentation_circle_bg);
        UIUtils.setBackground(holder.itemView, FastAdapterUIUtils.getSelectableBackground(holder.itemView.getContext(), selectedColor, true));
        holder.imageButton.setImageDrawable(new IconicsDrawable(holder.imageButton.getContext())
                .icon(GoogleMaterial.Icon.gmd_chevron_down)
                .colorRes(R.color.agenda_item_title)
                .paddingDp(10)
                .sizeDp(38));
    }

    @Override
    public void unbindView(ViewHolder holder) {
        super.unbindView(holder);
        holder.title.setText(null);
        holder.subtitle.setText(null);
        holder.imageButton.setRotation(0);
    }

    @Override
    public int compareTo(@NonNull AllergenGroupItem o) {
        return this.title.compareTo(o.title);
    }

    public String getTitle() {
        return title;
    }

    @Override
    public int getType() {
        return R.id.fastadapter_allergen_group_item;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.allergen_search_group_list_item;
    }

    @Override
    public boolean isAutoExpanding() {
        return false;
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public void setTitleSpannable(SpannableStringBuilder titleSpannable) {
        this.titleSpannable = titleSpannable;
    }

    @Override
    public void setExpanded(boolean b) {

    }

    @Override
    public void setSubItems(@NonNull List<ISubItem<?>> list) {

    }

    @Override
    public void setParent(@Nullable IParentItem<?> iParentItem) {

    }

    @NonNull
    @Override
    public ViewHolder getViewHolder(@NonNull View view) {
        return null;
    }

    @Override
    public void bindView(@NonNull AllergenGroupItem allergenGroupItem, @NonNull List<?> list) {

    }

    @Override
    public void unbindView(@NonNull AllergenGroupItem allergenGroupItem) {

    }

    @Override
    public void attachToWindow(@NonNull AllergenGroupItem allergenGroupItem) {

    }

    @Override
    public void detachFromWindow(@NonNull AllergenGroupItem allergenGroupItem) {

    }

    @Override
    public boolean failedToRecycle(@NonNull AllergenGroupItem allergenGroupItem) {
        return false;
    }

    public static class GroupExpandClickEvent extends ClickEventHook<AbstractItem<ViewHolder>> {
        @Override
        public View onBind(@NonNull RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof ViewHolder) {
                return ((ViewHolder) viewHolder).imageButton;
            }
            return null;
        }

        @Override
        public void onClick(@NonNull View view, int i, @NonNull FastAdapter<AbstractItem<ViewHolder>> fastAdapter, @NonNull AbstractItem<ViewHolder> item) {
            ExpandableExtension<AbstractItem<ViewHolder>> extension = fastAdapter.getOrCreateExtension(ExpandableExtension.class);
            IExpandable it = (IExpandable) item;
            if (it.isExpanded()) {
                extension.collapse(i);
                ViewCompat.animate(view).rotation(0);
            } else {
                extension.expand(i);
                ViewCompat.animate(view).rotation(180);
            }
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.text1)
        TextView title;
        @BindView(R.id.text2)
        TextView subtitle;
        @BindView(R.id.group_button)
        ImageButton imageButton;

        public ViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }

}
