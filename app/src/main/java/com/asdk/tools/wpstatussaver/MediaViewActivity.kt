package com.asdk.tools.wpstatussaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.asdk.tools.wpstatussaver.adapter.MediaPagerAdapter
import com.asdk.tools.wpstatussaver.databinding.ActivityMediaViewBinding
import com.asdk.tools.wpstatussaver.model.StatusMedia
import com.asdk.tools.wpstatussaver.util.StorageHelper
import com.asdk.tools.wpstatussaver.util.WhatsAppLauncher
import com.asdk.tools.wpstatussaver.util.WhatsAppPageTransformer
import kotlinx.coroutines.launch

class MediaViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewBinding
    private var mediaList: MutableList<StatusMedia> = mutableListOf()
    private var currentPosition: Int = 0
    private lateinit var pagerAdapter: MediaPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val passedList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_MEDIA_LIST, ArrayList::class.java) as? ArrayList<*>
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_MEDIA_LIST) as? ArrayList<*>
        }

        if (passedList != null) {
            mediaList = passedList.filterIsInstance<StatusMedia>().toMutableList()
        }

        if (mediaList.isEmpty()) {
            val singleStatus = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(EXTRA_STATUS_MEDIA, StatusMedia::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(EXTRA_STATUS_MEDIA) as? StatusMedia
            }
            if (singleStatus != null) {
                mediaList.add(singleStatus)
            }
        }

        currentPosition = intent.getIntExtra(EXTRA_INITIAL_POSITION, 0).coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))

        if (mediaList.isEmpty()) {
            Toast.makeText(this, "Media not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mediaRootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingStart,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingEnd,
                (systemBars.bottom + (10 * resources.displayMetrics.density).toInt())
            )
        setupViewPager()
        setupActions()
        setupSharedElementCallback()
        supportPostponeEnterTransition()
    }

    private fun setupSharedElementCallback() {
        setEnterSharedElementCallback(object : androidx.core.app.SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
                if (names != null && sharedElements != null) {
                    val holder = pagerAdapter.getViewHolderAt(currentPosition)
                    if (holder != null) {
                        val currentView = if (holder.currentStatus?.isVideo == true) {
                            holder.binding.layoutVideoPlayer
                        } else {
                            holder.binding.ivFullImage
                        }
                        sharedElements["transition_media"] = currentView
                    }
                }
            }
        })
    }

    private fun prepareFinishResult() {
        val data = android.content.Intent().apply {
            putExtra(EXTRA_CURRENT_POSITION, currentPosition)
        }
        setResult(RESULT_OK, data)
    }

    override fun finishAfterTransition() {
        prepareFinishResult()
        super.finishAfterTransition()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        prepareFinishResult()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun setupViewPager() {
        pagerAdapter = MediaPagerAdapter(
            items = mediaList,
            initialPosition = currentPosition,
            onMediaReady = {
                supportStartPostponedEnterTransition()
            },
            onDismiss = {
                supportFinishAfterTransition()
            },
            onToggleBars = {
                toggleTopBottomBars()
            },
            onZoomChanged = { isZoomed ->
                binding.viewPager.isUserInputEnabled = !isZoomed
            },
            getViewsToFade = {
                listOf(binding.appBarLayout, binding.bottomBar)
            }
        )

        binding.viewPager.apply {
            adapter = pagerAdapter
            offscreenPageLimit = 1
            setPageTransformer(androidx.viewpager2.widget.MarginPageTransformer((24 * resources.displayMetrics.density).toInt()))
            setCurrentItem(currentPosition, false)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPosition = position
                    pagerAdapter.onPageSelected(position)
                    updateCurrentItemUI()
                }
            })
        }

        updateCurrentItemUI()
    }

    private fun updateCurrentItemUI() {
        if (currentPosition !in mediaList.indices) return
        val current = mediaList[currentPosition]

        binding.tvMediaTitle.text = if (mediaList.size > 1) {
            "${currentPosition + 1} of ${mediaList.size}  •  ${current.title}"
        } else {
            current.title
        }

        if (current.isSaved) {
            binding.btnDeleteMedia.visibility = View.VISIBLE
            binding.btnSaveMedia.visibility = View.GONE
        } else {
            binding.btnDeleteMedia.visibility = View.GONE
            binding.btnSaveMedia.visibility = View.VISIBLE
            binding.btnSaveMedia.text = getString(R.string.action_save)
            binding.btnSaveMedia.setIconResource(R.drawable.ic_file_download)
            binding.btnSaveMedia.isEnabled = true
        }
    }

    private fun setupActions() {
        binding.toolbar.setNavigationOnClickListener {
            supportFinishAfterTransition()
        }

        val shareAction = View.OnClickListener {
            if (currentPosition in mediaList.indices) {
                WhatsAppLauncher.shareStatus(this, lifecycleScope, mediaList[currentPosition])
            }
        }
        binding.btnTopShare.setOnClickListener(shareAction)
        binding.btnShareMedia.setOnClickListener(shareAction)

        binding.btnRepostMedia.setOnClickListener {
            if (currentPosition in mediaList.indices) {
                WhatsAppLauncher.repostStatus(this, lifecycleScope, mediaList[currentPosition])
            }
        }

        binding.btnSaveMedia.setOnClickListener {
            saveCurrentMedia()
        }

        binding.btnDeleteMedia.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun toggleTopBottomBars() {
        val isVisible = binding.appBarLayout.visibility == View.VISIBLE
        val newVisibility = if (isVisible) View.GONE else View.VISIBLE
        binding.appBarLayout.visibility = newVisibility
        binding.bottomBar.visibility = newVisibility
    }

    private fun saveCurrentMedia() {
        if (currentPosition !in mediaList.indices) return
        val status = mediaList[currentPosition]

        StorageHelper.requestSaveWithLocationChoice(this, status) { location ->
            lifecycleScope.launch {
                val success = StorageHelper.saveMedia(this@MediaViewActivity, status, location)
                if (success) {
                    status.isSaved = true
                    binding.btnSaveMedia.text = getString(R.string.saved_success)
                    binding.btnSaveMedia.setIconResource(R.drawable.ic_check_circle)
                    binding.btnSaveMedia.isEnabled = false
                    Toast.makeText(this@MediaViewActivity, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MediaViewActivity, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteConfirmDialog() {
        if (currentPosition !in mediaList.indices) return
        val status = mediaList[currentPosition]

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteCurrentMedia(status)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteCurrentMedia(status: StatusMedia) {
        lifecycleScope.launch {
            val success = StorageHelper.deleteSavedMedia(this@MediaViewActivity, status)
            if (success) {
                Toast.makeText(this@MediaViewActivity, getString(R.string.deleted_success), Toast.LENGTH_SHORT).show()
                mediaList.removeAt(currentPosition)
                if (mediaList.isEmpty()) {
                    finish()
                } else {
                    currentPosition = currentPosition.coerceIn(0, mediaList.size - 1)
                    pagerAdapter.notifyDataSetChanged()
                    binding.viewPager.setCurrentItem(currentPosition, false)
                    updateCurrentItemUI()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pagerAdapter.pauseAllVideos()
    }

    companion object {
        const val EXTRA_STATUS_MEDIA = "extra_status_media"
        const val EXTRA_MEDIA_LIST = "extra_media_list"
        const val EXTRA_INITIAL_POSITION = "extra_initial_position"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
    }
}
