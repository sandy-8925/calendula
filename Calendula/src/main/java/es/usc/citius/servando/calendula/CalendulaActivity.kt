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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import es.usc.citius.servando.calendula.CalendulaApp.Companion.eventBus
import es.usc.citius.servando.calendula.activities.StartActivity
import es.usc.citius.servando.calendula.pinlock.PINManager
import es.usc.citius.servando.calendula.pinlock.PinLockActivity
import es.usc.citius.servando.calendula.pinlock.UnlockStateManager
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PermissionUtils
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.ScreenUtils

@SuppressLint("Registered")
abstract class CalendulaActivity : AppCompatActivity() {
    @JvmField protected var toolbar: Toolbar? = null
    private val permissionRequestListeners = mutableMapOf<Int, PermissionUtils.PermissionRequest>()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // set FLAG secure if the secure_window preference is enabled
        if (PreferenceUtils.getBoolean(PreferenceKeys.SECURE_WINDOW, false)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    @JvmOverloads
    protected fun setupToolbar(title: String?, @ColorInt color: Int, @ColorInt iconColor: Int = Color.WHITE): CalendulaActivity {
        // set up the toolbar
        findViewById<View>(R.id.toolbar)?.let { toolbar = it as Toolbar? }
        
        toolbar?.let {
            it.setBackgroundColor(color)
            it.navigationIcon = getNavigationIcon(iconColor)
        }
        setSupportActionBar(toolbar)

        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            if (title == null) {
                //set the back arrow in the toolbar
                it.setDisplayShowTitleEnabled(false)
                it.setHomeButtonEnabled(true)
            } else {
                it.setDisplayShowTitleEnabled(true)
                it.title = title
            }
        }
        return this
    }

    protected fun setupStatusBar(@ColorInt color: Int): CalendulaActivity {
        ScreenUtils.setStatusBarColor(this, color)
        return this
    }

    protected fun subscribeToEvents(): CalendulaActivity {
        eventBus().register(this)
        return this
    }

    protected fun unsubscribeFromEvents(): CalendulaActivity {
        val eventBus = eventBus()
        if (eventBus.isRegistered(this)) {
            eventBus.unregister(this)
        }
        return this
    }

    private fun getNavigationIcon(@ColorInt iconColor: Int): Drawable {
        return IconicsDrawable(this, GoogleMaterial.Icon.gmd_arrow_back)
            .color(iconColor)
            .actionBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        unsubscribeFromEvents()
    }

    fun requestPermission(req: PermissionUtils.PermissionRequest) {
        if (PermissionUtils.useRunTimePermissions()) {
            var shouldAsk = true
            var missingPermissions = false
            for (p in req.permissions()) {
                if (!PermissionUtils.hasPermission(this, p)) {
                    missingPermissions = true
                    if (!PermissionUtils.shouldAskForPermission(this, p)) {
                        shouldAsk = false
                    }
                }
            }
            if (missingPermissions && shouldAsk) {
                permissionRequestListeners[req.reqCode()] = req
                PermissionUtils.requestPermissions(this, req.permissions(), req.reqCode())
            } else if (missingPermissions) {
                showManualPermissionGrantDialog()
            } else {
                req.onPermissionGranted()
            }
        } else {
            req.onPermissionGranted()
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (permissionRequestListeners.containsKey(requestCode)) {
            val req = permissionRequestListeners[requestCode]
            for (p in req!!.permissions()) {
                PermissionUtils.markPermissionAsAsked(this, p)
            }
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                req.onPermissionGranted()
            } else {
                req.onPermissionDenied()
            }
            permissionRequestListeners.remove(requestCode)
        }
    }

    private fun showManualPermissionGrantDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(getString(R.string.permission_dialog_go_to_settings))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.permission_dialog_ok)) { dialog, id -> PermissionUtils.goToAppSettings(this@CalendulaActivity) }
            .setNegativeButton(getString(R.string.dialog_no_option)) { dialog, id -> dialog.cancel() }
        val alert = builder.create()
        alert.show()
    }

    override fun onResume() {
        super.onResume()
        if (PINManager.isPINSet() && !UnlockStateManager.getInstance().isUnlocked && this !is PinLockActivity) {
            // If we get unlock timeout on resume, we'll call StartActivity to ask for a PIN
            LogUtil.d("CalendulaActivity", "Unlock has expired")
            val i = Intent(this, StartActivity::class.java)
            i.putExtra(StartActivity.EXTRA_RETURN_TO_PREVIOUS, true)
            //i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i)
        }
    }
}
