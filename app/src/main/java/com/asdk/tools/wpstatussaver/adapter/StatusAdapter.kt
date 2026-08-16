package com.asdk.tools.wpstatussaver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.asdk.tools.wpstatussaver.R
import com.asdk.tools.wpstatussaver.databinding.ItemStatusCardBinding
import com.asdk.tools.wpstatussaver.model.StatusMedia
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class StatusAdapter(
    val isSavedTab: Boolean = false,
    private val onItemClick: (StatusMedia, View) -> Unit,
    private val onSaveClick: (StatusMedia, Int) -> Unit,
    private val onShareClick: (StatusMedia) -> Unit,
    private val onDeleteClick: ((StatusMedia, Int) -> Unit)? = null,
    var onItemLongClick: ((StatusMedia, Int) -> Unit)? = null,
    var onSelectionChanged: ((Int) -> Unit)? = null
) : ListAdapter<StatusMedia, StatusAdapter.StatusViewHolder>(DIFF_CALLBACK) {

    var isSelectionMode: Boolean = false
        private set

    val selectedItems: LinkedHashSet<StatusMedia> = LinkedHashSet()

    fun enterSelectionMode(initialItem: StatusMedia? = null) {
        isSelectionMode = true
        selectedItems.clear()
        if (initialItem != null) {
            selectedItems.add(initialItem)
        }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke(selectedItems.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke(0)
    }

    fun toggleSelection(status: StatusMedia, position: Int) {
        if (selectedItems.contains(status)) {
            selectedItems.remove(status)
        } else {
            selectedItems.add(status)
        }
        notifyItemChanged(position, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke(selectedItems.size)
        if (selectedItems.isEmpty()) {
            exitSelectionMode()
        }
    }

    fun selectAll(items: List<StatusMedia>) {
        if (!isSelectionMode) isSelectionMode = true
        selectedItems.clear()
        selectedItems.addAll(items)
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke(selectedItems.size)
    }

    fun getSelectedList(): List<StatusMedia> = selectedItems.toList()

    fun getSelectedCount(): Int = selectedItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val binding = ItemStatusCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelectionOnly(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class StatusViewHolder(
        val binding: ItemStatusCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bindSelectionOnly(status: StatusMedia) {
            val context = itemView.context
            val isSelected = selectedItems.contains(status)

            val showBadge = com.asdk.tools.wpstatussaver.util.SettingsManager.isShowVideoBadge(context)
            binding.layoutVideoBadge.visibility = if (status.isVideo && showBadge && !isSelectionMode) View.VISIBLE else View.GONE

            if (isSelectionMode) {
                binding.layoutCardActions.visibility = View.GONE

                binding.viewSelectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
                binding.ivCheckSelected.visibility = View.VISIBLE
                binding.ivCheckSelected.background = null

                if (isSelected) {
                    binding.ivCheckSelected.setImageResource(R.drawable.ic_check_circle)
                    binding.ivCheckSelected.setColorFilter(ContextCompat.getColor(context, R.color.white))
                } else {
                    binding.ivCheckSelected.setImageResource(R.drawable.ic_circle_outline)
                    binding.ivCheckSelected.setColorFilter(ContextCompat.getColor(context, R.color.white))
                }
            } else {
                binding.layoutCardActions.visibility = View.VISIBLE
                binding.viewSelectedOverlay.visibility = View.GONE
                binding.ivCheckSelected.visibility = View.GONE

                if (isSavedTab) {
                    binding.btnSave.visibility = View.GONE
                    binding.btnDelete.visibility = View.VISIBLE
                } else {
                    binding.btnDelete.visibility = View.GONE
                    binding.btnSave.visibility = View.VISIBLE

                    if (status.isSaved) {
                        binding.btnSave.setImageResource(R.drawable.ic_check_circle)
                        binding.btnSave.setColorFilter(
                            ContextCompat.getColor(context, R.color.saved_badge)
                        )
                    } else {
                        binding.btnSave.setImageResource(R.drawable.ic_file_download)
                        binding.btnSave.setColorFilter(
                            ContextCompat.getColor(context, R.color.white)
                        )
                    }
                }
                binding.btnShare.visibility = View.VISIBLE
            }
        }

        fun fadeOutCardControls() {
            binding.layoutCardActions.animate().alpha(0f).setDuration(120).start()
            binding.layoutVideoBadge.animate().alpha(0f).setDuration(120).start()
            binding.viewCardGradient.animate().alpha(0f).setDuration(120).start()
        }

        fun fadeInCardControls() {
            binding.layoutCardActions.animate().alpha(1f).setDuration(220).start()
            binding.layoutVideoBadge.animate().alpha(1f).setDuration(220).start()
            binding.viewCardGradient.animate().alpha(1f).setDuration(220).start()
        }

        fun bind(status: StatusMedia) {
            val context = itemView.context

            binding.layoutCardActions.alpha = 1f
            binding.layoutVideoBadge.alpha = 1f
            binding.viewCardGradient.alpha = 1f
            ViewCompat.setTransitionName(binding.ivThumbnail, null)

            // Load media thumbnail via Glide with smooth fade-in
            Glide.with(context)
                .load(status.uri)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(250))
                .into(binding.ivThumbnail)

            bindSelectionOnly(status)

            // Click listeners
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    val willBeSelected = !selectedItems.contains(status)
                    com.asdk.tools.wpstatussaver.util.HapticHelper.selection(binding.root, willBeSelected)
                    toggleSelection(status, bindingAdapterPosition)
                } else {
                    ViewCompat.setTransitionName(binding.ivThumbnail, "transition_media")
                    fadeOutCardControls()
                    onItemClick(status, binding.ivThumbnail)
                }
            }

            binding.root.setOnLongClickListener {
                com.asdk.tools.wpstatussaver.util.HapticHelper.longPress(binding.root)
                if (!isSelectionMode) {
                    enterSelectionMode(status)
                    onItemLongClick?.invoke(status, bindingAdapterPosition)
                } else {
                    val willBeSelected = !selectedItems.contains(status)
                    com.asdk.tools.wpstatussaver.util.HapticHelper.selection(binding.root, willBeSelected)
                    toggleSelection(status, bindingAdapterPosition)
                }
                true
            }

            binding.btnSave.setOnClickListener {
                com.asdk.tools.wpstatussaver.util.HapticHelper.success(binding.btnSave)
                onSaveClick(status, bindingAdapterPosition)
            }

            binding.btnShare.setOnClickListener {
                com.asdk.tools.wpstatussaver.util.HapticHelper.click(binding.btnShare)
                onShareClick(status)
            }

            binding.btnDelete.setOnClickListener {
                com.asdk.tools.wpstatussaver.util.HapticHelper.click(binding.btnDelete)
                onDeleteClick?.invoke(status, bindingAdapterPosition)
            }
        }
    }

    companion object {
        const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<StatusMedia>() {
            override fun areItemsTheSame(oldItem: StatusMedia, newItem: StatusMedia): Boolean {
                return oldItem.path == newItem.path || oldItem.uriString == newItem.uriString
            }

            override fun areContentsTheSame(oldItem: StatusMedia, newItem: StatusMedia): Boolean {
                return oldItem == newItem
            }
        }
    }
}
