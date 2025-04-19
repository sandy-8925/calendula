package es.usc.citius.servando.calendula.util

import android.arch.lifecycle.LiveData
import android.content.SharedPreferences
import android.text.TextUtils
import java.io.Closeable

@Suppress("LeakingThis")
abstract class SharedPrefsLiveData<T>(protected val sharedPreferences: SharedPreferences, private val watchKey: String) : LiveData<T>(),
    SharedPreferences.OnSharedPreferenceChangeListener, Closeable {
    init {
        value = getSharedPrefsValue(watchKey)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if(!TextUtils.equals(watchKey, key)) return
        value = getSharedPrefsValue(watchKey)
    }

    abstract fun getSharedPrefsValue(key: String): T?

    override fun close() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }
}

internal class BooleanSharedPrefsLiveData(sharedPreferences: SharedPreferences, watchKey: String) : SharedPrefsLiveData<Boolean>(sharedPreferences, watchKey) {
    override fun getSharedPrefsValue(key: String): Boolean = sharedPreferences.getBoolean(key, false)
}