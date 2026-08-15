package com.asdk.tools.wpstatussaver

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asdk.tools.wpstatussaver.databinding.ActivityMediaViewBinding
import com.asdk.tools.wpstatussaver.model.StatusMedia
import com.asdk.tools.wpstatussaver.util.StorageHelper
import com.asdk.tools.wpstatussaver.util.WhatsAppLauncher
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class MediaViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewBinding
    private var statusMedia: StatusMedia? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (binding.videoView.isPlaying && !isUserSeeking) {
                val current = binding.videoView.currentPosition
                binding.videoSeekBar.progress = current
                binding.tvCurrentDuration.text = formatDuration(current.toLong())
            }
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statusMedia = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_STATUS_MEDIA, StatusMedia::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_STATUS_MEDIA) as? StatusMedia
        }

        if (statusMedia == null) {
            Toast.makeText(this, "Media not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.mediaRootLayout) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingStart,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingEnd,
                (systemBars.bottom + (10 * resources.displayMetrics.density).toInt())
            )
            insets
        }

        setupUI()
        supportPostponeEnterTransition()
        setupMedia()
    }

    private fun setupUI() {
        val status = statusMedia ?: return

        binding.tvMediaTitle.text = status.title

        if (status.isSaved) {
            binding.btnDeleteMedia.visibility = View.VISIBLE
            binding.btnSaveMedia.visibility = View.GONE
        } else {
            binding.btnDeleteMedia.visibility = View.GONE
            binding.btnSaveMedia.visibility = View.VISIBLE
        }

        binding.toolbar.setNavigationOnClickListener {
            supportFinishAfterTransition()
        }

        val shareAction = View.OnClickListener {
            WhatsAppLauncher.shareStatus(this, lifecycleScope, status)
        }
        binding.btnTopShare.setOnClickListener(shareAction)
        binding.btnShareMedia.setOnClickListener(shareAction)

        binding.btnRepostMedia.setOnClickListener {
            WhatsAppLauncher.repostStatus(this, lifecycleScope, status)
        }

        binding.btnSaveMedia.setOnClickListener {
            saveMedia()
        }

        binding.btnDeleteMedia.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun setupMedia() {
        val status = statusMedia ?: return

        if (status.isVideo) {
            binding.ivFullImage.visibility = View.GONE
            binding.layoutVideoPlayer.visibility = View.VISIBLE
            binding.previewProgressBar.visibility = View.VISIBLE
            setupVideoPlayer(status.uri)
        } else {
            binding.layoutVideoPlayer.visibility = View.GONE
            binding.ivFullImage.visibility = View.VISIBLE
            binding.previewProgressBar.visibility = View.VISIBLE

            Glide.with(this)
                .load(status.uri)
                .fitCenter()
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.previewProgressBar.visibility = View.GONE
                        supportStartPostponedEnterTransition()
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.previewProgressBar.visibility = View.GONE
                        supportStartPostponedEnterTransition()
                        return false
                    }
                })
                .into(binding.ivFullImage)
        }

        binding.swipeDismissLayout.backgroundView = binding.mediaRootLayout
        binding.swipeDismissLayout.viewsToFade = listOf(binding.appBarLayout, binding.bottomBar)
        binding.swipeDismissLayout.isZoomedPredicate = {
            if (status.isVideo) false else binding.ivFullImage.scale > 1.05f
        }
        binding.swipeDismissLayout.onDismiss = {
            supportFinishAfterTransition()
        }
    }

    private fun setupVideoPlayer(uri: Uri) {
        binding.videoView.setVideoURI(uri)

        binding.videoView.setOnPreparedListener { mediaPlayer ->
            binding.previewProgressBar.visibility = View.GONE
            supportStartPostponedEnterTransition()
            mediaPlayer.isLooping = false
            val duration = mediaPlayer.duration
            binding.videoSeekBar.max = duration
            binding.tvTotalDuration.text = formatDuration(duration.toLong())
            binding.tvCurrentDuration.text = formatDuration(0)

            // Auto-start video if enabled in settings
            if (com.asdk.tools.wpstatussaver.util.SettingsManager.isAutoPlayVideo(this@MediaViewActivity)) {
                binding.videoView.start()
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                handler.post(updateProgressRunnable)
            } else {
                binding.btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
            }
        }

        binding.videoView.setOnCompletionListener {
            binding.btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
            binding.videoSeekBar.progress = binding.videoSeekBar.max
        }

        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        // Tapping the center area of the video toggles playback
        binding.viewCenterPlayToggle.setOnClickListener {
            togglePlayPause()
        }

        // Tapping outside the center area toggles toolbar & controls visibility
        binding.layoutVideoPlayer.setOnClickListener {
            toggleTopBottomBars()
        }

        binding.ivFullImage.setOnPhotoTapListener { _, _, _ ->
            toggleTopBottomBars()
        }
        binding.ivFullImage.setOnOutsidePhotoTapListener {
            toggleTopBottomBars()
        }

        binding.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.videoView.seekTo(progress)
                    binding.tvCurrentDuration.text = formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
            }
        })
    }

    private fun togglePlayPause() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            binding.btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
        } else {
            if (binding.videoSeekBar.progress >= binding.videoSeekBar.max && binding.videoSeekBar.max > 0) {
                binding.videoView.seekTo(0)
            }
            binding.videoView.start()
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            handler.post(updateProgressRunnable)
        }
    }

    private fun toggleTopBottomBars() {
        val isVisible = binding.appBarLayout.visibility == View.VISIBLE
        val newVisibility = if (isVisible) View.GONE else View.VISIBLE
        binding.appBarLayout.visibility = newVisibility
        binding.bottomBar.visibility = newVisibility
    }

    private fun saveMedia() {
        val status = statusMedia ?: return
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
        val status = statusMedia ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteMedia(status)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteMedia(status: StatusMedia) {
        lifecycleScope.launch {
            val success = StorageHelper.deleteSavedMedia(this@MediaViewActivity, status)
            if (success) {
                Toast.makeText(this@MediaViewActivity, getString(R.string.deleted_success), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onPause() {
        super.onPause()
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
        }
        handler.removeCallbacks(updateProgressRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.videoView.stopPlayback()
        handler.removeCallbacks(updateProgressRunnable)
    }

    companion object {
        const val EXTRA_STATUS_MEDIA = "extra_status_media"
    }
}
