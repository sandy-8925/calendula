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
package es.usc.citius.servando.calendula.activities

import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.mikepenz.aboutlibraries.LibsBuilder
import es.usc.citius.servando.calendula.CalendulaActivity
import es.usc.citius.servando.calendula.R

class AboutActivity : CalendulaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val darkgreyHomeColour = ResourcesCompat.getColor(resources, R.color.dark_grey_home, theme)
        setupToolbar(getString(R.string.title_about), darkgreyHomeColour)
        setupStatusBar(darkgreyHomeColour)

        if (savedInstanceState == null) {
            val fragment: Fragment = LibsBuilder()
                .withAboutAppName(getString(R.string.app_name))
                .withAboutIconShown(true)
                .withAboutVersionShown(true)
                .withLicenseShown(true)
                .withLicenseDialog(true)
                .withAboutDescription(getString(R.string.about_description))
                .fragment()

            supportFragmentManager.commit { add(R.id.fragment_holder, fragment) }
        }
    }
}
