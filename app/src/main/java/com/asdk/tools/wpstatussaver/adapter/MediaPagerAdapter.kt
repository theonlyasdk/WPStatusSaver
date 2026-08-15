package com.asdk.tools.wpstatussaver.adapter

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.asdk.tools.wpstatussaver.R
import com.asdk.tools.wpstatussaver.databinding.ItemMediaPagerBinding
import com.asdk.tools.wpstatussaver.model.StatusMedia
import com.asdk.tools.wpstatussaver.util.SettingsManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.util.Locale
import java.util.concurrent.TimeUnit

class MediaPagerAdapter(
    private val items: List<StatusMedia>,
    private val initialPosition: Int,
    private val onMediaReady: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onToggleBars: () -> Unit,
    private val onZoomChanged: (Boolean) -> Unit,
    private val getViewsToFade: () -> List<View>
) : RecyclerView.Adapter<MediaPagerAdapter.MediaViewHolder>() {

    private val activeHolders = mutableMapOf<Int, MediaViewHolder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaPagerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position], position)
        activeHolders[position] = holder
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            activeHolders.remove(pos)
        }
        holder.recycle()
    }

    fun getViewHolderAt(position: Int): MediaViewHolder? = activeHolders[position]

    fun pauseAllVideos() {
        for (holder in activeHolders.values) {
            holder.pauseVideo()
        }
    }

    fun onPageSelected(position: Int) {
        for ((pos, holder) in activeHolders) {
            val target = if (holder.currentStatus?.isVideo == true) holder.binding.layoutVideoPlayer else holder.binding.ivFullImage
            if (pos != position) {
                holder.pauseVideo()
                ViewCompat.setTransitionName(target, null)
            } else {
                holder.onPageActivated()
                ViewCompat.setTransitionName(target, "transition_media")
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class MediaViewHolder(val binding: ItemMediaPagerBinding) : RecyclerView.ViewHolder(binding.root) {

        private val handler = Handler(Looper.getMainLooper())
        private var isUserSeeking = false
        var currentStatus: StatusMedia? = null
            private set

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

        fun bind(status: StatusMedia, position: Int) {
            currentStatus = status

            binding.swipeDismissLayout.backgroundView = binding.containerLayout
            binding.swipeDismissLayout.viewsToFade = getViewsToFade()
            binding.swipeDismissLayout.isZoomedPredicate = {
                if (status.isVideo) false else binding.ivFullImage.scale > 1.05f
            }
            binding.swipeDismissLayout.onDismiss = onDismiss

            // Set transitionName ONLY on the item matching the requested transition position
            val targetSharedView = if (status.isVideo) binding.layoutVideoPlayer else binding.ivFullImage
            val otherView = if (status.isVideo) binding.ivFullImage else binding.layoutVideoPlayer
            ViewCompat.setTransitionName(otherView, null)

            if (position == initialPosition) {
                ViewCompat.setTransitionName(targetSharedView, "transition_media")
            } else {
                ViewCompat.setTransitionName(targetSharedView, null)
            }

            if (status.isVideo) {
                binding.ivFullImage.visibility = View.GONE
                binding.layoutVideoPlayer.visibility = View.VISIBLE
                binding.previewProgressBar.visibility = View.VISIBLE
                setupVideo(status.uri, position)
            } else {
                binding.layoutVideoPlayer.visibility = View.GONE
                binding.ivFullImage.visibility = View.VISIBLE
                binding.previewProgressBar.visibility = View.VISIBLE
                setupImage(status.uri, position)
            }
        }

        private fun setupImage(uri: Uri, position: Int) {
            binding.ivFullImage.setOnScaleChangeListener { _, _, _ ->
                val isZoomed = binding.ivFullImage.scale > 1.05f
                onZoomChanged(isZoomed)
            }

            binding.ivFullImage.setOnPhotoTapListener { _, _, _ ->
                onToggleBars()
            }
            binding.ivFullImage.setOnOutsidePhotoTapListener {
                onToggleBars()
            }

            Glide.with(itemView.context)
                .load(uri)
                .fitCenter()
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.previewProgressBar.visibility = View.GONE
                        if (position == initialPosition) {
                            onMediaReady()
                        }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.previewProgressBar.visibility = View.GONE
                        if (position == initialPosition) {
                            onMediaReady()
                        }
                        return false
                    }
                })
                .into(binding.ivFullImage)
        }

        private fun setupVideo(uri: Uri, position: Int) {
            binding.videoView.setVideoURI(uri)

            binding.videoView.setOnPreparedListener { mediaPlayer ->
                binding.previewProgressBar.visibility = View.GONE
                if (position == initialPosition) {
                    onMediaReady()
                }
                mediaPlayer.isLooping = false
                val duration = mediaPlayer.duration
                binding.videoSeekBar.max = duration
                binding.tvTotalDuration.text = formatDuration(duration.toLong())
                binding.tvCurrentDuration.text = formatDuration(0)

                if (position == initialPosition && SettingsManager.isAutoPlayVideo(itemView.context)) {
                    startVideo()
                } else {
                    binding.btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
                }
            }

            binding.videoView.setOnCompletionListener {
                binding.btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
                binding.videoSeekBar.progress = binding.videoSeekBar.max
                handler.removeCallbacks(updateProgressRunnable)
            }

            binding.btnPlayPause.setOnClickListener {
                togglePlayPause()
            }

            binding.viewCenterPlayToggle.setOnClickListener {
                togglePlayPause()
            }

            binding.layoutVideoPlayer.setOnClickListener {
                onToggleBars()
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

        fun onPageActivated() {
            if (currentStatus?.isVideo == true && SettingsManager.isAutoPlayVideo(itemView.context)) {
                startVideo()
            }
        }

        fun togglePlayPause() {
            com.asdk.tools.wpstatussaver.util.HapticHelper.click(binding.btnPlayPause)
            if (binding.videoView.isPlaying) {
                pauseVideo()
                showCenterIndicator(isPlaying = false)
            } else {
                startVideo()
                showCenterIndicator(isPlaying = true)
            }
        }

        fun startVideo() {
            if (binding.videoSeekBar.progress >= binding.videoSeekBar.max && binding.videoSeekBar.max > 0) {
                binding.videoView.seekTo(0)
            }
            binding.videoView.start()
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            handler.post(updateProgressRunnable)
        }

        fun pauseVideo() {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
            }
            binding.btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
            handler.removeCallbacks(updateProgressRunnable)
        }

        private fun showCenterIndicator(isPlaying: Boolean) {
            val iconRes = if (isPlaying) R.drawable.ic_play_arrow else R.drawable.ic_pause
            binding.ivCenterPlayIndicator.setImageResource(iconRes)
            binding.ivCenterPlayIndicator.animate().cancel()
            binding.ivCenterPlayIndicator.scaleX = 1f
            binding.ivCenterPlayIndicator.scaleY = 1f
            binding.ivCenterPlayIndicator.alpha = 1f
            binding.ivCenterPlayIndicator.visibility = View.VISIBLE

            binding.ivCenterPlayIndicator.animate()
                .alpha(0f)
                .setStartDelay(400)
                .setDuration(250)
                .withEndAction {
                    binding.ivCenterPlayIndicator.visibility = View.GONE
                }
                .start()
        }

        fun recycle() {
            handler.removeCallbacks(updateProgressRunnable)
            binding.videoView.stopPlayback()
            binding.ivFullImage.setImageDrawable(null)
        }

        private fun formatDuration(millis: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
