package com.asdk.tools.wpstatussaver.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.asdk.tools.wpstatussaver.model.AppType

object SettingsManager {

    private const val PREFS_NAME = "wp_status_saver_settings"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private const val KEY_THEME = "pref_theme"
    private const val KEY_DEFAULT_SOURCE = "pref_default_source"
    private const val KEY_GRID_COLUMNS = "pref_grid_columns"
    private const val KEY_AUTO_PLAY_VIDEO = "pref_auto_play_video"
    private const val KEY_SHOW_VIDEO_BADGE = "pref_show_video_badge"
    private const val KEY_ASK_SAVE_LOCATION = "pref_ask_save_location"
    private const val KEY_DEFAULT_SAVE_LOCATION = "pref_default_save_location"
    private const val KEY_SORT_ORDER = "pref_sort_order"
    private const val KEY_STATUS_FILTER = "pref_status_filter"
    private const val KEY_AUTO_REFRESH = "pref_auto_refresh"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTheme(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun setTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_THEME, theme).apply()
        applyTheme(context)
    }

    fun applyTheme(context: Context) {
        when (getTheme(context)) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun getDefaultSource(context: Context): AppType {
        val saved = getPrefs(context).getString(KEY_DEFAULT_SOURCE, AppType.WHATSAPP.name)
        return try {
            AppType.valueOf(saved ?: AppType.WHATSAPP.name)
        } catch (e: Exception) {
            AppType.WHATSAPP
        }
    }

    fun setDefaultSource(context: Context, appType: AppType) {
        getPrefs(context).edit().putString(KEY_DEFAULT_SOURCE, appType.name).apply()
    }

    fun getGridColumns(context: Context): Int {
        return getPrefs(context).getInt(KEY_GRID_COLUMNS, 2)
    }

    fun setGridColumns(context: Context, columns: Int) {
        getPrefs(context).edit().putInt(KEY_GRID_COLUMNS, columns).apply()
    }

    fun isAutoPlayVideo(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_PLAY_VIDEO, true)
    }

    fun setAutoPlayVideo(context: Context, autoPlay: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PLAY_VIDEO, autoPlay).apply()
    }

    fun isShowVideoBadge(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_VIDEO_BADGE, true)
    }

    fun setShowVideoBadge(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_VIDEO_BADGE, show).apply()
    }

    fun isAskSaveLocation(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ASK_SAVE_LOCATION, false)
    }

    fun setAskSaveLocation(context: Context, ask: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ASK_SAVE_LOCATION, ask).apply()
    }

    fun getDefaultSaveLocation(context: Context): com.asdk.tools.wpstatussaver.model.SaveLocation {
        val saved = getPrefs(context).getString(KEY_DEFAULT_SAVE_LOCATION, com.asdk.tools.wpstatussaver.model.SaveLocation.PICTURES.name)
        return try {
            com.asdk.tools.wpstatussaver.model.SaveLocation.valueOf(saved ?: com.asdk.tools.wpstatussaver.model.SaveLocation.PICTURES.name)
        } catch (e: Exception) {
            com.asdk.tools.wpstatussaver.model.SaveLocation.PICTURES
        }
    }

    fun setDefaultSaveLocation(context: Context, location: com.asdk.tools.wpstatussaver.model.SaveLocation) {
        getPrefs(context).edit().putString(KEY_DEFAULT_SAVE_LOCATION, location.name).apply()
    }

    fun getSortOrder(context: Context): com.asdk.tools.wpstatussaver.model.SortOrder {
        val saved = getPrefs(context).getString(KEY_SORT_ORDER, com.asdk.tools.wpstatussaver.model.SortOrder.NEWEST_FIRST.name)
        return try {
            com.asdk.tools.wpstatussaver.model.SortOrder.valueOf(saved ?: com.asdk.tools.wpstatussaver.model.SortOrder.NEWEST_FIRST.name)
        } catch (e: Exception) {
            com.asdk.tools.wpstatussaver.model.SortOrder.NEWEST_FIRST
        }
    }

    fun setSortOrder(context: Context, sortOrder: com.asdk.tools.wpstatussaver.model.SortOrder) {
        getPrefs(context).edit().putString(KEY_SORT_ORDER, sortOrder.name).apply()
    }

    fun getStatusFilter(context: Context): com.asdk.tools.wpstatussaver.model.StatusFilter {
        val saved = getPrefs(context).getString(KEY_STATUS_FILTER, com.asdk.tools.wpstatussaver.model.StatusFilter.ALL.name)
        return try {
            com.asdk.tools.wpstatussaver.model.StatusFilter.valueOf(saved ?: com.asdk.tools.wpstatussaver.model.StatusFilter.ALL.name)
        } catch (e: Exception) {
            com.asdk.tools.wpstatussaver.model.StatusFilter.ALL
        }
    }

    fun setStatusFilter(context: Context, filter: com.asdk.tools.wpstatussaver.model.StatusFilter) {
        getPrefs(context).edit().putString(KEY_STATUS_FILTER, filter.name).apply()
    }

    fun isAutoRefresh(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_REFRESH, true)
    }

    fun setAutoRefresh(context: Context, autoRefresh: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_REFRESH, autoRefresh).apply()
    }
}
