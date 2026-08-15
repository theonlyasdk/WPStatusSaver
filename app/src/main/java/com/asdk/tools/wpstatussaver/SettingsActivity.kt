package com.asdk.tools.wpstatussaver

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.asdk.tools.wpstatussaver.databinding.ActivitySettingsBinding
import com.asdk.tools.wpstatussaver.model.AppType
import com.asdk.tools.wpstatussaver.util.SettingsManager
import com.asdk.tools.wpstatussaver.util.StorageHelper
import com.asdk.tools.wpstatussaver.util.WhatsAppLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupSettingsViews()
    }

    private fun setupSettingsViews() {
        updateSummaries()

        // 1. Theme Setting
        binding.itemTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        // 2. Grid Columns Setting
        binding.itemGridColumns.setOnClickListener {
            showGridSelectionDialog()
        }

        // 4. Default Source Setting
        binding.itemDefaultSource.setOnClickListener {
            showSourceSelectionDialog()
        }

        // 5. Ask where to save Switch
        binding.switchAskSaveLocation.isChecked = SettingsManager.isAskSaveLocation(this)
        binding.switchAskSaveLocation.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAskSaveLocation(this, isChecked)
        }

        // 6. Default Save Location Picker
        binding.itemDefaultSaveLocation.setOnClickListener {
            showDefaultLocationDialog()
        }

        // 7. Storage Location Info
        binding.itemStorageLocation.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pref_storage_loc_title)
                .setMessage(
                    "All saved images and videos are stored in your device's public media storage:\n\n" +
                    "• Photos: Pictures/WPStatusSaver\n" +
                    "• DCIM: DCIM/WPStatusSaver\n" +
                    "• Downloads: Download/WPStatusSaver\n\n" +
                    "They will immediately appear in your Gallery and Photos apps."
                )
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        // 8. Reset SAF Folder Permissions
        binding.itemResetSaf.setOnClickListener {
            showResetSafConfirmDialog()
        }

        // 9. Auto-play Videos Switch
        binding.switchAutoPlay.isChecked = SettingsManager.isAutoPlayVideo(this)
        binding.switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoPlayVideo(this, isChecked)
        }

        // 10. Show Video Badges Switch
        binding.switchVideoBadge.isChecked = SettingsManager.isShowVideoBadge(this)
        binding.switchVideoBadge.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setShowVideoBadge(this, isChecked)
        }

        // 11. How to Use
        binding.itemHelp.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_content)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        // 12. About & License
        binding.itemAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(
                    "WP Status Saver v1.0\n\n" +
                    "Developed by theonlyasdk\n" +
                    "© 2026 theonlyasdk\n\n" +
                    "Licensed under the MIT License\n\n" +
                    "A fast, lightweight status saver with Material 1 & 2 UI styles, compatible with Android 6.0 and newer versions."
                )
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun updateSummaries() {
        // Theme summary
        val themeText = when (SettingsManager.getTheme(this)) {
            SettingsManager.THEME_LIGHT -> getString(R.string.pref_theme_light)
            SettingsManager.THEME_DARK -> getString(R.string.pref_theme_dark)
            else -> getString(R.string.pref_theme_system)
        }
        binding.tvThemeSummary.text = themeText

        // Grid summary
        val columns = SettingsManager.getGridColumns(this)
        binding.tvGridSummary.text = if (columns == 3) getString(R.string.pref_grid_3) else getString(R.string.pref_grid_2)

        // Source summary
        binding.tvSourceSummary.text = SettingsManager.getDefaultSource(this).title
        binding.tvDefaultLocationSummary.text = SettingsManager.getDefaultSaveLocation(this).displayName
    }

    private fun showDefaultLocationDialog() {
        val locations = com.asdk.tools.wpstatussaver.model.SaveLocation.values()
        val names = locations.map { it.displayName }.toTypedArray()
        val current = SettingsManager.getDefaultSaveLocation(this)
        val selectedIdx = locations.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pref_default_loc_title)
            .setSingleChoiceItems(names, selectedIdx) { dialog, which ->
                SettingsManager.setDefaultSaveLocation(this, locations[which])
                updateSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf(
            getString(R.string.pref_theme_system),
            getString(R.string.pref_theme_light),
            getString(R.string.pref_theme_dark)
        )

        val currentSelection = when (SettingsManager.getTheme(this)) {
            SettingsManager.THEME_LIGHT -> 1
            SettingsManager.THEME_DARK -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pref_theme_title)
            .setSingleChoiceItems(themes, currentSelection) { dialog, which ->
                val newTheme = when (which) {
                    1 -> SettingsManager.THEME_LIGHT
                    2 -> SettingsManager.THEME_DARK
                    else -> SettingsManager.THEME_SYSTEM
                }
                SettingsManager.setTheme(this, newTheme)
                updateSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showGridSelectionDialog() {
        val options = arrayOf(
            getString(R.string.pref_grid_2),
            getString(R.string.pref_grid_3)
        )

        val currentSelection = if (SettingsManager.getGridColumns(this) == 3) 1 else 0

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pref_grid_title)
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                SettingsManager.setGridColumns(this, if (which == 1) 3 else 2)
                updateSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSourceSelectionDialog() {
        val installed = WhatsAppLauncher.getInstalledWhatsAppApps(this)
        val appList = if (installed.isNotEmpty()) installed else listOf(AppType.WHATSAPP, AppType.WHATSAPP_BUSINESS)
        val names = appList.map { it.title }.toTypedArray()
        val currentSource = SettingsManager.getDefaultSource(this)
        val currentIdx = appList.indexOf(currentSource).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pref_source_title)
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                val chosen = appList[which]
                SettingsManager.setDefaultSource(this, chosen)
                updateSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showResetSafConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pref_reset_saf_title)
            .setMessage("Are you sure you want to reset granted folder permissions? You will be prompted to grant folder access again.")
            .setPositiveButton("Reset") { _, _ ->
                StorageHelper.clearSafTreeUri(this, AppType.WHATSAPP)
                StorageHelper.clearSafTreeUri(this, AppType.WHATSAPP_BUSINESS)
                Toast.makeText(this, getString(R.string.settings_saf_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
