package com.asdk.tools.wpstatussaver

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.asdk.tools.wpstatussaver.adapter.MainPagerAdapter
import com.asdk.tools.wpstatussaver.adapter.StatusAdapter
import com.asdk.tools.wpstatussaver.databinding.ActivityMainBinding
import com.asdk.tools.wpstatussaver.model.AppType
import com.asdk.tools.wpstatussaver.model.SortOrder
import com.asdk.tools.wpstatussaver.model.StatusFilter
import com.asdk.tools.wpstatussaver.model.StatusMedia
import com.asdk.tools.wpstatussaver.ui.SavedListFragment
import com.asdk.tools.wpstatussaver.ui.StatusListFragment
import com.asdk.tools.wpstatussaver.util.SettingsManager
import com.asdk.tools.wpstatussaver.util.StorageHelper
import com.asdk.tools.wpstatussaver.util.WhatsAppLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MainPagerAdapter

    var selectedAppType: AppType = AppType.WHATSAPP
        private set

    private var isSelectionActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply persisted user theme (Auto/Light/Dark)
        SettingsManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setupViewPager()
        setupHeaderActions()
        setupSelectionActions()

        // Handle system back button to exit multi-select if active
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionActive) {
                    exitActiveSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateAppSourceVisibility()
        if (SettingsManager.isAutoRefresh(this)) {
            reloadAllFragments()
        }
    }

    private fun setupViewPager() {
        pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                MainPagerAdapter.TAB_PHOTOS -> {
                    tab.setText(R.string.tab_images)
                    tab.setIcon(R.drawable.tab_ic_photo)
                }
                MainPagerAdapter.TAB_VIDEOS -> {
                    tab.setText(R.string.tab_videos)
                    tab.setIcon(R.drawable.tab_ic_video)
                }
                MainPagerAdapter.TAB_SAVED -> {
                    tab.setText(R.string.tab_saved)
                    tab.setIcon(R.drawable.tab_ic_saved)
                }
            }
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.viewPager.post {
                    val adapter = getActiveAdapter()
                    val count = adapter?.getSelectedCount() ?: 0
                    val isSavedTab = position == MainPagerAdapter.TAB_SAVED
                    updateSelectionBar(count, isSavedTab)
                }
            }
        })
    }

    private fun updateAppSourceVisibility() {
        val installedApps = WhatsAppLauncher.getInstalledWhatsAppApps(this)
        when {
            installedApps.size > 1 -> {
                binding.btnAppSource.visibility = View.VISIBLE
                binding.btnAppSource.text = selectedAppType.title
            }
            installedApps.size == 1 -> {
                selectedAppType = installedApps.first()
                binding.btnAppSource.text = selectedAppType.title
                binding.btnAppSource.visibility = View.GONE
            }
            else -> {
                binding.btnAppSource.visibility = View.GONE
            }
        }
    }

    private fun setupHeaderActions() {
        updateAppSourceVisibility()

        // App name click triggers Help / Usage Dialog
        binding.tvAppTitle.setOnClickListener {
            showHelpDialog()
        }

        // WhatsApp / WA Business switcher button
        binding.btnAppSource.setOnClickListener { view ->
            val installed = WhatsAppLauncher.getInstalledWhatsAppApps(this)
            val popup = PopupMenu(this, view)
            for (app in installed) {
                popup.menu.add(0, if (app == AppType.WHATSAPP) 1 else 2, 0, app.title)
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        selectedAppType = AppType.WHATSAPP
                        binding.btnAppSource.text = getString(R.string.source_whatsapp)
                        reloadAllFragments()
                        true
                    }
                    2 -> {
                        selectedAppType = AppType.WHATSAPP_BUSINESS
                        binding.btnAppSource.text = getString(R.string.source_wa_business)
                        reloadAllFragments()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // Long press tooltips for normal appbar
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.tvAppTitle, "About & Help")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnAppSource, "Switch WhatsApp source")
    }

    private var isBarInSelectionState = false
    private var colorAnimator: android.animation.ValueAnimator? = null

    private fun setupSelectionActions() {
        binding.btnCloseSelection.setOnClickListener {
            exitActiveSelectionMode()
        }

        binding.btnSelectAll.setOnClickListener {
            selectAllInActiveFragment()
        }

        binding.tvSelectedCount.setOnClickListener {
            selectAllInActiveFragment()
        }

        binding.btnBatchSave.setOnClickListener {
            batchSaveSelected()
        }

        binding.btnBatchShare.setOnClickListener {
            batchShareSelected()
        }

        binding.btnBatchDelete.setOnClickListener {
            batchDeleteSelected()
        }

        // Long press tooltips for selection appbar
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnCloseSelection, "Close selection")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.tvSelectedCount, "Select all")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnSelectAll, "Select all")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnBatchSave, "Save selected")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnBatchShare, "Share selected")
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnBatchDelete, "Delete selected")
    }

    fun updateSelectionBar(count: Int, isSavedTab: Boolean) {
        if (count > 0) {
            isSelectionActive = true
            binding.tvSelectedCount.text = "$count selected"
            binding.btnBatchDelete.visibility = if (isSavedTab) View.VISIBLE else View.GONE
            binding.btnBatchSave.visibility = if (isSavedTab) View.GONE else View.VISIBLE

            if (!isBarInSelectionState) {
                isBarInSelectionState = true
                animateSelectionBarTransition(entering = true)
            }
        } else {
            isSelectionActive = false
            if (isBarInSelectionState) {
                isBarInSelectionState = false
                animateSelectionBarTransition(entering = false)
            }
        }
    }

    private fun animateSelectionBarTransition(entering: Boolean) {
        colorAnimator?.cancel()

        val colorPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.whatsapp_primary)
        val colorPrimaryDark = androidx.core.content.ContextCompat.getColor(this, R.color.whatsapp_primary_dark)

        val colorFrom = if (entering) colorPrimary else colorPrimaryDark
        val colorTo = if (entering) colorPrimaryDark else colorPrimary

        colorAnimator = android.animation.ValueAnimator.ofArgb(colorFrom, colorTo).apply {
            duration = 220
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                binding.appBarLayout.setBackgroundColor(color)
                window.statusBarColor = color
            }
            start()
        }

        if (entering) {
            binding.toolbar.animate().alpha(0f).setDuration(180).withEndAction {
                binding.toolbar.visibility = View.GONE
            }
            binding.selectionToolbar.apply {
                alpha = 0f
                visibility = View.VISIBLE
            }.animate().alpha(1f).setDuration(220)
        } else {
            binding.selectionToolbar.animate().alpha(0f).setDuration(180).withEndAction {
                binding.selectionToolbar.visibility = View.GONE
            }
            binding.toolbar.apply {
                alpha = 0f
                visibility = View.VISIBLE
            }.animate().alpha(1f).setDuration(220)
        }
    }

    private fun getActiveAdapter(): StatusAdapter? {
        val currentFrag = supportFragmentManager.findFragmentByTag("f" + binding.viewPager.currentItem)
        return when (currentFrag) {
            is StatusListFragment -> currentFrag.getAdapter()
            is SavedListFragment -> currentFrag.getAdapter()
            else -> null
        }
    }

    private fun getActiveItems(): List<StatusMedia> {
        val currentFrag = supportFragmentManager.findFragmentByTag("f" + binding.viewPager.currentItem)
        return when (currentFrag) {
            is StatusListFragment -> currentFrag.getCurrentItems()
            is SavedListFragment -> currentFrag.getCurrentItems()
            else -> emptyList()
        }
    }

    private fun selectAllInActiveFragment() {
        val adapter = getActiveAdapter() ?: return
        val items = getActiveItems()
        adapter.selectAll(items)
    }

    private fun exitActiveSelectionMode() {
        for (fragment in supportFragmentManager.fragments) {
            when (fragment) {
                is StatusListFragment -> fragment.getAdapter().exitSelectionMode()
                is SavedListFragment -> fragment.getAdapter().exitSelectionMode()
            }
        }
        updateSelectionBar(0, false)
    }

    private var progressDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showLoadingDialog(message: String) {
        dismissLoadingDialog()
        val view = layoutInflater.inflate(R.layout.dialog_loading, null)
        view.findViewById<android.widget.TextView>(R.id.tvLoadingMessage).text = message
        progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .show()
    }

    private fun dismissLoadingDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun batchSaveSelected() {
        val adapter = getActiveAdapter() ?: return
        val selected = adapter.getSelectedList()
        if (selected.isEmpty()) return

        val unsaved = selected.filter { !it.isSaved }
        if (unsaved.isEmpty()) {
            Toast.makeText(this, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
            exitActiveSelectionMode()
            return
        }

        StorageHelper.requestSaveWithLocationChoice(this, unsaved.first()) { location ->
            showLoadingDialog("Saving ${unsaved.size} items...")
            lifecycleScope.launch {
                val savedCount = StorageHelper.saveMultipleMedia(this@MainActivity, unsaved, location)
                dismissLoadingDialog()
                Toast.makeText(this@MainActivity, "${getString(R.string.saved_success)} ($savedCount)", Toast.LENGTH_SHORT).show()
                exitActiveSelectionMode()
                reloadAllFragments(showLoading = false)
            }
        }
    }

    private fun batchShareSelected() {
        val adapter = getActiveAdapter() ?: return
        val selected = adapter.getSelectedList()
        if (selected.isEmpty()) return
        showLoadingDialog("Preparing items to share...")
        WhatsAppLauncher.shareMultipleStatuses(this, lifecycleScope, selected)
        binding.root.postDelayed({
            dismissLoadingDialog()
            exitActiveSelectionMode()
        }, 600)
    }

    private fun batchDeleteSelected() {
        val adapter = getActiveAdapter() ?: return
        val selected = adapter.getSelectedList()
        if (selected.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage("Delete ${selected.size} selected items?")
            .setPositiveButton(R.string.action_delete) { _, _ ->
                showLoadingDialog("Deleting ${selected.size} items...")
                lifecycleScope.launch {
                    val deletedCount = StorageHelper.deleteMultipleSavedMedia(this@MainActivity, selected)
                    dismissLoadingDialog()
                    Toast.makeText(this@MainActivity, "${getString(R.string.deleted_success)} ($deletedCount)", Toast.LENGTH_SHORT).show()
                    exitActiveSelectionMode()
                    reloadAllFragments(showLoading = false)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                reloadAllFragments(showLoading = true)
                Toast.makeText(this, getString(R.string.action_refresh), Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_filter -> {
                showSortAndFilterDialog()
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortAndFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sort_and_filter, null)
        val spinnerSort = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerSort)
        val spinnerFilter = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerFilter)

        val sortOptions = SortOrder.values().map { it.displayName }
        val filterOptions = StatusFilter.values().map { it.displayName }

        val sortAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortOptions)
        val filterAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterOptions)

        spinnerSort.adapter = sortAdapter
        spinnerFilter.adapter = filterAdapter

        val currentSort = SettingsManager.getSortOrder(this)
        val currentFilter = SettingsManager.getStatusFilter(this)

        spinnerSort.setSelection(currentSort.ordinal)
        spinnerFilter.setSelection(currentFilter.ordinal)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_sort_filter)
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val selectedSort = SortOrder.values().getOrNull(spinnerSort.selectedItemPosition) ?: currentSort
                val selectedFilter = StatusFilter.values().getOrNull(spinnerFilter.selectedItemPosition) ?: currentFilter
                SettingsManager.setSortOrder(this, selectedSort)
                SettingsManager.setStatusFilter(this, selectedFilter)
                reloadAllFragments(showLoading = false)
            }
            .setNeutralButton("Reset") { _, _ ->
                SettingsManager.setSortOrder(this, SortOrder.NEWEST_FIRST)
                SettingsManager.setStatusFilter(this, StatusFilter.ALL)
                reloadAllFragments(showLoading = false)
                Toast.makeText(this, "Filters reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun reloadAllFragments(showLoading: Boolean = false) {
        for (fragment in supportFragmentManager.fragments) {
            when (fragment) {
                is StatusListFragment -> fragment.loadStatuses(showLoading = showLoading)
                is SavedListFragment -> fragment.loadSavedList(showLoading = showLoading)
            }
        }
    }

    private fun showHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_content)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(
                "WP Status Saver\n\n" +
                "Developed by theonlyasdk\n" +
                "© 2026 theonlyasdk\n\n" +
                "Licensed under the MIT License\n\n" +
                "Features:\n" +
                "• Multi-select & Batch save / share / delete\n" +
                "• Sort & filter (Newest, Oldest, Saved, Unsaved)\n" +
                "• Auto-refresh on app return\n" +
                "• Storage Access Framework & MediaStore\n" +
                "• WhatsApp & WA Business support"
            )
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}